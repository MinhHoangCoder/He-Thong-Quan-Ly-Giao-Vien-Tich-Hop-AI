# Dev Note — Đăng nhập/Đăng ký/Đăng xuất/Quên mật khẩu (JWT + Spring Security)

> Ngày 2026-06-07 · Nhánh `feature/auth-jwt`. Viết kỹ vì đây là lần đầu đụng JWT + Spring Security.

## 1. Bức tranh tổng thể

```
[Vue LoginPage] --(username/password)--> POST /api/v1/auth/login
       <--(accessToken + refreshToken + user)--
mỗi request sau: header "Authorization: Bearer <accessToken>"
       --> JwtAuthenticationFilter đọc token -> set quyền -> controller chạy
accessToken hết hạn (15') -> FE tự gọi /refresh bằng refreshToken -> lấy token mới
logout -> /logout thu hồi refreshToken (RevokedAt)
```

## 2. Hai loại token — vì sao cần cả hai

| | Access token | Refresh token |
|---|---|---|
| Dạng | **JWT** có chữ ký HS256, mang sẵn `roles` | Chuỗi ngẫu nhiên (opaque), **không** phải JWT |
| Sống | ngắn (15 phút) | dài (7 ngày) |
| Lưu ở DB? | KHÔNG (stateless, chỉ verify chữ ký) | CÓ — lưu **HASH** (SHA-256) trong bảng `RefreshToken` |
| Thu hồi được? | Không (đợi hết hạn) | **Có** (set `RevokedAt`) → đây là cách "đăng xuất" thật |

Ý tưởng: access token cho phép mỗi request **không phải hỏi DB** (nhanh, scale tốt);
refresh token cho phép **thu hồi phiên** (logout, đổi mật khẩu) — thứ JWT thuần không làm được.
Chỉ lưu **hash** của token → lộ DB cũng không dùng lại được token.

## 3. Các lớp ở backend (đi từ ngoài vào)

- `controller/AuthController` — 7 endpoint dưới `/api/v1/auth`: `login`, `refresh`,
  `logout`, `register`, `forgot-password`, `reset-password`, `me`.
- `security/SecurityConfig` — luật phân quyền: các endpoint auth công khai;
  `register` chỉ `ROLE_ADMIN`/`ROLE_EMPLOYEE`; còn lại cần đăng nhập. Stateless, tắt CSRF, bật CORS cho `:5173`.
- `security/JwtAuthenticationFilter` — chạy 1 lần/request: đọc `Bearer`, verify, nạp
  `username` + `authorities` (`ROLE_<tên vai trò>`) vào `SecurityContext`.
- `security/JwtService` — sinh/parse JWT; sinh token opaque; băm SHA-256.
- `service/AuthService` — login/refresh/logout/me. Refresh có **rotation** (thu hồi token cũ, cấp cặp mới).
- `service/RegistrationService` — tạo `AppUser` + `UserRole` + hồ sơ `Teacher`/`School` trong **1 transaction**.
- `service/PasswordResetService` + `EmailService` — quên mật khẩu (xem mục 5).

## 4. RBAC — phân quyền theo vai trò

- Vai trò nằm trong claim `roles` của access token. Filter biến mỗi vai trò thành
  `GrantedAuthority` tên `ROLE_ADMIN`… để `hasRole("ADMIN")` nhận diện (Spring tự thêm tiền tố `ROLE_`).
- Đăng nhập **dùng chung 1 endpoint** cho cả 4 actor. KHÔNG cần 4 API/4 UI riêng:
  frontend đọc `roles` (qua `/me` hoặc lúc login) rồi điều hướng + dựng menu theo vai trò.

## 5. Quên mật khẩu (gửi email)

1. `forgot-password`: tìm user theo email → nếu có, sinh token opaque, lưu **hash** + hạn 30'
   vào bảng `PasswordResetToken`, gửi email link `…/reset-password?token=<raw>`.
   **Luôn trả về thông báo chung** dù email có tồn tại hay không (chống dò email).
2. `reset-password`: nhận `token` + `newPassword`, đối chiếu hash, kiểm tra chưa dùng/chưa hết hạn,
   đổi `PasswordHash`, đánh dấu `UsedAt` (token dùng 1 lần).
- Cấu hình SMTP ở `application.yaml` (`spring.mail.*`) qua biến môi trường
  `MAIL_USERNAME` / `MAIL_PASSWORD` (Gmail App Password). Khi chưa cấu hình, link reset
  được **log ra console** để test (xem `EmailService`).

## 6. "Bẫy" đã gặp & cách xử lý

- **JPA vs SQL Server PascalCase**: mặc định Spring đổi tên cột sang `snake_case`
  (`password_hash`) → sai với schema (`PasswordHash`). Đã set
  `spring.jpa.hibernate.naming.physical-strategy=PhysicalNamingStrategyStandardImpl`
  để giữ NGUYÊN tên trong `@Column`.
- **Bảng mới `PasswordResetToken`**: schema lõi không có chỗ lưu token reset → thêm bảng
  này (cả file thiết kế lẫn Flyway `V1`).
- **Hai file schema phân kỳ**: `database/schema/TSDMS_Schema.sql` (bấm Execute trong SSMS:
  có `CREATE DATABASE`/`USE` + `GO`) **khác** `V1__init_schema.sql` (Flyway đã kết nối sẵn vào
  DB nên KHÔNG có `CREATE DATABASE`/`USE`). Phần bảng + seed thì giống nhau.

## 7. Seed 4 tài khoản demo (mật khẩu `Tsdms@123`)

| Username | Vai trò | Hồ sơ |
|---|---|---|
| admin | ADMIN | — |
| employee | EMPLOYEE | Employee (chi nhánh) |
| school | SCHOOL | School (chi nhánh) |
| teacher | TEACHER | Teacher (chi nhánh) |

Mật khẩu seed là hash BCrypt (`$2b$`), khớp `BCryptPasswordEncoder` ở backend.

## 8. Cách chạy thử

```bash
# 1) DB: mở database/schema/TSDMS_Schema.sql trong SSMS -> Execute (tạo DB + bảng + seed)
#    (hoặc để Flyway tự build khi chạy app trên DB rỗng tên TSDMS)
# 2) Backend
cd backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:DB_PASSWORD='<mật khẩu sa>'
.\mvnw.cmd spring-boot:run
# 3) Frontend
cd frontend && npm install && npm run dev   # mở http://localhost:5173/login
```

## 9. Còn lại (PR sau)

- Trang dựng menu theo vai trò + layout cho SCHOOL/TEACHER (hiện mới có AdminLayout).
- Trang `/reset-password` ở FE (nhận token trên URL) — hiện mới có link trong email.
- Form "Tạo tài khoản GV/trường" cho Admin/NV (API `register` đã sẵn sàng).
- Test tự động (JUnit cho AuthService, e2e cho login).
