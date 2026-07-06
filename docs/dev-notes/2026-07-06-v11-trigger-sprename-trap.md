# V11 — Bẫy `sp_rename` không sửa thân TRIGGER (seed demo chết trên DB dựng bằng Flyway)

> Ngày: 2026-07-06 · Migration: `V11__fix_schedule_status_trigger.sql`

## Triệu chứng

Thành viên xóa DB làm lại → chạy `database/seed/TSDMS_Seed_Demo.sql` thì chết ở bước
"duyệt lịch" với:

```
Msg 207, Procedure TR_Schedule_StatusLog, Line 10
Invalid column name 'ScheduleId'.
```

(Đã tái hiện được 100% trên SQL Server container: DB rỗng → Flyway V1→V10 → chạy seed.)

## Nguyên nhân gốc

1. `V1` tạo trigger `TR_Schedule_StatusLog` đọc `inserted.ScheduleId` (PK cũ của `Schedule`).
2. `V7` chạy `sp_rename 'Schedule.ScheduleId' → 'Id'`. **`sp_rename` chỉ đổi metadata cột,
   KHÔNG viết lại chuỗi SQL trong thân trigger/procedure** — trigger vẫn giữ nguyên chữ
   `ScheduleId`. Trigger chỉ được biên dịch lúc BẮN, nên migration chạy êm, dựng bảng êm,
   và quả bom chỉ nổ ở câu `UPDATE Schedule` đầu tiên (seed bước 18, hoặc sau này là
   service duyệt lịch!).
3. Không ai phát hiện sớm vì bản mirror `database/schema/TSDMS_Schema.sql` đã được đồng bộ
   trigger đúng (`i.Id`) — máy dựng DB tay từ mirror thì không dính, chỉ DB dựng từ đầu
   bằng Flyway (cách reset chuẩn) mới dính. Hai "nguồn sự thật" lệch nhau đúng 1 chỗ này.

## Cách sửa

- **Không sửa V1/V7** — file đã chạy trên máy cả nhóm, đổi nội dung là sai checksum, Flyway
  từ chối khởi động toàn bộ.
- Thêm `V11__fix_schedule_status_trigger.sql` dùng `CREATE OR ALTER TRIGGER` (idempotent):
  DB Flyway (trigger hỏng) được thay bản đúng; DB dựng tay (trigger vốn đúng) bị ghi đè
  bằng chính nó — vô hại.
- Đã verify trọn vòng: DB rỗng → app khởi động (Flyway V1→V11, 11/11 success) → chạy seed
  demo → `>>> Seed demo hoàn tất`, trigger tự sinh đúng 7 dòng `ScheduleStatusLog`.

## Bài học cho migration sau này

1. **`sp_rename` cột = phải rà mọi trigger/procedure/view tham chiếu cột đó** và
   `CREATE OR ALTER` lại trong CÙNG migration. Tra nhanh đối tượng tham chiếu:
   `SELECT OBJECT_NAME(referencing_id) FROM sys.sql_expression_dependencies WHERE referenced_entity_name = 'Schedule';`
2. Lỗi nằm trong trigger là loại "nổ chậm" — build xanh, app chạy, chỉ chết khi DML bắn
   trigger. Test schema (Hibernate validate) KHÔNG bắt được; phải có bước chạy seed/DML thật.
3. Seed demo chạy bằng `sqlcmd` phải kèm cờ `-I` (`QUOTED_IDENTIFIER ON`) vì schema dùng
   filtered index `WHERE IsDeleted = 0`; SSMS mặc định ON nên không thấy vấn đề này.
4. Quy trình reset DB chuẩn đã ghi ở `database/README.md` mục "⚠ Reset DB đúng cách"
   (kèm cảnh báo to đầu file mirror): **DB rỗng + để Flyway tự dựng**, không chạy tay mirror.
