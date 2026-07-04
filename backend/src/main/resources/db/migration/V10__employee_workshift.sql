/* =====================================================================
   V10 — CA LÀM NHÂN VIÊN: loại hình làm việc + đăng ký ca + lịch thực tế
   ---------------------------------------------------------------------
   BỐI CẢNH:
     Nhân viên trung tâm có full-time (cố định T2-T6) và part-time (đăng ký
     ca theo tuần, HR duyệt). Cần: phân loại EmploymentType, bảng ĐĂNG KÝ ca
     (ý định) và bảng LỊCH thực tế (bản thể hóa) — song song mô hình
     AssignmentSlot → Schedule của lịch dạy GV.

   3 THAY ĐỔI:
     1) Employee += EmploymentType (FULL_TIME mặc định cho data cũ).
     2) PartTimeShiftRequest — NV part-time đăng ký ca, HR duyệt/từ chối.
     3) EmployeeSchedule     — lịch làm thực tế: FIXED (full-time cố định) |
                               FROM_REQUEST (sinh từ đăng ký đã duyệt) | MANUAL.

   GHI CHÚ:
     - Flyway tự chạy 1 lần → CREATE thẳng, KHÔNG DROP. PK "Id" (V7),
       audit DATETIME2(3).
     - Cửa sổ đăng ký (T4 mở · T7 12h đóng) và việc sinh EmployeeSchedule từ
       đăng ký đã duyệt do tầng Service xử lý (phụ thuộc thời gian thực).
   ===================================================================== */

/* ---------- 1) Employee += EmploymentType ---------- */
ALTER TABLE Employee ADD EmploymentType VARCHAR(20) NOT NULL
    CONSTRAINT DF_Employee_EmploymentType DEFAULT ('FULL_TIME');
GO
ALTER TABLE Employee ADD CONSTRAINT CK_Employee_EmploymentType
    CHECK (EmploymentType IN ('FULL_TIME','PART_TIME'));
GO

/* ---------- 2) PartTimeShiftRequest — NV part-time đăng ký ca (chờ HR duyệt) ---------- */
CREATE TABLE PartTimeShiftRequest (
    Id                   INT IDENTITY PRIMARY KEY,
    EmployeeId           INT           NOT NULL,       -- → Employee (người đăng ký)
    WorkDate             DATE          NOT NULL,       -- ngày làm cụ thể
    ShiftType            VARCHAR(10)   NOT NULL,       -- MORNING | AFTERNOON
    Note                 NVARCHAR(255) NULL,           -- ghi chú của NV
    Status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    ReviewedByEmployeeId INT           NULL,           -- → Employee (HR duyệt/từ chối)
    ReviewedAt           DATETIME2(3)  NULL,
    RejectionReason      NVARCHAR(255) NULL,
    IsDeleted   BIT          NOT NULL DEFAULT 0,
    DeletedAt   DATETIME2(3) NULL,
    DeletedBy   INT          NULL,
    CreatedAt   DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy   INT          NULL,
    UpdatedAt   DATETIME2(3) NULL,
    UpdatedBy   INT          NULL,
    CONSTRAINT FK_PTRequest_Employee FOREIGN KEY (EmployeeId)           REFERENCES Employee(Id),
    CONSTRAINT FK_PTRequest_Reviewer FOREIGN KEY (ReviewedByEmployeeId) REFERENCES Employee(Id),
    CONSTRAINT CK_PTRequest_Shift  CHECK (ShiftType IN ('MORNING','AFTERNOON')),
    CONSTRAINT CK_PTRequest_Status CHECK (Status IN ('PENDING','APPROVED','REJECTED'))
);
GO

/* Mỗi NV chỉ 1 đăng ký CÒN HIỆU LỰC / ngày / ca; loại REJECTED để được đăng ký lại. */
CREATE UNIQUE INDEX UX_PTRequest_Emp_Date_Shift
    ON PartTimeShiftRequest(EmployeeId, WorkDate, ShiftType)
    WHERE IsDeleted = 0 AND Status <> 'REJECTED';
GO
CREATE INDEX IX_PTRequest_Employee_Date ON PartTimeShiftRequest(EmployeeId, WorkDate);
GO

/* ---------- 3) EmployeeSchedule — lịch làm việc THỰC TẾ theo buổi ---------- */
CREATE TABLE EmployeeSchedule (
    Id              INT IDENTITY PRIMARY KEY,
    EmployeeId      INT           NOT NULL,            -- → Employee
    WorkDate        DATE          NOT NULL,
    ShiftType       VARCHAR(10)   NOT NULL,            -- MORNING | AFTERNOON
    StartTime       TIME(0)       NOT NULL,            -- Service set theo ShiftType (08:00 sáng / 14:00 chiều)
    EndTime         TIME(0)       NOT NULL,            -- (11:00 sáng / 17:00 chiều)
    Status          VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED | ON_LEAVE | CANCELLED
    Source          VARCHAR(20)   NOT NULL DEFAULT 'FIXED',      -- FIXED | FROM_REQUEST | MANUAL
    SourceRequestId INT           NULL,                -- → PartTimeShiftRequest (CHỈ khi Source = FROM_REQUEST)
    Note            NVARCHAR(255) NULL,
    IsDeleted   BIT          NOT NULL DEFAULT 0,
    DeletedAt   DATETIME2(3) NULL,
    DeletedBy   INT          NULL,
    CreatedAt   DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy   INT          NULL,
    UpdatedAt   DATETIME2(3) NULL,
    UpdatedBy   INT          NULL,
    CONSTRAINT FK_EmpSchedule_Employee FOREIGN KEY (EmployeeId)      REFERENCES Employee(Id),
    CONSTRAINT FK_EmpSchedule_Request  FOREIGN KEY (SourceRequestId) REFERENCES PartTimeShiftRequest(Id),
    CONSTRAINT CK_EmpSchedule_Shift  CHECK (ShiftType IN ('MORNING','AFTERNOON')),
    CONSTRAINT CK_EmpSchedule_Status CHECK (Status IN ('SCHEDULED','ON_LEAVE','CANCELLED')),
    CONSTRAINT CK_EmpSchedule_Source CHECK (Source IN ('FIXED','FROM_REQUEST','MANUAL')),
    -- Có SourceRequestId KHI VÀ CHỈ KHI Source = FROM_REQUEST
    CONSTRAINT CK_EmpSchedule_SourceLink CHECK (
        (Source = 'FROM_REQUEST' AND SourceRequestId IS NOT NULL)
        OR (Source <> 'FROM_REQUEST' AND SourceRequestId IS NULL)
    )
);
GO

/* Mỗi NV chỉ 1 dòng lịch / ngày / ca (tránh xếp trùng). */
CREATE UNIQUE INDEX UX_EmpSchedule_Emp_Date_Shift
    ON EmployeeSchedule(EmployeeId, WorkDate, ShiftType) WHERE IsDeleted = 0;
GO
CREATE INDEX IX_EmpSchedule_Employee_Date ON EmployeeSchedule(EmployeeId, WorkDate);
GO
