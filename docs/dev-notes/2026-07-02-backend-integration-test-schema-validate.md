# Lưới an toàn schema ↔ entity: Testcontainers + Flyway + Hibernate validate

**Ngày:** 2026-07-02 · **Tầng:** Backend test (base layer) · **Chạy:** `mvnw verify` (cần Docker Desktop)

> Bộ integration test đầu tiên của dự án chạy trên **SQL Server thật** (trong Docker), thay vì
> mock. Mục tiêu: biến "drift âm thầm" giữa entity và schema thành **test đỏ nhìn thấy được**.
> Và nó bắt được bug thật ngay lần chạy đầu (xem mục 4).

## 1. Vấn đề: 40 entity không có lưới an toàn

App chạy `ddl-auto: none` (đúng — Flyway sở hữu schema). Nhưng nghĩa là **Hibernate tin
entity 100%, không bao giờ đối chiếu với DB**. Hệ quả:

- Gõ sai tên trong `@Column(name = "...")`, sai kiểu Java, quên map cột mới của migration →
  **không có gì báo** lúc khởi động; chỉ nổ khi một query đụng đúng cột đó chạy thật —
  thường là trên máy thành viên khác, vài tuần sau.
- `TsdmsApplicationTests` (contextLoads) cũ đòi SQL Server **localhost + biến `DB_PASSWORD`**
  → `mvnw test` đỏ trên máy chưa bật DB; test phụ thuộc môi trường cá nhân.
- Không có gì chứng minh chuỗi migration V1→V10 **chạy sạch từ đầu trên DB rỗng** (máy mới
  clone về là dùng được).

## 2. Kiến trúc harness

```
Docker Desktop
   └── mcr.microsoft.com/mssql/server:2022-latest   (Testcontainers bật, ~20-40s)
         └── CREATE DATABASE TSDMS COLLATE Vietnamese_CI_AS   (khớp DB thật)
               └── Spring context @DataJpaTest:
                     1) Flyway migrate V1→V10 trên DB RỖNG      ← chứng minh migration sạch
                     2) Hibernate ddl-auto=validate              ← soi 36 entity × từng cột
                     3) Các test chạy (transactional, rollback)
```

File trong `backend/src/test/java/com/kdc/tsdms/`:

| File | Vai trò |
|---|---|
| `integration/AbstractSqlServerIT` | Bật container (singleton) + tạo DB TSDMS + trỏ datasource. KHÔNG chọn kiểu context |
| `integration/AbstractJpaSliceIT` | `@DataJpaTest` + `ddl-auto=validate` — nền cho test tầng JPA |
| `integration/SchemaMigrationValidationIT` | Assert Flyway đủ V1→V10 đúng thứ tự · validate phủ ≥36 entity · round-trip Instant/UTC |
| `integration/TimetableRepositorySmokeIT` | Smoke repo V9 (Period, AssignmentSlot) — derived query + lọc xóa mềm + FK |
| `integration/EmployeeShiftRepositorySmokeIT` | Smoke repo V10 (PartTimeShiftRequest, EmployeeSchedule) — gồm CHECK `SourceLink` |
| `TsdmsApplicationIT` | contextLoads FULL app (web+security+JPA) trên container — **thay** `TsdmsApplicationTests` cũ |

Quyết định thiết kế (để hiểu, không phải để thuộc):

- **Container singleton, không dùng `@Container`**: `@Container` là lifecycle per-class → mỗi
  lớp IT khởi động lại SQL Server ~40s. Start thủ công 1 lần trong `@DynamicPropertySource`,
  mọi lớp dùng chung; Ryuk (container phụ Testcontainers tự sinh) dọn khi JVM thoát.
- **`@DataJpaTest` (slice) thay vì `@SpringBootTest`** cho phần schema/repo: chỉ dựng tầng
  JPA + Flyway, không web/security/mail → nhẹ; và vì các lớp con dùng **chung cấu hình
  context** nên Spring cache lại — container + migrate + validate chỉ tốn 1 lần cho cả lượt
  (lớp IT thứ 2, 3 chạy trong ~0.2s).
- **Tạo DB `TSDMS` riêng thay vì xài `master`** của container, kèm đúng collation
  `Vietnamese_CI_AS` — môi trường test giống DB thật nhất có thể.
- **Đặt tên `*IT` + maven-failsafe**: surefire (`mvnw test`) chỉ chạy `*Test` thuần unit —
  không cần Docker/DB; failsafe (`mvnw verify`) chạy thêm `*IT`. Máy không có Docker: `*IT`
  tự SKIP (`@Testcontainers(disabledWithoutDocker = true)`), build vẫn xanh.

## 3. Bẫy version — Boot 4 + Testcontainers 2.x (đọc trước khi Google!)

Tài liệu/AI trên mạng đa số nói về Boot 3 + Testcontainers 1.x. Dự án này ở thế hệ mới hơn,
tên gói **đã đổi**:

| Thứ | Boot 3 / TC 1.x (tài liệu cũ) | Dự án này (Boot 4.0.6 / TC 2.0.5) |
|---|---|---|
| Artifact Maven | `org.testcontainers:mssqlserver` | `org.testcontainers:testcontainers-mssqlserver` |
| Class container | `org.testcontainers.containers.MSSQLServerContainer<?>` (generic) | `org.testcontainers.mssqlserver.MSSQLServerContainer` (hết generic) |
| `@DataJpaTest` | `o.s.boot.test.autoconfigure.orm.jpa` | `o.s.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `o.s.boot.test.autoconfigure.jdbc` | `o.s.boot.jdbc.test.autoconfigure` |
| `TestEntityManager` | `o.s.boot.test.autoconfigure.orm.jpa` | `o.s.boot.jpa.test.autoconfigure` |

Version do BOM của Boot quản (không ghi version trong pom). SQL Server container bắt buộc
`.acceptLicense()` (EULA của Microsoft) — thiếu là container từ chối khởi động.

## 4. Mismatch THẬT harness bắt được ngay lần chạy đầu — và cách sửa

### `Instant` ↔ `DATETIME2`: lệch kiểu mang tính hệ thống

Lần chạy đầu, Hibernate validate đỏ ngay:

```
Schema validation: wrong column type encountered in column [CreatedAt] in table [AppUser];
found [datetime2 (Types#TIMESTAMP)], but expecting [datetimeoffset(7) (Types#TIMESTAMP_UTC)]
```

Nghĩa là: **Hibernate 6+ mặc định map `Instant` → `DATETIMEOFFSET`**, trong khi quy ước schema
(header V1) là *"DATETIME2(3) lưu giờ UTC"*. Dính **mọi field `Instant` của mọi entity**
(CreatedAt/UpdatedAt/DeletedAt kế thừa từ base + LastLoginAt, ExpiresAt, ReviewedAt...).
Trước giờ không ai thấy vì `ddl-auto=none` — đây đúng là loại drift bộ test này sinh ra để bắt.

Fix trong `application.yaml` chính (không phải chỉ trong test — vì nó đổi cách Hibernate
**bind tham số lúc runtime**), và phải đủ **một CẶP** cấu hình:

```yaml
spring.jpa.properties.hibernate:
  type.preferred_instant_jdbc_type: TIMESTAMP   # Instant -> datetime2 (khớp cột thật)
  jdbc.time_zone: UTC                           # bind theo UTC, KHÔNG theo múi giờ JVM
```

Vì sao phải có dòng thứ hai: khi map sang `TIMESTAMP`, Hibernate ghi **wall-clock** — mặc định
theo múi giờ JVM (**+07** ở VN). Thiếu `jdbc.time_zone: UTC` thì giá trị ghi xuống lệch 7 giờ
so với `DEFAULT SYSUTCDATETIME()` của DB → so sánh thời gian (vd token hết hạn) sai âm thầm.
Test `instant_ghiXuongDatetime2TheoUtc_docLenKhongLechGio` chốt quy ước này bằng dữ liệu thật:
ghi `08:30 UTC` → DB phải chứa đúng chuỗi `08:30:00.123` (kiểm bằng SQL thô) → đọc lên khớp
100% Instant ban đầu. Ai lỡ xóa 1 trong 2 dòng cấu hình là test này đỏ.

### Dọn kèm

- Bỏ `hibernate.dialect` khai tường minh trong yaml — Hibernate 7 tự chọn `SQLServerDialect`,
  khai thừa bị cảnh báo deprecation `HHH90000025`.
- `TsdmsApplicationTests` → `TsdmsApplicationIT`: contextLoads giờ chạy trên container (hết
  phụ thuộc localhost + `DB_PASSWORD`), và dời sang phase verify cùng các IT khác.

## 5. Trung thực về giới hạn: validate bắt gì / KHÔNG bắt gì

Hibernate validate so **tên bảng, tên cột, và HỌ kiểu JDBC**. Cụ thể với bộ schema này:

| Bắt được (test đỏ) | KHÔNG bắt (pass im lặng) |
|---|---|
| Thiếu bảng / thiếu cột (typo tên trong `@Column`) | Độ dài `NVARCHAR(50)` vs `(255)` |
| Sai kiểu **khác họ**: `datetime2` vs `datetimeoffset`, chuỗi vs số, DATE vs TIME | `NOT NULL` / nullable |
| Entity có cột mà migration chưa thêm (quên viết V mới) | `VARCHAR` vs `NVARCHAR` (cùng họ chuỗi) |
| | `TINYINT` vs `SMALLINT` (cùng họ số — vd `Period.periodNumber Short` vẫn pass) |
| | Index / unique / FK / CHECK |

Phần "không bắt" được **bù một phần bằng smoke test**: insert thật qua repository nên FK sai,
CHECK sai, NOT NULL thiếu... sẽ nổ khi flush (vd test EmployeeSchedule đi qua CHECK
`CK_EmpSchedule_SourceLink` của V10 thật).

## 6. Cách chạy & viết IT mới

```bash
mvnw test     # unit thuần (52 test) — KHÔNG cần Docker/DB
mvnw verify   # + integration test — cần Docker Desktop ĐANG CHẠY
              #   lần đầu: docker pull mcr.microsoft.com/mssql/server:2022-latest (~1.5GB)
mvnw verify -DskipITs   # chủ động bỏ qua IT
```

- Mỗi lượt `verify` tốn thêm ~60–90s (khởi động SQL Server + migrate + 4 lớp IT).
- Máy không có Docker: IT hiện `SKIPPED`, build vẫn xanh — thành viên không bị chặn.
- **Viết IT mới**: tầng JPA/repo → `extends AbstractJpaSliceIT`; cần full context
  (controller/security) → `@SpringBootTest` + `extends AbstractSqlServerIT`. Nhớ đuôi `*IT`.
- Thêm migration V11: sửa `SO_MIGRATION_HIEN_TAI` trong `SchemaMigrationValidationIT` (test
  dùng `≥` nên không đỏ, nhưng cập nhật để assert có nghĩa).

## 7. Trả lời nhanh cho hội đồng: "Làm sao đảm bảo entity khớp DB?"

1. Schema do **Flyway** sở hữu độc quyền (`ddl-auto=none`) — mọi thay đổi là một file V có
   version, review được, chạy đúng thứ tự.
2. Mỗi lượt `mvnw verify`, CI/máy dev dựng **SQL Server thật trong Docker**, chạy **đủ chuỗi
   migration trên DB rỗng**, rồi bật **Hibernate validate** đối chiếu từng cột của 36 entity
   với schema đó — lệch là build đỏ trước khi code kịp merge.
3. Quy ước thời gian (UTC trong `datetime2`) không chỉ là comment — có **test round-trip**
   khóa lại bằng dữ liệu thật.
