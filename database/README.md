# TSDMS — Database

Microsoft SQL Server 2019+. Schema gồm **42 bảng** (36 bảng lõi + 6 bảng AI). Bảng lõi = 28 (V1) + 2 module Bài giảng (V2) + ServiceContract (V4) + SubjectCategory (V8) + Period & AssignmentSlot (V9) + PartTimeShiftRequest & EmployeeSchedule (V10).

## Cấu trúc thư mục

| Thư mục / file | Nội dung |
|---|---|
| `schema/TSDMS_Schema.sql` | Schema lõi (bản thiết kế, để đọc & tham chiếu) |
| `migrations/` | Nơi đặt migration nguồn trước khi đưa vào Flyway (tùy chọn) |
| `seed/TSDMS_Seed_Demo.sql` | Bộ dữ liệu demo toàn hệ thống (~5 dòng/bảng) — chạy TAY trong SSMS, KHÔNG đưa vào Flyway |
| `seed/TSDMS_Seed_100GiaoVien.sql` | 100 giáo viên + 23 môn học (kèm bằng cấp, hợp đồng, môn dạy được) — chạy TAY, KHÔNG đưa vào Flyway |
| `seed/TSDMS_Rollback_100GiaoVien.sql` | Gỡ đúng bộ 100 giáo viên ở trên, không đụng dữ liệu khác |
| `seed/TSDMS_Seed_TruongHaiPhong.sql` | 30 trường TH/THCS công lập Hải Phòng + khung tiết + 198 lớp — chạy TAY, KHÔNG đưa vào Flyway |
| `seed/TSDMS_Rollback_TruongHaiPhong.sql` | Gỡ đúng bộ 30 trường ở trên |
| `seed/TSDMS_TruongHaiPhong_CanRaSoat.md` | Bảng đối chiếu tên trường + độ tin cậy — **đọc trước khi dùng thật** |
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
- `V8__subject_category_lookup.sql` — bảng lookup `SubjectCategory` (4 nhóm môn), thay cột text `Subject.Category` cũ.
- `V9__teacher_timetable.sql` — thời khóa biểu dạy: `Period` (khung tiết theo trường) + `AssignmentSlot` (mẫu lặp tuần); `Schedule` thêm `PeriodId`, `SourceSlotId`.
- `V10__employee_workshift.sql` — ca làm nhân viên: `Employee.EmploymentType` + `PartTimeShiftRequest` (đăng ký ca) + `EmployeeSchedule` (lịch làm thực tế).
- `V11__fix_schedule_status_trigger.sql` — dựng lại trigger `TR_Schedule_StatusLog` theo PK mới (`Id`): V7 rename cột nhưng `sp_rename` KHÔNG sửa thân trigger → `UPDATE Schedule` nổ "Invalid column name 'ScheduleId'" trên DB dựng bằng Flyway.
- Thêm thay đổi schema sau này bằng file mới: `V12__<mô_tả>.sql`, `V13__...` (KHÔNG sửa file đã chạy). Nhớ "xí số" version trong nhóm trước khi tạo (xem quy ước làm việc nhóm).

> **Giữ đồng bộ:** `schema/TSDMS_Schema.sql` là bản thiết kế để đọc; `db/migration/V1__init_schema.sql` là bản Flyway thực thi. Khi đổi schema, cập nhật cả hai (hoặc coi migration là nguồn chính).

### Lưu ý SQL Server + Flyway
- Schema dùng `GO` batch separator và có `CREATE TRIGGER` — cần dependency `flyway-sqlserver` (đã thêm trong `pom.xml`) để tách batch đúng.
- Phần AI (`TSDMS_Schema_AI.sql`, 6 bảng) làm ở giai đoạn sau → sẽ thành `V2__ai_schema.sql`.

## ⚠ Reset DB đúng cách (đọc kỹ trước khi "xóa DB làm lại")

1. `DROP DATABASE TSDMS;` → `CREATE DATABASE TSDMS;` (DB **RỖNG**, không chạy file .sql nào).
2. Chạy backend (`mvnw spring-boot:run`, nhớ `$env:DB_PASSWORD`) — Flyway tự dựng V1→V11
   kèm seed hệ thống (role, permission, tài khoản demo `Tsdms@123`).
3. (Tùy chọn) Muốn có dữ liệu nghiệp vụ mẫu: chạy `seed/TSDMS_Seed_Demo.sql` bằng SSMS.
   Nếu dùng `sqlcmd` thì PHẢI kèm cờ `-I` (bật `QUOTED_IDENTIFIER ON` — schema có filtered
   index `WHERE IsDeleted = 0`, thiếu cờ này mọi INSERT đều lỗi Msg 1934).
4. (Tùy chọn) Muốn có đội ngũ giáo viên đủ lớn để thử phân công / lọc / báo cáo:
   chạy `seed/TSDMS_Seed_100GiaoVien.sql`. File này tự đặt `SET QUOTED_IDENTIFIER ON`
   nên KHÔNG cần cờ `-I`; chạy lại lần hai sẽ tự bỏ qua. Gỡ ra bằng
   `seed/TSDMS_Rollback_100GiaoVien.sql`. Dữ liệu trong đó là dữ liệu GIẢ (CCCD, số
   điện thoại, email `@tsdms.local`) — chỉ dùng cho máy dev/demo.
5. (Tùy chọn) Muốn có trường + lớp để thử phân công: chạy
   `seed/TSDMS_Seed_TruongHaiPhong.sql` (30 trường TH/THCS ở 5 phường Hồng Bàng,
   Lê Chân, Ngô Quyền, Hải An, Kiến An; tự tạo khung tiết chuẩn V22 cho từng
   trường và 198 lớp cho các trường đang hoạt động). Gỡ bằng
   `seed/TSDMS_Rollback_TruongHaiPhong.sql`.
   ⚠ **Tên trường là trường học CÓ THẬT và CHƯA được đối chiếu danh bạ chính
   thức** — đọc `seed/TSDMS_TruongHaiPhong_CanRaSoat.md` trước khi dùng cho
   mục đích thật.
6. (Tùy chọn) Muốn Bảng điều khiển / Lịch dạy / Chấm công / Bảng lương có số
   thật thay vì toàn số 0: chạy `seed/TSDMS_Seed_PhanCong.sql` — sinh cả dây
   chuyền `Assignment → AssignmentSlot → Schedule → Attendance → Payroll` cho
   ba đợt (học kỳ đã xong, học kỳ đang chạy, phiếu chờ xác nhận). ĐÒI bước 5 đã
   chạy và bảng `Holiday` (V29) đã có — buổi dạy không sinh vào ngày nghỉ. Gỡ
   bằng `seed/TSDMS_Rollback_PhanCong.sql` (⚠ file gỡ xóa TOÀN BỘ dữ liệu phân
   công/chấm công/lương, chỉ chạy trên máy demo). Chi tiết:
   [dev-note 2026-08-19](../docs/dev-notes/2026-08-19-fullstack-lich-nghi-va-seed-dieu-phoi.md).
7. (Tùy chọn) Ba bộ dữ liệu bổ sung, chạy được độc lập — mỗi bộ có file gỡ
   cùng tên dạng `TSDMS_Rollback_*.sql`:
   - `seed/TSDMS_Seed_DanhGia.sql` — đánh giá giáo viên 2 kỳ (cần bước 6 để
     biết giáo viên nào thực sự đứng lớp). Đồng thời dọn dòng đánh giá test cũ.
   - `seed/TSDMS_Seed_PhongHoc_HopDong.sql` — phòng học + hợp đồng dịch vụ
     (nguồn số cho module Doanh thu). Chỉ cần bước 5.
   - `seed/TSDMS_Seed_BaiGiang.sql` — ~234 bài giảng từ 46 chủ đề × các khối,
     kèm học liệu link Canva. Cần bước 4 (giáo viên) + 23 môn học.
     Sau khi chạy nhớ xóa file mồ côi: `rm -rf backend/uploads/lessons/{6,432,434,435,436}`.

**TUYỆT ĐỐI KHÔNG** dựng DB dev bằng cách chạy tay `schema/TSDMS_Schema.sql` rồi mới bật
backend: file đó là bản **mirror trạng thái CUỐI** (sau V10). Flyway thấy DB có bảng nhưng
chưa có `flyway_schema_history` sẽ baseline ở V1 rồi chạy đè V2→V11 lên schema cuối —
V6/V7/V9/V10 không idempotent nên vỡ ngay ("migration chồng lấn"). Mirror chỉ để ĐỌC.

## Quy ước chung mọi bảng nghiệp vụ
- **Khóa chính:** tên cột là `Id` ở mọi bảng (đồng nhất từ V7). Khóa ngoại giữ tên `<Bảng>Id` (vd `TeacherId`, `SchoolId`) để tự mô tả trỏ tới bảng nào.
- **Soft delete:** `IsDeleted` + `DeletedAt` + `DeletedBy`.
- **Audit:** `CreatedAt/CreatedBy/UpdatedAt/UpdatedBy`.
- Unique index lọc theo `IsDeleted = 0` để tái dùng mã của bản ghi đã xóa mềm.
- Index trên mọi khóa ngoại + cột tìm kiếm (tên, email, trạng thái).
