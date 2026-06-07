# TSDMS — Teacher Schedule & Dispatch Management System

Hệ thống quản lý & điều phối giáo viên tích hợp AI cho trung tâm giáo dục (mảng STEM & Công dân số).

> **Tên dự án:** TSDMS · **Package backend:** `com.kdc.tsdms`

## Stack

| Phần | Công nghệ |
|---|---|
| Backend | Java 21+ · Spring Boot · JPA/Hibernate · Spring Security + JWT |
| Frontend | VueJS 3 · Vite · Pinia · Vue Router · Axios · FullCalendar |
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
├── frontend/       # VueJS 3 + Vite (chưa scaffold — xem frontend/README.md)
│   └── src/
│       ├── api/          # File gọi API (axios), mỗi resource một file
│       ├── components/   # Component tái sử dụng
│       ├── layouts/      # Layout theo role
│       ├── pages/        # Các trang chính
│       ├── router/       # Vue Router + route guard
│       ├── stores/       # Pinia store
│       └── utils/        # Helper
│
├── database/       # Schema gốc, migration nguồn, seed, từ điển DB
│   ├── schema/TSDMS_Schema.sql
│   ├── migrations/
│   ├── seed/
│   └── TSDMS_TuDien_DB.md
│
├── docs/           # Tài liệu dự án (spec, sơ đồ...)
├── .github/        # CI workflows, template
├── .gitignore / .gitattributes
└── README.md
```

## Chạy nhanh

### Backend
```bash
cd backend
./mvnw spring-boot:run     # Windows: .\mvnw.cmd spring-boot:run
```
Cấu hình DB qua biến môi trường `DB_USERNAME`, `DB_PASSWORD` (xem `backend/src/main/resources/application.yaml`). Flyway tự chạy migration khi khởi động.

### Frontend
Xem [frontend/README.md](frontend/README.md) — thư mục đã có sẵn skeleton, scaffold Vite ở bước tiếp theo.

### Database
Xem [database/README.md](database/README.md) — schema, quy ước, hướng dẫn migration.
