# BE: TeacherController — bỏ neo role, chuyển sang phân quyền theo permission (2026-07-21)

## Vấn đề (lỗi #1 trong rà soát 2026-07-21)

Mọi endpoint GHI của `TeacherController` neo `@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")`.
Nhưng RBAC (V3) cấp `TEACHER_MANAGE`/`CONTRACT_MANAGE` cho **HR** — tài khoản `nhansu`
có đủ quyền quản lý GV nhưng KHÔNG có role `EMPLOYEE` → **403 khi tạo/sửa/xóa GV, chứng
chỉ, hợp đồng**. Chỉ `admin` + tài khoản test `employee` (V5 gộp 4 role phòng ban) dùng
được. Đây là sai mô hình: kiểm tra theo TÊN ROLE thay vì theo QUYỀN.

Ghi chú thẩm quyền: đây là file của thành viên khác; chủ tầng base (user) trực tiếp yêu
cầu sửa vì đã báo người phụ trách nhiều lần không xử lý.

## Sửa — map đúng quyền cho từng endpoint

Áp đúng pattern chuẩn của dự án (giống `UserAdminController`): `hasRole('ADMIN') or hasAuthority('X')`.

| Endpoint | Trước | Sau |
|---|---|---|
| `GET /teacher` (list) | `hasAnyRole('ADMIN','EMPLOYEE') or hasAuthority('TEACHER_VIEW')` | `hasRole('ADMIN') or hasAuthority('TEACHER_VIEW')` |
| `POST /teacher` (tạo) | `hasAnyRole('ADMIN','EMPLOYEE')` | `hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE')` |
| `PUT /teacher/{id}` (sửa) | ″ | `... TEACHER_MANAGE` |
| `POST /{id}/certificates` | ″ | `... TEACHER_MANAGE` |
| `DELETE /{id}/certificates/{certId}` | ″ | `... TEACHER_MANAGE` |
| `PUT /{id}/contract` | ″ | `hasRole('ADMIN') or hasAuthority('CONTRACT_MANAGE')` |
| `GET /trash` | ″ | `... TEACHER_VIEW` |
| `POST /trash/{id}/restore` | ″ | `... TEACHER_MANAGE` |

**GIỮ NGUYÊN** `hasRole('ADMIN')` ở `DELETE /{id}` (xóa mềm) và `DELETE /trash/{id}`
(xóa vĩnh viễn) — comment gốc ghi rõ "CHỈ ADMIN được xóa", là chủ đích, không đụng.

Hợp đồng dùng `CONTRACT_MANAGE` (không phải TEACHER_MANAGE) vì đó là nghiệp vụ hợp đồng
riêng; HR có cả hai quyền nên vẫn thông, nhưng phân tách đúng để sau này chức danh chỉ
có TEACHER_MANAGE (không CONTRACT_MANAGE) sẽ bị chặn đúng ở endpoint hợp đồng.

## Vì sao KHÔNG vỡ tài khoản đang chạy

- `admin`: qua `hasRole('ADMIN')`.
- `employee` (test): V5 gộp HR+ACCOUNTANT+ACADEMIC+SALES → có TEACHER_MANAGE, CONTRACT_MANAGE,
  TEACHER_VIEW qua HR → vẫn thông mọi endpoint.
- `nhansu` (HR): ĐƯỢC MỞ KHÓA — có TEACHER_MANAGE/CONTRACT_MANAGE/TEACHER_VIEW (V3 dòng 78-79).
- `teacher`: KHÔNG có các quyền _MANAGE → vẫn 403 khi ghi (đúng); xem chi tiết của chính
  mình vẫn qua `GET /teacher/{id}` (không đổi, chốt sở hữu trong service).

## Kiểm chứng

- `mvnw spotless:apply compile` PASS; grep xác nhận không còn `hasAnyRole`/`EMPLOYEE` trong file.
- Cơ chế `hasAuthority` cho họ quyền `TEACHER_*` ĐÃ chứng minh chạy thật: dòng 36 cũ vốn
  cho HR xem GV qua `hasAuthority('TEACHER_VIEW')`; `UserAdminController` dùng cùng pattern
  với `USER_MANAGE`. Perm là mã thô trong JWT, role có tiền tố `ROLE_` → `hasRole('ADMIN')` đúng.
- **CHƯA chạy live API test** vì backend cần DB_PASSWORD + SQL Server đang tắt. Khi bật lại
  nên xác nhận: đăng nhập `nhansu` (Tsdms@123) → `POST /api/v1/teacher` KHÔNG còn 403;
  `teacher` → vẫn 403.
