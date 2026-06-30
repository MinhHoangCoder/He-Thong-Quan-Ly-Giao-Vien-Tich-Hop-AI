/* =====================================================================
   V9 — THỜI KHÓA BIỂU DẠY: danh mục Tiết + mẫu lặp tuần (AssignmentSlot)
   ---------------------------------------------------------------------
   BỐI CẢNH:
     Assignment là PHÂN CÔNG mức KỲ (vài tháng: GV ↔ trường ↔ môn ↔ lớp),
     nở ra nhiều Schedule (buổi dạy cụ thể ngày-giờ). Trước V9 chưa có chỗ
     mô tả "thời khóa biểu tuần" (thứ mấy, tiết mấy), và Schedule cũng chưa
     biết một buổi thuộc "tiết" nào.

   3 THAY ĐỔI:
     1) Period         — khung tiết theo TỪNG TRƯỜNG (mỗi trường giờ giấc riêng);
                         dữ liệu seed theo trường ở seed demo, không seed trong migration.
     2) AssignmentSlot — MẪU lặp tuần của một Assignment: (thứ, tiết[, phòng]).
                         Đặt ở bảng CON để 1 phân công ôm được NHIỀU tiết/tuần
                         mà KHÔNG phá grain "mức kỳ" của Assignment.
     3) Schedule += PeriodId, SourceSlotId — buổi thật biết "tiết mấy" và sinh
                         ra từ slot nào (để sinh lại / hủy khi slot đổi).

   GHI CHÚ:
     - Flyway tự chạy 1 lần → CREATE TABLE thẳng (giống V8), KHÔNG DROP.
     - PK đặt tên "Id" (V7); cột FK giữ prefix tự mô tả. Audit DATETIME2(3).
     - Không trùng lịch GV kiểm ở tầng Service (so chồng StartDate/EndDate);
       index bên dưới chỉ để truy vấn nhanh — giống cách Schedule đang làm.
   ===================================================================== */

/* ---------- 1) Period — khung TIẾT theo TỪNG TRƯỜNG (mỗi trường giờ giấc riêng) ----------
   Mỗi trường có số tiết & giờ giấc khác nhau → Period gắn SchoolId. Là dữ liệu VẬN HÀNH
   (trường sửa được qua admin, UPDATE từng dòng) nên có soft-delete + audit như bảng khác.
   DỮ LIỆU tiết KHÔNG seed trong migration mà nằm ở database/seed/TSDMS_Seed_Demo.sql,
   seed theo từng trường — dễ maintain & demo được sự khác nhau giữa các trường. */
CREATE TABLE Period (
    Id           INT IDENTITY PRIMARY KEY,
    SchoolId     INT          NOT NULL,                -- → School: khung tiết của riêng trường này
    PeriodNumber TINYINT      NOT NULL,                -- số tiết trong ngày của trường (1..n)
    SessionType  VARCHAR(10)  NOT NULL,                -- MORNING | AFTERNOON
    StartTime    TIME(0)      NOT NULL,                -- giờ bắt đầu tiết (khung cố định mọi ngày, KHÔNG kèm ngày)
    EndTime      TIME(0)      NOT NULL,                -- giờ kết thúc tiết
    IsDeleted    BIT          NOT NULL DEFAULT 0,
    DeletedAt    DATETIME2(3) NULL,
    DeletedBy    INT          NULL,
    CreatedAt    DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy    INT          NULL,
    UpdatedAt    DATETIME2(3) NULL,
    UpdatedBy    INT          NULL,
    CONSTRAINT FK_Period_School  FOREIGN KEY (SchoolId) REFERENCES School(Id),
    CONSTRAINT CK_Period_Session CHECK (SessionType IN ('MORNING','AFTERNOON'))
);
GO
/* Trong 1 trường không trùng số tiết (trên bản ghi chưa xóa mềm). */
CREATE UNIQUE INDEX UX_Period_School_Number ON Period(SchoolId, PeriodNumber) WHERE IsDeleted = 0;
GO

/* ---------- 2) AssignmentSlot — mẫu lặp tuần của một Assignment ---------- */
CREATE TABLE AssignmentSlot (
    Id           INT IDENTITY PRIMARY KEY,
    AssignmentId INT          NOT NULL,                -- → Assignment (mức kỳ)
    TeacherId    INT          NOT NULL,                -- = GV của Assignment, lưu kèm để dò trùng nhanh (giống Schedule)
    DayOfWeek    VARCHAR(10)  NOT NULL,                -- MON..SUN
    PeriodId     INT          NOT NULL,                -- → Period (tiết)
    RoomId       INT          NULL,                    -- phòng MẶC ĐỊNH của ô lịch (generator copy xuống Schedule, cho override)
    IsDeleted    BIT          NOT NULL DEFAULT 0,
    DeletedAt    DATETIME2(3) NULL,
    DeletedBy    INT          NULL,
    CreatedAt    DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy    INT          NULL,
    UpdatedAt    DATETIME2(3) NULL,
    UpdatedBy    INT          NULL,
    CONSTRAINT FK_AssignmentSlot_Assignment FOREIGN KEY (AssignmentId) REFERENCES Assignment(Id),
    CONSTRAINT FK_AssignmentSlot_Teacher    FOREIGN KEY (TeacherId)    REFERENCES Teacher(Id),
    CONSTRAINT FK_AssignmentSlot_Period     FOREIGN KEY (PeriodId)     REFERENCES Period(Id),
    CONSTRAINT FK_AssignmentSlot_Room       FOREIGN KEY (RoomId)       REFERENCES Room(Id),
    CONSTRAINT CK_AssignmentSlot_Day CHECK (DayOfWeek IN ('MON','TUE','WED','THU','FRI','SAT','SUN'))
);
GO

/* 1 phân công không lặp cùng (thứ, tiết) trên bản ghi chưa xóa mềm. */
CREATE UNIQUE INDEX UX_AssignmentSlot_Assign_Day_Period
    ON AssignmentSlot(AssignmentId, DayOfWeek, PeriodId) WHERE IsDeleted = 0;
GO
/* Hỗ trợ dò TRÙNG LỊCH GV toàn hệ thống (cùng GV + thứ + tiết). */
CREATE INDEX IX_AssignmentSlot_Teacher_Day_Period
    ON AssignmentSlot(TeacherId, DayOfWeek, PeriodId) WHERE IsDeleted = 0;
GO

/* ---------- 3) Schedule += PeriodId, SourceSlotId (cột nullable, an toàn data cũ) ---------- */
ALTER TABLE Schedule ADD
    PeriodId     INT NULL CONSTRAINT FK_Schedule_Period     REFERENCES Period(Id),          -- buổi thuộc tiết nào
    SourceSlotId INT NULL CONSTRAINT FK_Schedule_SourceSlot REFERENCES AssignmentSlot(Id);  -- buổi sinh từ slot nào
GO
/* Tìm nhanh các buổi sinh ra từ 1 slot (để sinh lại / hủy khi slot đổi). */
CREATE INDEX IX_Schedule_SourceSlot ON Schedule(SourceSlotId) WHERE IsDeleted = 0 AND SourceSlotId IS NOT NULL;
GO
