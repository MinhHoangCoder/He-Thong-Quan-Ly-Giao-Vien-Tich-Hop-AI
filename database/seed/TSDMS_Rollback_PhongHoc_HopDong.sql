/* =====================================================================
   TSDMS — GỠ SEED PHÒNG HỌC + HỢP ĐỒNG DỊCH VỤ
   ---------------------------------------------------------------------
   Đảo ngược database/seed/TSDMS_Seed_PhongHoc_HopDong.sql.

   CHỐT CHẶN AN TOÀN: dừng lại nếu có phòng nào đã được xếp vào lịch dạy
   (`AssignmentSlot.RoomId` hoặc `Schedule.RoomId` trỏ tới) — xóa phòng lúc đó
   sẽ làm gãy khóa ngoại hoặc để lại buổi dạy trỏ vào phòng không tồn tại.

   ⚠ Xóa TOÀN BỘ hai bảng, không chỉ phần seed sinh ra: cả hai rỗng trước khi
   seed chạy nên "toàn bộ" và "phần của seed" là một. Chỉ chạy trên máy demo.
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
BEGIN TRANSACTION;

IF EXISTS (SELECT 1 FROM AssignmentSlot WHERE RoomId IS NOT NULL)
    OR EXISTS (SELECT 1 FROM Schedule WHERE RoomId IS NOT NULL)
    THROW 50032, N'Có phòng học đã được xếp vào lịch dạy — gỡ phân công/lịch trước, hoặc bỏ phòng khỏi các tiết đó rồi chạy lại.', 1;

DECLARE @n INT;

DELETE FROM ServiceContract;
SET @n = @@ROWCOUNT; PRINT N'1) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' hợp đồng dịch vụ.';

DELETE FROM Room;
SET @n = @@ROWCOUNT; PRINT N'2) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' phòng học.';

COMMIT TRANSACTION;
PRINT N'';
PRINT N'>>> XONG — DB trở lại trạng thái trước khi chạy TSDMS_Seed_PhongHoc_HopDong.sql.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    PRINT N'!!! LỖI — đã rollback, DB giữ nguyên.';
    THROW;
END CATCH
GO
