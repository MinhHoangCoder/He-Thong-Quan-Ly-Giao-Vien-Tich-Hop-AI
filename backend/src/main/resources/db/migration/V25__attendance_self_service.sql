/* =====================================================================
   TSDMS - V25: Chấm công CHỈ DO GIÁO VIÊN TỰ CHẤM
   ---------------------------------------------------------------------
   BỐI CẢNH:
     Trước đây có hai đường tạo dòng chấm công: giáo viên tự check-in, và
     kế toán bấm "Sinh từ lịch dạy" đẻ hàng loạt dòng Có mặt kèm đủ giờ
     vào/ra lấy từ khung tiết. Đường thứ hai tạo công cho những buổi CHƯA
     diễn ra — tính ra tiền cho việc chưa làm.

     Nghiệp vụ chốt lại: chỉ giáo viên tự chấm mới sinh ra công. Buổi chưa
     tới giờ thì KHÔNG ai ghi được, kể cả admin. Giáo viên lỡ buổi thì gửi
     yêu cầu bổ sung cho admin duyệt, không tự sửa được.

   4 THAY ĐỔI:
     1) XÓA SẠCH dữ liệu chấm công + bảng lương cũ. Dự án chưa triển khai
        thực tế, mà dữ liệu cũ toàn là công sinh hàng loạt — giữ lại thì
        mọi báo cáo đều đọc trên số liệu không có thật.

     2) Attendance.ScheduleId thành NOT NULL. "Chấm công lẻ không gắn buổi"
        là lỗ hổng của luật mới: không có buổi thì không có mốc giờ nào để
        kiểm tra "đã tới giờ dạy chưa". Bỏ khái niệm đó luôn.

     3) Attendance += AdjustReason — admin sửa tay phải giải trình. Không
        dùng chung cột Note vì Note là ghi chú của chính giáo viên lúc
        check-in; ghi đè lên là mất tiếng nói của người trong cuộc.

     4) AttendanceAmendRequest — bảng yêu cầu bổ sung chấm công. Tách bảng
        riêng chứ không nhét trạng thái PENDING vào Attendance: dòng chấm
        công là dữ liệu tính tiền, thêm một trạng thái "chưa chắc" vào đó
        là mọi chỗ đọc lương đều phải nhớ loại trừ nó.
   ===================================================================== */

/* ---------- 1) Xóa sạch dữ liệu cũ ----------
   Thứ tự bắt buộc: AttendanceChangeLog trước (FK_AttChangeLog_Attendance
   chặn), rồi tới Attendance. Payroll không có bảng nào trỏ vào nên xóa
   thẳng. Lịch dạy / phân công / giáo viên GIỮ NGUYÊN — xóa thì không còn
   buổi nào để chấm. */
DELETE FROM AttendanceChangeLog;
GO
DELETE FROM Attendance;
GO
DELETE FROM Payroll;
GO
/* Thông báo cũ trỏ tới các dòng vừa xóa — để lại thì bấm vào là lỗi 404. */
DELETE FROM Notification WHERE Type IN ('ATTENDANCE', 'PAYROLL');
GO
DBCC CHECKIDENT ('Attendance', RESEED, 0) WITH NO_INFOMSGS;
GO
DBCC CHECKIDENT ('AttendanceChangeLog', RESEED, 0) WITH NO_INFOMSGS;
GO
DBCC CHECKIDENT ('Payroll', RESEED, 0) WITH NO_INFOMSGS;
GO

/* ---------- 2) ScheduleId bắt buộc ----------
   Phải DROP index lọc trước: index đang phụ thuộc vào cột nên ALTER COLUMN
   bị chặn. Bảng rỗng sau bước 1 nên đổi NOT NULL không vướng dữ liệu cũ. */
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_Attendance_ScheduleId')
    DROP INDEX UX_Attendance_ScheduleId ON dbo.Attendance;
GO
ALTER TABLE Attendance ALTER COLUMN ScheduleId BIGINT NOT NULL;
GO
/* Không cần WHERE lọc nữa: mọi dòng đều có ScheduleId, unique thẳng là đủ.
   Vẫn là hàng rào cuối chặn 2 request check-in đồng thời cùng lọt qua bước
   kiểm tra ở tầng service. */
CREATE UNIQUE NONCLUSTERED INDEX UX_Attendance_ScheduleId
    ON dbo.Attendance (ScheduleId);
GO

/* ---------- 3) Lý do sửa tay ---------- */
IF COL_LENGTH('dbo.Attendance', 'AdjustReason') IS NULL
    ALTER TABLE Attendance ADD AdjustReason NVARCHAR(255) NULL;
GO

/* ---------- 4) Yêu cầu bổ sung chấm công ----------
   Giáo viên đề xuất giờ vào/ra, admin duyệt là lấy nguyên (có thể chỉnh
   trạng thái). Cùng khuôn với PartTimeShiftRequest (V10) để hai luồng
   duyệt trong hệ thống đọc giống nhau. */
IF OBJECT_ID('dbo.AttendanceAmendRequest', 'U') IS NULL
CREATE TABLE AttendanceAmendRequest (
    Id               BIGINT IDENTITY PRIMARY KEY,
    TeacherId        INT           NOT NULL,           -- → Teacher (người xin)
    ScheduleId       BIGINT        NOT NULL,           -- → Schedule (buổi bị lỡ)
    Reason           NVARCHAR(500) NOT NULL,           -- giáo viên giải trình
    ProposedCheckIn  TIME(0)       NOT NULL,           -- giờ vào tự khai
    ProposedCheckOut TIME(0)       NOT NULL,           -- giờ ra tự khai
    Status           VARCHAR(20)   NOT NULL
                     CONSTRAINT DF_AttAmend_Status DEFAULT 'PENDING'
                     CONSTRAINT CK_AttAmend_Status CHECK (Status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ReviewedByUserId INT           NULL,               -- → AppUser (admin xử lý)
    ReviewedAt       DATETIME2(3)  NULL,
    ReviewNote       NVARCHAR(500) NULL,               -- lý do từ chối (bắt buộc khi REJECTED)
    CreatedAt        DATETIME2(3)  NOT NULL
                     CONSTRAINT DF_AttAmend_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_AttAmend_Teacher  FOREIGN KEY (TeacherId)        REFERENCES Teacher(Id),
    CONSTRAINT FK_AttAmend_Schedule FOREIGN KEY (ScheduleId)       REFERENCES Schedule(Id),
    CONSTRAINT FK_AttAmend_User     FOREIGN KEY (ReviewedByUserId) REFERENCES AppUser(Id),
    CONSTRAINT CK_AttAmend_Time     CHECK (ProposedCheckIn < ProposedCheckOut)
);
GO

/* Mỗi buổi chỉ được có MỘT yêu cầu đang chờ: không thì giáo viên gửi 5 lần
   là admin thấy 5 dòng y hệt, duyệt cái nào cũng được. Đã xử lý xong
   (APPROVED/REJECTED) thì cho gửi lại — trường hợp bị từ chối vì thiếu
   thông tin rồi bổ sung. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_AttAmend_PendingSchedule')
    CREATE UNIQUE NONCLUSTERED INDEX UX_AttAmend_PendingSchedule
        ON dbo.AttendanceAmendRequest (ScheduleId)
        WHERE Status = 'PENDING';
GO

/* Hộp "chờ duyệt" của admin — truy vấn chính của bảng này. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_AttAmend_Status')
    CREATE INDEX IX_AttAmend_Status ON dbo.AttendanceAmendRequest (Status, CreatedAt DESC);
GO

/* Danh sách yêu cầu của chính giáo viên. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_AttAmend_Teacher')
    CREATE INDEX IX_AttAmend_Teacher ON dbo.AttendanceAmendRequest (TeacherId, CreatedAt DESC);
GO
