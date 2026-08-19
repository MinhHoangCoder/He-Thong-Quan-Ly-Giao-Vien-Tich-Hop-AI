/* =====================================================================
   TSDMS — GỠ SEED ĐÁNH GIÁ GIÁO VIÊN
   ---------------------------------------------------------------------
   Đảo ngược database/seed/TSDMS_Seed_DanhGia.sql: xóa các đánh giá thuộc hai
   kỳ do seed sinh ra (`HK2 2025-2026` và `HK1 2026-2027`).

   KHÔNG khôi phục lại dòng test cũ (điểm 1, nội dung tục tĩu) — đó là rác, gỡ
   seed không có nghĩa là dựng rác dậy.

   Đánh giá do người thật nhập trên giao diện ở HAI KỲ NÀY cũng sẽ mất theo.
   Chỉ chạy trên máy demo.
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
BEGIN TRANSACTION;

DECLARE @n INT;

DELETE FROM TeacherEvaluation
WHERE PeriodNote IN (N'HK2 2025-2026', N'HK1 2026-2027');
SET @n = @@ROWCOUNT;

COMMIT TRANSACTION;
PRINT N'>>> XONG — đã xóa ' + CAST(@n AS NVARCHAR(10)) + N' đánh giá của hai kỳ seed.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    PRINT N'!!! LỖI — đã rollback, DB giữ nguyên.';
    THROW;
END CATCH
GO
