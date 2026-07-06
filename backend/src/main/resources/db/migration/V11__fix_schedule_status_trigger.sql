/* =====================================================================
   TSDMS - V11: DỰNG LẠI TRIGGER TR_Schedule_StatusLog THEO TÊN PK MỚI
   ---------------------------------------------------------------------
   Lỗi gốc: V7 đã `sp_rename 'Schedule.ScheduleId' -> 'Id'` nhưng KHÔNG dựng lại
   trigger. sp_rename chỉ đổi metadata cột — THÂN trigger vẫn là chuỗi SQL cũ đọc
   `i.ScheduleId`/`d.ScheduleId`. Hệ quả: trên DB dựng bằng Flyway, MỌI câu
   `UPDATE Schedule` nổ "Invalid column name 'ScheduleId'" (Msg 207) ngay khi
   trigger bắn — file seed demo chết ở bước "duyệt lịch bằng UPDATE".

   Vì sao trước giờ không ai thấy: bản mirror `database/schema/TSDMS_Schema.sql`
   đã được đồng bộ trigger đúng (`i.Id`), nên máy dựng DB tay từ mirror chạy êm;
   chỉ DB dựng từ đầu bằng Flyway (cách reset CHUẨN) mới dính.

   Quy tắc nhóm: KHÔNG sửa V1/V7 đã chạy (sai checksum -> cả nhóm lỗi khởi động)
   -> vá bằng migration mới. `CREATE OR ALTER` = idempotent: chạy lại nhiều lần,
   hoặc chạy đè trên DB dựng tay (trigger vốn đã đúng) đều an toàn.

   Lưu ý: cột FK `ScheduleStatusLog.ScheduleId` GIỮ NGUYÊN tên (quy ước V7 chỉ đổi
   PK thành Id, không đổi FK) — chỉ phía `inserted`/`deleted` (bảng Schedule) đổi.
   ===================================================================== */
GO
CREATE OR ALTER TRIGGER TR_Schedule_StatusLog
ON Schedule
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    INSERT INTO ScheduleStatusLog (ScheduleId, OldStatus, NewStatus, ChangedByUserId, ChangedAt)
    SELECT i.Id, d.Status, i.Status, i.UpdatedBy, SYSUTCDATETIME()
    FROM inserted i
    JOIN deleted  d ON i.Id = d.Id
    WHERE i.Status <> d.Status;   -- chỉ ghi khi trạng thái THỰC SỰ thay đổi
END;
GO
