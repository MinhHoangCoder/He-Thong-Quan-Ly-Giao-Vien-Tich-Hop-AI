/* =====================================================================
   V35 — RÀ SOÁT DỮ LIỆU MỒ CÔI (đợt cuối của chuỗi ràng buộc toàn vẹn).

   Bối cảnh: Đợt 1–3 đã bịt mọi đường SINH RA mồ côi mới — xóa mềm cha
   khi còn con đang sống bị chặn ở tầng service (DeleteGuard). Nhưng
   những dòng mồ côi ĐÃ CÓ SẴN từ trước khi có chốt thì không ai đụng
   tới, và chúng vô hình: không câu query nghiệp vụ nào lọc theo cờ
   IsDeleted của bảng CHA, nên một lớp học trỏ vào trường đã xóa vẫn
   hiện đầy đủ ở mọi màn hình — chỉ là trỏ vào một cái tên đã biến mất.

   V35 KHÔNG XÓA GÌ CẢ. Cố ý.

   Vì sao chỉ báo cáo mà không tự dọn: mỗi cặp mồ côi có hai cách xử lý
   trái ngược nhau và chỉ con người mới chọn được. Một trường bị xóa
   nhầm mà còn 12 lớp đang học — đúng việc phải làm là KHÔI PHỤC trường,
   không phải xóa nốt 12 lớp. Một migration tự xóa theo cờ sẽ chọn
   phương án hủy diệt trong cả hai trường hợp, và làm việc đó âm thầm
   trên dữ liệu thật. Cùng lý do đã chốt luật RESTRICT thay vì CASCADE ở
   Đợt 1: không giao cho máy cái quyền dọn dẹp mà nó không hiểu hậu quả.

   Cái V35 giao lại cho người vận hành:

     1) OrphanScan — bảng nhật ký kết quả rà soát, mỗi lần quét ghi lại
        một ảnh chụp. Có lịch sử thì mới trả lời được câu quan trọng
        nhất: "số mồ côi có ĐANG TĂNG không?" — nếu tăng thì tức là còn
        một đường sinh mồ côi nào đó lọt qua chốt của Đợt 1–3.

     2) usp_ScanOrphanRows — thủ tục quét, chạy lại được bất cứ lúc nào.

   Vì sao thủ tục QUÉT ĐỘNG qua sys.foreign_keys thay vì viết tay 43 câu
   đếm: danh sách khóa ngoại còn đổi dài dài (riêng chuỗi V27→V34 đã
   thêm/bớt vài bảng). Một danh sách chép tay sẽ lệch khỏi schema đúng
   vào lúc không ai để ý, và lệch theo hướng nguy hiểm nhất — bỏ SÓT
   bảng mới mà vẫn báo "sạch". Quét động thì không có cách nào lệch.

   ĐỊNH NGHĨA "mồ côi" ở đây: dòng CON đang sống (IsDeleted = 0, hoặc
   bảng không có cờ xóa mềm) nhưng trỏ vào dòng CHA đã bị xóa mềm
   (IsDeleted = 1). Con đã xóa trỏ vào cha đã xóa thì KHÔNG tính — đó là
   trạng thái nhất quán, không phải rác.

   LƯU Ý khi đọc kết quả: cặp School → Room và School → Period gần như
   chắc chắn sẽ có số. Đó là hệ quả CỐ Ý của Đợt 1: SchoolService.delete
   không chặn theo phòng học và khung tiết vì chúng là cấu hình nội bộ
   của chính trường đó (chặn thì mọi trường seed sẵn không bao giờ xóa
   được). Hai cặp này là mồ côi vô hại — xem dev-note.
   ===================================================================== */

/* ---------- 1. Bảng nhật ký kết quả rà soát ---------- */
CREATE TABLE OrphanScan (
    Id              INT IDENTITY PRIMARY KEY,
    ScanAt          DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),  -- giờ UTC, đúng quy ước V1
    ParentTable     SYSNAME      NOT NULL,   -- bảng CHA có dòng đã xóa mềm
    ChildTable      SYSNAME      NOT NULL,   -- bảng CON còn dòng sống trỏ vào
    ChildColumn     SYSNAME      NOT NULL,   -- cột khóa ngoại ở bảng con
    ChildSoftDelete BIT          NOT NULL,   -- 0 = bảng con không có IsDeleted (bảng log/nối)
    OrphanCount     INT          NOT NULL
);
CREATE INDEX IX_OrphanScan_ScanAt ON OrphanScan(ScanAt DESC);
GO

/* ---------- 2. Thủ tục quét ---------- */
CREATE OR ALTER PROCEDURE usp_ScanOrphanRows
    @GhiNhatKy BIT = 1   -- 1 = ghi kết quả vào OrphanScan; 0 = chỉ xem, không ghi
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @KetQua TABLE (
        ParentTable     SYSNAME,
        ChildTable      SYSNAME,
        ChildColumn     SYSNAME,
        ChildSoftDelete BIT,
        OrphanCount     INT
    );

    DECLARE @parent SYSNAME, @child SYSNAME, @childCol SYSNAME, @parentCol SYSNAME;
    DECLARE @childSoft BIT, @sql NVARCHAR(MAX), @cnt INT;

    /* Chỉ xét cặp mà bảng CHA có cờ xóa mềm — cha không xóa mềm được thì
       không thể sinh mồ côi kiểu này. Bỏ qua khóa ngoại NHIỀU CỘT và khóa
       tự trỏ vào chính mình: schema hiện không có, và xử lý chúng bằng SQL
       động sẽ phức tạp hơn giá trị nó mang lại. */
    DECLARE cur CURSOR LOCAL FAST_FORWARD FOR
        SELECT
            OBJECT_NAME(fk.referenced_object_id),
            OBJECT_NAME(fk.parent_object_id),
            cc.name,
            rc.name,
            CASE WHEN EXISTS (SELECT 1 FROM sys.columns x
                              WHERE x.object_id = fk.parent_object_id AND x.name = 'IsDeleted')
                 THEN 1 ELSE 0 END
        FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns cc ON cc.object_id = fkc.parent_object_id     AND cc.column_id = fkc.parent_column_id
        JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
        WHERE EXISTS (SELECT 1 FROM sys.columns p
                      WHERE p.object_id = fk.referenced_object_id AND p.name = 'IsDeleted')
          AND fk.parent_object_id <> fk.referenced_object_id
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns z
               WHERE z.constraint_object_id = fk.object_id) = 1;

    OPEN cur;
    FETCH NEXT FROM cur INTO @parent, @child, @childCol, @parentCol, @childSoft;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @sql = N'SELECT @c = COUNT(*) FROM ' + QUOTENAME(@child) + N' c'
                 + N' JOIN ' + QUOTENAME(@parent) + N' p'
                 + N' ON c.' + QUOTENAME(@childCol) + N' = p.' + QUOTENAME(@parentCol)
                 + N' WHERE p.IsDeleted = 1'
                 + CASE WHEN @childSoft = 1 THEN N' AND c.IsDeleted = 0' ELSE N'' END;

        EXEC sp_executesql @sql, N'@c INT OUTPUT', @c = @cnt OUTPUT;

        INSERT INTO @KetQua (ParentTable, ChildTable, ChildColumn, ChildSoftDelete, OrphanCount)
        VALUES (@parent, @child, @childCol, @childSoft, @cnt);

        FETCH NEXT FROM cur INTO @parent, @child, @childCol, @parentCol, @childSoft;
    END
    CLOSE cur;
    DEALLOCATE cur;

    /* Chỉ ghi nhật ký các cặp THẬT SỰ có mồ côi — ghi cả dòng 0 thì mỗi lần
       quét đẻ ra ~30 dòng vô nghĩa, che mất tín hiệu khi số bắt đầu tăng. */
    IF @GhiNhatKy = 1
        INSERT INTO OrphanScan (ParentTable, ChildTable, ChildColumn, ChildSoftDelete, OrphanCount)
        SELECT ParentTable, ChildTable, ChildColumn, ChildSoftDelete, OrphanCount
        FROM @KetQua WHERE OrphanCount > 0;

    SELECT ParentTable, ChildTable, ChildColumn, ChildSoftDelete, OrphanCount
    FROM @KetQua
    WHERE OrphanCount > 0
    ORDER BY OrphanCount DESC, ParentTable, ChildTable;
END
GO

/* ---------- 3. Chụp ảnh nền ngay lúc nâng cấp ---------- */
/* Chạy một lượt để có mốc so sánh: mọi lần quét sau đối chiếu với dòng này
   mà biết tình hình đang xấu đi hay đứng yên. */
EXEC usp_ScanOrphanRows @GhiNhatKy = 1;
GO
