/* =====================================================================
   TSDMS - V13: CHUẨN HÓA FileUrl CŨ CỦA LessonFile VỀ ĐƯỜNG DẪN TƯƠNG ĐỐI
   ---------------------------------------------------------------------
   Lỗi gốc: trước PR #71 (13/07), khi upload file bài giảng backend lưu vào
   LessonFile.FileUrl NGUYÊN đường dẫn tuyệt đối của máy chủ, ví dụ:
       /D:\...\backend\uploads\lessons/12/a1b2-c3.pdf
   PR #60 + #71 đã sửa CODE: file mở qua API download có kiểm quyền, và
   upload mới chỉ lưu đường dẫn tương đối dạng {LessonId}/{tênFileLưu}
   (LessonService.uploadFiles). Nhưng DỮ LIỆU cũ trong DB không ai dọn:
   bản ghi upload trước 13/07 vẫn giữ chuỗi tuyệt đối, endpoint download
   resolve thất bại -> bấm "Xem/Tải" file cũ trả 400/404 dù file còn trên đĩa.

   Cách vá: cắt lấy đúng tên file lưu (đoạn sau dấu '/' hoặc '\' cuối cùng)
   rồi ghép lại '{LessonId}/{tênFileLưu}' từ chính cột LessonId có sẵn.
   Chỉ đụng bản ghi dạng cũ (bắt đầu '/' hoặc chứa ':' của ổ đĩa/URL):
   - Bản ghi đã đúng dạng mới '12/abc.pdf' không khớp điều kiện -> giữ nguyên.
   - FileType 'canva' bỏ qua: FileUrl của canva là URL ngoài thật (https://...),
     cố tình chứa ':' — không phải file vật lý, không được đụng.
   Idempotent: chạy lại lần nữa thì không còn dòng nào khớp điều kiện.

   Lưu ý: sau khi chuẩn hóa, file chỉ mở được nếu file vật lý thật sự nằm
   trong backend/uploads/lessons/ của máy đang chạy (file do máy khác upload
   thì vốn không có trên đĩa máy này -> 404 là đúng, không phải lỗi migration).
   ===================================================================== */

UPDATE LessonFile
SET FileUrl = CAST(LessonId AS VARCHAR(10)) + '/'
            + RIGHT(FileUrl, PATINDEX('%[/\]%', REVERSE(FileUrl)) - 1)
WHERE FileType <> 'canva'
  AND (FileUrl LIKE '/%' OR FileUrl LIKE '%:%')
  AND PATINDEX('%[/\]%', REVERSE(FileUrl)) > 1;
