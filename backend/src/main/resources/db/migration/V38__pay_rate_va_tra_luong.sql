/* =====================================================================
   V38 — ĐƠN GIÁ TIẾT DẠY RA KHỎI CODE + KHÉP VÒNG ĐỜI PHIẾU LƯƠNG.

   Hai lỗ hổng cùng nằm ở bước cuối của dây chuyền tiền lương.

   (1) ĐƠN GIÁ HARD-CODE. PayrollService giữ 115.000đ (khối 1-5) và
       125.000đ (khối 6-9) làm hằng số Java. Trung tâm tăng giá tiết =
       sửa code, build lại, deploy lại — một quyết định kinh doanh bình
       thường lại phải đi qua lập trình viên.

       Nặng hơn: kể cả sửa code cũng SAI. Bấm "Tính lại" cho tháng 7 sau
       khi đã tăng giá từ 1/9 sẽ tính tháng 7 theo giá mới, vì hằng số
       không biết gì về thời gian. Nên bảng này lưu theo KHOẢNG HIỆU LỰC
       [EffectiveFrom, EffectiveTo], và service tra giá theo NGÀY DẠY
       của từng buổi chứ không theo ngày bấm nút.

       EffectiveTo NULL = còn hiệu lực. Tăng giá là đóng dòng cũ
       (UPDATE EffectiveTo) rồi thêm dòng mới, KHÔNG sửa đè dòng cũ —
       sửa đè là xóa mất lịch sử giá, và mọi phiếu lương cũ tính lại sẽ
       ra số khác với số đã trả.

   (2) TRẠNG THÁI PAID LÀ TRẠNG THÁI CHẾT. 'PAID' có trong CHECK
       constraint của bảng Payroll, có trong danh sách trạng thái giáo
       viên được xem, và PayrollService.assertReopenable từ chối mở lại
       phiếu PAID — nhưng KHÔNG có đường code nào đặt được nó. Kế toán
       chi tiền xong không có nút nào để đánh dấu.

       V38 thêm quyền PAYROLL_PAY và mở rộng CHECK của PayrollChangeLog
       để ghi được hành động 'PAY'. Quyền RIÊNG chứ không gộp vào
       PAYROLL_MANAGE, cùng lý lẽ với PAYROLL_REOPEN ở V32: "tính lại
       lương" và "xác nhận đã chi tiền" là hai việc khác hẳn nhau về
       trách nhiệm.
   ===================================================================== */

/* ---------------------------------------------------------------------
   BẢNG ĐƠN GIÁ TIẾT DẠY THEO KHỐI, CÓ HIỆU LỰC THEO THỜI GIAN.

   Khoảng khối [GradeFrom, GradeTo] chứ không một dòng mỗi khối: barem
   thực tế đi theo CẤP HỌC (1-5 tiểu học, 6-9 THCS), khai chín dòng là
   chín chỗ có thể gõ lệch nhau.
   --------------------------------------------------------------------- */
IF OBJECT_ID('dbo.PayRate', 'U') IS NULL
CREATE TABLE PayRate (
    Id             INT IDENTITY PRIMARY KEY,
    GradeFrom      TINYINT       NOT NULL,
    GradeTo        TINYINT       NOT NULL,
    /* Đơn giá MỘT TIẾT dạy, đồng. */
    Amount         DECIMAL(18,2) NOT NULL,
    EffectiveFrom  DATE          NOT NULL,
    /* NULL = còn hiệu lực tới nay. */
    EffectiveTo    DATE          NULL,
    Note           NVARCHAR(255) NULL,
    CreatedAt      DATETIME2(3)  NOT NULL CONSTRAINT DF_PayRate_CreatedAt DEFAULT SYSUTCDATETIME(),
    CreatedBy      INT           NULL,
    UpdatedAt      DATETIME2(3)  NULL,
    UpdatedBy      INT           NULL,
    CONSTRAINT CK_PayRate_Grade CHECK (GradeFrom BETWEEN 1 AND 12 AND GradeTo BETWEEN GradeFrom AND 12),
    CONSTRAINT CK_PayRate_Amount CHECK (Amount > 0),
    CONSTRAINT CK_PayRate_Range CHECK (EffectiveTo IS NULL OR EffectiveTo >= EffectiveFrom)
);
GO

/* Truy vấn duy nhất bảng này phục vụ: "khối K ngày D thì giá bao nhiêu". */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_PayRate_Hieu_Luc')
    CREATE INDEX IX_PayRate_Hieu_Luc ON PayRate(EffectiveFrom, EffectiveTo);
GO

/* ---------------------------------------------------------------------
   Nạp đúng hai mức đang hard-code trong PayrollService.

   EffectiveFrom lùi về 2020-01-01 chứ không phải hôm nay: mọi phiếu
   lương đã sinh từ trước tới giờ đều tính theo hai mức này, nên nếu để
   ngày hiệu lực là hôm nay thì bấm "Tính lại" một kỳ cũ sẽ không tra
   được giá nào và ra 0đ.
   --------------------------------------------------------------------- */
INSERT INTO PayRate (GradeFrom, GradeTo, Amount, EffectiveFrom, Note)
SELECT v.GradeFrom, v.GradeTo, v.Amount, v.EffectiveFrom, v.Note
FROM (VALUES
    (1, 5, 115000.00, CAST('2020-01-01' AS DATE), N'Tiểu học — mức đang dùng, chuyển từ hằng số trong code'),
    (6, 9, 125000.00, CAST('2020-01-01' AS DATE), N'THCS — mức đang dùng, chuyển từ hằng số trong code')
) AS v(GradeFrom, GradeTo, Amount, EffectiveFrom, Note)
WHERE NOT EXISTS (SELECT 1 FROM PayRate);
GO

/* ---------------------------------------------------------------------
   ĐƠN GIÁ RIÊNG THEO HỢP ĐỒNG.

   Bảng PayRate ở trên là barem CHUNG theo cấp học. Thực tế trung tâm vẫn
   thương lượng riêng với một vài giáo viên (người có bằng cấp cao, người
   dạy môn khó tuyển). Thay vì đẻ ra một dòng PayRate cho mỗi người —
   phá luôn ý nghĩa "barem theo cấp" — thì ghi thẳng vào hợp đồng đã ký
   của họ, đúng chỗ con số đó thực sự nằm ngoài đời.

   NULL = không thương lượng riêng, dùng barem chung.

   Contract.BaseSalary đã có sẵn từ V1 nhưng chưa ai đọc. V38 nối nó vào
   PayrollService: giáo viên CƠ HỮU (EmploymentType = CO_HUU) có lương
   cứng theo hợp đồng cộng tiền tiết; THỈNH GIẢNG chỉ có tiền tiết.
   --------------------------------------------------------------------- */
IF COL_LENGTH('dbo.Contract', 'RatePerPeriod') IS NULL
    ALTER TABLE Contract ADD RatePerPeriod DECIMAL(18,2) NULL;
GO

/* ---------------------------------------------------------------------
   QUYỀN: xem/sửa bảng đơn giá, và xác nhận đã trả lương.
   --------------------------------------------------------------------- */
INSERT INTO Permission (Code, Description)
SELECT v.Code, v.Description
FROM (VALUES
    ('PAYRATE_MANAGE', N'Sửa bảng đơn giá tiết dạy'),
    ('PAYROLL_PAY',    N'Xác nhận đã trả lương (chốt → đã trả)')
) AS v(Code, Description)
WHERE NOT EXISTS (SELECT 1 FROM Permission p WHERE p.Code = v.Code);
GO

/* Không gán cho role nào — ADMIN đi tắt bằng hasRole, giống PAYROLL_REOPEN (V32). */

/* ---------------------------------------------------------------------
   PayrollChangeLog phải ghi được hành động PAY.

   CHECK constraint cũ chỉ cho FINALIZE/REOPEN, nên ghi log lúc trả
   lương sẽ văng lỗi ràng buộc. Drop rồi tạo lại — cách duy nhất sửa
   một CHECK trong SQL Server.
   --------------------------------------------------------------------- */
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_PayrollChangeLog_Action')
    ALTER TABLE PayrollChangeLog DROP CONSTRAINT CK_PayrollChangeLog_Action;
GO

ALTER TABLE PayrollChangeLog ADD CONSTRAINT CK_PayrollChangeLog_Action
    CHECK (Action IN ('FINALIZE', 'REOPEN', 'PAY'));
GO
