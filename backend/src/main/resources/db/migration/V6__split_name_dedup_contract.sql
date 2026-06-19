/* =====================================================================
   TSDMS - V6: TÁCH HỌ TÊN + AppUser BỎ TRÙNG + Teacher↔Contract 1-1
   ---------------------------------------------------------------------
   Theo yêu cầu GIẢNG VIÊN HƯỚNG DẪN (chốt qua điện thoại):
     #1 Tách họ tên: FullName -> FirstName (tên gọi / given name)
        + LastName (họ và tên đệm / family name). Áp dụng Teacher, Employee, Student.
        Quy tắc tách: TỪ CUỐI của FullName = FirstName, phần đầu còn lại = LastName
        (vd "Trần Nguyễn Văn A" -> FirstName "A", LastName "Trần Nguyễn Văn").
     #2 AppUser BỎ FullName, Phone (chuyển về bảng tác nhân). Employee nay tự giữ
        họ tên + SĐT (trước đây mượn AppUser).
     #3 Teacher có quan hệ 1-1 với Contract: mỗi GV tối đa 1 hợp đồng CHƯA xóa mềm
        (filtered UNIQUE). Hợp đồng cũ -> xóa mềm.

   LƯU Ý FLYWAY (quy tắc nhóm): KHÔNG sửa V1..V5 đã chạy. File này chỉ ALTER/UPDATE.
   GO tách BATCH cho SQL Server (cần thiết vì UPDATE tham chiếu cột vừa ADD).
   ===================================================================== */

/* ===== #1a Teacher: tách FullName -> FirstName + LastName ===== */
ALTER TABLE Teacher ADD FirstName NVARCHAR(50) NULL, LastName NVARCHAR(100) NULL;
GO
UPDATE Teacher
SET FirstName = RIGHT(RTRIM(FullName), CHARINDEX(' ', REVERSE(RTRIM(FullName)) + ' ') - 1),
    LastName  = RTRIM(LEFT(RTRIM(FullName),
                LEN(RTRIM(FullName)) - (CHARINDEX(' ', REVERSE(RTRIM(FullName)) + ' ') - 1)));
GO
ALTER TABLE Teacher ALTER COLUMN FirstName NVARCHAR(50) NOT NULL;
ALTER TABLE Teacher ALTER COLUMN LastName NVARCHAR(100) NOT NULL;
GO
DROP INDEX IX_Teacher_FullName ON Teacher;
GO
ALTER TABLE Teacher DROP COLUMN FullName;
GO
CREATE INDEX IX_Teacher_FirstName ON Teacher(FirstName, LastName);  -- sắp xếp theo TÊN
GO

/* ===== #1b + #2 Employee: thêm FirstName/LastName/Phone (lấy từ AppUser) ===== */
ALTER TABLE Employee ADD FirstName NVARCHAR(50) NULL, LastName NVARCHAR(100) NULL, Phone VARCHAR(20) NULL;
GO
-- Employee trước đây KHÔNG có tên/SĐT, mượn AppUser -> copy sang trước khi AppUser bỏ cột.
UPDATE e
SET e.FirstName = RIGHT(RTRIM(u.FullName), CHARINDEX(' ', REVERSE(RTRIM(u.FullName)) + ' ') - 1),
    e.LastName  = RTRIM(LEFT(RTRIM(u.FullName),
                  LEN(RTRIM(u.FullName)) - (CHARINDEX(' ', REVERSE(RTRIM(u.FullName)) + ' ') - 1))),
    e.Phone     = u.Phone
FROM Employee e JOIN AppUser u ON u.AppUserId = e.AppUserId;
GO
ALTER TABLE Employee ALTER COLUMN FirstName NVARCHAR(50) NOT NULL;
ALTER TABLE Employee ALTER COLUMN LastName NVARCHAR(100) NOT NULL;
GO
CREATE INDEX IX_Employee_FirstName ON Employee(FirstName, LastName);
GO

/* ===== #1c Student: tách FullName -> FirstName + LastName ===== */
ALTER TABLE Student ADD FirstName NVARCHAR(50) NULL, LastName NVARCHAR(100) NULL;
GO
UPDATE Student
SET FirstName = RIGHT(RTRIM(FullName), CHARINDEX(' ', REVERSE(RTRIM(FullName)) + ' ') - 1),
    LastName  = RTRIM(LEFT(RTRIM(FullName),
                LEN(RTRIM(FullName)) - (CHARINDEX(' ', REVERSE(RTRIM(FullName)) + ' ') - 1)));
GO
ALTER TABLE Student ALTER COLUMN FirstName NVARCHAR(50) NOT NULL;
ALTER TABLE Student ALTER COLUMN LastName NVARCHAR(100) NOT NULL;
GO
DROP INDEX IX_Student_FullName ON Student;
GO
ALTER TABLE Student DROP COLUMN FullName;
GO
CREATE INDEX IX_Student_FirstName ON Student(FirstName, LastName);
GO

/* ===== #2 AppUser: bỏ FullName, Phone (đã chuyển về bảng tác nhân) =====
   AppUser giờ CHỈ giữ định danh đăng nhập. Tên hiển thị ghép từ hồ sơ actor ở tầng app. */
ALTER TABLE AppUser DROP COLUMN FullName;
ALTER TABLE AppUser DROP COLUMN Phone;
GO

/* ===== #3 Teacher↔Contract 1-1 ===== */
-- Dữ liệu cũ có thể có GV nhiều hợp đồng -> giữ HĐ MỚI NHẤT (StartDate lớn nhất),
-- các hợp đồng còn lại chuyển XÓA MỀM để không vi phạm ràng buộc 1-1.
WITH ranked AS (
    SELECT ContractId,
           ROW_NUMBER() OVER (PARTITION BY TeacherId ORDER BY StartDate DESC, ContractId DESC) AS rn
    FROM Contract
    WHERE IsDeleted = 0
)
UPDATE c
SET c.IsDeleted = 1, c.DeletedAt = SYSUTCDATETIME()
FROM Contract c JOIN ranked r ON c.ContractId = r.ContractId
WHERE r.rn > 1;
GO
-- Thay index thường bằng filtered UNIQUE: mỗi GV tối đa 1 hợp đồng chưa xóa mềm (1-1).
DROP INDEX IX_Contract_Teacher ON Contract;
GO
CREATE UNIQUE INDEX UX_Contract_OneActivePerTeacher ON Contract(TeacherId) WHERE IsDeleted = 0;
GO
