/* =====================================================================
   V14 — THÔNG BÁO CÓ HÀNH ĐỘNG (Xác nhận / Hủy).
   Bổ sung 2 cột cho bảng Notification để phục vụ luồng "phân công lịch dạy":
     - RequiresAction : thông báo này cần giáo viên bấm Xác nhận/Hủy hay không.
     - ActionStatus   : trạng thái hành động — PENDING | CONFIRMED | CANCELLED.
   Guard COL_LENGTH để chạy lại nhiều lần vẫn an toàn (idempotent).
   ===================================================================== */
IF COL_LENGTH('Notification', 'RequiresAction') IS NULL
    ALTER TABLE Notification
        ADD RequiresAction BIT NOT NULL
            CONSTRAINT DF_Notification_RequiresAction DEFAULT 0;

IF COL_LENGTH('Notification', 'ActionStatus') IS NULL
    ALTER TABLE Notification
        ADD ActionStatus VARCHAR(20) NULL;   -- PENDING | CONFIRMED | CANCELLED
