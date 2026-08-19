/* =====================================================================
   TSDMS — GỠ SEED KHO BÀI GIẢNG
   ---------------------------------------------------------------------
   Đảo ngược database/seed/TSDMS_Seed_BaiGiang.sql: xóa học liệu rồi xóa bài
   giảng do seed sinh ra.

   NHẬN DIỆN BÀI CỦA SEED: tiêu đề kết thúc bằng " — Lớp N". Bài do người thật
   soạn trên giao diện gần như chắc chắn không đặt tên theo đúng khuôn đó, nên
   xóa theo dấu hiệu này an toàn hơn nhiều so với DELETE sạch bảng.

   KHÔNG khôi phục bài "abc" và 7 file PDF mồ côi đã dọn — đó là rác.
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

CREATE TABLE #Xoa (LessonId INT PRIMARY KEY);
INSERT INTO #Xoa (LessonId)
SELECT Id FROM Lesson
WHERE Title LIKE N'%— Lớp [1-9]';

DELETE lf FROM LessonFile lf JOIN #Xoa x ON x.LessonId = lf.LessonId;
SET @n = @@ROWCOUNT; PRINT N'1) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' học liệu đính kèm.';

DELETE l FROM Lesson l JOIN #Xoa x ON x.LessonId = l.Id;
SET @n = @@ROWCOUNT; PRINT N'2) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' bài giảng.';

DROP TABLE #Xoa;

COMMIT TRANSACTION;
PRINT N'';
PRINT N'>>> XONG — kho bài giảng trở lại trạng thái trước khi chạy TSDMS_Seed_BaiGiang.sql.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DROP TABLE IF EXISTS #Xoa;
    PRINT N'!!! LỖI — đã rollback, DB giữ nguyên.';
    THROW;
END CATCH
GO
