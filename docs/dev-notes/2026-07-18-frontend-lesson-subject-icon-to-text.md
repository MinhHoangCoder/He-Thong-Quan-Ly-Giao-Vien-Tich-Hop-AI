# 2026-07-18 — frontend: bỏ icon emoji, thay bằng chữ ở khu vực Bài giảng & Nhóm môn học

Yêu cầu: các nút/nhãn đang dùng icon emoji trong khu vực Lesson (Bài giảng) và
Subject (Nhóm môn/Môn học) đổi hết sang chữ. Mục tiêu là đồng bộ với
`SubjectCategoryListPage.vue` — trang này vốn đã dùng chữ "Sửa" / "Xóa" / "Thêm"
từ đầu, không có icon nào cả, nên coi đây là chuẩn để 3 trang Lesson còn lại
(vốn còn sót icon từ hồi mới code) chạy theo.

## 1. Các thay đổi

| File | Trước | Sau |
|---|---|---|
| `LessonListPage.vue` | nút Sửa/Xóa trong bảng chỉ có ✏️ / 🗑️ (đã có sẵn `title` tooltip) | chữ "Sửa" / "Xóa" |
| `TeacherLessonListPage.vue` | nút Xem chỉ có 👁 | chữ "Xem" |
| `LessonFormPage.vue` | nút "← Danh sách"; tiêu đề card "📄 Tải lên giáo án" / "🎨 Tải lên bài giảng"; icon loại file (🎨/📊/📄/📎) cạnh tên file đính kèm; nút xóa file chỉ có ✕ | bỏ mũi tên (giữ chữ "Danh sách"); bỏ icon trong tiêu đề card; **xóa hẳn** span icon loại file (vì `fileLabel()` đã hiện chữ loại file ngay dưới tên file rồi, icon chỉ dư); nút xóa file đổi thành chữ "Xóa" / lúc đang xóa hiện "Đang xóa…" |
| `TeacherLessonViewPage.vue` | nút "← Quay lại"; icon loại file 🎨/📄 cạnh tên file | bỏ mũi tên (giữ chữ "Quay lại"); icon loại file đổi thành chữ "Canva" / "PDF" |

`fileIcon(type)` trong `LessonFormPage.vue` bị xóa luôn vì sau khi bỏ icon
không còn nơi nào gọi hàm này nữa (đã grep toàn repo để chắc).

## 2. Cố tình KHÔNG đổi

- Mũi tên phân trang `« ‹ › »` — đây là điều hướng trang, không phải "chức năng"
  riêng của lesson/subject. `SubjectCategoryListPage.vue` (trang chuẩn, đã
  không-icon từ đầu) vẫn giữ nguyên `‹ ›`, kể cả dấu `›` dùng làm mũi tên mở/thu
  gọn danh sách môn học trong nhóm.
- Dấu "+" trước "Thêm bài giảng" / "Thêm nhóm môn" / "Thêm môn học" — ký tự
  thường, không phải icon kiểu emoji.
- 2 mũi tên `→` trong comment code (`LessonFormPage.vue` dòng 4 và 47) — comment
  giải thích logic cho dev đọc, không hiển thị ra UI.

Nếu bạn muốn đổi luôn mấy chỗ này thì nói mình làm tiếp.

## 3. Kiểm chứng

- Scan lại toàn bộ 5 file Lesson + Subject bằng regex Unicode cho icon/emoji:
  không còn ký tự nào ngoài 2 comment và mấy mũi tên phân trang nói ở mục 2.
- `npx eslint` trên 4 file đã sửa: pass, không lỗi/cảnh báo.
- CSS `.act-btn` ở 2 trang danh sách (Lesson, Teacher-Lesson) chỉnh lại
  `font-size`/`padding`: icon cũ để `font-size: 17–18px` cho to bằng emoji, chữ
  thì không cần to vậy nên hạ về `13px` + `font-weight: 500` cho rõ, đồng thời
  thêm khoảng cách giữa 2 nút Sửa/Xóa (`margin-right`) vì trước giờ dựa vào
  khoảng trắng giữa 2 emoji, giờ chữ dính nhau nếu không có margin.
