# Đóng băng tầng Entity: map FULL 28 bảng (chống conflict khi làm việc nhóm)

> Ngày: 2026-06-12 · Phần: backend · Nhánh: `feature/auth-jwt`

## 1. Vì sao làm

Trước đây entity chỉ map các cột mà tính năng hiện có dùng tới (YAGNI). Cách đó gọn,
nhưng có nhược điểm khi **nhiều người làm song song**: entity là file DÙNG CHUNG — feature
nào cũng phải "mở rộng entity thêm vài cột" → nhiều nhánh cùng sửa 1 file → merge conflict
liên tục.

Quyết định: **map đủ 100% cột của cả 28 bảng một lần, ngay bây giờ**, kèm repository sẵn
cho từng bảng. Từ giờ:

- **Quy tắc nhóm: KHÔNG sửa file trong `entity/` nữa** (trừ khi schema DB thay đổi —
  lúc đó sửa entity phải đi kèm file migration `V2__...sql`).
- Làm feature mới = chỉ THÊM file mới (service/controller/dto) + THÊM method vào
  repository có sẵn. Thêm dòng mới vào cuối interface gần như không bao giờ conflict.

An toàn vì: JPA chỉ đụng tới cột đã map khi entity **được dùng**; các entity chưa ai
query thì không sinh SQL nào → không ảnh hưởng hệ thống đang chạy (`ddl-auto: none`,
không validate schema lúc khởi động). Đã chạy lại toàn bộ test: 12/12 pass.

## 2. Hai lớp cha `@MappedSuperclass` (điểm mới cần hiểu)

13 bảng có chung đúng một bộ 7 cột audit + xóa mềm → gom vào 2 lớp cha:

```
AuditableEntity                 (CreatedAt*, CreatedBy, UpdatedAt, UpdatedBy)
   └── SoftDeletableEntity      (+ IsDeleted, DeletedAt, DeletedBy)
```

- `@MappedSuperclass` nghĩa là: **không phải bảng riêng** — các cột của lớp cha được
  "dán" thẳng vào bảng của entity con. JPQL/derived query dùng property của lớp cha
  bình thường (vd `findByUsernameAndDeletedFalse` vẫn chạy vì `deleted` nằm ở lớp cha).
- `CreatedAt` đánh dấu `insertable = false, updatable = false`: DB tự điền
  `DEFAULT SYSUTCDATETIME()` → Java **chỉ đọc**. Đây là pattern cho mọi cột có DEFAULT
  mà ta muốn DB quản.
- `UpdatedAt/UpdatedBy` chưa tự động — service phải tự set khi sửa (sau này có thể nâng
  cấp bằng JPA Auditing `@EnableJpaAuditing`).

Ai extends gì:

| Lớp cha | Entity |
|---|---|
| `SoftDeletableEntity` | Branch, AppUser, Employee, Subject, Teacher, Certificate, Contract, School, Room, SchoolClass, Student, Assignment, TeacherEvaluation |
| `AuditableEntity` | Payroll, Feedback |
| Không (map tay / bảng log) | Schedule¹, Attendance, Notification, AuditLog, ScheduleStatusLog, RefreshToken, PasswordResetToken, Role, Permission, các bảng nối |

¹ **Schedule là ngoại lệ dễ vấp nhất**: bảng có đủ cột audit **trừ `CreatedBy`** (người tạo
nằm ở `CreatedByUserId`) — nếu cho extends lớp cha, Hibernate sẽ SELECT/INSERT cột
`CreatedBy` không tồn tại → lỗi SQL. Vì vậy Schedule map tay từng cột.

## 3. Quy ước map kiểu dữ liệu SQL Server → Java (theo đúng cái đã làm)

| SQL Server | Java | Ghi chú |
|---|---|---|
| `INT` (khóa, FK) | `Integer` | |
| `BIGINT` (bảng log/sự kiện) | `Long` | RefreshToken, Schedule, Attendance, Notification, AuditLog, ScheduleStatusLog |
| `BIT NOT NULL` | `boolean` | vd `IsDeleted`, `IsRead` — có default Java khớp default DB |
| `BIT NULL` | `Boolean` (hoa) | vd `Gender` — phải nullable, dùng primitive là mất giá trị NULL |
| `TINYINT` / `SMALLINT` | `Short` | TINYINT của SQL Server là 0..255 (unsigned) → đừng dùng `Byte` |
| `DECIMAL` (tiền, giờ) | `BigDecimal` | tuyệt đối không dùng `double` cho tiền |
| `DATE` | `LocalDate` | |
| `TIME(0)` | `LocalTime` | CheckIn/CheckOut |
| `DATETIME2(3)` (UTC, audit) | `Instant` | nhất quán với code auth có sẵn |
| `DATETIME2(0)` (giờ buổi dạy) | `LocalDateTime` | giờ "treo tường" 8:00 sáng — không gắn múi giờ |
| `NVARCHAR` / `VARCHAR` | `String` | kể cả `NVARCHAR(MAX)` (OldValue/NewValue của AuditLog) |

## 4. Các ngoại lệ phải biết khi dùng

- **`Payroll.netAmount` là cột TÍNH SẴN** (computed `PERSISTED` trong DB:
  `BaseSalary + TaughtHours×RatePerHour + Allowance + Bonus − Deduction`) → map
  `insertable=false, updatable=false`. **Không bao giờ set giá trị này từ Java** —
  cứ lưu các thành phần, đọc lại sẽ có NetAmount đúng.
- **Trigger `TR_Schedule_StatusLog`**: mỗi lần UPDATE đổi `Schedule.Status`, DB tự ghi
  một dòng `ScheduleStatusLog`. Hợp đồng ngầm: service phải **set `schedule.updatedBy`
  = id người thao tác TRƯỚC khi save** thì log mới biết ai đổi. `ScheduleStatusLog`
  phía Java chủ yếu để ĐỌC.
- **4 bảng nối khóa kép** dùng `@IdClass`: UserRole (có sẵn), RolePermission,
  TeacherSubject, ClassEnrollment — mỗi cái có class `*Id` implements `Serializable`
  + `equals/hashCode`.
- **Cột DEFAULT phía DB** (`EnrolledAt`, `AssignedAt`, `ChangedAt`, mọi `CreatedAt`):
  map chỉ-đọc. Muốn giá trị ngay sau khi save thì phải refresh/đọc lại từ DB.
- **Soft delete chưa tự lọc**: entity có cờ `deleted` nhưng JPA không tự thêm
  `WHERE IsDeleted = 0` — đặt tên derived query dạng `...AndDeletedFalse` (theo mẫu
  `AppUserRepository`).

## 5. Giới hạn của lần kiểm chứng này

Test context (`TsdmsApplicationTests`) xác nhận Hibernate dựng được metadata cho 28
entity + 28 repository, nhưng **không** so cột với DB thật (`ddl-auto: none` → không
validate). Tên cột được chép tay 1-1 từ `V1__init_schema.sql`. Nếu nghi ngờ một entity
mới khi bắt đầu dùng nó, cách check nhanh: viết 1 test gọi `repository.findAll()` với
DB thật, hoặc bật tạm `spring.jpa.properties.hibernate.hbm2ddl.auto=validate` ở máy dev.

## 6. File thay đổi

- Mới: `entity/AuditableEntity`, `entity/SoftDeletableEntity`, 20 entity
  (Permission, RolePermission, Employee, Subject, TeacherSubject, Certificate, Contract,
  Room, SchoolClass, Student, ClassEnrollment, Assignment, Schedule, ScheduleStatusLog,
  Attendance, Payroll, TeacherEvaluation, Feedback, Notification, AuditLog),
  3 id class (RolePermissionId, TeacherSubjectId, ClassEnrollmentId), 20 repository.
- Sửa (map đủ cột + extends lớp cha): `Branch`, `AppUser`, `School`, `Teacher`.
- Không đổi: `Role`, `UserRole(+Id)`, `RefreshToken`, `PasswordResetToken` (đã đủ cột sẵn),
  toàn bộ service/controller/dto.
