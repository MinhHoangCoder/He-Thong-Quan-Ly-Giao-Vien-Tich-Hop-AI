# Áp góp ý GVHD vào DB — tách họ tên, AppUser bỏ trùng, Contract 1-1, đổi PK → `Id` (V6 + V7)

> Ngày: 2026-06-19 → 2026-06-20 · Phần: fullstack (database + backend + FE) · Nhánh: `feature/auth-jwt`

> Tài liệu **phòng vệ trước hội đồng**: gom toàn bộ thay đổi DB theo góp ý GVHD vào một chỗ,
> giải thích *vì sao* mỗi quyết định. Quy tắc Flyway bất biến (không sửa V1..V5 đã chạy) →
> mọi thứ làm bằng migration mới: **V6** (06-19) cho 3 thay đổi cấu trúc, **V7** (06-20) cho đổi tên PK.

## 0. Bối cảnh

GVHD góp ý 4 điểm về thiết kế DB. Vì DB đã seed/chạy, không được sửa migration cũ → viết
2 migration cộng dồn, đồng thời cập nhật entity / service / FE / schema doc / seed cho khớp.

## 1. (#1 — V6) Tách họ tên: `FullName` → `FirstName` + `LastName`

Áp cho **Teacher, Employee, Student**. Ý nghĩa cột (theo cách đọc tiếng Việt):
- `FirstName` = **tên gọi** (given name), vd "A".
- `LastName` = **họ và tên đệm** (family name), vd "Trần Nguyễn Văn".

**Quy tắc tách dữ liệu cũ:** từ **cuối** của `FullName` là `FirstName`, phần còn lại là
`LastName` (vd `"Trần Nguyễn Văn A"` → FirstName `"A"`, LastName `"Trần Nguyễn Văn"`).
Thực hiện bằng `CHARINDEX`/`REVERSE` trong V6, sau đó mới `ALTER ... NOT NULL` và `DROP FullName`.
Index đổi sang `(FirstName, LastName)` để **sắp xếp theo tên gọi** đúng tập quán VN.

> Lý do tách: tìm kiếm/sắp xếp theo tên, ghép linh hoạt khi hiển thị, đúng chuẩn dữ liệu cá nhân.

## 2. (#2 — V6) `AppUser` bỏ trùng + `DisplayNameResolver`

`AppUser` trước đây giữ `FullName`, `Phone` — **trùng** với hồ sơ tác nhân. Sau V6, `AppUser`
**chỉ còn giữ định danh đăng nhập** (username, mật khẩu…). Cụ thể:

- `Employee` trước đây **không** có tên/SĐT (mượn của `AppUser`) → V6 thêm `FirstName/LastName/Phone`
  cho Employee và **copy dữ liệu sang TRƯỚC khi** drop cột bên `AppUser` (thứ tự quan trọng,
  nếu không sẽ mất dữ liệu).
- `AppUser` `DROP COLUMN FullName, Phone`.

Vì giờ `AppUser` không còn tên, **tên hiển thị được ghép ở tầng app** qua
[`DisplayNameResolver`](../../backend/src/main/java/com/kdc/tsdms/service/DisplayNameResolver.java):

```
GV / NV   → LastName + " " + FirstName   (họ+đệm rồi tên)
Trường    → tên trường
admin/tk không có hồ sơ → username (fallback, không bao giờ null)
```

Resolver dò lần lượt Teacher → Employee → School theo `appUserId`. Được gọi ở `AuthService`,
`JwtService`, `PasswordResetService` (mọi nơi trước đây đọc `user.getFullName()`).

> Nguyên tắc: **một dữ liệu chỉ sống ở một bảng** (single source of truth). Tên thuộc về
> hồ sơ con người/đơn vị, không thuộc về "tài khoản đăng nhập".

## 3. (#3 — V6) Teacher ↔ Contract: 1-N → 1-1

Mỗi giáo viên tối đa **1 hợp đồng đang hiệu lực (chưa xóa mềm)**:

- Dữ liệu cũ có thể có GV nhiều HĐ → V6 giữ **HĐ mới nhất** (`StartDate` lớn nhất, dùng
  `ROW_NUMBER()`), các HĐ còn lại **xóa mềm** (`IsDeleted=1`) để không vi phạm ràng buộc.
- Thay index thường bằng **filtered UNIQUE**:
  ```sql
  CREATE UNIQUE INDEX UX_Contract_OneActivePerTeacher ON Contract(TeacherId) WHERE IsDeleted = 0;
  ```
  → ràng buộc 1-1 **chỉ tính trên dòng chưa xóa mềm**; HĐ cũ đã xóa mềm không cản HĐ mới.

## 4. (#4 — V7) Đổi tên khóa chính `<Bảng>Id` → `Id`

GVHD: đặt PK nhất quán là `Id` cho mọi bảng (thay vì `RoleId`, `TeacherId`…). PK chỉ định danh
dòng **trong phạm vi bảng** nên prefix tên bảng là thừa.

**Phạm vi (rất quan trọng, dễ làm sai):**
- ✅ Chỉ đổi **PK đơn** (surrogate identity) của **27 bảng**.
- ❌ **KHÔNG** đổi cột **khóa ngoại** (`TeacherId`, `AppUserId`, `SchoolId`…) — giữ prefix để FK
  tự mô tả "trỏ tới bảng nào".
- ❌ **KHÔNG** đổi 4 bảng nối PK ghép (`ClassEnrollment`, `RolePermission`, `TeacherSubject`,
  `UserRole`) vì PK của chúng chính là các cột FK.

**Vì sao `sp_rename` là đủ (không cần drop/recreate FK):** trong SQL Server, FK/PK/index tham
chiếu cột theo `column_id` **nội bộ**, không theo tên. `sp_rename ... 'COLUMN'` tự cập nhật mọi
FK đang trỏ tới. (Nó in cảnh báo "Caution: Changing any part of an object name..." — vô hại.)

**Phía Java không phải đổi gì ở tầng trên:** entity chỉ thêm `@Column(name = "Id")`, còn **field
Java vẫn là `id`** → getter/setter, service, DTO, FE **giữ nguyên**. Chỉ ánh xạ cột thay đổi.

## 5. Lan tỏa lên tầng ứng dụng (đồng bộ cùng V6/V7)

- `RegisterRequest` / `RegistrationService`: validate tên **theo role** (tách 2 ô).
- Repository: thêm `findByAppUserIdAndDeletedFalse` (Teacher/Employee/School) phục vụ resolver.
- FE `RegisterUserPage.vue`: tách 2 ô nhập **"Họ và tên đệm" / "Tên gọi"**.
- `database/schema/TSDMS_Schema.sql` + `database/seed/TSDMS_Seed_Demo.sql`: cập nhật khớp cấu trúc mới
  (26 PK + 47 FK REFERENCES → `Id`).
- `AuthServiceTest`: cập nhật theo API tên mới.

## 6. Tóm tắt cho hội đồng

| # | Góp ý | Làm gì | Vì sao |
|---|---|---|---|
| 1 | Tách họ tên | `FullName` → `FirstName`+`LastName` (Teacher/Employee/Student) | Tìm/sắp xếp theo tên, đúng chuẩn dữ liệu cá nhân |
| 2 | `AppUser` bỏ trùng | Bỏ `FullName`/`Phone`, ghép tên qua `DisplayNameResolver` | Single source of truth — tên thuộc hồ sơ, không thuộc tài khoản |
| 3 | Contract 1-1 | Filtered UNIQUE theo `TeacherId WHERE IsDeleted=0` | Mỗi GV 1 HĐ hiệu lực, vẫn lưu lịch sử HĐ cũ (xóa mềm) |
| 4 | PK → `Id` | `sp_rename` 27 PK, giữ FK & PK ghép | Nhất quán, gọn; SQL Server không cần drop/recreate FK |
