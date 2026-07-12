/* =====================================================================
   PATCH 2026-07-11 — LessonFile: thay seed file vật lý bằng link Canva demo

   Dành cho DB ĐANG CÓ SẴN dữ liệu seed cũ (không muốn seed lại từ đầu).
   DB dựng mới từ TSDMS_Seed_Demo.sql bản mới KHÔNG cần chạy file này.

   Lý do (xem dev-note 2026-07-11-db-seed-lessonfile-canva.md):
   - 7 dòng seed cũ trỏ file vật lý /uploads/lessons/<tên> không tồn tại
     trên đĩa → nút "Tải về" demo luôn 404.
   - 1 dòng fileType='link' (youtube) cũng vỡ: FE chỉ window.open cho
     fileType='canva', loại khác bị đẩy vào luồng tải file vật lý.

   CÁCH CHẠY (bắt buộc cờ -I vì LessonFile có filtered index):
   sqlcmd -S localhost -E -C -I -d TSDMS -f 65001 -i 2026-07-11-lessonfile-canva.sql
   (hoặc -U sa -P <mật khẩu> thay cho -E)

   Idempotent: chạy lại lần 2 không tạo thêm dòng trùng.
   ===================================================================== */
SET NOCOUNT ON;

DECLARE @Admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');

/* 1) Xóa mềm các dòng seed cũ bị hỏng:
      - đường dẫn phẳng /uploads/lessons/<tên> (KHÔNG có thư mục con {id} —
        phân biệt với file upload thật /uploads/lessons/{id}/<uuid> để
        KHÔNG đụng vào dữ liệu người dùng upload)
      - link youtube demo cũ                                              */
UPDATE LessonFile
SET IsDeleted = 1, DeletedAt = SYSUTCDATETIME(), DeletedBy = @Admin
WHERE IsDeleted = 0
  AND ( (FileUrl LIKE '/uploads/lessons/%' AND FileUrl NOT LIKE '/uploads/lessons/%/%')
        OR FileUrl = 'https://youtu.be/demo-robot-lap-rap' );
PRINT CONCAT(N'Đã xóa mềm dòng seed cũ: ', @@ROWCOUNT);

/* 2) Thêm 1 link Canva demo cho mỗi bài giảng seed — bỏ qua bài đã có
      link canva đang sống (khớp logic 1-canva/bài của addCanvaLink).     */
INSERT INTO LessonFile (LessonId, FileName, FileUrl, FileType, FileSizeKb, CreatedBy)
SELECT l.Id, v.FileName, 'https://www.canva.com/', 'canva', NULL, l.CreatedBy
FROM (VALUES
  (N'Giới thiệu lập trình Scratch',            N'Slide Canva — Giới thiệu Scratch'),
  (N'Scratch: Vòng lặp và điều kiện',          N'Slide Canva — Vòng lặp và điều kiện'),
  (N'Lắp ráp robot đầu tiên',                  N'Slide Canva — Lắp ráp robot đầu tiên'),
  (N'Python: Biến và kiểu dữ liệu',            N'Slide Canva — Python: Biến và kiểu dữ liệu'),
  (N'Định danh số và dấu chân số',             N'Slide Canva — Định danh số và dấu chân số'),
  (N'Nhận diện lừa đảo trực tuyến (bản 2025)', N'Slide Canva — Nhận diện lừa đảo (2025)')
) v(Title, FileName)
JOIN Lesson l ON l.Title = v.Title
WHERE NOT EXISTS (SELECT 1 FROM LessonFile x
                  WHERE x.LessonId = l.Id AND x.FileType = 'canva' AND x.IsDeleted = 0);
PRINT CONCAT(N'Đã thêm link canva: ', @@ROWCOUNT);
