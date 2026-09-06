/* =====================================================================
   TSDMS — GỠ SEED CHẤM CÔNG + BẢNG LƯƠNG KỲ 9/2026
   ---------------------------------------------------------------------
   Đảo ngược TSDMS_Seed_ChamCong_2026_09.sql. Chạy TRƯỚC
   TSDMS_Rollback_PhanCong_2026_09.sql (chấm công treo vào buổi dạy).

   PHẠM VI: chỉ đụng vào khoảng 03/09 → 06/09/2026 và kỳ lương 9/2026.
   Chấm công cùng các kỳ lương của năm học 2025-2026 KHÔNG bị chạm tới —
   đó là phần lịch sử mà seed cũng đã cố ý giữ lại.

   ⚠ Không khôi phục được dữ liệu chấm công có TRƯỚC khi seed chạy: seed đã
   xóa nó đi rồi. Muốn quay về nguyên trạng thì phục hồi bản sao lưu toàn bộ
   CSDL đã tạo trước lúc seed.
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @tuNgay  DATE = '2026-09-03';
DECLARE @denNgay DATE = '2026-09-06';

/* 1) Trả buổi "Nghỉ có phép" về trạng thái đã duyệt như trước khi seed đụng vào.
      Set UpdatedBy trước khi đổi Status — trigger TR_Schedule_StatusLog đọc cột đó. */
UPDATE Schedule
SET UpdatedBy  = (SELECT MIN(Id) FROM AppUser WHERE Username = 'admin'),
    UpdatedAt  = SYSUTCDATETIME(),
    Status     = 'APPROVED',
    CancelKind = NULL
WHERE CancelKind = 'LEAVE'
  AND CAST(StartTime AS DATE) BETWEEN @tuNgay AND @denNgay;

/* 2) Đơn xin nghỉ do seed dựng. */
DELETE FROM AssignmentLeaveRequest
WHERE EffectiveDate BETWEEN @tuNgay AND @denNgay;

/* 3) Chấm công của khoảng ngày này (+ nhật ký treo vào nó). */
DELETE FROM AttendanceChangeLog
WHERE AttendanceId IN (SELECT Id FROM Attendance WHERE WorkDate BETWEEN @tuNgay AND @denNgay);
DELETE FROM AttendanceAmendRequest
WHERE ScheduleId IN (SELECT Id FROM Schedule WHERE CAST(StartTime AS DATE) BETWEEN @tuNgay AND @denNgay);
DELETE FROM Attendance WHERE WorkDate BETWEEN @tuNgay AND @denNgay;

/* 4) Phiếu lương nháp kỳ 9/2026. Chỉ DRAFT — nếu ai đã chốt kỳ này sau khi
      seed chạy thì dừng tay, để người dùng tự quyết định thay vì xóa ngầm. */
IF EXISTS (SELECT 1 FROM Payroll WHERE PeriodYear = 2026 AND PeriodMonth = 9 AND Status <> 'DRAFT')
BEGIN
    RAISERROR (N'Kỳ lương 9/2026 đã được chốt hoặc đã trả — không tự xóa. Mở lại kỳ rồi chạy lại nếu thật sự muốn gỡ.', 16, 1);
END
ELSE
BEGIN
    DELETE FROM PayrollChangeLog
    WHERE PayrollId IN (SELECT Id FROM Payroll WHERE PeriodYear = 2026 AND PeriodMonth = 9);
    DELETE FROM Payroll WHERE PeriodYear = 2026 AND PeriodMonth = 9;
END
GO

SELECT N'Chấm công còn lại 03–06/09' AS chiTieu, COUNT(*) AS soLuong
FROM Attendance WHERE WorkDate BETWEEN '2026-09-03' AND '2026-09-06'
UNION ALL
SELECT N'Phiếu lương kỳ 9/2026 còn lại', COUNT(*) FROM Payroll
 WHERE PeriodYear = 2026 AND PeriodMonth = 9;
GO
