/* =====================================================================
   TSDMS — SEED ĐÁNH GIÁ GIÁO VIÊN (TeacherEvaluation)
   ---------------------------------------------------------------------
   CHẠY THẾ NÀO
     Mở trong SSMS rồi Execute, hoặc:
       sqlcmd -S localhost -d TSDMS -U tsdms_app -P *** -i TSDMS_Seed_DanhGia.sql
     ĐÒI seed phân công đã chạy trước (TSDMS_Seed_PhanCong.sql). Chạy lại tự bỏ qua.

   VÌ SAO CÓ FILE NÀY
     Bảng TeacherEvaluation chỉ có ĐÚNG 1 dòng, mà lại là dòng test điểm 1 kèm
     nhận xét tục tĩu. Hậu quả nhìn thấy ngay trên Bảng điều khiển: ô "Điểm đánh
     giá trung bình" hiện 1.0/5 — trông như hệ thống hỏng chứ không phải thiếu
     dữ liệu. File này xóa dòng đó và dựng bộ đánh giá thật.

   PHẠM VI
     Chỉ đánh giá giáo viên ĐÃ THỰC SỰ ĐỨNG LỚP trong kỳ (có ô thời khóa biểu
     thuộc phiếu ACTIVE/COMPLETED). Đánh giá một người chưa dạy buổi nào là dữ
     liệu vô nghĩa: không có gì để mà nhận xét.

     Hai kỳ, khớp đúng hai đợt phân công đã seed:
       · HK2 2025-2026 — học kỳ đã kết thúc
       · HK1 2026-2027 — học kỳ đang chạy

   NGƯỜI ĐÁNH GIÁ
     Nhân viên trung tâm (`daotao`, `employee`, `nhansu`), xoay vòng theo giáo
     viên. KHÔNG dùng tài khoản trường: dự án chốt bỏ cổng đăng nhập của trường,
     trường không còn là tác nhân trong hệ thống.

   ĐIỂM & NHẬN XÉT
     Phân bố lệch về 4-5 kèm vài ca kém (2-3 điểm), trung bình ~4.2/5 — giống dữ
     liệu thật, và quan trọng hơn: để màn Đánh giá có ca thật mà lọc ra khi cần
     xử lý giáo viên yếu. Toàn 5 điểm thì tính năng đó không có gì để demo.

     Nhận xét viết theo NHÓM MÔN giáo viên dạy nhiều nhất trong kỳ, và theo mức
     điểm — 20 mẫu (5 nhóm môn × 4 mức). Điểm thấp luôn kèm hướng xử lý, vì một
     dòng đánh giá 2 điểm mà không nói làm gì tiếp thì vô dụng với người quản lý.

     Điểm và người đánh giá suy ra từ CHECKSUM(TeacherId) nên CỐ ĐỊNH: chạy lại
     ở máy khác vẫn ra đúng bộ dữ liệu ấy.

   GỠ RA: chạy database/seed/TSDMS_Rollback_DanhGia.sql
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---- CHỐT CHẶN ---- */
IF EXISTS (SELECT 1 FROM TeacherEvaluation WHERE PeriodNote = N'HK2 2025-2026')
BEGIN
    PRINT N'>>> Đã có đánh giá seed — bỏ qua, không chạy lại.';
    PRINT N'    Muốn nạp lại: chạy TSDMS_Rollback_DanhGia.sql trước.';
    RETURN;
END

BEGIN TRY
BEGIN TRANSACTION;

DECLARE @n INT;
DECLARE @KyA NVARCHAR(50) = N'HK2 2025-2026';
DECLARE @KyB NVARCHAR(50) = N'HK1 2026-2027';

/* Ranh giới hai kỳ: phiếu bắt đầu trước tháng 6/2026 thuộc năm học cũ. */
DECLARE @MocKy DATE = '2026-06-01';

IF NOT EXISTS (SELECT 1 FROM AssignmentSlot)
    THROW 50020, N'Chưa có ô thời khóa biểu nào — chạy TSDMS_Seed_PhanCong.sql trước, nếu không sẽ không biết giáo viên nào thực sự đứng lớp để mà đánh giá.', 1;

/* ═══════════════ 0) DỌN DÒNG TEST ═══════════════
   Dòng duy nhất đang có là bản ghi test (điểm 1, nội dung tục tĩu, tạo 19/08).
   Để lại thì nó vừa kéo tụt điểm trung bình vừa nằm chình ình trên màn Đánh giá. */

DELETE FROM TeacherEvaluation
WHERE Comment IS NOT NULL AND Comment LIKE N'%c[uứ]t%';
SET @n = @@ROWCOUNT;
PRINT N'0) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' dòng đánh giá test cũ.';

/* ═══════════════ 1) AI ĐÁNH GIÁ ═══════════════ */

CREATE TABLE #NguoiDG (Ord INT IDENTITY(0,1) PRIMARY KEY, UserId INT);
INSERT INTO #NguoiDG (UserId)
SELECT u.Id
FROM AppUser u
WHERE u.Username IN ('daotao', 'employee', 'nhansu') AND u.IsDeleted = 0
ORDER BY CASE u.Username WHEN 'daotao' THEN 1 WHEN 'employee' THEN 2 ELSE 3 END;

DECLARE @SoNguoiDG INT = (SELECT COUNT(*) FROM #NguoiDG);
IF @SoNguoiDG = 0
    THROW 50021, N'Không tìm thấy tài khoản nhân viên nào (daotao/employee/nhansu) để làm người đánh giá.', 1;

/* ═══════════════ 2) GIÁO VIÊN ĐỨNG LỚP TRONG TỪNG KỲ ═══════════════
   Kèm TRƯỜNG và NHÓM MÔN dạy nhiều nhất — hai thứ này quyết định nhận xét viết
   gì, và cột SchoolId cho bộ lọc theo trường ở màn Đánh giá dùng được. */

CREATE TABLE #GV (
    Ky        NVARCHAR(50) COLLATE DATABASE_DEFAULT,
    TeacherId INT,
    SchoolId  INT,
    NhomMon   NVARCHAR(100) COLLATE DATABASE_DEFAULT,
    Diem      TINYINT,
    PRIMARY KEY (Ky, TeacherId)
);

;WITH Tiet AS (
    SELECT CASE WHEN a.StartDate < @MocKy THEN @KyA ELSE @KyB END AS Ky,
           a.TeacherId,
           sl.SchoolId,
           ISNULL(c.Name, N'Kĩ năng sống') AS NhomMon,
           COUNT(*) AS SoTiet
    FROM AssignmentSlot sl
    JOIN Assignment a ON a.Id = sl.AssignmentId
    JOIN Subject    s ON s.Id = a.SubjectId
    LEFT JOIN SubjectCategory c ON c.Id = s.CategoryId
    WHERE sl.IsDeleted = 0
      AND a.IsDeleted = 0
      AND a.Status IN ('ACTIVE', 'COMPLETED')
    GROUP BY CASE WHEN a.StartDate < @MocKy THEN @KyA ELSE @KyB END,
             a.TeacherId, sl.SchoolId, ISNULL(c.Name, N'Kĩ năng sống')
),
Chinh AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY Ky, TeacherId ORDER BY SoTiet DESC, SchoolId) AS rk
    FROM Tiet
)
INSERT INTO #GV (Ky, TeacherId, SchoolId, NhomMon, Diem)
SELECT Ky, TeacherId, SchoolId, NhomMon,
       /* Nhân với hằng số khác nhau theo kỳ để một giáo viên không bị lặp y hệt
          điểm ở cả hai kỳ — người thật có kỳ lên kỳ xuống. */
       CASE
           WHEN ABS(CHECKSUM(TeacherId * CASE WHEN Ky = @KyA THEN 13 ELSE 29 END)) % 100 <= 45 THEN 5
           WHEN ABS(CHECKSUM(TeacherId * CASE WHEN Ky = @KyA THEN 13 ELSE 29 END)) % 100 <= 80 THEN 4
           WHEN ABS(CHECKSUM(TeacherId * CASE WHEN Ky = @KyA THEN 13 ELSE 29 END)) % 100 <= 93 THEN 3
           ELSE 2
       END
FROM Chinh
WHERE rk = 1;

/* ═══════════════ 3) MẪU NHẬN XÉT: 5 NHÓM MÔN × 4 MỨC ĐIỂM ═══════════════ */

CREATE TABLE #NhanXet (
    NhomMon NVARCHAR(100) COLLATE DATABASE_DEFAULT,
    Diem    TINYINT,
    NoiDung NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    PRIMARY KEY (NhomMon, Diem)
);

INSERT INTO #NhanXet (NhomMon, Diem, NoiDung) VALUES
 -- Tin học
 (N'Tin học', 5, N'Hướng dẫn thao tác trên máy rõ ràng, học sinh yếu vẫn theo kịp. File mẫu và bài tập chuẩn bị đầy đủ trước mỗi buổi.'),
 (N'Tin học', 4, N'Nội dung bám sát chương trình, làm chủ phòng máy tốt. Cần chia nhỏ bước thao tác hơn cho học sinh khối 3-4.'),
 (N'Tin học', 3, N'Kiến thức vững nhưng nhịp dạy nhanh so với khối tiểu học; một số học sinh không theo kịp phần thực hành.'),
 (N'Tin học', 2, N'Nhiều buổi vào lớp muộn, giáo án nộp chậm. Đã trao đổi trực tiếp và yêu cầu cam kết cải thiện trong kỳ tới.'),
 -- Tiếng Anh
 (N'Tiếng Anh', 5, N'Phát âm chuẩn, tổ chức luyện nói theo cặp hiệu quả. Học sinh mạnh dạn giao tiếp hơn hẳn so với đầu kỳ.'),
 (N'Tiếng Anh', 4, N'Giữ nhịp lớp tốt, giáo cụ chuẩn bị chu đáo. Nên tăng thời lượng luyện nói cho nhóm học sinh còn rụt rè.'),
 (N'Tiếng Anh', 3, N'Cần chuẩn bị giáo cụ kỹ hơn, vài buổi phải mượn tài liệu của trường. Lớp còn ồn ở phần hoạt động nhóm.'),
 (N'Tiếng Anh', 2, N'Trường phản ánh giáo viên đổi lịch nhiều lần trong kỳ. Cần chấn chỉnh; nếu tiếp diễn sẽ chuyển phân công cho người khác.'),
 -- STEM - AI
 (N'STEM - AI', 5, N'Điều phối nhóm lắp ráp robot tốt, xử lý sự cố thiết bị nhanh gọn. Học sinh hoàn thành sản phẩm đúng tiến độ.'),
 (N'STEM - AI', 4, N'Nắm chắc thiết bị, học sinh hứng thú. Cần siết thời gian phần lắp ráp để kịp phần trình bày cuối buổi.'),
 (N'STEM - AI', 3, N'Còn lúng túng khi bộ kit lỗi giữa buổi, mất khá nhiều thời gian lớp. Nên chạy thử thiết bị trước khi lên lớp.'),
 (N'STEM - AI', 2, N'Phần lớn buổi học sinh không hoàn thành được sản phẩm. Đề nghị bố trí dự giờ và hỗ trợ chuyên môn trong kỳ tới.'),
 -- Kĩ năng sống
 (N'Kĩ năng sống', 5, N'Dẫn dắt thảo luận tự nhiên, tạo được không khí an toàn để học sinh chia sẻ. Ví dụ gần gũi với lứa tuổi.'),
 (N'Kĩ năng sống', 4, N'Hoạt động nhóm sinh động, học sinh tham gia đều. Cần chốt lại thông điệp chính rõ hơn ở cuối buổi.'),
 (N'Kĩ năng sống', 3, N'Nội dung ổn nhưng phần lớn thời gian là thuyết trình một chiều, học sinh ít được thực hành.'),
 (N'Kĩ năng sống', 2, N'Khả năng kiểm soát lớp còn yếu, nhiều buổi mất trật tự. Cần bố trí kèm cặp cùng giáo viên có kinh nghiệm.'),
 -- Kĩ năng số
 (N'Kĩ năng số', 5, N'Tình huống an toàn mạng cập nhật sát thực tế, học sinh hào hứng tham gia. Nhà trường phản hồi rất tốt.'),
 (N'Kĩ năng số', 4, N'Bài giảng dễ hiểu, có liên hệ thực tế. Nên bổ sung bài tập về nhà để phụ huynh cùng theo dõi.'),
 (N'Kĩ năng số', 3, N'Bài soạn còn chung chung, chưa bám sát độ tuổi của lớp. Cần cụ thể hóa ví dụ theo từng khối.'),
 (N'Kĩ năng số', 2, N'Nội dung lặp lại giữa các buổi, học sinh phản hồi nhàm chán. Yêu cầu soạn lại giáo án trước khi tiếp tục phân công.');

/* Nhóm môn lạ (dữ liệu cũ / môn chưa gắn danh mục) rơi về mẫu "Kĩ năng sống"
   thay vì để trống nhận xét — nhưng phải biết là có bao nhiêu ca như vậy. */
SELECT @n = COUNT(*) FROM #GV g WHERE NOT EXISTS (SELECT 1 FROM #NhanXet x WHERE x.NhomMon = g.NhomMon);
IF @n > 0
    PRINT N'   (lưu ý: ' + CAST(@n AS NVARCHAR(10)) + N' giáo viên có nhóm môn ngoài 5 nhóm chuẩn — dùng mẫu nhận xét mặc định)';

/* ═══════════════ 4) GHI ĐÁNH GIÁ ═══════════════ */

INSERT INTO TeacherEvaluation (TeacherId, EvaluatorUserId, SchoolId, Score, Comment, PeriodNote, CreatedAt, CreatedBy)
SELECT g.TeacherId,
       d.UserId,
       g.SchoolId,
       g.Diem,
       ISNULL(nx.NoiDung, nxMacDinh.NoiDung),
       g.Ky,
       /* Đánh giá viết vào cuối kỳ, không phải hôm nay. */
       CASE WHEN g.Ky = @KyA THEN CAST('2026-05-25T09:00:00' AS DATETIME2(3))
            ELSE DATEADD(DAY, -3, CAST(GETDATE() AS DATETIME2(3))) END,
       d.UserId
FROM #GV g
JOIN #NguoiDG d ON d.Ord = ABS(CHECKSUM(g.TeacherId)) % @SoNguoiDG
LEFT JOIN #NhanXet nx        ON nx.NhomMon = g.NhomMon      AND nx.Diem = g.Diem
JOIN      #NhanXet nxMacDinh ON nxMacDinh.NhomMon = N'Kĩ năng sống' AND nxMacDinh.Diem = g.Diem;

SET @n = @@ROWCOUNT;
PRINT N'4) Đã ghi ' + CAST(@n AS NVARCHAR(10)) + N' đánh giá.';

SELECT @n = COUNT(*) FROM #GV WHERE Ky = @KyA;
PRINT N'   · ' + @KyA + N': ' + CAST(@n AS NVARCHAR(10)) + N' giáo viên';
SELECT @n = COUNT(*) FROM #GV WHERE Ky = @KyB;
PRINT N'   · ' + @KyB + N': ' + CAST(@n AS NVARCHAR(10)) + N' giáo viên';

DECLARE @tb DECIMAL(4,2) = (SELECT AVG(CAST(Score AS DECIMAL(4,2))) FROM TeacherEvaluation WHERE IsDeleted = 0);
PRINT N'   · Điểm trung bình toàn hệ thống: ' + CAST(@tb AS NVARCHAR(10)) + N'/5';

DROP TABLE #NguoiDG; DROP TABLE #GV; DROP TABLE #NhanXet;

COMMIT TRANSACTION;
PRINT N'';
PRINT N'>>> XONG. Kiểm tra: Bảng điều khiển (ô "Điểm đánh giá trung bình") và màn Đánh giá giáo viên.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DROP TABLE IF EXISTS #NguoiDG; DROP TABLE IF EXISTS #GV; DROP TABLE IF EXISTS #NhanXet;
    PRINT N'!!! LỖI — đã rollback toàn bộ, DB giữ nguyên.';
    THROW;
END CATCH
GO
