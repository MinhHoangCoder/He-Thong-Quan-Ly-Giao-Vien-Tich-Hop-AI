# 2026-07-09 — backend: mở lại `/uploads` (thu hẹp đúng `/uploads/lessons/**`)

## 1. Bối cảnh — vì sao có bug

Dev-note `2026-06-19-backend-lesson-api-crud-upload.md` (mục 5) ghi: khi làm module
Bài giảng đã **thêm `/uploads/**` vào `permitAll()`** trong `SecurityConfig` để client
xem/tải file đính kèm không cần JWT.

Nhưng khi **revert nhánh lesson** (commit `90911e4`), dòng `permitAll()` đó bị mất
theo, trong khi:

- `WebConfig` **vẫn** serve `/uploads/**` từ thư mục `uploads/` trên đĩa.
- FE **vẫn** mở file bằng thẻ `<a :href="fileUrl" target="_blank">`
  (`TeacherLessonViewPage.vue`, `LessonFormPage.vue`).

→ Thẻ `<a>`/`<img>` **không gửi header `Authorization`**, mà `SecurityConfig` lại
`anyRequest().authenticated()` ⇒ mọi request tới `/uploads/...` trả **401**, người
dùng **không tải được** file bài giảng. Đây là regression, không phải thiết kế.

## 2. Sửa gì

Khôi phục quyền truy cập công khai cho file bài giảng, nhưng **thu hẹp phạm vi** so
với docs cũ (an toàn hơn):

```java
.requestMatchers(HttpMethod.GET, "/uploads/lessons/**")
.permitAll()
```

- **Chỉ `/uploads/lessons/**`**: `LessonService` luôn sinh `FileUrl` dạng
  `/uploads/lessons/{lessonId}/<uuid>.<ext>`, nên chỉ cần mở đúng thư mục con này.
  Nếu sau này có module đặt file **nhạy cảm** (scan hợp đồng, CCCD…) vào thư mục
  `uploads/` khác thì chúng **vẫn cần đăng nhập** — không bị lộ theo kiểu `/uploads/**`.
- **Chỉ method GET**: file là tài nguyên chỉ-đọc; POST/PUT/DELETE tới path này không
  có handler và không nên mở công khai.

Vị trí: đặt matcher này **trước** `.anyRequest().authenticated()` (thứ tự rule trong
Spring Security có ý nghĩa — rule khớp đầu tiên thắng).

## 3. Ranh giới trách nhiệm

`SecurityConfig` thuộc phần **base security** (user phụ trách) nên sửa ở đây không đụng
code module Bài giảng/Giáo viên của thành viên khác. Các phát hiện còn lại trong đợt rà
soát (GET giáo viên chưa `@PreAuthorize`, validate upload, `@PreAuthorize` neo role-name
ở `TeacherController`/`LessonController`) nằm ở **module của thành viên khác** → báo cho
người phụ trách xử lý, không tự sửa để tránh conflict.

## 4. Kiểm chứng

- `./mvnw spotless:apply compile` — **pass** (JDK 21).
- Chưa chạy E2E qua app thật ở đợt này (cần SQL Server + file trên đĩa); thay đổi chỉ là
  một security matcher, rủi ro thấp, đã đối chiếu đường dẫn `FileUrl` do `LessonService`
  sinh ra. Khi chạy app: GET `/uploads/lessons/{id}/<file>` (không token) → **200**;
  GET một `/uploads/<khác>/...` (nếu có) → vẫn **401**.
