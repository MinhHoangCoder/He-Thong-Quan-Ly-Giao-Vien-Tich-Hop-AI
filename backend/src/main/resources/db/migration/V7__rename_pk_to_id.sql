/* =====================================================================
   V7 — Đổi tên cột KHÓA CHÍNH: <Bảng>Id  ->  Id
   ---------------------------------------------------------------------
   Góp ý GVHD: đặt tên khóa chính nhất quán là "Id" cho mọi bảng, thay vì
   mỗi bảng một tên (RoleId, TeacherId, ...). Lý do: PK chỉ định danh dòng
   trong phạm vi bảng, prefix tên bảng là thừa; "Id" gọn và đồng nhất.

   PHẠM VI:
     - CHỈ đổi khóa chính ĐƠN (surrogate identity) của 27 bảng.
     - KHÔNG đổi cột KHÓA NGOẠI (TeacherId, AppUserId, SchoolId, ...) —
       giữ prefix để FK tự mô tả "trỏ tới bảng nào".
     - KHÔNG đổi 4 bảng nối có PK GHÉP (ClassEnrollment, RolePermission,
       TeacherSubject, UserRole) vì PK của chúng = các cột FK.

   VÌ SAO sp_rename là đủ:
     Trong SQL Server, ràng buộc FOREIGN KEY / PRIMARY KEY và index tham
     chiếu cột theo column_id (nội bộ), KHÔNG theo tên. Đổi tên cột bằng
     sp_rename sẽ tự cập nhật mọi FK đang trỏ tới — không cần DROP/CREATE
     lại constraint. (sp_rename có in cảnh báo "Caution..." nhưng vô hại.)
   ===================================================================== */

EXEC sp_rename 'Branch.BranchId',                         'Id', 'COLUMN';
EXEC sp_rename 'AppUser.AppUserId',                       'Id', 'COLUMN';
EXEC sp_rename 'Role.RoleId',                             'Id', 'COLUMN';
EXEC sp_rename 'Permission.PermissionId',                 'Id', 'COLUMN';
EXEC sp_rename 'RefreshToken.RefreshTokenId',             'Id', 'COLUMN';
EXEC sp_rename 'PasswordResetToken.PasswordResetTokenId', 'Id', 'COLUMN';
EXEC sp_rename 'Employee.EmployeeId',                     'Id', 'COLUMN';
EXEC sp_rename 'Subject.SubjectId',                       'Id', 'COLUMN';
EXEC sp_rename 'Teacher.TeacherId',                       'Id', 'COLUMN';
EXEC sp_rename 'Certificate.CertificateId',               'Id', 'COLUMN';
EXEC sp_rename 'Contract.ContractId',                     'Id', 'COLUMN';
EXEC sp_rename 'School.SchoolId',                         'Id', 'COLUMN';
EXEC sp_rename 'Room.RoomId',                             'Id', 'COLUMN';
EXEC sp_rename 'SchoolClass.ClassId',                     'Id', 'COLUMN';
EXEC sp_rename 'Student.StudentId',                       'Id', 'COLUMN';
EXEC sp_rename 'Assignment.AssignmentId',                 'Id', 'COLUMN';
EXEC sp_rename 'Schedule.ScheduleId',                     'Id', 'COLUMN';
EXEC sp_rename 'ScheduleStatusLog.LogId',                 'Id', 'COLUMN';
EXEC sp_rename 'Attendance.AttendanceId',                 'Id', 'COLUMN';
EXEC sp_rename 'Payroll.PayrollId',                       'Id', 'COLUMN';
EXEC sp_rename 'TeacherEvaluation.EvaluationId',          'Id', 'COLUMN';
EXEC sp_rename 'Feedback.FeedbackId',                     'Id', 'COLUMN';
EXEC sp_rename 'Notification.NotificationId',             'Id', 'COLUMN';
EXEC sp_rename 'AuditLog.AuditLogId',                     'Id', 'COLUMN';
EXEC sp_rename 'Lesson.LessonId',                         'Id', 'COLUMN';
EXEC sp_rename 'LessonFile.LessonFileId',                 'Id', 'COLUMN';
EXEC sp_rename 'ServiceContract.ServiceContractId',       'Id', 'COLUMN';
GO
