# Sao Việt — Hệ thống quản lý & điều phối giáo viên

Phần mềm vận hành cho trung tâm giáo dục (mảng STEM & Công dân số): quản lý hồ sơ giáo viên,
phân công giảng dạy theo thời khóa biểu, chấm công, tính lương theo tiết, đánh giá và kho bài
giảng.

> **Tên hiển thị:** Sao Việt · **Tên mã nội bộ:** TSDMS — vẫn dùng trong package
> `com.kdc.tsdms`, tên database `TSDMS` và tên thư mục. Đổi tên mã là đổi cả package lẫn
> migration nên cố ý giữ nguyên.

## Hệ thống có hai tác nhân

| Tác nhân | Làm gì |
|---|---|
| **Quản trị (ADMIN)** | Toàn quyền: trường/lớp, giáo viên, phân công, chấm công, bảng lương, đánh giá, kho bài giảng, lịch nghỉ, người dùng & phân quyền |
| **Giáo viên (TEACHER)** | Xem lịch dạy của mình, tự chấm công, xác nhận/từ chối lời mời phân công, xem phiếu lương và đánh giá của mình, đọc bài giảng đã xuất bản |

Trước đây còn hai tác nhân nữa và đã được bỏ hẳn ở tầng dữ liệu: **Nhà trường** (V31) và
**Nhân viên phòng ban** cùng 4 phòng Đào tạo/Kế toán/Nhân sự/Tuyển sinh (V33). Gặp chữ
`EMPLOYEE` hay `SCHOOL` trong migration cũ thì đó là dấu vết lịch sử, không phải tính năng
đang chạy.

Phân quyền chi tiết hơn vai trò: hệ thống dùng **RBAC theo permission** — quyền dạng
`TEACHER_VIEW`, `PAYROLL_PAY`, `LESSON_MANAGE`… gán cho vai trò qua bảng `RolePermission`,
còn ADMIN đi đường tắt `hasRole('ADMIN')`. Bộ quyền đầy đủ xem
[dev-note RBAC](docs/dev-notes/2026-06-14-backend-rbac-permission-matrix.md).

## Stack

| Phần | Công nghệ |
|---|---|
| Backend | Java (release 17, build bằng JDK 21) · Spring Boot 4.0.6 · JPA/Hibernate · Spring Security + JWT |
| Frontend | Vue 3.5 · Vite 8 · Pinia 3 · Vue Router 5 · Axios · Chart.js · Font Awesome |
| Database | Microsoft SQL Server 2019+ · Flyway |
| Build | Maven wrapper (BE) · npm (FE) · Spotless (palantir-java-format) · ESLint + Prettier (FE) |

## Chạy nhanh

Cần sẵn: **JDK 21** (đặt `JAVA_HOME`), **Node 18+**, **SQL Server** đang chạy với một
database rỗng tên `TSDMS` — Flyway tự dựng toàn bộ bảng và seed dữ liệu demo.

### Backend

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

API ở `http://localhost:8080`, đường dẫn gốc `/api/v1`. Máy dev **không cần đặt biến môi
trường nào ngoài mật khẩu DB** — mọi biến đều có mặc định trỏ về localhost (xem bảng cuối).

```powershell
$env:DB_PASSWORD = '<mat-khau-sa>'
```

### Frontend

```bash
cd frontend
npm install                     # lần đầu, hoặc khi có người thêm package
npm run dev                     # http://localhost:5173
```

Vite proxy sẵn `/api/...` sang backend `:8080` nên dev không dính CORS. Cổng 5173 đặt
`strictPort` — **bận cổng thì báo lỗi và dừng hẳn**, cố ý như vậy: backend chỉ cho phép origin
`localhost:5173`, nếu Vite tự né sang 5174 thì mọi request dính 403 *"Invalid CORS request"*
mà nhìn giao diện không đoán ra nguyên nhân.

### Đăng nhập thử

Tài khoản demo do seed tạo: **`admin` / `Tsdms@123`** (trang đăng nhập có sẵn chip bấm nhanh).

## Bản đồ tính năng

**Khu quản trị**

| Đường dẫn | Màn hình |
|---|---|
| `/dashboard` | Bảng điều khiển — thống kê theo kỳ, xuất CSV |
| `/admin/schools`, `/admin/classes` | Trường hợp tác & lớp học (có thùng rác) |
| `/assignments` | Phân công giảng dạy — thời khóa biểu lặp tuần, mời giáo viên xác nhận, thùng rác |
| `/schedule` | Lịch dạy toàn trung tâm |
| `/attendance` | Chấm công |
| `/payroll` | Bảng lương theo tiết + bảng đơn giá có hiệu lực theo ngày |
| `/admin/evaluations` | Đánh giá giáo viên |
| `/admin/lessons`, `/admin/subject-categories` | Kho bài giảng, nhóm môn & môn học (có thùng rác) |
| `/admin/holidays` | Lịch nghỉ — xem tác động trước khi xóa |
| `/settings` | Người dùng, phân quyền, phiên đăng nhập |

**Khu giáo viên** — `/teacher` (tổng quan), `/teacher/schedule`, `/teacher/attendance`,
`/teacher/payroll`, `/teacher/evaluations`, `/teacher/lessons`, `/teacher/profile`,
`/teacher/settings`.

## Cấu trúc thư mục

```
repo-root/
├── backend/                      # Spring Boot API (Maven)
│   └── src/main/java/com/kdc/tsdms/
│       ├── controller/           # Nhận request, gọi service (KHÔNG chứa business logic)
│       ├── service/              # Toàn bộ business logic
│       ├── repository/           # JPA repository
│       ├── entity/               # Class map bảng DB
│       ├── dto/                  # Request/Response object
│       ├── mapper/               # Entity ⇄ DTO
│       ├── security/             # JWT, filter, Spring Security config
│       ├── exception/            # Global exception handler
│       ├── common/               # Tiện ích dùng chung: DeleteGuard, BusinessTime, Paging…
│       ├── config/               # CORS, Bean, cấu hình chung
│       └── ai/                   # CHỖ TRỐNG — mới chỉ có .gitkeep, chưa có code
│   └── src/main/resources/
│       ├── application.yaml
│       └── db/migration/         # Flyway (V1__init_schema.sql … V38)
│
├── frontend/                     # SPA Vue 3 + Vite (xem frontend/README.md)
│   └── src/
│       ├── api/                  # Gọi API (axios), mỗi resource một file
│       ├── components/           # Component tái sử dụng
│       ├── composables/          # Logic tái dùng dạng hook (useXxx)
│       ├── layouts/              # Layout khu quản trị / khu giáo viên
│       ├── pages/                # Các trang chính
│       ├── router/               # Vue Router + route guard theo quyền
│       ├── stores/               # Pinia store
│       ├── assets/               # main.css — design token, theme sáng/tối
│       └── utils/                # Helper
│
├── database/                     # Schema gốc, seed & script rollback, từ điển DB
├── docs/dev-notes/               # Ghi chú giải thích từng tính năng khó (62 file)
├── .github/workflows/ci.yml      # CI
└── README.md
```

> Thư mục `backend/.../ai/` hiện **chỉ có `.gitkeep`**. Nó được giữ lại làm chỗ cho phần AI
> (gợi ý xếp lịch, ghép giáo viên) nhưng chưa có dòng code nào — README cũ mô tả nó như đã
> làm xong, nay sửa lại cho đúng.

## Cơ sở dữ liệu

42 bảng do migration tạo ra, dựng hoàn toàn bằng **Flyway** — không sửa schema bằng tay.
Ba quy ước phải biết trước khi viết migration mới:

1. **Dự án KHUYẾT V18** (nhảy thẳng V17 → V19). Migration mới phải lấy số **lớn hơn số lớn
   nhất hiện có** (hiện là V38), **tuyệt đối không** điền vào chỗ trống — điền vào là mọi DB
   đã chạy V19 trở đi đều đỏ *"resolved migration not applied to database"*.
2. **Không sửa migration đã chạy.** Đổi nội dung là đổi checksum, cả nhóm lỗi khởi động.
3. **Xóa MỀM là chính** (`IsDeleted`). Khóa ngoại không bao giờ nhìn thấy một câu `UPDATE`,
   nên ràng buộc toàn vẹn khi xóa phải viết ở tầng service — dùng
   `com.kdc.tsdms.common.DeleteGuard`, xem
   [dev-note Đợt 1–4](docs/dev-notes/2026-08-17-database-rang-buoc-toan-ven-khi-xoa.md) và
   [Đợt 5](docs/dev-notes/2026-08-24-fullstack-rang-buoc-xoa-dot-5.md).

Chi tiết schema & từ điển dữ liệu: [database/README.md](database/README.md).

## Kiểm thử

```bash
cd backend
./mvnw test         # 347 unit test — KHÔNG cần Docker (CI chạy đúng lệnh này)
./mvnw verify       # thêm 28 integration test (*IT) — CẦN Docker
```

`*IT` dùng **Testcontainers** dựng SQL Server thật để chạy toàn bộ Flyway và bắt lỗi migration.

> ⚠️ **Bẫy phải nhớ:** không có Docker thì các `*IT` **bị bỏ qua trong im lặng** và build vẫn
> in `BUILD SUCCESS`. Luôn đọc dòng `Skipped:` chứ đừng đọc chữ `SUCCESS`. Đã có lần một
> migration hỏng làm chết Flyway trên DB trống mà CI không thấy vì đúng lý do này.

Frontend:

```bash
cd frontend
npm run lint        # eslint --fix
npm run format      # prettier --write src/
npm run build
```

## CI & quy ước đóng góp

CI (`.github/workflows/ci.yml`) chạy trên mọi Pull Request và mọi push lên master:
`./mvnw -B test` — gồm **spotless:check** (code chưa format ⇒ check đỏ) và **unit test**.

Hai điều CI **không** làm, cần tự chạy trước khi mở PR:

- **Không chạy `*IT`** (dùng `test` chứ không phải `verify`). Ai sửa migration hoặc entity thì
  phải bật Docker chạy `./mvnw verify` ở máy.
- **Không kiểm frontend.** Không có eslint/prettier/build trong CI, nên hãy tự chạy — đã từng
  có 11 file trôi mất định dạng vì không ai để ý.

Quy ước làm việc nhóm (đặt tên nhánh, chống merge conflict, review):
[docs/dev-notes/quy-uoc-lam-viec-nhom.md](docs/dev-notes/quy-uoc-lam-viec-nhom.md).

## Biến môi trường

Tất cả theo cú pháp `${BIẾN:mặc-định}` trong
[application.yaml](backend/src/main/resources/application.yaml): **không đặt thì dùng mặc định
local**, nên chỉ cần quan tâm khi deploy.

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

> Mặc định của `MAIL_FROM` vẫn còn tên thương hiệu cũ **KDC EduOps** — sót lại từ lần đổi tên
> sang Sao Việt. Nó chỉ là giá trị mặc định trong `application.yaml`, chưa sửa vì đổi tên người
> gửi là đổi hành vi chứ không phải sửa tài liệu.

Không cấu hình mail thì app **vẫn chạy bình thường**, chỉ là email đặt lại mật khẩu không gửi
được — link reset được ghi ra log ở mức `WARN` để dev tự copy mà test luồng.

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

## Tài liệu

- **[docs/dev-notes/](docs/dev-notes/)** — 62 ghi chú giải thích từng tính năng khó, có mục
  lục ở [README](docs/dev-notes/README.md). Đây là chỗ nên đọc đầu tiên khi cần hiểu *vì sao*
  một đoạn code được viết như vậy.
- **[frontend/README.md](frontend/README.md)** — cấu trúc & quy ước FE.
- **[database/README.md](database/README.md)** — schema, quy ước, hướng dẫn migration.
