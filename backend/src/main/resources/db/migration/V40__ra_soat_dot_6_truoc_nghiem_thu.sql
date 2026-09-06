/* =====================================================================
   V40 — BỐN LỖ HỔNG CÒN LẠI TRƯỚC NGHIỆM THU.

   Gom vào MỘT migration vì cả bốn đều là "thêm chỗ để ghi lại sự thật"
   chứ không đổi luồng nghiệp vụ nào đang chạy. Tách thành bốn file chỉ
   làm số hiệu migration phình ra mà người đọc vẫn phải mở cả bốn mới
   hiểu đợt rà soát này sửa gì.

   (1) TRUNG TÂM TỰ GIA HẠN HỢP ĐỒNG DỊCH VỤ. School.ContractEndDate là
       một ô ngày sửa được tự do. Hết hạn hợp tác mà nhân viên trung tâm
       kéo ngày lên một năm là trường đó "sống lại", nhận lớp mới, nhận
       phân công mới — không ai ký, không ai biết. Đây là lỗ hổng KIỂM
       SOÁT chứ không phải lỗi kỹ thuật: dữ liệu vẫn hợp lệ, chỉ là
       không có ai chịu trách nhiệm cho con số đó.

       Không khoá cứng ô ngày, vì trường hợp gõ nhầm ngày lúc tạo hồ sơ
       là có thật và khoá lại thì phải đẻ ra "phụ lục hợp đồng" — một
       khái niệm nghiệp vụ mới chỉ để sửa một lỗi chính tả. Cách rẻ hơn
       và đủ mạnh: VẪN cho sửa, nhưng mỗi lần sửa bắt buộc nhập lý do và
       để lại một dòng nhật ký không xoá được. Người sửa vẫn sửa được
       trong 10 giây; người kiểm tra vẫn thấy ai đã kéo ngày, kéo từ đâu
       sang đâu, vì cớ gì. Răn đe bằng dấu vết thay vì bằng rào chắn.

   (2) MỘT TRƯỜNG CÓ HAI LỚP TRÙNG TÊN TRONG CÙNG NĂM HỌC. Màn thêm lớp
       hàng loạt nạp một lúc vài chục dòng, gõ trùng là chuyện thường,
       mà DB không có gì chặn. Trùng tên lớp thì phân công, chấm công và
       lương của hai lớp đó lẫn vào nhau và không cách nào gỡ ra được
       nữa. Chặn ở tầng DB chứ không chỉ ở service: file Excel nạp vào,
       seed dữ liệu demo và màn nhập tay là ba đường khác nhau cùng ghi
       vào một bảng.

       Khoá là (SchoolId, Name, SchoolYear) chứ không phải (SchoolId,
       Name): "10A1" của năm 2025-2026 và "10A1" của 2026-2027 là hai
       lớp khác nhau, chặn cả hai là chặn nhầm.

   (3) BUỔI DẠY BỊ HỦY KHÔNG NÓI ĐƯỢC VÌ SAO BỊ HỦY. Từ V39 giáo viên
       gửi được đơn xin nghỉ, và màn Lịch nghỉ thì đặt được kỳ nghỉ lễ —
       cả hai đều kết thúc bằng việc buổi dạy chuyển CANCELLED. Nhưng
       "nghỉ có phép", "nghỉ lễ" và "admin hủy phân công" mà cùng hiện
       một chữ "Đã hủy" thì kế toán không phân biệt được dòng nào đáng
       trừ lương, dòng nào không.

       KHÔNG nới CK_Schedule_Status thành 5-6 giá trị: mọi câu truy vấn
       đang lọc Status <> 'CANCELLED' để đếm buổi thật sẽ lặng lẽ đếm
       sai khi có giá trị mới, và đó là loại lỗi không nổ ở đâu cả, chỉ
       ra sai số ở bảng lương. Thay vào đó tách LÝ DO thành cột riêng —
       cùng lối V39 đã chọn khi từ chối nới CK_Assignment_Status cho
       "kết thúc sớm". Status trả lời "buổi này còn hiệu lực không",
       CancelKind trả lời "vì sao", hai câu hỏi khác nhau thì hai cột.

   (4) TĂNG ĐƠN GIÁ TIẾT DẠY LÀM SAI SỐ LƯƠNG ĐÃ TRẢ. V38 đã đưa đơn giá
       ra khỏi code và tra theo NGÀY DẠY, nên tính lại một kỳ cũ vẫn ra
       đúng — với điều kiện không ai đụng vào bảng PayRate. Nhưng sửa
       một dòng PayRate cũ (gõ nhầm số, nhập sai ngày hiệu lực) thì mọi
       phiếu lương từng tính theo dòng đó đổi số mà không để lại dấu.

       Chốt triệt để: ĐÓNG BĂNG đơn giá vào từng buổi ngay lúc chấm
       công. Buổi đã dạy mang theo cái giá của chính nó, không đi tra
       bảng nữa. Bảng PayRate từ đó chỉ còn phục vụ những buổi CHƯA
       chấm — sửa nó không thể chạm tới quá khứ. Cột để NULL được cho
       dữ liệu cũ: PayrollService tra bảng như trước khi cột trống, nên
       migration này không làm lệch bất kỳ phiếu lương nào đang có.
   ===================================================================== */

/* =====================================================================
   1) NHẬT KÝ ĐỔI NGÀY HẾT HẠN HỢP ĐỒNG DỊCH VỤ
   ===================================================================== */

/* Bảng riêng chứ không thêm cột "LyDoSuaCuoi" vào School: một trường bị
   kéo ngày ba lần thì cột đơn chỉ giữ được lần cuối, mà chính chuỗi ba
   lần mới là thứ nhìn ra được ý đồ. */
IF OBJECT_ID('dbo.SchoolContractChangeLog', 'U') IS NULL
CREATE TABLE SchoolContractChangeLog (
    Id              INT IDENTITY PRIMARY KEY,
    SchoolId        INT           NOT NULL,
    /* NULL = trước đó chưa nhập ngày hết hạn. */
    OldEndDate      DATE          NULL,
    NewEndDate      DATE          NULL,
    /* EXTEND = kéo dài (đáng ngờ) | SHORTEN = rút ngắn | SET = điền lần đầu | CLEAR = xoá ngày. */
    ChangeKind      VARCHAR(10)   NOT NULL,
    /* Bắt buộc ở tầng service — cột NOT NULL để không ai ghi chui bằng SQL trống. */
    Reason          NVARCHAR(500) NOT NULL,
    ChangedByUserId INT           NULL,
    ChangedAt       DATETIME2(3)  NOT NULL
        CONSTRAINT DF_SCCL_ChangedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_SCCL_School FOREIGN KEY (SchoolId) REFERENCES School(Id),
    CONSTRAINT FK_SCCL_User FOREIGN KEY (ChangedByUserId) REFERENCES AppUser(Id),
    CONSTRAINT CK_SCCL_Kind CHECK (ChangeKind IN ('EXTEND','SHORTEN','SET','CLEAR'))
);
GO

/* Màn sửa trường luôn đọc theo một trường và xếp mới nhất trước. */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'IX_SCCL_School' AND object_id = OBJECT_ID(N'dbo.SchoolContractChangeLog'))
    CREATE INDEX IX_SCCL_School ON dbo.SchoolContractChangeLog(SchoolId, ChangedAt DESC);
GO

/* =====================================================================
   2) MỖI TRƯỜNG CHỈ MỘT LỚP CÙNG TÊN TRONG MỘT NĂM HỌC
   ===================================================================== */

/* Gỡ trùng sẵn có trước khi dựng UNIQUE — CREATE UNIQUE INDEX gặp trùng
   là nổ, và migration nổ thì backend không khởi động được. Ghép " (#Id)"
   như V36 đã làm với tên trường: Id là khoá chính nên tên mới chắc chắn
   không đụng nhau, khỏi phải chạy lặp. Giữ dòng Id nhỏ nhất — dòng tạo
   trước thường là dòng thật. */
WITH trung AS (
    SELECT Id,
           Name,
           ROW_NUMBER() OVER (PARTITION BY SchoolId, Name, SchoolYear ORDER BY Id) AS thuTu
    FROM dbo.SchoolClass
    WHERE IsDeleted = 0
)
UPDATE trung
SET Name = LEFT(Name, 80) + N' (#' + CAST(Id AS NVARCHAR(10)) + N')'
WHERE thuTu > 1;
GO

/* Lọc WHERE IsDeleted = 0: lớp đã xoá mềm không được chiếm chỗ tên, nếu
   không thì xoá nhầm một lớp là vĩnh viễn không tạo lại được tên đó. */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'UX_SchoolClass_SchoolNameYear' AND object_id = OBJECT_ID(N'dbo.SchoolClass'))
    CREATE UNIQUE INDEX UX_SchoolClass_SchoolNameYear
        ON dbo.SchoolClass(SchoolId, Name, SchoolYear) WHERE IsDeleted = 0;
GO

/* =====================================================================
   3) LÝ DO BUỔI DẠY BỊ HỦY
   ===================================================================== */

IF COL_LENGTH('dbo.Schedule', 'CancelKind') IS NULL
    ALTER TABLE Schedule ADD
        /* NULL = hủy hành chính (admin hủy phân công) — giữ nguyên nghĩa cũ
           của CANCELLED để dữ liệu đang có không phải diễn giải lại. */
        CancelKind NVARCHAR(20) NULL,
        /* Kỳ nghỉ đã làm buổi này rơi — có nó thì xoá kỳ nghỉ mới biết
           đường trả đúng những buổi mình đã đụng vào, thay vì quét mù cả
           khoảng ngày và trả nhầm cả buổi admin hủy tay. */
        HolidayId  INT          NULL;
GO

IF OBJECT_ID('CK_Schedule_CancelKind', 'C') IS NULL
    ALTER TABLE Schedule ADD CONSTRAINT CK_Schedule_CancelKind
        CHECK (CancelKind IS NULL OR CancelKind IN ('LEAVE','HOLIDAY'));
GO

IF OBJECT_ID('FK_Schedule_Holiday', 'F') IS NULL
    ALTER TABLE Schedule ADD CONSTRAINT FK_Schedule_Holiday
        FOREIGN KEY (HolidayId) REFERENCES Holiday(Id);
GO

/* Xoá kỳ nghỉ = trả lại đúng những buổi mang HolidayId đó. Index lọc để
   không phình: tuyệt đại đa số buổi có HolidayId NULL. */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'IX_Schedule_Holiday' AND object_id = OBJECT_ID(N'dbo.Schedule'))
    CREATE INDEX IX_Schedule_Holiday ON dbo.Schedule(HolidayId) WHERE HolidayId IS NOT NULL;
GO

/* =====================================================================
   4) ĐÓNG BĂNG ĐƠN GIÁ TIẾT DẠY VÀO BUỔI ĐÃ CHẤM CÔNG
   ===================================================================== */

IF COL_LENGTH('dbo.Attendance', 'RateAmount') IS NULL
    ALTER TABLE Attendance ADD
        /* Đơn giá MỘT TIẾT tại thời điểm chấm công, đồng. NULL = dòng cũ
           trước V40, PayrollService tra bảng PayRate như trước. */
        RateAmount DECIMAL(18,2) NULL,
        /* PAY_RATE = tra từ bảng đơn giá | CONTRACT = đơn giá riêng trong
           hợp đồng giáo viên. Ghi lại NGUỒN chứ không chỉ con số: hai
           nguồn cùng ra 120.000đ nhưng khi đối soát thì phải biết nên đi
           hỏi bảng giá hay hỏi bản hợp đồng. */
        RateSource VARCHAR(20)   NULL;
GO

IF OBJECT_ID('CK_Attendance_RateSource', 'C') IS NULL
    ALTER TABLE Attendance ADD CONSTRAINT CK_Attendance_RateSource
        CHECK (RateSource IS NULL OR RateSource IN ('PAY_RATE','CONTRACT'));
GO
