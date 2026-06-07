# Chuẩn hóa tên dự án về TSDMS (dọn TSMS cũ + sửa TDSMS gõ nhầm)

> Ngày 2026-06-07. Đồng bộ toàn bộ tên dự án/DB về **TSDMS**.

## 1. Vấn đề
Repo lẫn lộn **3 cách viết** tên dự án:
- `TSDMS` — tên chuẩn, dùng nhất quán ở: `README`, `frontend`, `pom.xml`,
  package Java `com.kdc.tsdms`, và **connection string** `databaseName=TSDMS`
  (`application.yaml`).
- `TSMS` — tên **cũ** còn sót: tên file `TSMS_Schema.sql`, `TSMS_TuDien_DB.md`,
  comment đầu các file SQL, và các tham chiếu `TSMS_Schema_AI.sql`,
  `TSMS_Project_Spec.pdf`.
- `TDSMS` — **gõ nhầm** ở 2 dòng comment trong `schema` (CREATE DATABASE / USE).

→ Chốt: chuẩn hóa **tất cả về `TSDMS`** (khớp package + connection string đang chạy).

## 2. Đã làm
**Đổi tên file (git mv, giữ lịch sử):**
- `database/schema/TSMS_Schema.sql` → `TSDMS_Schema.sql`
- `database/TSMS_TuDien_DB.md` → `TSDMS_TuDien_DB.md`

**Sửa nội dung (TSMS/TDSMS → TSDMS):**
- `database/schema/TSDMS_Schema.sql`: header + tham chiếu `*_Schema_AI.sql` +
  sửa `TDSMS` → `TSDMS` ở dòng `CREATE DATABASE` / `USE`.
- `backend/.../db/migration/V1__init_schema.sql`: tương tự (bản sao của schema).
- `database/TSDMS_TuDien_DB.md`: tiêu đề + tham chiếu file. **Xóa dòng 1** là link
  đính kèm GitHub bị dán nhầm (`github.com/user-attachments/.../TSMS_TuDien_DB.md`).
- `README.md`, `database/README.md`, `docs/README.md`: cập nhật đường dẫn file.
- `docs/dev-notes/2026-06-06-fullstack-bo-teacherrequest.md`: cập nhật đường dẫn
  tham chiếu cho khớp tên file mới.

**KHÔNG đụng** (vốn đã đúng TSDMS): `application.yaml` (connection string),
`pom.xml`, package `com.kdc.tsdms`, frontend.

## 3. Kiểm tra
`git grep 'TSMS|TDSMS'` (trừ node_modules) → **0 kết quả**. 14 file chứa `TSDMS`.

## 4. Lưu ý cho sau này
- Các file AI dự kiến nên đặt theo tên mới: `TSDMS_Schema_AI.sql`,
  `TSDMS_Project_Spec.pdf` (đã sửa tham chiếu sẵn, file tạo sau).
- `databaseName=TSDMS` trong `application.yaml` — khi tạo DB thật trên SQL Server
  nhớ `CREATE DATABASE TSDMS` cho khớp (dòng comment trong schema đã đổi đúng).
