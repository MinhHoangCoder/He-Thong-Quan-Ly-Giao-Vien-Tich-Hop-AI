# 2026-07-11 — DB seed: LessonFile bỏ file vật lý "ma", thay bằng link Canva demo

Việc cuối trong cụm dọn hạ tầng uploads sau PR #60 (2 việc trước: xóa WebConfig
static mount + gitignore `uploads/` — xem dev-note 2026-07-11-backend-xoa-webconfig-uploads-gitignore.md).

## 1. Vấn đề

Seed cũ (`TSDMS_Seed_Demo.sql` mục 26) chèn 8 dòng `LessonFile`:

- **7 dòng file vật lý** dạng `/uploads/lessons/<tên>.pdf|pptx` — hỏng kép:
  1. Không có file thật nào trên đĩa (và giờ cũng **không thể** ship theo repo
     vì `uploads/` đã gitignore).
  2. Sai cả cấu trúc: endpoint download (`LessonService.openFile`) **tự dựng lại**
     đường dẫn từ `uploads/lessons/{LessonId}/<tên file cắt từ FileUrl>` — tức là
     kể cả có file nằm phẳng đúng như FileUrl ghi, server vẫn đi tìm trong thư mục
     con `{id}/` và trả 404. Cấu trúc chuẩn của hệ thống là
     `/uploads/lessons/{LessonId}/<uuid>.<ext>` (điều LessonService thật sự ghi).
- **1 dòng `fileType='link'`** (youtube demo) — cũng vỡ từ PR #60: FE chỉ
  `window.open` cho `fileType='canva'`; mọi loại khác bị đẩy vào luồng gọi API
  tải file vật lý → 404.

Hậu quả: trong demo, bấm "Tải về"/"Xem" trên MỌI file đính kèm seed đều lỗi.

## 2. Cách sửa

Nguyên tắc: **seed chỉ chứa dữ liệu không phụ thuộc đĩa**. Loại duy nhất thỏa là
`canva` (URL ngoài). Muốn demo file pdf/pptx thật → upload trực tiếp qua UI (luồng
upload/download đã chạy tốt từ PR #60).

- `TSDMS_Seed_Demo.sql` mục 26: 8 dòng cũ → **6 dòng `canva`** (mỗi bài giảng đúng
  1 link — khớp logic update-in-place của `LessonService.addCanvaLink`: mỗi bài chỉ
  có 1 link Canva "chính"). URL dùng `https://www.canva.com/` — trang thật, mở được,
  không giả mạo link design cụ thể (link design bịa sẽ 404 xấu demo hơn).
- Thêm `database/seed/patches/2026-07-11-lessonfile-canva.sql` cho các máy **đang
  có DB seed cũ** (đỡ phải dựng lại DB): xóa mềm 8 dòng hỏng + chèn 6 dòng canva.
  - Idempotent (chạy lại không tạo trùng).
  - Điều kiện lọc `NOT LIKE '/uploads/lessons/%/%'` để chỉ đụng dòng seed phẳng,
    **không** đụng file người dùng upload thật (`/uploads/lessons/{id}/<uuid>`).
  - Nhớ cờ `-I`: `LessonFile` có filtered index (`IX_LessonFile_Lesson WHERE
    IsDeleted=0`) — cùng bẫy QUOTED_IDENTIFIER như `AppUser`, sqlcmd mặc định OFF
    sẽ fail `Msg 1934` khi INSERT/UPDATE.

## 3. Đã chạy & kiểm chứng trên DB local

- Lần 1: xóa mềm 8, chèn 6. Lần 2 (kiểm idempotent): 0/0.
- SELECT sau patch: 6 dòng canva sống, đúng 1 dòng/bài, khớp trạng thái bài giảng
  (bài DRAFT/ARCHIVED giữ link — chỉ admin/staff thấy; teacher chỉ thấy PUBLISHED
  theo rule forcePublished sẵn có).

## 4. Ghi chú cho team lesson (không sửa trong đợt này)

Loại `fileType='link'` (video…) hiện không có đường hiển thị nào chạy được trên FE.
Nếu muốn hỗ trợ lại, cách gọn nhất là FE mở `window.open` cho **mọi** `fileUrl`
bắt đầu bằng `http` thay vì chỉ check `fileType==='canva'` (backend `openFile`
cũng đã có nhánh redirect tương ứng cho canva có thể mở rộng theo).
