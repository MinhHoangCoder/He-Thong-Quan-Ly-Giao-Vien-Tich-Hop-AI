/* =====================================================================
   TSDMS - V26: BỎ HẲN CẤP 3 — hệ thống chỉ còn khối 1..9
   ---------------------------------------------------------------------
   Bối cảnh: trung tâm chỉ dạy tiểu học (khối 1-5) và THCS (khối 6-9).
   Cấp 3 chưa bao giờ là nghiệp vụ thật, chỉ còn sót lại trong dữ liệu
   demo và trong các ràng buộc "1..12" của tầng service.

   Vì sao phải dọn tận DB chứ không chỉ sửa validate: bảng lương suy đơn
   giá từ SchoolClass.GradeLevel, mà biểu thức cũ là "khối <= 5 thì tiểu
   học, CÒN LẠI THCS" — nên một lớp khối 10 lọt vào DB là âm thầm được
   tính 125.000đ/tiết theo giá THCS, sai tiền lương mà không có lỗi nào
   bắn ra. Chặn ở DB thì kịch bản đó hết đường xảy ra.

   LƯU Ý FLYWAY (quy tắc nhóm):
     - Dự án KHUYẾT V18 (nhảy V17 -> V19). Migration mới phải lấy số LỚN
       HƠN số lớn nhất hiện có, TUYỆT ĐỐI không điền vào chỗ trống — điền
       vào là mọi DB đã chạy V19 trở đi đều đỏ "resolved migration not
       applied to database".
     - KHÔNG sửa V1..V25 đã chạy (sai checksum -> cả nhóm lỗi khởi động).
       Vài comment cũ trong V15/V22 còn nhắc THPT — cố ý để nguyên.
     - Mọi bước đều idempotent, chạy lại nhiều lần vẫn an toàn.
   ===================================================================== */

/* ========== 1) Lớp khối 10-12 còn sót -> xóa mềm ==========
   KHÔNG xóa cứng: lớp có thể đang bị Assignment / Schedule / Attendance
   tham chiếu, xóa cứng là gãy khóa ngoại (hoặc mất lịch sử chấm công đã
   tính lương). Xóa mềm thì mọi màn hình đều đã lọc IsDeleted = 0. */
UPDATE SchoolClass
SET IsDeleted = 1,
    DeletedAt = SYSUTCDATETIME()
WHERE IsDeleted = 0
  AND (GradeLevel LIKE '%1[0-2]%' OR Name LIKE '1[0-2][A-Z]%');
GO

/* ========== 2) Chuẩn hóa GradeLevel về đúng MỘT chữ số 1..9 ==========
   Dữ liệu cũ lẫn lộn "7", "Lớp 7", "Khối 7". Bước này áp cho CẢ dòng đã
   xóa mềm, vì CHECK ở bước 4 soi toàn bảng chứ không chỉ dòng đang sống.

   Thứ tự nhánh CASE quan trọng: phải bắt 10-12 TRƯỚC. Nếu để PATINDEX
   chạy trên "Khối 10" thì nó tìm thấy ký tự '1' đầu tiên và cho ra khối
   "1" — biến một lớp cấp 3 thành lớp 1, sai âm thầm. */
UPDATE SchoolClass
SET GradeLevel = CASE
        WHEN GradeLevel LIKE '%1[0-2]%' THEN NULL
        WHEN PATINDEX('%[1-9]%', GradeLevel) > 0
            THEN SUBSTRING(GradeLevel, PATINDEX('%[1-9]%', GradeLevel), 1)
        ELSE NULL
    END
WHERE GradeLevel IS NOT NULL
  AND GradeLevel NOT LIKE '[1-9]';
GO

/* ========== 3) Bài giảng gắn nhãn khối cấp 3 -> gỡ nhãn ==========
   Chỉ gỡ NHÃN khối, giữ nguyên bài giảng: nội dung là công sức soạn thật,
   không phải thứ để xóa theo. GradeLevel của Lesson là text tự do nên
   không đặt CHECK — dropdown ở service đã chỉ còn "Lớp 1".."Lớp 9". */
UPDATE Lesson
SET GradeLevel = NULL
WHERE GradeLevel LIKE '%1[0-2]%';
GO

/* ========== 4) Chốt chặn ở DB: GradeLevel chỉ nhận NULL hoặc 1..9 ==========
   LIKE '[1-9]' khớp đúng MỘT ký tự trong khoảng — nên "10" (hai ký tự)
   bị chặn, và mọi dạng chữ như "Khối 7" cũng bị chặn luôn. Service đã
   chuẩn hóa về đúng dạng này trước khi lưu (SchoolClassService). */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Class_Grade')
    ALTER TABLE SchoolClass
        ADD CONSTRAINT CK_Class_Grade
        CHECK (GradeLevel IS NULL OR GradeLevel LIKE '[1-9]');
GO

/* ========== 5) Trường demo cấp 3 -> đổi thành THCS ==========
   KHÔNG xóa trường này: nó gắn với tài khoản đăng nhập demo 'school'
   (cổng Trường khách hàng) và với 3 hợp đồng dịch vụ seed ở V4. Xóa đi là
   mất luôn phần demo cổng trường. Đổi tên là đủ để cấp 3 biến mất khỏi
   mọi màn hình.

   Chỉ cần sửa School.Name: AppUser KHÔNG còn cột FullName (V6 đã bỏ, tên
   hiển thị nay lấy từ bảng tác nhân qua DisplayNameResolver). */
UPDATE School
SET Name = N'Trường THCS Demo'
WHERE Name = N'Trường THPT Demo';
GO
