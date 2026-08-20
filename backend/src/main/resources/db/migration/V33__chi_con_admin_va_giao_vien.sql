/* =====================================================================
   V33 — THU HỆ THỐNG VỀ HAI TÁC NHÂN: ADMIN và GIÁO VIÊN.

   Quyết định nghiệp vụ (chủ dự án, 2026-08-20): trung tâm chỉ vận hành với
   tài khoản quản trị và tài khoản giáo viên. Bốn phòng ban (Đào tạo, Kế
   toán, Nhân sự, Tuyển sinh) và role EMPLOYEE bị bỏ hẳn — cùng hướng với
   V31 đã bỏ tác nhân Nhà trường.

   ⚠ MIGRATION NÀY XÓA DỮ LIỆU GIAO DỊCH, KHÔNG CHỈ ĐỔI SCHEMA.
   Nó chạy trên MỌI máy khi backend khởi động. Ai pull về sẽ mất phân công /
   lịch dạy / chấm công / lương / đánh giá trên máy mình. Bản sao nằm ở các
   bảng zz_bak5_* NGAY TRONG DB (xem mục 1) nên khôi phục được, nhưng phải
   biết là nó ở đó.

   VÌ SAO PHẢI ĐỘNG TỚI DỮ LIỆU GIAO DỊCH:
   Xóa tài khoản nhân viên không phải chuyện gỡ vài dòng RolePermission.
   Chúng bị 296 phiếu phân công trỏ vào qua Assignment.AssignedByEmployeeId
   và 74 đánh giá trỏ vào qua TeacherEvaluation.EvaluatorUserId — cả hai cột
   đều NOT NULL ở phía đánh giá. Chủ dự án chọn xóa luôn phần hệ quả thay vì
   gỡ liên kết, nên toàn bộ chuỗi phân công → lịch dạy → chấm công → lương
   được dọn sạch một thể.

   GIỮ LẠI:
     · 101 giáo viên, tài khoản đăng nhập của họ, hồ sơ, hợp đồng, chứng chỉ.
     · Trường, lớp, học sinh, môn, khung tiết, kho bài giảng, LỊCH NGHỈ.
     · Toàn bộ 33 dòng Permission — ADMIN đi tắt bằng hasRole nên không cần
       gán, còn TEACHER vẫn giữ 4 quyền của mình. Để nguyên danh mục thì sau
       này muốn mở lại phòng ban chỉ là thêm RolePermission, không phải dựng
       lại từ đầu.
   ===================================================================== */

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ---------------------------------------------------------------------
   1. SAO LƯU TRƯỚC KHI XÓA — zz_bak5_*

   Đi theo đúng nếp của 4 đợt wipe trước (zz_bak_*, zz_bak3_*): chép sang
   bảng nằm ngay trong DB thay vì file .bak, vì thứ cần khôi phục thường là
   MỘT bảng chứ không phải cả database.

   Có IF OBJECT_ID(...) IS NULL để chạy lại không đè mất bản sao gốc.
   --------------------------------------------------------------------- */
IF OBJECT_ID('dbo.zz_bak5_Assignment', 'U') IS NULL
    SELECT * INTO zz_bak5_Assignment FROM Assignment;
IF OBJECT_ID('dbo.zz_bak5_AssignmentSlot', 'U') IS NULL
    SELECT * INTO zz_bak5_AssignmentSlot FROM AssignmentSlot;
IF OBJECT_ID('dbo.zz_bak5_Schedule', 'U') IS NULL
    SELECT * INTO zz_bak5_Schedule FROM Schedule;
IF OBJECT_ID('dbo.zz_bak5_ScheduleStatusLog', 'U') IS NULL
    SELECT * INTO zz_bak5_ScheduleStatusLog FROM ScheduleStatusLog;
IF OBJECT_ID('dbo.zz_bak5_Attendance', 'U') IS NULL
    SELECT * INTO zz_bak5_Attendance FROM Attendance;
IF OBJECT_ID('dbo.zz_bak5_AttendanceChangeLog', 'U') IS NULL
    SELECT * INTO zz_bak5_AttendanceChangeLog FROM AttendanceChangeLog;
IF OBJECT_ID('dbo.zz_bak5_Payroll', 'U') IS NULL
    SELECT * INTO zz_bak5_Payroll FROM Payroll;
IF OBJECT_ID('dbo.zz_bak5_TeacherEvaluation', 'U') IS NULL
    SELECT * INTO zz_bak5_TeacherEvaluation FROM TeacherEvaluation;
IF OBJECT_ID('dbo.zz_bak5_Notification', 'U') IS NULL
    SELECT * INTO zz_bak5_Notification FROM Notification;
IF OBJECT_ID('dbo.zz_bak5_AppUser', 'U') IS NULL
    SELECT * INTO zz_bak5_AppUser FROM AppUser;
IF OBJECT_ID('dbo.zz_bak5_UserRole', 'U') IS NULL
    SELECT * INTO zz_bak5_UserRole FROM UserRole;
IF OBJECT_ID('dbo.zz_bak5_Employee', 'U') IS NULL
    SELECT * INTO zz_bak5_Employee FROM Employee;
IF OBJECT_ID('dbo.zz_bak5_Role', 'U') IS NULL
    SELECT * INTO zz_bak5_Role FROM Role;
IF OBJECT_ID('dbo.zz_bak5_RolePermission', 'U') IS NULL
    SELECT * INTO zz_bak5_RolePermission FROM RolePermission;
GO

/* ---------------------------------------------------------------------
   2. XÁC ĐỊNH TÀI KHOẢN BỊ XÓA — có RÀO AN TOÀN.

   Không lấy thẳng "ai có role phòng ban" rồi xóa: trên máy khác có thể có
   tài khoản vừa giữ role phòng ban vừa là ADMIN hoặc GIÁO VIÊN. Xóa nhầm
   một trong hai là hỏng hệ thống ở đúng chỗ migration này định giữ lại.

   Dùng bảng tạm #doomed (chỉ cột INT nên không dính bẫy collation của
   tempdb) — sống qua các batch GO vì Flyway chạy trên cùng một kết nối.
   --------------------------------------------------------------------- */
IF OBJECT_ID('tempdb..#doomed') IS NOT NULL DROP TABLE #doomed;
CREATE TABLE #doomed (Id INT PRIMARY KEY);

INSERT INTO #doomed (Id)
SELECT DISTINCT ur.AppUserId
FROM UserRole ur
JOIN Role r ON r.Id = ur.RoleId
WHERE r.Name IN ('ACADEMIC', 'ACCOUNTANT', 'HR', 'SALES', 'EMPLOYEE');

/* Rào 1: ai còn giữ ADMIN hoặc TEACHER thì THA — chỉ gỡ role phòng ban. */
DELETE FROM #doomed
WHERE Id IN (
    SELECT ur.AppUserId FROM UserRole ur
    JOIN Role r ON r.Id = ur.RoleId
    WHERE r.Name IN ('ADMIN', 'TEACHER')
);

/* Rào 2: ai có hồ sơ giáo viên thì THA, kể cả khi role gắn sai. */
DELETE FROM #doomed
WHERE Id IN (SELECT AppUserId FROM Teacher WHERE AppUserId IS NOT NULL);
GO

/* ---------------------------------------------------------------------
   3. XÓA DỮ LIỆU GIAO DỊCH.

   Xóa TOÀN BỘ chứ không lọc theo người tạo: mọi phiếu phân công hiện có
   đều do nhân viên phòng ban lập, nên "phần còn lại" là tập rỗng. Lọc cho
   có chỉ làm câu lệnh khó đọc mà kết quả y hệt.

   Thứ tự đi từ lá lên gốc theo khóa ngoại — đảo thứ tự là lỗi 547.
   --------------------------------------------------------------------- */
DELETE FROM AttendanceChangeLog;
DELETE FROM PayrollChangeLog;
DELETE FROM AttendanceAmendRequest;
DELETE FROM Attendance;
DELETE FROM ScheduleStatusLog;
DELETE FROM Schedule;
DELETE FROM AssignmentSlot;
DELETE FROM Assignment;
DELETE FROM Payroll;
DELETE FROM TeacherEvaluation;
GO

/* Thông báo trỏ tới dữ liệu vừa xóa, cộng thông báo của chính người bị xóa
   (RecipientUserId là NOT NULL nên buộc phải dọn trước khi xóa AppUser).
   Thông báo khác của giáo viên KHÔNG đụng tới. */
DELETE FROM Notification
WHERE RefEntity IN ('Assignment', 'Schedule', 'Attendance', 'AttendanceAmendRequest',
                    'TeacherEvaluation', 'Payroll')
   OR RecipientUserId IN (SELECT Id FROM #doomed);
GO

/* ---------------------------------------------------------------------
   4. XÓA TÀI KHOẢN NHÂN VIÊN VÀ HỒ SƠ CỦA HỌ.
   --------------------------------------------------------------------- */
DELETE FROM Feedback
WHERE SenderUserId IN (SELECT Id FROM #doomed)
   OR HandledByEmployeeId IN (SELECT Id FROM Employee WHERE AppUserId IN (SELECT Id FROM #doomed));

DELETE FROM EmployeeSchedule
WHERE EmployeeId IN (SELECT Id FROM Employee WHERE AppUserId IN (SELECT Id FROM #doomed));

DELETE FROM PartTimeShiftRequest
WHERE EmployeeId IN (SELECT Id FROM Employee WHERE AppUserId IN (SELECT Id FROM #doomed))
   OR ReviewedByEmployeeId IN (SELECT Id FROM Employee WHERE AppUserId IN (SELECT Id FROM #doomed));

DELETE FROM RefreshToken WHERE AppUserId IN (SELECT Id FROM #doomed);
DELETE FROM PasswordResetToken WHERE AppUserId IN (SELECT Id FROM #doomed);

/* AuditLog.ActorUserId cho phép NULL — gỡ liên kết thay vì xóa vết kiểm toán. */
UPDATE AuditLog SET ActorUserId = NULL WHERE ActorUserId IN (SELECT Id FROM #doomed);

DELETE FROM Employee WHERE AppUserId IN (SELECT Id FROM #doomed);
DELETE FROM UserRole WHERE AppUserId IN (SELECT Id FROM #doomed);
DELETE FROM AppUser WHERE Id IN (SELECT Id FROM #doomed);
GO

/* ---------------------------------------------------------------------
   5. XÓA 5 ROLE.

   UserRole dọn lần nữa để bắt trường hợp rào an toàn ở mục 2 đã THA một tài
   khoản (vừa là ADMIN vừa mang role phòng ban): người đó ở lại, nhưng role
   phòng ban trên họ vẫn phải biến mất.
   --------------------------------------------------------------------- */
DELETE FROM RolePermission
WHERE RoleId IN (SELECT Id FROM Role WHERE Name IN ('ACADEMIC', 'ACCOUNTANT', 'HR', 'SALES', 'EMPLOYEE'));

DELETE FROM UserRole
WHERE RoleId IN (SELECT Id FROM Role WHERE Name IN ('ACADEMIC', 'ACCOUNTANT', 'HR', 'SALES', 'EMPLOYEE'));

DELETE FROM Role WHERE Name IN ('ACADEMIC', 'ACCOUNTANT', 'HR', 'SALES', 'EMPLOYEE');

DROP TABLE #doomed;
GO

/* CỐ Ý KHÔNG xóa dòng nào trong Permission — xem mục "GIỮ LẠI" ở đầu file. */
