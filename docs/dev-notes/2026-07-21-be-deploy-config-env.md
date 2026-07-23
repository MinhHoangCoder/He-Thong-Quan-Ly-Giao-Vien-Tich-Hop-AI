# BE: Chuẩn hóa cấu hình cho deploy — bỏ 2 giá trị hard-code (2026-07-21)

## Vấn đề

Còn 2 giá trị **ghi cứng** khiến không deploy được nếu không sửa code:
- `spring.datasource.url` = `localhost:1433` (DB thật ở nơi khác).
- CORS origin = `http://localhost:5173` ghi thẳng trong `SecurityConfig.java` — origin
  này **không đọc từ property nào**, nên FE ở domain khác (Vercel) sẽ dính 403
  "Invalid CORS request" trên MỌI API (đúng lỗi đã gặp 2026-07-16 khi vite chạy nhầm cổng).

## Thay đổi (2 file)

| File | Cũ | Mới |
|---|---|---|
| `application.yaml` datasource | `url: jdbc:sqlserver://localhost...` | `url: ${DB_URL:jdbc:sqlserver://localhost...}` |
| `application.yaml` (mới) | — | `tsdms.cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}` |
| `SecurityConfig.java` | `setAllowedOrigins(List.of("http://localhost:5173"))` | đọc `tsdms.cors.allowed-origins`, tách theo dấu `,`, trim/bỏ rỗng |

## VÌ SAO KHÔNG ẢNH HƯỞNG máy local (quan trọng)

Cú pháp `${BIẾN:mặc-định}` = "không đặt biến thì dùng mặc định". **Mặc định đặt đúng
bằng giá trị hard-code cũ**, và máy dev không đặt biến nào → app dùng mặc định →
hành vi **giống hệt từng byte** so với trước. CORS với 1 origin `localhost:5173` qua
`split(",")` vẫn ra `["http://localhost:5173"]`. Đây là code "ngủ yên", chỉ thức khi
deploy có đặt biến. Đã verify: `mvnw spotless:apply compile` PASS. (Chưa chạy runtime
test CORS end-to-end vì backend cần DB_PASSWORD + SQL Server đang tắt — logic là no-op
local nên rủi ro ~0; test lại khi bật backend nếu muốn chắc.)

## Checklist BIẾN MÔI TRƯỜNG khi deploy (Railway)

Đặt các biến sau ở dashboard Railway (backend). Không đặt = dùng mặc định local.

| Biến | Ý nghĩa | Ví dụ giá trị deploy |
|---|---|---|
| `DB_URL` | Chuỗi JDBC tới DB thật | `jdbc:sqlserver://xxx.database.windows.net:1433;databaseName=TSDMS;encrypt=true` |
| `DB_USERNAME` / `DB_PASSWORD` | Tài khoản DB | (của Azure SQL) |
| `CORS_ALLOWED_ORIGINS` | Domain FE được gọi API (nhiều thì cách `,`) | `https://kdc-eduops.vercel.app` |
| `JWT_SECRET` | Khóa ký JWT, ≥32 byte, **đổi khác dev** | (chuỗi ngẫu nhiên dài) |
| `APP_BASE_URL` | FE base url để ghép link reset mật khẩu | `https://kdc-eduops.vercel.app` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail phụ + App Password gửi mail | (xem bên dưới) |
| `MAIL_FROM` | Địa chỉ hiện ở ô "người gửi" | `no-reply@...` |
| `TSDMS_RATELIMIT_TRUSTFORWARDEDHEADER` | **BẬT `true`** khi sau proxy Railway | `true` |

**Bẫy `TRUST_FORWARDED_HEADER`**: sau proxy Railway mọi request mang cùng 1 IP proxy;
rate-limit đang 5 req/phút/IP → cả hệ thống khóa sau 5 lượt bấm. Bật `true` để đọc IP
thật từ header `X-Forwarded-For`. (Không cần sửa code — Spring relaxed binding map biến
này vào `tsdms.rate-limit.trust-forwarded-header`.)

**Mail (trả lời câu hỏi 2026-07-21)**: KHÔNG dùng Gmail cá nhân — địa chỉ người gửi lộ
cho mọi user nhận mail. Tạo Gmail phụ chuyên dụng, bật 2FA, tạo App Password, đặt qua
`MAIL_USERNAME`/`MAIL_PASSWORD` (env, không viết vào code → không vào repo). App Password
lộ chỉ cho phép gửi mail giả danh, thu hồi 1 phút; không đăng nhập được tài khoản.

**Frontend (Vercel)** — ngoài phạm vi commit này nhưng cần khi deploy:
- Thêm biến `VITE_API_URL` = URL backend Railway; sửa `api/http.js` dùng nó thay `/api/v1`
  (local có proxy vite nên `/api/v1` chạy; Vercel không có proxy → phải URL đầy đủ).
- Cấu hình rewrite mọi path về `index.html` (SPA) để F5 tại URL sâu không ra 404 của host.
