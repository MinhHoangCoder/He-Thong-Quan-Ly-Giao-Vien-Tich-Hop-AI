# TSDMS — Database

Microsoft SQL Server 2019+. Schema gồm **34 bảng** chia 7 nhóm (28 bảng lõi + 6 bảng AI).

## Cấu trúc thư mục

| Thư mục / file | Nội dung |
|---|---|
| `schema/TSDMS_Schema.sql` | Schema lõi (bản thiết kế, để đọc & tham chiếu) |
| `migrations/` | Nơi đặt migration nguồn trước khi đưa vào Flyway (tùy chọn) |
| `seed/` | Script dữ liệu mẫu (roles, branch demo...) |
| `TSDMS_TuDien_DB.md` | Từ điển thuật ngữ & hướng dẫn đọc database (tiếng Việt) |

## Flyway (chạy migration tự động)

Migration thực thi nằm trong backend: `backend/src/main/resources/db/migration/`.

- `V1__init_schema.sql` — bản sao của `schema/TSDMS_Schema.sql`, Flyway chạy khi backend khởi động.
- Thêm thay đổi schema sau này bằng file mới: `V2__<mô_tả>.sql`, `V3__...` (KHÔNG sửa file đã chạy).

> **Giữ đồng bộ:** `schema/TSDMS_Schema.sql` là bản thiết kế để đọc; `db/migration/V1__init_schema.sql` là bản Flyway thực thi. Khi đổi schema, cập nhật cả hai (hoặc coi migration là nguồn chính).

### Lưu ý SQL Server + Flyway
- Schema dùng `GO` batch separator và có `CREATE TRIGGER` — cần dependency `flyway-sqlserver` (đã thêm trong `pom.xml`) để tách batch đúng.
- Phần AI (`TSDMS_Schema_AI.sql`, 6 bảng) làm ở giai đoạn sau → sẽ thành `V2__ai_schema.sql`.

## Quy ước chung mọi bảng nghiệp vụ
- **Soft delete:** `IsDeleted` + `DeletedAt` + `DeletedBy`.
- **Audit:** `CreatedAt/CreatedBy/UpdatedAt/UpdatedBy`.
- Unique index lọc theo `IsDeleted = 0` để tái dùng mã của bản ghi đã xóa mềm.
- Index trên mọi khóa ngoại + cột tìm kiếm (tên, email, trạng thái).
