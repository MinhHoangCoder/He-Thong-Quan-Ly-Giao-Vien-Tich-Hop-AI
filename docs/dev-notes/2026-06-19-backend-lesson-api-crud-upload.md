# 2026-06-15 — backend: API module Bài giảng (Lesson) — CRUD + Upload file đính kèm

## 1. Làm gì trong đợt này?

Hoàn thiện phần backend còn thiếu của module Bài giảng đã ghi trong
`2026-06-19-database-lesson-module-seed.md` (mục 6 — "Việc còn lại"):

1. **DTO**: `LessonRequest` (tạo/sửa), `LessonResponse` (chi tiết + file đính kèm),
   `LessonSummary` (1 dòng trong danh sách), `LessonFileResponse` (file đính kèm).
2. **Repository**: thêm method vào `LessonRepository` (`findByIdAndDeletedFalse`,
   `search` có phân trang/lọc) và `LessonFileRepository` (`findByLessonId`,
   `findByIdAndDeletedFalse`).
3. **Service**: `LessonService` — danh sách (phân trang/lọc), chi tiết, tạo, sửa,
   xóa mềm, và upload/xóa file đính kèm (lưu file thật vào `uploads/lessons/{id}/`).
4. **Controller**: `LessonController` — `/api/v1/lessons` (REST đầy đủ).
5. **Config**: `WebConfig` (serve `/uploads/**` từ thư mục `uploads/` trên đĩa) +
   thêm `/uploads/**` vào `permitAll()` trong `SecurityConfig`.

## 2. Endpoint

| Method | Path                                  | Quyền           | Ghi chú                                                                   |
| ------ | ------------------------------------- | --------------- | ------------------------------------------------------------------------- |
| GET    | `/api/v1/lessons`                     | `LESSON_VIEW`   | Phân trang (`page`,`size`), lọc `subjectId`/`branchId`/`status`/`keyword` |
| GET    | `/api/v1/lessons/{id}`                | `LESSON_VIEW`   | Chi tiết, gồm `content` + danh sách file                                  |
| POST   | `/api/v1/lessons`                     | `LESSON_MANAGE` | Tạo mới, body = `LessonRequest` (JSON)                                    |
| PUT    | `/api/v1/lessons/{id}`                | `LESSON_MANAGE` | Sửa, body = `LessonRequest` (JSON)                                        |
| DELETE | `/api/v1/lessons/{id}`                | `LESSON_MANAGE` | Xóa mềm                                                                   |
| GET    | `/api/v1/lessons/{id}/files`          | `LESSON_VIEW`   | Danh sách file đính kèm                                                   |
| POST   | `/api/v1/lessons/{id}/files`          | `LESSON_MANAGE` | Upload (multipart/form-data, field `files`, nhiều file)                   |
| DELETE | `/api/v1/lessons/{id}/files/{fileId}` | `LESSON_MANAGE` | Xóa mềm 1 file đính kèm                                                   |

`ADMIN` luôn đi tắt qua mọi `@PreAuthorize` theo quy ước chung (mục 5b RBAC doc).

## 3. TEACHER chỉ xem bài PUBLISHED — xử lý ở đâu?

`LESSON_VIEW` được cấp cho cả `ACADEMIC` và `TEACHER`, nhưng theo chú thích¹ của ma
trận RBAC: ô của TEACHER **phải kèm lọc ownership/đúng-phạm-vi** ở service.

- `LessonController.forcePublishedForCurrentUser()`: nếu người gọi KHÔNG có role
  `ADMIN`/`ACADEMIC`/`EMPLOYEE` (tức là TEACHER, hoặc role khác chỉ có `LESSON_VIEW`)
  → ép `status = PUBLISHED`, bỏ qua `status` client gửi lên.
- `LessonService.getDetail(...)`/`listFiles(...)`: nếu `forcePublished=true` mà bài
  giảng không phải `PUBLISHED` → trả `404` (coi như không tồn tại với người xem này,
  không lộ thông tin bài DRAFT/ARCHIVED).

`SCHOOL` không có `LESSON_VIEW`/`LESSON_MANAGE` trong ma trận → mọi endpoint trả `403`.

## 4. Upload file đính kèm — lưu ở đâu, serve thế nào?

- File vật lý lưu vào `uploads/lessons/{lessonId}/<uuid>.<ext>` (thư mục `uploads/`
  tương đối **working directory** lúc chạy app — khi chạy bằng `mvnw spring-boot:run`
  từ `backend/`, đường dẫn thật là `backend/uploads/...`).
- Tên file gốc giữ trong `FileName` (hiển thị cho người dùng); tên trên đĩa là UUID
  để tránh trùng/ký tự đặc biệt.
- `FileUrl` lưu dạng `/uploads/lessons/{lessonId}/<uuid>.<ext>` — **khớp pattern**
  `/uploads/...` đã có trong `database/seed/TSDMS_Seed_Demo.sql` (8 file mẫu).
- `WebConfig` ánh xạ `/uploads/**` → thư mục `uploads/` trên đĩa.
- `SecurityConfig` thêm `/uploads/**` vào `permitAll()`: file đính kèm phục vụ như
  tài nguyên tĩnh công khai (không cần JWT để xem/tải qua link trực tiếp). Quyền
  XEM bài giảng (biết được `FileUrl` là gì) vẫn qua `LESSON_VIEW` ở API.
- Xóa file đính kèm chỉ **xóa mềm** record trong DB (`LessonFile.deleted=true`),
  KHÔNG xóa file vật lý khỏi đĩa — giữ khả năng khôi phục.

⚠️ **Việc cần làm thêm sau này (chưa làm đợt này)**:

- `uploads/` chưa có trong `.gitignore` — nên thêm để không commit file người dùng
  upload vào git.
- Chưa giới hạn loại file / kích thước file upload (Spring Boot default multipart
  size ~1MB) — nếu cần file lớn hơn, cấu thêm `spring.servlet.multipart.max-file-size`
  / `max-request-size` trong `application.yaml`.
- Chưa xóa file vật lý khi xóa mềm/xóa cứng — cron dọn rác sau nếu cần tiết kiệm
  dung lượng.

## 5. Xóa bài giảng — chỉ xóa mềm Lesson, KHÔNG cascade xóa LessonFile

`LessonService.delete(id)` chỉ đặt `Lesson.deleted = true`. Các `LessonFile` của
bài giảng đó **vẫn còn `deleted = false`** trong DB. Đây là lựa chọn đơn giản nhất
cho đợt này; nếu sau này muốn ẩn luôn file đính kèm khi bài giảng bị xóa, sửa
`delete()` để lặp `lessonFileRepo.findByLessonId(id)` và đánh dấu xóa mềm từng file
(đã ghi chú TODO ngay trong code).

## 6. Validate dữ liệu vào (LessonRequest)

- `subjectId`, `branchId`: bắt buộc, và **service kiểm tra tồn tại thật** trong
  `Subject`/`Branch` trước khi lưu (`validateReferences`) — tránh insert
  `SubjectId`/`BranchId` rác (FK constraint ở DB sẽ chặn nhưng lỗi FK trả về khó
  hiểu hơn `ApiException` rõ message).
- `teacherId`: tùy chọn (`null` được), nếu có thì cũng kiểm tra tồn tại trong `Teacher`.
- `status`/`difficultyLevel`: dùng `@Pattern` khớp đúng `CHECK` constraint ở DB
  (`DRAFT|PUBLISHED|ARCHIVED`, `BASIC|INTERMEDIATE|ADVANCED`) — sai giá trị bị chặn
  ở tầng validate (400) trước khi chạm DB.

## 7. Test nhanh gợi ý (Postman/curl, sau khi có JWT)

```
# Đăng nhập lấy access token (vd tài khoản 'daotao' — role ACADEMIC, có LESSON_MANAGE)
POST /api/v1/auth/login

# Danh sách bài giảng, trang 1, 5 dòng/trang, chỉ PUBLISHED
GET /api/v1/lessons?status=PUBLISHED&page=0&size=5

# Tạo bài giảng mới
POST /api/v1/lessons
{
  "subjectId": 1, "branchId": 1, "teacherId": null,
  "title": "Bài test", "duration": 45,
  "difficultyLevel": "BASIC", "status": "DRAFT"
}

# Upload file (multipart, field "files", có thể chọn nhiều file)
POST /api/v1/lessons/{id}/files

# Sửa
PUT /api/v1/lessons/{id}

# Xóa file đính kèm
DELETE /api/v1/lessons/{id}/files/{fileId}

# Xóa bài giảng
DELETE /api/v1/lessons/{id}
```

Tài khoản `teacher`/`teacher2`...`teacher6` (role TEACHER, có `LESSON_VIEW`) gọi
`GET /api/v1/lessons` sẽ luôn chỉ thấy bài `PUBLISHED`, dù truyền `status=DRAFT`.
Tài khoản `school`... gọi bất kỳ endpoint nào trong module này → `403`.

## 8. Còn lại cho FE (chưa làm đợt này)

- Trang danh sách/chi tiết/form tạo-sửa bài giảng cho portal Admin/Đào tạo
  (`frontend/src/pages`, thêm route vào `frontend/src/router/index.js`).
- Trang xem bài giảng (chỉ đọc, PUBLISHED) cho portal Giáo viên.
- Component upload file (input `type="file" multiple"` + gọi
  `POST /api/v1/lessons/{id}/files` bằng `FormData`).
