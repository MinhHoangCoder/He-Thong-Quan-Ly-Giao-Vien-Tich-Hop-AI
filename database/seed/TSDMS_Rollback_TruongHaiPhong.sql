/* =====================================================================
   TSDMS — GỠ BỘ 30 TRƯỜNG TH/THCS HẢI PHÒNG
   ---------------------------------------------------------------------
   Đảo ngược database/seed/TSDMS_Seed_TruongHaiPhong.sql: xóa lớp, khung tiết
   và bản ghi trường của ĐÚNG 30 tên trường liệt kê trong file này.

   CHỐT CHẶN AN TOÀN: nếu bất kỳ trường nào đã được dùng thật (có phân công,
   ô lịch, buổi dạy, học sinh, phòng học), script DỪNG và báo lỗi thay vì xóa
   mù làm gãy dữ liệu vận hành.
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
BEGIN TRANSACTION;

CREATE TABLE #T (Name NVARCHAR(200) COLLATE DATABASE_DEFAULT PRIMARY KEY);
INSERT INTO #T (Name) VALUES
 (N'TH Quán Toan'),
 (N'TH Hùng Vương'),
 (N'TH Thượng Lý'),
 (N'THCS Quán Toan'),
 (N'THCS Hùng Vương'),
 (N'THCS Thượng Lý'),
 (N'TH Dư Hàng'),
 (N'TH Lê Văn Tám'),
 (N'TH Vĩnh Niệm'),
 (N'THCS Dư Hàng Kênh'),
 (N'THCS Vĩnh Niệm'),
 (N'THCS Nghĩa Xá'),
 (N'TH Lạc Viên'),
 (N'TH Đông Khê'),
 (N'TH Đằng Giang'),
 (N'THCS Chu Văn An'),
 (N'THCS Lạc Viên'),
 (N'THCS Đông Khê'),
 (N'TH Đằng Hải'),
 (N'TH Cát Bi'),
 (N'TH Tràng Cát'),
 (N'THCS Đằng Hải'),
 (N'THCS Cát Bi'),
 (N'THCS Nam Hải'),
 (N'TH Quán Trữ'),
 (N'TH Đồng Hòa'),
 (N'TH Nguyễn Công Hòa'),
 (N'THCS Quán Trữ'),
 (N'THCS Đồng Hòa'),
 (N'THCS Trần Thành Ngọ');

/* Diện gỡ = danh sách tên ở trên, CỘNG mọi trường mang đúng "dấu vân tay" của
   bộ seed này: tên bắt đầu 'TH '/'THCS ', địa chỉ đúng một trong 5 chuỗi phường,
   và cả ba ô Phone / Email / AppUserId đều trống.

   Vì sao cần vế thứ hai: danh sách trường có thể bị rút gọn hoặc sửa lại giữa
   các lần sinh (69 -> 30). Chỉ dựa vào danh sách tên thì file rollback MỚI
   không dọn nổi các trường do bản seed CŨ nạp vào, để sót hàng chục dòng.

   Vì sao vẫn an toàn: trường bạn tự thêm qua giao diện gần như luôn có số điện
   thoại hoặc địa chỉ chi tiết tới số nhà, nên không lọt vào diện này. Ngoài ra
   toàn bộ chốt chặn bên dưới vẫn chạy trước khi xóa bất cứ dòng nào. */
DECLARE @S TABLE (Id INT PRIMARY KEY);
INSERT INTO @S (Id)
SELECT s.Id
FROM School s
WHERE s.Name IN (SELECT Name FROM #T)
   OR (
        (s.Name LIKE N'TH %' OR s.Name LIKE N'THCS %')
        AND s.Address IN (N'phường Hồng Bàng, Hải Phòng', N'phường Lê Chân, Hải Phòng',
                          N'phường Ngô Quyền, Hải Phòng', N'phường Hải An, Hải Phòng',
                          N'phường Kiến An, Hải Phòng')
        AND s.Phone IS NULL AND s.Email IS NULL AND s.AppUserId IS NULL
      );

/* ---- Có dữ liệu vận hành thì DỪNG ----
   Liệt kê ĐỦ 8 bảng có khóa ngoại trỏ tới School / SchoolClass / Period.
   Lưu ý hai chỗ dễ sót:
     - Schedule KHÔNG trỏ thẳng tới lớp; nó nối qua Period (PeriodId), nên
       phải dò theo đường Schedule -> Period -> School.
     - ServiceContract và TeacherEvaluation cũng trỏ tới School. Bỏ sót là
       lệnh DELETE cuối file gãy khóa ngoại giữa chừng. */
IF EXISTS (SELECT 1 FROM Assignment a JOIN @S z ON z.Id = a.SchoolId)
    THROW 50020, N'Có trường đã được phân công (Assignment) — hủy phân công trước khi gỡ.', 1;
IF EXISTS (SELECT 1 FROM Assignment a JOIN SchoolClass c ON c.Id = a.ClassId JOIN @S z ON z.Id = c.SchoolId)
    THROW 50021, N'Có lớp của trường đang nằm trong một phân công (Assignment.ClassId).', 1;
IF EXISTS (SELECT 1 FROM AssignmentSlot sl JOIN @S z ON z.Id = sl.SchoolId)
    THROW 50022, N'Có trường đang nằm trong ô lịch phân công (AssignmentSlot).', 1;
IF EXISTS (SELECT 1 FROM AssignmentSlot sl JOIN Period p ON p.Id = sl.PeriodId JOIN @S z ON z.Id = p.SchoolId)
    THROW 50023, N'Có tiết của trường đang được dùng trong ô lịch (AssignmentSlot.PeriodId).', 1;
IF EXISTS (SELECT 1 FROM Schedule sc JOIN Period p ON p.Id = sc.PeriodId JOIN @S z ON z.Id = p.SchoolId)
    THROW 50024, N'Có buổi dạy đã phát sinh trên khung tiết của trường (Schedule).', 1;
IF EXISTS (SELECT 1 FROM ClassEnrollment e JOIN SchoolClass c ON c.Id = e.ClassId JOIN @S z ON z.Id = c.SchoolId)
    THROW 50025, N'Có học sinh đã được xếp vào lớp của trường (ClassEnrollment).', 1;
IF EXISTS (SELECT 1 FROM Student st JOIN @S z ON z.Id = st.SchoolId)
    THROW 50026, N'Có trường đã có danh sách học sinh (Student).', 1;
IF EXISTS (SELECT 1 FROM Room r JOIN @S z ON z.Id = r.SchoolId)
    THROW 50027, N'Có trường đã khai báo phòng học (Room).', 1;
IF EXISTS (SELECT 1 FROM ServiceContract sv JOIN @S z ON z.Id = sv.SchoolId)
    THROW 50028, N'Có trường đã có hợp đồng dịch vụ (ServiceContract).', 1;
IF EXISTS (SELECT 1 FROM TeacherEvaluation te JOIN @S z ON z.Id = te.SchoolId)
    THROW 50029, N'Có trường đã có phiếu đánh giá giáo viên (TeacherEvaluation).', 1;
IF EXISTS (SELECT 1 FROM School s JOIN @S z ON z.Id = s.Id WHERE s.AppUserId IS NOT NULL)
    THROW 50030, N'Có trường đã được gắn tài khoản đăng nhập — gỡ tài khoản trước.', 1;

DECLARE @GoBo INT = (SELECT COUNT(*) FROM @S);

/* ---- Xóa theo đúng thứ tự khóa ngoại ---- */
DELETE FROM SchoolClass WHERE SchoolId IN (SELECT Id FROM @S);
DELETE FROM Period      WHERE SchoolId IN (SELECT Id FROM @S);
DELETE FROM School      WHERE Id       IN (SELECT Id FROM @S);

PRINT N'Đã gỡ ' + CAST(@GoBo AS NVARCHAR(10)) + N' trường (kèm lớp và khung tiết).';

DROP TABLE #T;

COMMIT TRANSACTION;
PRINT N'>>> XONG.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DROP TABLE IF EXISTS #T;
    PRINT N'!!! LỖI — đã rollback, không xóa gì cả.';
    THROW;
END CATCH
GO
