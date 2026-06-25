# Chuẩn hóa nhóm môn: Subject.Category (text) → bảng lookup SubjectCategory

**Ngày:** 2026-06-25 · **Tầng:** Database + Entity/Repository (base layer) · **Migration:** Flyway **V8**

## Vấn đề

`Subject.Category` (từ V1) là `NVARCHAR(50)` **text tự do**, KHÔNG có `CHECK` lẫn `FK`.
Đây là cột phân loại DUY NHẤT trong DB không được ràng buộc — mọi cột khác cùng kiểu
(`Status`, `Type`, `Source`, `DifficultyLevel`, `CheckInMethod`...) đều có `CHECK (... IN ...)`
khớp Enum Java. Hệ quả: giá trị nhóm môn (`STEM`, `CONG_DAN_SO`) chỉ là "magic string",
dễ sai chính tả, không quản lý được qua UI, không gom nhóm chuẩn.

> Có tiền lệ: module Lesson (V2) ban đầu cũng có cột `Category` text và đã được bỏ để
> trỏ FK `SubjectId → Subject`. `Subject.Category` là chỗ còn sót của pattern cũ.

Hai cột khác cùng tình trạng (chưa xử lý ở đợt này, chỉ ghi nhận): `Notification.Type`,
`AuditLog.Action`. Các cột `RefEntity`/`Entity` là tham chiếu đa hình → free-text cố ý, không tính.

## Giải pháp — lookup + FK (cách 2)

Tạo bảng danh mục `SubjectCategory` và cho `Subject` trỏ tới qua FK `CategoryId`.

### Migration `V8__subject_category_lookup.sql`
Tách bước bằng `GO` (SQL Server phân tích cả batch trước khi chạy, nên cột mới phải tồn tại
ở batch trước mới `UPDATE`/`DROP` được ở batch sau):

1. `CREATE TABLE SubjectCategory` (Id PK theo quy ước V7; `Code` unique + `Name` hiển thị; có `CHECK` Status, soft-delete + audit như `Subject`).
2. Seed **4 nhóm CHÍNH THỨC** (chốt theo module Lesson, dùng chung cả Subject lẫn Bài giảng):
   `TIN_HOC` (Tin học), `TIENG_ANH` (Tiếng Anh), `STEM_AI` (STEM - AI), `KY_NANG_SONG` (Kĩ năng sống).
   → Bỏ hẳn `STEM`/`CONG_DAN_SO` cũ (không còn là nhóm).
3. `ALTER TABLE Subject ADD CategoryId INT NULL` + `FK_Subject_Category`.
4. Backfill `Subject.CategoryId` có **ánh xạ** giá trị text cũ về nhóm chính thức (`CASE`):
   `STEM → STEM_AI`, `CONG_DAN_SO → KY_NANG_SONG`; giá trị khác thử khớp trực tiếp theo `Code`, không khớp thì để NULL.
5. `DROP COLUMN Category` (không còn nguồn tham chiếu).
6. `CREATE INDEX IX_Subject_Category ON Subject(CategoryId)`.

`CategoryId` để **NULL-able** đúng như `Category` cũ (vài môn có thể chưa gán nhóm).
DB rỗng (máy mới): bước backfill là no-op vì chưa có dòng `Subject` nào (môn demo do seed gán nhóm trực tiếp).

### Entity / Repository
- **Mới:** `SubjectCategory.java` (extends `SoftDeletableEntity`, mirror `Subject`) + `SubjectCategoryRepository`.
- **Sửa `Subject.java`:** cột `String category` → quan hệ
  `@ManyToOne(fetch = EAGER) @JoinColumn(name = "CategoryId") private SubjectCategory category;`
  EAGER vì là bảng danh mục nhỏ và tầng feature đọc category SAU khi entity đã detached
  (vd `LessonController.subjects()`) → tránh `LazyInitializationException`.

### Tầng feature (chỉ sửa cho khỏi vỡ build — việc của thành viên khác)
`Subject.getCategory()` đổi từ `String` → `SubjectCategory`. 3 call-site đọc category lấy
`.getName()` — tức **tên hiển thị chính thức** ("STEM - AI", "Kĩ năng sống"...), trùng đúng
4 chuỗi FE đang hard-code nên badge/bộ lọc khớp luôn:
- `LessonController.subjects()` (dựng `SubjectDto`)
- `LessonService` (build `LessonSummary` ở list, `LessonResponse` ở chi tiết)

> **Lưu ý đổi hành vi:** chuỗi `category` trả ra qua API GIỜ là tên nhóm chính thức
> ("STEM - AI"...) thay cho mã cũ ("STEM"/"CONG_DAN_SO"). Đây là chủ đích — đưa về đúng
> danh mục chính thức. `getCode()` (TIN_HOC...) vẫn sẵn nếu sau này cần giá trị máy ổn định.

## Đồng bộ tài liệu
- `database/schema/TSDMS_Schema.sql`: thêm bảng `SubjectCategory` (đặt TRƯỚC `Subject` vì FK) + đổi `Subject.Category` → `CategoryId` + index.
- `database/TSDMS_TuDien_DB.md`: thêm thuật ngữ + mục `9b. SubjectCategory`.
- `database/seed/TSDMS_Seed_Demo.sql`: insert `Subject` qua `CategoryId` — 4 môn STEM → `STEM_AI`, 2 môn Công dân số → `KY_NANG_SONG`.

## Cách áp dụng
1. Chạy app → Flyway tự áp **V8** lên DB `TSDMS` (baseline-version=1 nên DB cũ vẫn migrate bình thường).
2. Nếu seed lại demo: chạy `database/seed/TSDMS_Seed_Demo.sql` SAU khi đã migrate (cần `SubjectCategory` tồn tại).
3. Build kiểm chứng: `mvnw spotless:apply` rồi `mvnw compile`.

## Việc còn nợ (ngoài phạm vi đợt này)
- **FE nên đọc danh mục từ API:** 4 nhóm hiện vẫn hard-code trong `lessons.js`/`LessonListPage`/`LessonFormPage`. Giờ DB đã là nguồn chính thức → nên thêm endpoint trả `SubjectCategory` và cho FE đọc động (bỏ hard-code). Chưa làm đợt này.
- Có thể làm CRUD `SubjectCategory` cho Admin + endpoint trả danh mục cho dropdown (feature layer).
- Áp pattern tương tự cho `Notification.Type`, `AuditLog.Action` nếu muốn siết enum.
