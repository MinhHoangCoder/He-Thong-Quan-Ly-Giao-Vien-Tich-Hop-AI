/* =====================================================================
   TSDMS — GỠ SEED PHÂN CÔNG HỌC KỲ I 2026-2027
   ---------------------------------------------------------------------
   Đảo ngược TSDMS_Seed_PhanCong_2026_09.sql. Chạy SAU
   TSDMS_Rollback_ChamCong_2026_09.sql, vì chấm công treo vào buổi dạy.

   PHẠM VI: chỉ xóa các phiếu có StartDate = 03/09/2026 (đúng lô do seed sinh
   ra) cùng mọi thứ treo vào chúng. 280 phiếu COMPLETED của năm học 2025-2026
   KHÔNG bị chạm tới — seed cũng đã cố ý giữ chúng.

   ⚠ Không khôi phục được 141 phiếu ACTIVE mà seed đã xóa. Muốn quay về đúng
   nguyên trạng thì phục hồi bản sao lưu toàn bộ CSDL tạo trước lúc seed:
       RESTORE DATABASE TSDMS FROM DISK = N'TSDMS_truoc_seed_20260906.bak'
       WITH REPLACE;
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @tuNgay DATE = '2026-09-03';

IF OBJECT_ID('tempdb..#PhieuGo') IS NOT NULL DROP TABLE #PhieuGo;
SELECT Id INTO #PhieuGo FROM Assignment WHERE StartDate = @tuNgay AND Status <> 'COMPLETED';

IF OBJECT_ID('tempdb..#BuoiGo') IS NOT NULL DROP TABLE #BuoiGo;
SELECT s.Id INTO #BuoiGo FROM Schedule s JOIN #PhieuGo p ON p.Id = s.AssignmentId;
CREATE UNIQUE CLUSTERED INDEX IX_BuoiGo ON #BuoiGo(Id);

/* Đi ngược chiều khóa ngoại: nhật ký → bảng con → bảng cha. */
DELETE n FROM Notification n
WHERE (n.RefEntity = 'Assignment' AND n.RefId IN (SELECT Id FROM #PhieuGo))
   OR n.RefEntity = 'AssignmentLeaveRequest';

DELETE FROM AttendanceChangeLog
WHERE AttendanceId IN (SELECT a.Id FROM Attendance a JOIN #BuoiGo b ON b.Id = a.ScheduleId);
DELETE FROM AttendanceAmendRequest WHERE ScheduleId IN (SELECT Id FROM #BuoiGo);
DELETE FROM Attendance WHERE ScheduleId IN (SELECT Id FROM #BuoiGo);

DELETE FROM ScheduleStatusLog WHERE ScheduleId IN (SELECT Id FROM #BuoiGo);
DELETE FROM Schedule WHERE Id IN (SELECT Id FROM #BuoiGo);

DELETE FROM AssignmentLeaveRequest WHERE AssignmentId IN (SELECT Id FROM #PhieuGo);
DELETE FROM AssignmentSlot WHERE AssignmentId IN (SELECT Id FROM #PhieuGo);
DELETE FROM Assignment WHERE Id IN (SELECT Id FROM #PhieuGo);
GO

SELECT N'Phiếu còn lại (phải chỉ còn COMPLETED)' AS chiTieu, Status, COUNT(*) AS soLuong
FROM Assignment WHERE IsDeleted = 0 GROUP BY Status;
GO
