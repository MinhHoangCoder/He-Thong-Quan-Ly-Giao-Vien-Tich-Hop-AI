/* =====================================================================
   TSDMS — GỠ DỮ LIỆU NĂM HỌC 2025–2026
   ---------------------------------------------------------------------
   Đảo ngược trọn vẹn TSDMS_Seed_NamHoc2025.sql.

   DẤU VÂN TAY nhận diện dữ liệu do seed sinh ra: các phân công có
   StartDate = 2025-09-08. Mọi thứ khác (ô lịch, buổi dạy, chấm công,
   lương, đánh giá) đều lần ra từ đó theo khóa ngoại, nên không có cách
   nào xoá nhầm sang năm học 2026–2027.

   Riêng Payroll và TeacherEvaluation không nối trực tiếp tới Assignment
   nên lọc theo KỲ (09/2025–05/2026) và theo nhãn kỳ đánh giá.

   THỨ TỰ XOÁ bắt buộc đi ngược chiều khóa ngoại:
       Payroll → AttendanceChangeLog → Attendance → ScheduleStatusLog
       → Schedule → AssignmentSlot → Assignment → TeacherEvaluation
   ===================================================================== */

SET QUOTED_IDENTIFIER ON;   -- BẮT BUỘC: Payroll có index trên cột tính sẵn NetAmount
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

PRINT N'===== GỠ DỮ LIỆU NĂM HỌC 2025–2026 =====';
GO

BEGIN TRANSACTION;

-- Gom trước danh sách khoá cần xoá: sau khi xoá Assignment thì không lần lại được nữa.
CREATE TABLE #Asg   (Id INT PRIMARY KEY);
CREATE TABLE #Sched (Id BIGINT PRIMARY KEY);
CREATE TABLE #Att   (Id BIGINT PRIMARY KEY);

INSERT INTO #Asg (Id)
SELECT Id FROM Assignment WHERE StartDate = '2025-09-08';

INSERT INTO #Sched (Id)
SELECT s.Id FROM Schedule s JOIN #Asg a ON a.Id = s.AssignmentId;

INSERT INTO #Att (Id)
SELECT at.Id FROM Attendance at JOIN #Sched s ON s.Id = at.ScheduleId;

IF NOT EXISTS (SELECT 1 FROM #Asg)
BEGIN
    PRINT N'>> Không tìm thấy dữ liệu năm học 2025–2026 — không có gì để gỡ.';
    ROLLBACK TRANSACTION;
    RETURN;
END;

DELETE FROM Payroll WHERE PeriodYear * 100 + PeriodMonth BETWEEN 202509 AND 202605;
PRINT N'  Bảng lương      : ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

DELETE l FROM AttendanceChangeLog l JOIN #Att a ON a.Id = l.AttendanceId;
PRINT N'  Nhật ký chấm công: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

DELETE at FROM Attendance at JOIN #Att a ON a.Id = at.Id;
PRINT N'  Chấm công       : ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

DELETE l FROM ScheduleStatusLog l JOIN #Sched s ON s.Id = l.ScheduleId;
PRINT N'  Nhật ký lịch    : ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

DELETE sch FROM Schedule sch JOIN #Sched s ON s.Id = sch.Id;
PRINT N'  Buổi dạy        : ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

DELETE sl FROM AssignmentSlot sl JOIN #Asg a ON a.Id = sl.AssignmentId;
PRINT N'  Ô lịch tuần     : ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

DELETE ag FROM Assignment ag JOIN #Asg a ON a.Id = ag.Id;
PRINT N'  Phân công       : ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

DELETE FROM TeacherEvaluation WHERE PeriodNote LIKE N'%2025–2026%';
PRINT N'  Đánh giá        : ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

DROP TABLE #Asg, #Sched, #Att;

COMMIT TRANSACTION;
GO

PRINT N'>> Đã gỡ xong. Dữ liệu năm học 2026–2027 giữ nguyên.';
GO
