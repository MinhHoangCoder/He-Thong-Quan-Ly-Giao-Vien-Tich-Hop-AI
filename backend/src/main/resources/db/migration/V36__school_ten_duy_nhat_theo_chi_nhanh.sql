/* =====================================================================
   TSDMS - V36: Tên trường DUY NHẤT trong một chi nhánh (filtered index)
   ---------------------------------------------------------------------
   VẤN ĐỀ:
     Bảng School không có ràng buộc nào về tên, nên tạo mười trường cùng
     tên "THCS Ban Mai" trong một chi nhánh là chuyện bình thường. Dữ liệu
     không hỏng, nhưng mọi chỗ CHỌN trường thì hỏng: dropdown ở màn Phân
     công hiện nhiều dòng y hệt nhau, chọn nhầm là cả chuỗi lịch dạy →
     chấm công → lương chạy sang trường khác mà không có lỗi nào bắn ra.

   GIẢI PHÁP (đúng khuôn V21 đã làm cho Subject.Code):
     UNIQUE INDEX có lọc WHERE IsDeleted = 0 — chỉ ép duy nhất trên trường
     CÒN SỐNG, để tên của trường đã xóa mềm vẫn dùng lại được, đúng như
     tầng nghiệp vụ (SchoolService.assertTenChuaDung) đang kiểm.

     Duy nhất theo CẶP (BranchId, Name) chứ không theo riêng Name: hai chi
     nhánh ở hai tỉnh có thể có trường trùng tên thật.

   AN TOÀN:
     Khác V21 (cột Code vốn đã UNIQUE nên chắc chắn không trùng), cột Name
     ở đây CHƯA từng bị ép gì — DB của mỗi người có thể đang có sẵn dòng
     trùng, mà CREATE UNIQUE INDEX gặp trùng là nổ, và migration nổ thì
     backend không khởi động được. Nên bước 1 đổi tên các dòng trùng trước.

     Đổi tên bằng cách ghép " (#<Id>)" chứ không phải " (2)", " (3)": Id là
     khóa chính nên tên mới chắc chắn không đụng nhau, không cần chạy lặp
     tới khi hết trùng. Giữ nguyên dòng có Id nhỏ nhất — dòng tạo trước
     thường là dòng thật, mấy dòng sau mới là bấm nhầm.
   ===================================================================== */

/* ---------- 1) Gỡ trùng tên trong cùng chi nhánh (nếu có) ---------- */
WITH trung AS (
    SELECT Id,
           Name,
           ROW_NUMBER() OVER (PARTITION BY BranchId, Name ORDER BY Id) AS thuTu
    FROM dbo.School
    WHERE IsDeleted = 0
)
UPDATE trung
/* LEFT(...) vì cột Name chỉ NVARCHAR(200): tên đã dài sẵn mà ghép thêm là tràn cột. */
SET Name = LEFT(Name, 180) + N' (#' + CAST(Id AS NVARCHAR(10)) + N')'
WHERE thuTu > 1;
GO

/* ---------- 2) UNIQUE có lọc: chỉ áp trên trường CHƯA xóa mềm ---------- */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'UX_School_BranchName' AND object_id = OBJECT_ID(N'dbo.School'))
    CREATE UNIQUE INDEX UX_School_BranchName ON dbo.School(BranchId, Name) WHERE IsDeleted = 0;
GO
