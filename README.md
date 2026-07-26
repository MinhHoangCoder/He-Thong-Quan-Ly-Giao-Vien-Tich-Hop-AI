# TSDMS — Teacher Schedule & Dispatch Management System

Hệ thống quản lý & điều phối giáo viên tích hợp AI cho trung tâm giáo dục (mảng STEM & Công dân số).

> **Tên dự án:** TSDMS · **Package backend:** `com.kdc.tsdms`

## Stack

| Phần | Công nghệ |
|---|---|
| Backend | Java 21+ · Spring Boot · JPA/Hibernate · Spring Security + JWT |
| Frontend | VueJS 3 · Vite · Pinia · Vue Router · Axios · Font Awesome |
| Database | Microsoft SQL Server 2019+ · Flyway migration |
| Build | Maven (BE) · npm (FE) |

## Cấu trúc thư mục (monorepo)

```
repo-root/
├── backend/        # Spring Boot API (Maven)
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd, .mvn/
│   └── src/main/java/com/kdc/tsdms/
│       ├── controller/   # Nhận request, gọi service (KHÔNG chứa business logic)
│       ├── service/      # Toàn bộ business logic
│       ├── repository/   # JPA repository (query)
│       ├── entity/       # Class map bảng DB
│       ├── dto/          # Request/Response object
│       ├── mapper/       # Entity ⇄ DTO
│       ├── security/     # JWT, filter, Spring Security config
│       ├── exception/    # Global exception handler
│       ├── config/       # CORS, Bean, cấu hình chung
│       └── ai/           # Xếp lịch, matching, cảnh báo, chatbot
│   └── src/main/resources/
│       ├── application.yaml
│       └── db/migration/ # Flyway migrations (V1__init_schema.sql ...)
│
├── frontend/       # SPA VueJS 3 + Vite (xem frontend/README.md)
│   └── src/
│       ├── api/          # File gọi API (axios), mỗi resource một file
│       ├── components/   # Component tái sử dụng
│       ├── composables/  # Logic tái dùng dạng hook (useXxx)
│       ├── layouts/      # Layout theo khu vực (portal GV / trường / phòng ban)
│       ├── pages/        # Các trang chính
│       ├── router/       # Vue Router + route guard
│       ├── stores/       # Pinia store
│       ├── assets/       # CSS chung (main.css — design token, theme sáng/tối)
│       └── utils/        # Helper
│
├── database/       # Schema gốc, migration nguồn, seed, từ điển DB
│   ├── schema/TSDMS_Schema.sql
│   ├── migrations/
│   ├── seed/
│   └── TSDMS_TuDien_DB.md
│
├── docs/           # Tài liệu dự án
│   └── dev-notes/  # Ghi chú giải thích từng tính năng khó (đọc README trong đó)
├── .github/        # CI workflows, template
├── .gitignore / .gitattributes
└── README.md
```

## Chạy nhanh

Cần sẵn: **JDK 21+** (đặt `JAVA_HOME`), **Node 18+**, **SQL Server** đang chạy với database
tên `TSDMS` (tạo database rỗng là đủ — Flyway tự dựng bảng).

### Backend
```bash
cd backend
./mvnw spring-boot:run     # Windows: .\mvnw.cmd spring-boot:run
```
API chạy ở `http://localhost:8080`, đường dẫn gốc `/api/v1`. Flyway tự chạy migration khi
khởi động. Máy dev **không cần đặt biến môi trường nào** ngoài mật khẩu DB — mọi biến đều
có giá trị mặc định trỏ về localhost (xem bảng dưới).

### Frontend
```bash
cd frontend
npm install     # lần đầu, hoặc khi có người thêm package
npm run dev     # mở http://localhost:5173
```
Vite proxy sẵn `/api/...` sang backend `:8080` nên dev không dính CORS. Cổng 5173 đặt
`strictPort` — **bận cổng thì báo lỗi và dừng hẳn**, cố ý như vậy vì backend chỉ cho phép
origin `localhost:5173`, chạy nhầm cổng khác là mọi API dính 403 "Invalid CORS request".

Chi tiết cấu trúc & quy ước FE: [frontend/README.md](frontend/README.md).

### Database
Xem [database/README.md](database/README.md) — schema, quy ước, hướng dẫn migration.

## Biến môi trường

Tất cả đều theo cú pháp `${BIẾN:mặc-định}` trong
[application.yaml](backend/src/main/resources/application.yaml): **không đặt thì dùng mặc
định local**, nên chỉ cần quan tâm khi deploy.

| Biến | Ý nghĩa | Mặc định (local) |
|---|---|---|
| `DB_URL` | Chuỗi JDBC tới SQL Server | `jdbc:sqlserver://localhost:1433;databaseName=TSDMS;...` |
| `DB_USERNAME` | Tài khoản DB | `sa` |
| `DB_PASSWORD` | Mật khẩu DB — **thứ duy nhất máy dev thường phải đặt** | (rỗng) |
| `JWT_SECRET` | Khóa ký JWT, tối thiểu 32 byte — **bắt buộc đổi ở môi trường thật** | secret dev ghi sẵn |
| `CORS_ALLOWED_ORIGINS` | Origin FE được gọi API, nhiều thì cách nhau dấu `,` | `http://localhost:5173` |
| `APP_BASE_URL` | Base URL của FE, dùng ghép link đặt lại mật khẩu | `http://localhost:5173` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP gửi mail | `smtp.gmail.com` / `587` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Tài khoản Gmail + **App Password** (không phải mật khẩu đăng nhập) | (rỗng) |
| `MAIL_FROM` | Địa chỉ hiện ở ô người gửi — phần trong `<...>` phải khớp `MAIL_USERNAME` | `KDC EduOps <kdceduopsai@gmail.com>` |

Không cấu hình mail thì app **vẫn chạy bình thường**, chỉ là email đặt lại mật khẩu không
gửi được — link reset được ghi ra log ở mức `WARN` để dev tự copy mà test luồng.

- **Máy dev**: khỏi phải set biến mỗi phiên bằng cách tạo file
  `backend/src/main/resources/mail-local.properties` (đã gitignore, app tự nạp nếu có):

  ```properties
  MAIL_USERNAME=<gmail-cua-ban>@gmail.com
  MAIL_PASSWORD=<app-password-16-ky-tu>
  ```

  Giải thích đầy đủ: [dev-note cấu hình mail](docs/dev-notes/2026-07-25-be-mail-local-config.md).
- **Deploy**: đặt qua dashboard hosting; biến môi trường luôn thắng file cấu hình. Checklist
  đầy đủ (kèm bẫy rate-limit sau reverse proxy) ở
  [dev-note cấu hình deploy](docs/dev-notes/2026-07-21-be-deploy-config-env.md).
