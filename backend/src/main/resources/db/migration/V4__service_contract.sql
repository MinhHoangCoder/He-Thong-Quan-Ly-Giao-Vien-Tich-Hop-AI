/* =====================================================================
   TSDMS - V4: HỢP ĐỒNG DỊCH VỤ TRƯỜNG ↔ TRUNG TÂM (NGUỒN DOANH THU)
   ---------------------------------------------------------------------
   Thêm 1 bảng:
     ServiceContract : hợp đồng dịch vụ giữa trung tâm và trường khách hàng.
                       Đây là NGUỒN DOANH THU cho dashboard "doanh thu theo
                       năm học" (combo box). Khác hẳn bảng Contract (= hợp đồng
                       LAO ĐỘNG của giáo viên).

   Vì sao tách bảng riêng (không nhét vào School):
     - 1 trường KÝ LẠI hợp đồng mỗi năm học -> cần LỊCH SỬ nhiều hợp đồng.
       School.ContractStart/EndDate chỉ giữ được đúng 1 hợp đồng hiện tại.
     - Cần cột tiền (ContractValue) để cộng doanh thu -> School không có.

   LƯU Ý FLYWAY: KHÔNG sửa V1/V2/V3 đã chạy. Dùng IF OBJECT_ID(...) IS NULL để
   tương thích cả cách dựng DB bằng tay (TSDMS_Schema.sql) lẫn Flyway tự chạy.
   ===================================================================== */

IF OBJECT_ID(N'dbo.ServiceContract', N'U') IS NULL
BEGIN
CREATE TABLE ServiceContract (
    ServiceContractId INT IDENTITY PRIMARY KEY,
    SchoolId          INT            NOT NULL,            -- → School (trường khách hàng)
    BranchId          INT            NOT NULL,            -- → Branch (chi nhánh phụ trách)
    ContractCode      NVARCHAR(50)   NOT NULL UNIQUE,     -- mã hợp đồng, vd HDDV-2025-001
    StartDate         DATE           NOT NULL,            -- ngày bắt đầu hiệu lực
    EndDate           DATE           NOT NULL,            -- ngày hết hạn
    ContractValue     DECIMAL(18,2)  NOT NULL DEFAULT 0   -- GIÁ TRỊ HĐ = doanh thu ghi nhận
                      CONSTRAINT CK_ServiceContract_Value CHECK (ContractValue >= 0),
    Status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                      CONSTRAINT CK_ServiceContract_Status
                      CHECK (Status IN ('DRAFT','ACTIVE','EXPIRED','TERMINATED')),
    CONSTRAINT CK_ServiceContract_Dates CHECK (EndDate >= StartDate),
    -- 7 cột audit/xóa mềm chuẩn (khớp SoftDeletableEntity)
    IsDeleted         BIT            NOT NULL DEFAULT 0,
    DeletedAt         DATETIME2(3)   NULL,
    DeletedBy         INT            NULL,
    CreatedAt         DATETIME2(3)   NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy         INT            NULL,
    UpdatedAt         DATETIME2(3)   NULL,
    UpdatedBy         INT            NULL,
    FOREIGN KEY (SchoolId) REFERENCES School(SchoolId),
    FOREIGN KEY (BranchId) REFERENCES Branch(BranchId)
);
CREATE INDEX IX_ServiceContract_School ON ServiceContract(SchoolId);
CREATE INDEX IX_ServiceContract_Start  ON ServiceContract(StartDate);
END
GO

/* ========== Seed demo: 3 hợp đồng cho "Trường THPT Demo" ==========
   2 hợp đồng năm học 2025-2026 (StartDate trong 8/2025–6/2026) + 1 của năm trước
   -> đủ để combo box dashboard lọc theo năm học cho ra số khác nhau. */
INSERT INTO ServiceContract (SchoolId, BranchId, ContractCode, StartDate, EndDate, ContractValue, Status)
SELECT s.SchoolId, s.BranchId, v.ContractCode, v.StartDate, v.EndDate, v.ContractValue, v.Status
FROM School s
CROSS APPLY (VALUES
    (N'HDDV-2024-001', '2024-09-05', '2025-06-30', CAST(90000000  AS DECIMAL(18,2)), 'EXPIRED'),
    (N'HDDV-2025-001', '2025-08-15', '2026-06-30', CAST(120000000 AS DECIMAL(18,2)), 'ACTIVE'),
    (N'HDDV-2025-002', '2025-09-10', '2026-06-30', CAST(60000000  AS DECIMAL(18,2)), 'ACTIVE')
) AS v(ContractCode, StartDate, EndDate, ContractValue, Status)
WHERE s.Name = N'Trường THPT Demo'
  AND NOT EXISTS (SELECT 1 FROM ServiceContract sc WHERE sc.ContractCode = v.ContractCode);
GO
