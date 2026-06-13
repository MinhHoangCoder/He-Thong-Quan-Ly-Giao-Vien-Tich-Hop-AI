# 2026-06-13 — database: Module Bài giảng (Lesson) + Seed demo toàn hệ thống

## 1. Làm gì trong đợt này?

1. Thêm **module Bài giảng**: 2 bảng mới `Lesson` (Bảng 28) và `LessonFile` (Bảng 29)
   → tổng schema giờ có **29 bảng**.
2. Tạo migration **`V2__lesson_module.sql`** cho Flyway (KHÔNG sửa V1 — xem mục 3).
3. Thêm 4 file Java: entity `Lesson`, `LessonFile` + repository tương ứng.
4. Tạo **seed demo** `database/seed/TSDMS_Seed_Demo.sql`: ≥5 dòng cho mọi bảng nghiệp vụ.

## 2. Thiết kế module Bài giảng

```
Subject ──┐
Teacher ──┼──< Lesson ──< LessonFile
Branch  ──┘
```

- **Lesson**: nhân viên trung tâm tạo/sửa/xóa; gắn môn học (bắt buộc), chi nhánh
  (bắt buộc), GV phụ trách soạn (tùy chọn — `TeacherId NULL`).
  Trường & giáo viên chỉ XEM bài đã `PUBLISHED`.
- **Vòng đời**: `DRAFT` (đang soạn) → `PUBLISHED` (phát hành) → `ARCHIVED` (lưu kho).
  Khớp 1-1 với chuỗi `status` trong entity Java (giống quy ước các bảng khác).
- **LessonFile**: nhiều file / 1 bài giảng. `FileUrl` chỉ là CHUỖI đường dẫn —
  việc upload file vật lý (lưu local folder hay S3) là việc của tầng service SAU NÀY.

### Điểm đã CẢI TIẾN so với bản nháp SQL ban đầu

| # | Cải tiến | Lý do |
|---|---|---|
| 1 | `LessonFile` thêm đủ bộ `DeletedAt/DeletedBy/UpdatedAt/UpdatedBy` | Bản nháp chỉ có `IsDeleted + CreatedAt/CreatedBy` — lệch quy ước chung. Thêm đủ bộ để entity **tái sử dụng `SoftDeletableEntity`** y hệt 20+ bảng khác, không phải map tay. |
| 2 | Gộp 2 index `IX_Lesson_Branch` + `IX_Lesson_Status` thành 1 index kép `IX_Lesson_Branch_Status (BranchId, Status) WHERE IsDeleted = 0` | Màn danh sách bài giảng luôn lọc "chi nhánh X" hoặc "chi nhánh X + trạng thái Y". Index kép phục vụ cả 2 truy vấn (quy tắc *leftmost prefix*), đỡ 1 index thừa khi INSERT/UPDATE. |
| 3 | Thêm `CK_LessonFile_Size CHECK (FileSizeKb > 0)` | Cùng tinh thần `CK_Lesson_Duration` — chặn dữ liệu rác từ tầng DB. |
| 4 | Sửa toàn bộ comment tiếng Việt bị lỗi font (mojibake) trong file nháp | File gốc bị lưu sai encoding. |

### Điểm GIỮ NGUYÊN (đã tốt sẵn)

- CHECK constraint cho `Status` / `DifficultyLevel` / `Duration` — khớp enum Java.
- Soft delete (`IsDeleted`) + index lọc `WHERE IsDeleted = 0`.
- `Content NVARCHAR(MAX)` cho nội dung rich text/markdown.

## 3. ⭐ Flyway: vì sao tạo V2 thay vì sửa V1? (quy tắc nhóm BẮT BUỘC)

Flyway lưu **checksum từng file migration đã chạy** vào bảng `flyway_schema_history`
trong DB. Khi app khởi động, Flyway so checksum file hiện tại với checksum đã lưu:

- Sửa `V1__init_schema.sql` (dù chỉ 1 dấu cách) → checksum lệch →
  **mọi máy đã từng chạy app bị lỗi `Migration checksum mismatch`**, backend không khởi động.
- Quy tắc: **file migration đã chạy = bất biến**. Muốn đổi schema → tạo file mới
  `V<số tiếp theo>__<mô tả>.sql` (lần này là `V2__lesson_module.sql`).

Thành viên khác chỉ cần `git pull` rồi chạy backend như bình thường —
Flyway thấy V2 chưa chạy trên máy họ và tự áp dụng. **Không ai phải làm gì thủ công.**

Lưu ý: file `database/schema/TSDMS_Schema.sql` (bản "trọn gói" chạy tay trong SSMS
cho máy mới tinh) thì VẪN sửa trực tiếp được — nó không thuộc Flyway. Hai bản phải
luôn được giữ ĐỒNG BỘ nội dung: `TSDMS_Schema.sql` = `V1 + V2 + ... + Vn`.

### Phát hiện khi kiểm tra máy dev: DB hiện tại CHƯA TỪNG chạy Flyway

DB `TSDMS` local không có bảng `flyway_schema_history` → DB được dựng bằng cách
chạy tay `TSDMS_Schema.sql`. Nhờ cấu hình `baseline-on-migrate: true`, lần chạy
backend tới Flyway sẽ: tạo bảng history → đánh dấu "baseline = version 1" (bỏ qua V1)
→ chạy V2 → 2 bảng Lesson xuất hiện. 

Vì nhóm có 2 cách dựng DB (chạy tay schema ĐÃ chứa Lesson, hoặc để Flyway chạy),
V2 được bọc `IF OBJECT_ID(N'dbo.Lesson', N'U') IS NULL` — bảng đã tồn tại thì V2
thành no-op, không vỡ ở cả hai luồng.

## 4. Entity & Repository — quy ước chống conflict

- Mỗi bảng = **1 file entity + 1 file repository riêng** → 2 người sửa 2 bảng khác
  nhau thì git không bao giờ conflict.
- `Lesson`, `LessonFile` đều `extends SoftDeletableEntity` (đủ 7 cột audit + soft delete).
- Repository để TRỐNG (`extends JpaRepository`) — ai làm feature thì tự thêm
  method truy vấn vào, ví dụ `findByBranchIdAndStatusAndDeletedFalse(...)`.
- Dự án bật **Spotless** kiểm tra format khi build: nếu bị báo lỗi format,
  chạy `mvnw spotless:apply` trong thư mục `backend` rồi build lại.

## 5. Seed demo — nằm ở đâu, chạy thế nào?

**Trả lời câu hỏi "file seed nằm trong folder hay trong DB?":** cả hai, theo 2 vai trò:

- **File `database/seed/TSDMS_Seed_Demo.sql` nằm trong git** → nguồn chuẩn (source
  of truth), cả nhóm cùng một bộ dữ liệu demo, máy mới chỉ cần chạy lại file.
- **Dữ liệu chỉ vào DB khi mở file trong SSMS và Execute** (sau khi schema đã có).
- **KHÔNG** nhét data demo vào Flyway migration: migration tự chạy ở MỌI môi trường;
  sau này deploy thật sẽ dính nguyên bộ dữ liệu giả vào production.

### Cách chạy

1. Đảm bảo DB `TSDMS` đã có đủ bảng (chạy `TSDMS_Schema.sql`, hoặc backend đã chạy
   qua Flyway V1+V2).
2. Mở `database/seed/TSDMS_Seed_Demo.sql` trong SSMS → Execute. Xong.

### Cơ chế an toàn trong file seed

- **Chốt chặn đầu file**: đã seed rồi thì chạy lại sẽ tự bỏ qua (kiểm tra sự tồn tại
  của `Chi nhánh Cầu Giấy`), không tạo dữ liệu trùng.
- **TRANSACTION + TRY/CATCH**: lỗi giữa chừng → rollback toàn bộ, DB không bẩn.
- **Tra ID theo Username/Code/Name** — không hard-code ID tự tăng (ID mỗi máy có thể khác).
- `ScheduleStatusLog` **không insert tay**: cuối file UPDATE duyệt 7 buổi dạy →
  trigger `TR_Schedule_StatusLog` tự ghi log, đúng cơ chế chạy thật.

### Bảng cố tình KHÔNG seed

| Bảng | Lý do |
|---|---|
| `RefreshToken`, `PasswordResetToken` | Sinh lúc CHẠY APP (đăng nhập / quên mật khẩu). Seed hash giả vô nghĩa. |
| `Role` | Danh mục cố định 4 vai trò, V1 đã seed đủ. |

### Tài khoản demo sau khi seed (mật khẩu chung: `Tsdms@123`)

| Vai trò | Username |
|---|---|
| Admin | `admin` |
| Nhân viên | `employee`, `employee2`…`employee5` (mỗi người 1 chi nhánh) |
| Giáo viên | `teacher`, `teacher2`…`teacher6` |
| Trường | `school`, `school2`…`school5` |

## 6. Việc CÒN LẠI để module Bài giảng dùng được (chưa làm đợt này)

- [ ] Tầng BE: DTO + Service + Controller (`/api/v1/lessons`, `/api/v1/lessons/{id}/files`).
- [ ] Phân quyền: EMPLOYEE/ADMIN được ghi (`LESSON_MANAGE`), SCHOOL/TEACHER chỉ đọc
      (`LESSON_VIEW`) — mã quyền đã seed sẵn trong bảng `Permission`.
- [ ] Upload file thật (lưu local `/uploads` hay cloud) — hiện `FileUrl` chỉ là chuỗi.
- [ ] FE: trang danh sách/chi tiết bài giảng cho 3 portal (admin/giáo viên/trường),
      thêm route vào `frontend/src/router/index.js`.
- [ ] Enum Java cho `Status`/`DifficultyLevel` nếu muốn chặt chẽ hơn chuỗi thuần.
