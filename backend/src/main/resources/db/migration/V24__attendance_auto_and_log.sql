/* =====================================================================
   TSDMS - V24: Chấm công TỰ ĐỘNG khép sổ + NHẬT KÝ thay đổi
   ---------------------------------------------------------------------
   BỐI CẢNH:
     Giáo viên tự check-in/out từng buổi, nhưng quên bấm thì dòng chấm công
     kẹt vĩnh viễn: quên check-out -> treo không có giờ ra; quên cả check-in
     -> buổi dạy không có dòng nào, kế toán phải dò bằng mắt.

     Nghiệp vụ chốt: hệ thống tự khép sổ sau khi buổi kết thúc — tự chốt giờ
     ra bằng GIỜ TAN TIẾT, và tự ghi Vắng nếu quá 30 phút vẫn không ai chấm.

   3 THAY ĐỔI:
     1) Attendance += AutoCheckOut — đánh dấu giờ ra do HỆ THỐNG chốt, không
        phải giáo viên bấm. Kế toán nhìn cột này để biết dòng nào cần soát.
        Kèm UpdatedAt/UpdatedBy: bảng này vốn chỉ có CreatedAt/CreatedBy nên
        sửa tay KHÔNG để lại vết — mà đây là dữ liệu tính ra tiền lương.

     2) CheckInMethod nhận thêm 'SYSTEM' — nguồn của các dòng Vắng do job
        sinh ra. Giữ SELF/EMPLOYEE cho người thật để phân biệt rạch ròi.

     3) AttendanceChangeLog + trigger — mọi lần thêm/sửa đều tự ghi vết
        (ai, lúc nào, đổi gì). Đặt ở TRIGGER chứ không ở tầng service để
        không đường ghi nào lọt sổ, giống cách TR_Schedule_StatusLog đang làm.
   ===================================================================== */

/* ---------- 1) Cột mới trên Attendance ---------- */
IF COL_LENGTH('dbo.Attendance', 'AutoCheckOut') IS NULL
    ALTER TABLE Attendance ADD AutoCheckOut BIT NOT NULL CONSTRAINT DF_Attendance_AutoCheckOut DEFAULT 0;
GO
IF COL_LENGTH('dbo.Attendance', 'UpdatedAt') IS NULL
    ALTER TABLE Attendance ADD UpdatedAt DATETIME2(3) NULL;
GO
IF COL_LENGTH('dbo.Attendance', 'UpdatedBy') IS NULL
    ALTER TABLE Attendance ADD UpdatedBy INT NULL;
GO

/* ---------- 2) Cho phép nguồn 'SYSTEM' ---------- */
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'CK_Attendance_Method' AND parent_object_id = OBJECT_ID(N'dbo.Attendance'))
    ALTER TABLE Attendance DROP CONSTRAINT CK_Attendance_Method;
GO
ALTER TABLE Attendance ADD CONSTRAINT CK_Attendance_Method
    CHECK (CheckInMethod IN ('SELF', 'EMPLOYEE', 'SCHOOL', 'DEVICE', 'SYSTEM'));
GO

/* ---------- 3) Nhật ký thay đổi ----------
   Chỉ giữ 3 trường thật sự ảnh hưởng tới lương (trạng thái, giờ vào, giờ ra):
   ghi cả hai phía cũ/mới để đọc log là dựng lại được diễn biến mà không phải
   join ngược. ChangedByUserId NULL = do hệ thống (job), không phải người. */
IF OBJECT_ID('dbo.AttendanceChangeLog', 'U') IS NULL
CREATE TABLE AttendanceChangeLog (
    Id              BIGINT IDENTITY PRIMARY KEY,
    AttendanceId    BIGINT       NOT NULL,
    Action          VARCHAR(20)  NOT NULL           -- CREATE | UPDATE | AUTO_CHECKOUT | AUTO_ABSENT
                    CONSTRAINT CK_AttChangeLog_Action
                    CHECK (Action IN ('CREATE', 'UPDATE', 'AUTO_CHECKOUT', 'AUTO_ABSENT')),
    OldStatus       VARCHAR(20)  NULL,
    NewStatus       VARCHAR(20)  NULL,
    OldCheckIn      TIME(0)      NULL,
    NewCheckIn      TIME(0)      NULL,
    OldCheckOut     TIME(0)      NULL,
    NewCheckOut     TIME(0)      NULL,
    ChangedByUserId INT          NULL,              -- NULL = hệ thống tự làm
    ChangedAt       DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_AttChangeLog_Attendance FOREIGN KEY (AttendanceId) REFERENCES Attendance(Id)
);
GO
/* Xem lịch sử của một dòng chấm công — truy vấn duy nhất mà bảng này phục vụ. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_AttChangeLog_Attendance')
    CREATE INDEX IX_AttChangeLog_Attendance ON AttendanceChangeLog(AttendanceId, ChangedAt DESC);
GO

/* ---------- 4) Trigger ghi vết ----------
   UPDATE chỉ ghi khi một trong ba trường THẬT SỰ đổi — nếu không mỗi lần lưu
   lại đẻ một dòng log rác. Phân biệt việc của hệ thống với việc của người dựa
   vào cờ AutoCheckOut / nguồn SYSTEM chứ không dựa vào ChangedByUserId, vì
   job chạy nền vốn không có người dùng nào để ghi. */
CREATE OR ALTER TRIGGER TR_Attendance_ChangeLog
ON Attendance
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO AttendanceChangeLog
        (AttendanceId, Action, OldStatus, NewStatus, OldCheckIn, NewCheckIn, OldCheckOut, NewCheckOut, ChangedByUserId)
    SELECT
        i.Id,
        CASE
            WHEN d.Id IS NULL AND i.CheckInMethod = 'SYSTEM'                     THEN 'AUTO_ABSENT'
            WHEN d.Id IS NULL                                                    THEN 'CREATE'
            WHEN i.AutoCheckOut = 1 AND ISNULL(d.AutoCheckOut, 0) = 0            THEN 'AUTO_CHECKOUT'
            ELSE 'UPDATE'
        END,
        d.Status, i.Status,
        d.CheckIn, i.CheckIn,
        d.CheckOut, i.CheckOut,
        CASE WHEN d.Id IS NULL THEN i.CreatedBy ELSE i.UpdatedBy END
    FROM inserted i
    LEFT JOIN deleted d ON d.Id = i.Id
    WHERE d.Id IS NULL                              -- thêm mới: luôn ghi
       OR i.Status  <> d.Status                     -- sửa: chỉ ghi khi đổi thật
       OR ISNULL(CONVERT(varchar(8), i.CheckIn),  '') <> ISNULL(CONVERT(varchar(8), d.CheckIn),  '')
       OR ISNULL(CONVERT(varchar(8), i.CheckOut), '') <> ISNULL(CONVERT(varchar(8), d.CheckOut), '');
END;
GO
