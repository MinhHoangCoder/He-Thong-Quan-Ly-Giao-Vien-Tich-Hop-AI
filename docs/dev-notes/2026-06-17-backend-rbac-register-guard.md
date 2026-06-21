# Chốt chặn `/register` theo quyền + tài khoản test `employee`

> Ngày: 2026-06-17 · Phần: backend · Nhánh: `feature/auth-jwt`

> Đây là **bản hiện thực** của quy ước mục 6.5 trong `2026-06-14-backend-rbac-permission-matrix.md`
> (endpoint phục vụ nhiều loại đối tượng). Đọc note RBAC trước để hiểu Role vs Permission.

## 1. Vấn đề

`POST /api/v1/auth/register` tạo **cả giáo viên lẫn trường** trong cùng một endpoint. Chặn
thô theo role ở `SecurityConfig` không đủ tinh:

- **Nhân sự (HR)** chỉ được tạo **giáo viên**.
- **Tuyển sinh (SALES)** chỉ được tạo **trường khách hàng**.

`@PreAuthorize` là annotation **tĩnh** — nó kiểm *trước khi* hàm chạy nên **không "nhìn" được
nội dung request** (đang tạo GV hay trường). Vậy không thể quyết toàn bộ ở annotation.

## 2. Giải pháp: kiểm 2 tầng (thô ở cửa, tinh trong service)

**Tầng 1 — `@PreAuthorize` ở controller (lọc thô):** chỉ hỏi "có ít nhất 1 quyền liên quan?"

```java
@PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE') or hasAuthority('SCHOOL_MANAGE')")
```

→ Loại ngay người chẳng có quyền tạo gì. Đồng thời **bỏ** chặn `/register` theo role cứng ở
`SecurityConfig` (chuyển toàn bộ quyết định về annotation + service cho nhất quán).

**Tầng 2 — kiểm CHI TIẾT trong `RegistrationService` (theo dữ liệu request):**

- Request tạo **GV** → đòi `TEACHER_MANAGE` (HR có).
- Request tạo **trường** → đòi `SCHOOL_MANAGE` (SALES có).
- ADMIN đi tắt qua hết.

Thiếu quyền đúng loại → ném 403, dù đã qua được tầng 1.

> ⚠️ Đây là **chỗ DUY NHẤT** được phép kiểm quyền trong tầng service. Lý do đã nói ở mục 1:
> chỉ service mới đọc được nội dung request. Mọi endpoint "một loại đối tượng" khác vẫn chỉ
> dùng `@PreAuthorize`, KHÔNG kiểm quyền rải rác trong service.

## 3. `SecurityUtils.hasAuthority(...)`

Helper mới đọc danh sách quyền của người gọi từ `SecurityContext` để service tự kiểm:

```java
if (taoGiaoVien && !SecurityUtils.hasAuthority("TEACHER_MANAGE")
        && !SecurityUtils.hasRole("ADMIN")) throw forbidden();
```

`SecurityContext` chính là "thẻ nhân viên" đã được `JwtAuthenticationFilter` nạp ở đầu request
(xem note RBAC mục 2). `SecurityUtils` chỉ là lớp bọc cho gọn.

## 4. Tài khoản test `employee` gộp 4 phòng (Flyway V5)

`V5__employee_all_departments.sql`: gán cho tài khoản `employee` **cả 4 role phòng ban**
(HR + ACCOUNTANT + ACADEMIC + SALES) → token gộp **27 quyền**. Mục đích:

- Test **đủ chức năng 4 phòng trong 1 lần đăng nhập**, khỏi đổi tài khoản liên tục.
- Nhưng **vẫn bị giới hạn chi nhánh** (vì không phải ADMIN) → đúng hành vi nhân viên thật.

Cặp tài khoản test chuẩn: `admin` = full + **không** giới hạn chi nhánh · `employee` = full
+ **scoped** 1 chi nhánh. (Flyway bất biến — viết V5 mới, không sửa V1..V4.)

## 5. Ảnh hưởng phía FE

- `LoginPage`: **ẩn** chip đăng nhập nhanh `employee` (gõ tay `employee` / `Tsdms@123`) — nó
  là tài khoản tiện ích cho dev/demo, không phải vai trò thật.
- `roleHome`: đưa `employee` vào **staff portal** (`/staff`).
- Trang tạo tài khoản (`admin.routes`): mở cho cả **HR & SALES**, không chỉ ADMIN.

## 6. Tài liệu

Cập nhật ma trận RBAC (`2026-06-14-backend-rbac-permission-matrix.md`): bổ sung vai trò
`employee`, quy ước **giới hạn chi nhánh** (branch-scope), và cách enforce 2 tầng cho `/register`.
