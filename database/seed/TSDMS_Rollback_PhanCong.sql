/* =====================================================================
   TSDMS — GỠ SEED LUỒNG ĐIỀU PHỐI
   ---------------------------------------------------------------------
   Đảo ngược database/seed/TSDMS_Seed_PhanCong.sql, xóa theo đúng thứ tự
   khóa ngoại: lời mời → lương → chấm công (+ nhật ký) → buổi dạy (+ nhật ký)
   → ô thời khóa biểu → phiếu phân công → lớp năm học 2025-2026.

   ⚠ FILE NÀY XÓA TOÀN BỘ dữ liệu phân công / chấm công / lương trong DB, chứ
   không chỉ phần do seed sinh ra: bốn bảng đó rỗng trước khi seed chạy nên
   "toàn bộ" và "phần của seed" là một. Nếu sau đó đã có người nhập liệu thật
   trên giao diện thì dữ liệu ấy CŨNG MẤT — chỉ chạy trên máy demo.

   CHỐT CHẶN AN TOÀN: dừng lại nếu lớp năm học 2025-2026 đã có học sinh ghi
   danh (dấu hiệu dữ liệu đã được dùng thật, không còn là data demo).
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
BEGIN TRANSACTION;

IF EXISTS (
    SELECT 1 FROM ClassEnrollment e
    JOIN SchoolClass c ON c.Id = e.ClassId
    WHERE c.SchoolYear = '2025-2026')
    THROW 50010, N'Lớp năm học 2025-2026 đã có học sinh ghi danh — dừng lại, không xóa mù.', 1;

DECLARE @n INT;

DELETE n FROM Notification n
 WHERE n.RefEntity = 'Assignment'
   AND EXISTS (SELECT 1 FROM Assignment a WHERE a.Id = n.RefId);
SET @n = @@ROWCOUNT; PRINT N'1) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' lời mời dạy.';

DELETE FROM Payroll;
SET @n = @@ROWCOUNT; PRINT N'2) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' dòng lương.';

DELETE FROM AttendanceChangeLog;
DELETE FROM AttendanceAmendRequest;
DELETE FROM Attendance;
SET @n = @@ROWCOUNT; PRINT N'3) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' dòng chấm công (kèm nhật ký).';

DELETE FROM ScheduleStatusLog;
DELETE FROM Schedule;
SET @n = @@ROWCOUNT; PRINT N'4) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' buổi dạy (kèm nhật ký trạng thái).';

DELETE FROM AssignmentSlot;
SET @n = @@ROWCOUNT; PRINT N'5) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' ô thời khóa biểu.';

DELETE FROM Assignment;
SET @n = @@ROWCOUNT; PRINT N'6) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' phiếu phân công.';

DELETE FROM SchoolClass WHERE SchoolYear = '2025-2026';
SET @n = @@ROWCOUNT; PRINT N'7) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' lớp của năm học 2025-2026.';

COMMIT TRANSACTION;
PRINT N'';
PRINT N'>>> XONG — DB trở lại trạng thái trước khi chạy TSDMS_Seed_PhanCong.sql.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    PRINT N'!!! LỖI — đã rollback toàn bộ, DB giữ nguyên.';
    THROW;
END CATCH
GO
