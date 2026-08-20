/* =====================================================================
   V32 — MỞ LẠI BẢNG LƯƠNG ĐÃ CHỐT (PAYROLL_REOPEN + PayrollChangeLog).

   Bối cảnh: nút "Chốt" của bảng lương là một chiều. Sau khi chốt,
   AttendanceService.assertPeriodOpen chặn MỌI thao tác ghi lên chấm công
   thuộc kỳ đó — kể cả sửa một dòng VẮNG mà chính hệ thống ghi nhầm vào
   ngày lễ (buổi "ma" sinh trước khi kỳ nghỉ được khai báo, xem V29).

   Hệ quả: một lỗi dữ liệu hoàn toàn có thật, hoàn toàn nhìn thấy, trở
   thành KHÔNG THỂ SỬA. Giáo viên mang dòng Vắng giả trong hồ sơ chuyên
   cần vĩnh viễn, và người dùng không làm sai bước nào cả — họ chỉ chốt
   lương đúng hạn.

   V32 biến lỗi không-thể-sửa thành lỗi sửa-được, kèm đủ rào:

     1) PAYROLL_REOPEN — quyền RIÊNG, KHÔNG gán cho role nào. Chỉ ADMIN
        đi tắt (hasRole('ADMIN')) như mọi quyền khác từ V3. Cố ý không
        gán cho ACCOUNTANT: người chốt và người mở khóa là cùng một
        người thì việc chốt mất hết ý nghĩa kiểm soát. Muốn mở cho kế
        toán trưởng sau này chỉ là thêm một dòng RolePermission.

     2) PayrollChangeLog — nhật ký RIÊNG cho lương, đi đúng khuôn mẫu
        AttendanceChangeLog (V24). Vì sao không thêm cột ReopenedAt/By
        vào chính bảng Payroll: cột chỉ giữ được LẦN MỚI NHẤT, mở lại
        hai lần là mất dấu lần đầu. Với dữ liệu tiền lương thì "đã từng
        mở ra rồi chốt lại mấy lần, mỗi lần vì sao, số tiền đổi từ bao
        nhiêu sang bao nhiêu" mới là thứ cần trả lời khi có tranh chấp.

   GIỚI HẠN 3 THÁNG nằm ở tầng service chứ không ở DB: đó là luật nghiệp
   vụ ("đừng đào lại sổ sách năm ngoái"), và nó cần báo lỗi tiếng Việt
   đọc được, không phải một CHECK constraint văng ra SQL error.
   ===================================================================== */

INSERT INTO Permission (Code, Description)
SELECT v.Code, v.Description
FROM (VALUES
    ('PAYROLL_REOPEN', N'Mở lại bảng lương đã chốt để điều chỉnh')
) AS v(Code, Description)
WHERE NOT EXISTS (SELECT 1 FROM Permission p WHERE p.Code = v.Code);
GO

/* CỐ Ý KHÔNG có INSERT INTO RolePermission ở đây — xem lý do (1) bên trên. */

/* ---------------------------------------------------------------------
   NHẬT KÝ VÒNG ĐỜI PHIẾU LƯƠNG.

   Ghi cả FINALIZE lẫn REOPEN chứ không chỉ REOPEN: một dòng "mở lại"
   đứng trơ trọi không trả lời được câu hỏi thực tế "phiếu này đã qua tay
   những ai, bao nhiêu lần" — phải có cả hai chiều mới đọc ra được mạch.

   Lưu kèm số tiền TRƯỚC/SAU để đọc log là biết ngay lần mở đó có làm đổi
   tiền hay không, khỏi phải dựng lại từ chấm công.
   --------------------------------------------------------------------- */
IF OBJECT_ID('dbo.PayrollChangeLog', 'U') IS NULL
CREATE TABLE PayrollChangeLog (
    Id              BIGINT IDENTITY PRIMARY KEY,
    PayrollId       INT           NOT NULL,
    Action          VARCHAR(20)   NOT NULL
                    CONSTRAINT CK_PayrollChangeLog_Action
                    CHECK (Action IN ('FINALIZE', 'REOPEN')),
    /* Bắt buộc với REOPEN (service ép), để trống với FINALIZE. */
    Reason          NVARCHAR(255) NULL,
    StatusBefore    VARCHAR(20)   NULL,
    StatusAfter     VARCHAR(20)   NULL,
    NetAmountBefore DECIMAL(18,2) NULL,
    NetAmountAfter  DECIMAL(18,2) NULL,
    ChangedBy       INT           NULL,
    ChangedAt       DATETIME2(3)  NOT NULL CONSTRAINT DF_PayrollChangeLog_At DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_PayrollChangeLog_Payroll FOREIGN KEY (PayrollId) REFERENCES Payroll(Id)
);
GO

/* Truy vấn duy nhất bảng này phục vụ: "phiếu này đã qua những gì" — mới nhất trước. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_PayrollChangeLog_Payroll')
    CREATE INDEX IX_PayrollChangeLog_Payroll ON PayrollChangeLog(PayrollId, ChangedAt DESC);
GO
