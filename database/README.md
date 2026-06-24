# TSDMS — Database

Microsoft SQL Server 2019+. Schema gồm **36 bảng** (30 bảng lõi + 6 bảng AI). Bảng lõi = 28 bảng V1 + 2 bảng module Bài giảng (V2).

## Cấu trúc thư mục

| Thư mục / file | Nội dung |
|---|---|
| `schema/TSDMS_Schema.sql` | Schema lõi (bản thiết kế, để đọc & tham chiếu) |
| `migrations/` | Nơi đặt migration nguồn trước khi đưa vào Flyway (tùy chọn) |
| `seed/TSDMS_Seed_Demo.sql` | Bộ dữ liệu demo toàn hệ thống (~5 dòng/bảng) — chạy TAY trong SSMS, KHÔNG đưa vào Flyway |
| `TSDMS_TuDien_DB.md` | Từ điển thuật ngữ & hướng dẫn đọc database (tiếng Việt) |

## Flyway (chạy migration tự động)

Migration thực thi nằm trong backend: `backend/src/main/resources/db/migration/`.

- `V1__init_schema.sql` — 28 bảng lõi ban đầu, Flyway chạy khi backend khởi động.
- `V2__lesson_module.sql` — module Bài giảng (Lesson + LessonFile), có chốt `IF OBJECT_ID(...) IS NULL` để tương thích DB dựng tay từ schema.
- `V3__rbac_permissions.sql` — phân quyền RBAC (Permission + RolePermission).
- `V4__service_contract.sql` — hợp đồng dịch vụ trung tâm ↔ trường (ServiceContract).
- `V5__employee_all_departments.sql` — mở Employee cho mọi phòng ban (không chỉ chi nhánh).
- `V6__split_name_dedup_contract.sql` — tách `FirstName`/`LastName`, bỏ trùng ở AppUser, Contract 1-1 (góp ý GVHD).
- `V7__rename_pk_to_id.sql` — đổi tên cột khóa chính `<Bảng>Id → Id` cho 27 bảng PK đơn; GIỮ tên khóa ngoại; KHÔNG đổi 4 bảng nối PK ghép (góp ý GVHD).
- Thêm thay đổi schema sau này bằng file mới: `V8__<mô_tả>.sql`, `V9__...` (KHÔNG sửa file đã chạy). Nhớ "xí số" version trong nhóm trước khi tạo (xem quy ước làm việc nhóm).

> **Giữ đồng bộ:** `schema/TSDMS_Schema.sql` là bản thiết kế để đọc; `db/migration/V1__init_schema.sql` là bản Flyway thực thi. Khi đổi schema, cập nhật cả hai (hoặc coi migration là nguồn chính).

### Lưu ý SQL Server + Flyway
- Schema dùng `GO` batch separator và có `CREATE TRIGGER` — cần dependency `flyway-sqlserver` (đã thêm trong `pom.xml`) để tách batch đúng.
- Phần AI (`TSDMS_Schema_AI.sql`, 6 bảng) làm ở giai đoạn sau → sẽ thành `V2__ai_schema.sql`.

## Quy ước chung mọi bảng nghiệp vụ
- **Khóa chính:** tên cột là `Id` ở mọi bảng (đồng nhất từ V7). Khóa ngoại giữ tên `<Bảng>Id` (vd `TeacherId`, `SchoolId`) để tự mô tả trỏ tới bảng nào.
- **Soft delete:** `IsDeleted` + `DeletedAt` + `DeletedBy`.
- **Audit:** `CreatedAt/CreatedBy/UpdatedAt/UpdatedBy`.
- Unique index lọc theo `IsDeleted = 0` để tái dùng mã của bản ghi đã xóa mềm.
- Index trên mọi khóa ngoại + cột tìm kiếm (tên, email, trạng thái).
