/* =====================================================================
   TSDMS - SEED DỮ LIỆU DEMO (~5 dòng trở lên cho mỗi bảng)
   ---------------------------------------------------------------------
   FILE NÀY NẰM Ở ĐÂU / CHẠY THẾ NÀO?
     - File SQL nằm trong git tại database/seed/ → cả nhóm dùng chung 1 bộ data.
     - Dữ liệu chỉ vào DB khi bạn MỞ FILE NÀY TRONG SSMS VÀ BẤM EXECUTE
       (sau khi đã có schema: chạy TSDMS_Schema.sql, hoặc Flyway V1+V2 đã chạy).
     - KHÔNG để data demo trong Flyway migration: migration chạy tự động ở
       MỌI môi trường, còn data demo chỉ dành cho máy dev/máy demo đồ án.

   QUY TẮC TRONG FILE:
     - Tra ID theo Username/Code/Name — KHÔNG hard-code Id tự tăng.
     - Có CHỐT CHẶN ở đầu: lỡ chạy 2 lần sẽ tự bỏ qua, không tạo data trùng.
     - Toàn bộ bọc trong TRANSACTION: lỗi giữa chừng → rollback sạch sẽ.
     - Mật khẩu của TẤT CẢ tài khoản demo: Tsdms@123 (giống 4 tài khoản gốc).

   BẢNG CỐ TÌNH ĐỂ TRỐNG (không seed):
     - RefreshToken / PasswordResetToken: sinh ra lúc CHẠY APP (đăng nhập /
       quên mật khẩu). Seed hash giả vào đây vô nghĩa và dễ gây hiểu lầm.
     - Role: danh mục cố định 4 vai trò, V1 đã seed đủ.
     - ScheduleStatusLog: KHÔNG insert tay — cuối file có lệnh UPDATE duyệt
       lịch, trigger TR_Schedule_StatusLog sẽ TỰ ghi log (đúng cơ chế thật).
   ===================================================================== */

USE TSDMS;
GO
SET NOCOUNT ON;

/* ---- CHỐT CHẶN: đã seed rồi thì thoát, không chạy lại ---- */
IF EXISTS (SELECT 1 FROM Branch WHERE Name = N'Chi nhánh Cầu Giấy')
BEGIN
    PRINT N'>>> Seed demo đã được nạp trước đó — bỏ qua, không chạy lại.';
    RETURN;
END

BEGIN TRY
BEGIN TRANSACTION;

/* =====================================================================
   1) Branch — thêm 4 chi nhánh (cùng "Chi nhánh trung tâm" từ schema = 5)
   ===================================================================== */
INSERT INTO Branch (Name, Address, Phone) VALUES
 (N'Chi nhánh Cầu Giấy', N'25 Trần Thái Tông, Cầu Giấy, Hà Nội',   '0241111111'),
 (N'Chi nhánh Hà Đông',  N'12 Quang Trung, Hà Đông, Hà Nội',       '0242222222'),
 (N'Chi nhánh Đà Nẵng',  N'88 Nguyễn Văn Linh, Hải Châu, Đà Nẵng', '0243333333'),
 (N'Chi nhánh TP.HCM',   N'215 Điện Biên Phủ, Bình Thạnh, TP.HCM', '0284444444');

DECLARE @BrTT  INT = (SELECT BranchId FROM Branch WHERE Name = N'Chi nhánh trung tâm');
DECLARE @BrCG  INT = (SELECT BranchId FROM Branch WHERE Name = N'Chi nhánh Cầu Giấy');
DECLARE @BrHD  INT = (SELECT BranchId FROM Branch WHERE Name = N'Chi nhánh Hà Đông');
DECLARE @BrDN  INT = (SELECT BranchId FROM Branch WHERE Name = N'Chi nhánh Đà Nẵng');
DECLARE @BrHCM INT = (SELECT BranchId FROM Branch WHERE Name = N'Chi nhánh TP.HCM');

/* =====================================================================
   2) AppUser — thêm 13 tài khoản (4 NV + 5 GV + 4 trường), tổng 17.
      PasswordHash = BCrypt('Tsdms@123') — dùng lại hash hợp lệ có sẵn.
   ===================================================================== */
DECLARE @Hash VARCHAR(255) = '$2b$10$QNvoqOIPKkrbysnPpWc5buzR/mVnKyCDL//p8jiTfl3VGTcs2XdfK';

-- AppUser chỉ còn ĐỊNH DANH đăng nhập (họ tên/SĐT nằm ở bảng tác nhân — xem #2).
INSERT INTO AppUser (Username, PasswordHash, Email) VALUES
 ('employee2', @Hash, 'employee2@tsdms.local'),
 ('employee3', @Hash, 'employee3@tsdms.local'),
 ('employee4', @Hash, 'employee4@tsdms.local'),
 ('employee5', @Hash, 'employee5@tsdms.local'),
 ('teacher2',  @Hash, 'teacher2@tsdms.local'),
 ('teacher3',  @Hash, 'teacher3@tsdms.local'),
 ('teacher4',  @Hash, 'teacher4@tsdms.local'),
 ('teacher5',  @Hash, 'teacher5@tsdms.local'),
 ('teacher6',  @Hash, 'teacher6@tsdms.local'),
 ('school2',   @Hash, 'school2@tsdms.local'),
 ('school3',   @Hash, 'school3@tsdms.local'),
 ('school4',   @Hash, 'school4@tsdms.local'),
 ('school5',   @Hash, 'school5@tsdms.local');

DECLARE @UAdmin INT = (SELECT AppUserId FROM AppUser WHERE Username = 'admin');
DECLARE @UEmp1  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'employee');
DECLARE @UEmp2  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'employee2');
DECLARE @UEmp3  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'employee3');
DECLARE @UEmp4  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'employee4');
DECLARE @UEmp5  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'employee5');
DECLARE @UTea1  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'teacher');
DECLARE @UTea2  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'teacher2');
DECLARE @UTea3  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'teacher3');
DECLARE @UTea4  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'teacher4');
DECLARE @UTea5  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'teacher5');
DECLARE @UTea6  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'teacher6');
DECLARE @USch1  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'school');
DECLARE @USch2  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'school2');
DECLARE @USch3  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'school3');
DECLARE @USch4  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'school4');
DECLARE @USch5  INT = (SELECT AppUserId FROM AppUser WHERE Username = 'school5');

/* =====================================================================
   3) UserRole — gán vai trò cho 13 tài khoản mới
   ===================================================================== */
INSERT INTO UserRole (AppUserId, RoleId)
SELECT u.AppUserId, r.RoleId FROM AppUser u JOIN Role r ON r.Name = 'EMPLOYEE'
WHERE u.Username IN ('employee2','employee3','employee4','employee5');

INSERT INTO UserRole (AppUserId, RoleId)
SELECT u.AppUserId, r.RoleId FROM AppUser u JOIN Role r ON r.Name = 'TEACHER'
WHERE u.Username IN ('teacher2','teacher3','teacher4','teacher5','teacher6');

INSERT INTO UserRole (AppUserId, RoleId)
SELECT u.AppUserId, r.RoleId FROM AppUser u JOIN Role r ON r.Name = 'SCHOOL'
WHERE u.Username IN ('school2','school3','school4','school5');

/* =====================================================================
   4) Permission + RolePermission — ĐÃ CHUYỂN SANG FLYWAY (V3)
   ---------------------------------------------------------------------
   RBAC (danh mục quyền + gán quyền cho vai trò + 4 role phòng ban) GIỜ DO
   migration V3__rbac_permissions.sql quản lý DUY NHẤT, theo ma trận chuẩn ở
   docs/dev-notes/2026-06-14-backend-rbac-permission-matrix.md.
   KHÔNG seed Permission/RolePermission ở đây nữa — tránh trùng khóa & lệch
   bộ quyền với V3 (file seed này chỉ còn lo DỮ LIỆU DEMO).
   ===================================================================== */

/* =====================================================================
   5) Employee — 4 hồ sơ nhân viên mới, mỗi người 1 chi nhánh (tổng 5)
   ===================================================================== */
-- FirstName = tên gọi, LastName = họ + tên đệm (chuyển từ AppUser về).
INSERT INTO Employee (AppUserId, BranchId, FirstName, LastName, Phone, Position) VALUES
 (@UEmp2, @BrCG,  N'Bình', N'Trần Văn', '0901000001', N'Điều phối viên'),
 (@UEmp3, @BrHD,  N'Cúc',  N'Lê Thị',   '0901000002', N'Điều phối viên'),
 (@UEmp4, @BrDN,  N'Dũng', N'Phạm Văn', '0901000003', N'Trưởng chi nhánh'),
 (@UEmp5, @BrHCM, N'Em',   N'Võ Thị',   '0901000004', N'Trưởng chi nhánh');

DECLARE @EmpTT  INT = (SELECT EmployeeId FROM Employee WHERE AppUserId = @UEmp1);
DECLARE @EmpCG  INT = (SELECT EmployeeId FROM Employee WHERE AppUserId = @UEmp2);
DECLARE @EmpHD  INT = (SELECT EmployeeId FROM Employee WHERE AppUserId = @UEmp3);
DECLARE @EmpDN  INT = (SELECT EmployeeId FROM Employee WHERE AppUserId = @UEmp4);
DECLARE @EmpHCM INT = (SELECT EmployeeId FROM Employee WHERE AppUserId = @UEmp5);

/* =====================================================================
   6) Subject — 6 môn (STEM & Công dân số)
   ===================================================================== */
INSERT INTO Subject (Code, Name, Category, Description) VALUES
 ('STEM01', N'Lập trình Scratch',            N'STEM',        N'Lập trình kéo-thả cho học sinh tiểu học'),
 ('STEM02', N'Robotics cơ bản',              N'STEM',        N'Lắp ráp và lập trình robot giáo dục'),
 ('STEM03', N'Lập trình Python thiếu nhi',   N'STEM',        N'Python nhập môn cho học sinh THCS'),
 ('STEM04', N'Thiết kế 3D & In 3D',          N'STEM',        N'Mô hình hóa 3D và vận hành máy in 3D'),
 ('CDS01',  N'Công dân số cơ bản',           N'CONG_DAN_SO', N'Kỹ năng số nền tảng, định danh số'),
 ('CDS02',  N'An toàn trên không gian mạng', N'CONG_DAN_SO', N'Nhận diện lừa đảo, bảo vệ thông tin cá nhân');

DECLARE @SubSc  INT = (SELECT SubjectId FROM Subject WHERE Code = 'STEM01');
DECLARE @SubRo  INT = (SELECT SubjectId FROM Subject WHERE Code = 'STEM02');
DECLARE @SubPy  INT = (SELECT SubjectId FROM Subject WHERE Code = 'STEM03');
DECLARE @Sub3D  INT = (SELECT SubjectId FROM Subject WHERE Code = 'STEM04');
DECLARE @SubCD1 INT = (SELECT SubjectId FROM Subject WHERE Code = 'CDS01');
DECLARE @SubCD2 INT = (SELECT SubjectId FROM Subject WHERE Code = 'CDS02');

/* =====================================================================
   7) Teacher — 5 hồ sơ giáo viên mới (tổng 6 cùng 'Giáo viên Demo')
   ===================================================================== */
INSERT INTO Teacher (AppUserId, BranchId, FirstName, LastName, DateOfBirth, Gender, IdCardNo, Phone, Address, HireDate, EmploymentType) VALUES
 (@UTea2, @BrCG,  N'An',   N'Nguyễn Văn', '1995-03-12', 1, '001095000001', '0902000001', N'Cầu Giấy, Hà Nội',   '2024-08-01', 'FULL_TIME'),
 (@UTea3, @BrCG,  N'Bích', N'Trần Thị',   '1998-07-25', 0, '001098000002', '0902000002', N'Nam Từ Liêm, Hà Nội','2025-01-15', 'PART_TIME'),
 (@UTea4, @BrHD,  N'Châu', N'Lê Minh',    '1992-11-02', 0, '001092000003', '0902000003', N'Hà Đông, Hà Nội',    '2023-09-01', 'FULL_TIME'),
 (@UTea5, @BrDN,  N'Đạt',  N'Phạm Quốc',  '1990-01-30', 1, '001090000004', '0902000004', N'Hải Châu, Đà Nẵng',  '2025-06-01', 'CONTRACT'),
 (@UTea6, @BrHCM, N'Hà',   N'Hoàng Thu',  '1996-05-18', 0, '001096000005', '0902000005', N'Bình Thạnh, TP.HCM', '2024-02-01', 'FULL_TIME');

DECLARE @TeaDemo INT = (SELECT TeacherId FROM Teacher WHERE AppUserId = @UTea1);
DECLARE @TeaAn   INT = (SELECT TeacherId FROM Teacher WHERE AppUserId = @UTea2);
DECLARE @TeaBich INT = (SELECT TeacherId FROM Teacher WHERE AppUserId = @UTea3);
DECLARE @TeaChau INT = (SELECT TeacherId FROM Teacher WHERE AppUserId = @UTea4);
DECLARE @TeaDat  INT = (SELECT TeacherId FROM Teacher WHERE AppUserId = @UTea5);
DECLARE @TeaHa   INT = (SELECT TeacherId FROM Teacher WHERE AppUserId = @UTea6);

/* =====================================================================
   8) TeacherSubject — môn dạy được + mức thành thạo (cho AI matching)
   ===================================================================== */
INSERT INTO TeacherSubject (TeacherId, SubjectId, ProficiencyLevel) VALUES
 (@TeaDemo, @SubSc,  5), (@TeaDemo, @SubCD1, 4),
 (@TeaAn,   @SubSc,  4), (@TeaAn,   @SubRo,  5), (@TeaAn, @SubPy, 3),
 (@TeaBich, @SubCD1, 5), (@TeaBich, @SubCD2, 4),
 (@TeaChau, @SubPy,  5), (@TeaChau, @Sub3D,  4),
 (@TeaDat,  @SubRo,  4), (@TeaDat,  @SubCD1, 3),
 (@TeaHa,   @SubSc,  3), (@TeaHa,   @SubCD2, 5);

/* =====================================================================
   9) Certificate — 6 bằng cấp / chứng chỉ
   ===================================================================== */
INSERT INTO Certificate (TeacherId, Name, Issuer, IssueDate, ExpiryDate) VALUES
 (@TeaAn,   N'Cử nhân Sư phạm Tin học',           N'ĐH Sư phạm Hà Nội',     '2017-06-30', NULL),
 (@TeaAn,   N'Chứng chỉ STEM Robotics',            N'LEGO Education',        '2023-08-15', '2026-08-15'),
 (@TeaBich, N'Cử nhân Công nghệ thông tin',        N'ĐH Bách Khoa Hà Nội',   '2020-07-15', NULL),
 (@TeaChau, N'Thạc sĩ Khoa học máy tính',          N'ĐH Quốc gia Hà Nội',    '2018-09-20', NULL),
 (@TeaDat,  N'Chứng chỉ nghiệp vụ sư phạm',        N'ĐH Sư phạm Đà Nẵng',    '2022-12-01', NULL),
 (@TeaHa,   N'Chứng chỉ An toàn thông tin cơ bản', N'Trung tâm VNCERT',      '2024-04-10', '2027-04-10');

/* =====================================================================
   10) Contract — 1-1: MỖI giáo viên đúng 1 hợp đồng (theo yêu cầu GVHD)
   ===================================================================== */
INSERT INTO Contract (TeacherId, ContractNo, StartDate, EndDate, BaseSalary, Allowance, Status) VALUES
 (@TeaDemo, 'HD-2024-001', '2024-01-01', NULL,         12000000, 1000000, 'ACTIVE'),
 (@TeaAn,   'HD-2024-002', '2024-08-01', '2026-07-31', 11000000,  800000, 'ACTIVE'),
 (@TeaBich, 'HD-2026-004', '2026-01-15', NULL,          7000000,  500000, 'ACTIVE'),
 (@TeaChau, 'HD-2023-005', '2023-09-01', NULL,         13000000, 1200000, 'ACTIVE'),
 (@TeaDat,  'HD-2025-006', '2025-06-01', '2026-05-31',  9000000,  600000, 'EXPIRED'),
 (@TeaHa,   'HD-2024-007', '2024-02-01', NULL,         11500000,  900000, 'ACTIVE');

/* =====================================================================
   11) School — 4 trường mới (tổng 5 cùng 'Trường THPT Demo')
   ===================================================================== */
INSERT INTO School (BranchId, Name, Address, Phone, Email, ContactPerson, AppUserId, ContractStartDate, ContractEndDate) VALUES
 (@BrCG,  N'Trường Tiểu học Ban Mai',  N'Cầu Giấy, Hà Nội',   '0903000001', 'banmai@school.local',   N'Cô Nguyễn Thu Hằng', @USch2, '2025-09-01', '2026-08-31'),
 (@BrHD,  N'Trường THCS Nguyễn Trãi',  N'Hà Đông, Hà Nội',    '0903000002', 'ntrai@school.local',    N'Thầy Đỗ Văn Kiên',   @USch3, '2025-09-01', '2026-08-31'),
 (@BrDN,  N'Trường Tiểu học Hòa Bình', N'Hải Châu, Đà Nẵng',  '0903000003', 'hoabinh@school.local',  N'Cô Trần Mỹ Linh',    @USch4, '2026-01-01', '2026-12-31'),
 (@BrHCM, N'Trường THPT Lê Quý Đôn',   N'Quận 3, TP.HCM',     '0903000004', 'lequydon@school.local', N'Thầy Lê Hoàng Nam',  @USch5, '2025-08-01', '2027-07-31');

DECLARE @SchDemo INT = (SELECT SchoolId FROM School WHERE AppUserId = @USch1);
DECLARE @SchBM   INT = (SELECT SchoolId FROM School WHERE AppUserId = @USch2);
DECLARE @SchNT   INT = (SELECT SchoolId FROM School WHERE AppUserId = @USch3);
DECLARE @SchHB   INT = (SELECT SchoolId FROM School WHERE AppUserId = @USch4);
DECLARE @SchLQD  INT = (SELECT SchoolId FROM School WHERE AppUserId = @USch5);

/* =====================================================================
   12) Room — 10 phòng học rải trên 5 trường
   ===================================================================== */
INSERT INTO Room (SchoolId, Name, Building, Floor, Type, Capacity) VALUES
 (@SchDemo, N'A101',       N'Nhà A', '1', 'CLASSROOM', 40),
 (@SchDemo, N'Lab Tin 1',  N'Nhà B', '2', 'LAB',       30),
 (@SchBM,   N'P201',       N'Nhà chính', '2', 'CLASSROOM', 35),
 (@SchBM,   N'P202',       N'Nhà chính', '2', 'CLASSROOM', 35),
 (@SchBM,   N'Lab STEM',   N'Nhà chính', '3', 'LAB',       25),
 (@SchNT,   N'B101',       N'Nhà B', '1', 'CLASSROOM', 40),
 (@SchNT,   N'Hội trường', N'Nhà A', '1', 'HALL',     200),
 (@SchHB,   N'C101',       NULL,     '1', 'CLASSROOM', 30),
 (@SchLQD,  N'D301',       N'Dãy D', '3', 'CLASSROOM', 45),
 (@SchLQD,  N'Lab Tin',    N'Dãy D', '1', 'LAB',       35);

DECLARE @RoomLabDemo INT = (SELECT RoomId FROM Room WHERE SchoolId = @SchDemo AND Name = N'Lab Tin 1');
DECLARE @RoomLabBM   INT = (SELECT RoomId FROM Room WHERE SchoolId = @SchBM   AND Name = N'Lab STEM');
DECLARE @RoomP202    INT = (SELECT RoomId FROM Room WHERE SchoolId = @SchBM   AND Name = N'P202');
DECLARE @RoomB101    INT = (SELECT RoomId FROM Room WHERE SchoolId = @SchNT   AND Name = N'B101');
DECLARE @RoomC101    INT = (SELECT RoomId FROM Room WHERE SchoolId = @SchHB   AND Name = N'C101');
DECLARE @RoomD301    INT = (SELECT RoomId FROM Room WHERE SchoolId = @SchLQD  AND Name = N'D301');

/* =====================================================================
   13) SchoolClass — 6 lớp, năm học 2025-2026
   ===================================================================== */
INSERT INTO SchoolClass (SchoolId, Name, GradeLevel, SchoolYear) VALUES
 (@SchDemo, N'10A1', N'Lớp 10', '2025-2026'),
 (@SchDemo, N'11A2', N'Lớp 11', '2025-2026'),
 (@SchBM,   N'4A',   N'Lớp 4',  '2025-2026'),
 (@SchBM,   N'5B',   N'Lớp 5',  '2025-2026'),
 (@SchNT,   N'6A1',  N'Lớp 6',  '2025-2026'),
 (@SchLQD,  N'10C3', N'Lớp 10', '2025-2026');

DECLARE @Cls10A1 INT = (SELECT ClassId FROM SchoolClass WHERE SchoolId = @SchDemo AND Name = N'10A1' AND SchoolYear = '2025-2026');
DECLARE @Cls11A2 INT = (SELECT ClassId FROM SchoolClass WHERE SchoolId = @SchDemo AND Name = N'11A2' AND SchoolYear = '2025-2026');
DECLARE @Cls4A   INT = (SELECT ClassId FROM SchoolClass WHERE SchoolId = @SchBM   AND Name = N'4A'   AND SchoolYear = '2025-2026');
DECLARE @Cls5B   INT = (SELECT ClassId FROM SchoolClass WHERE SchoolId = @SchBM   AND Name = N'5B'   AND SchoolYear = '2025-2026');
DECLARE @Cls6A1  INT = (SELECT ClassId FROM SchoolClass WHERE SchoolId = @SchNT   AND Name = N'6A1'  AND SchoolYear = '2025-2026');
DECLARE @Cls10C3 INT = (SELECT ClassId FROM SchoolClass WHERE SchoolId = @SchLQD  AND Name = N'10C3' AND SchoolYear = '2025-2026');

/* =====================================================================
   14) Student — 10 học sinh thuộc đúng trường của lớp sẽ ghi danh
   ===================================================================== */
INSERT INTO Student (SchoolId, FirstName, LastName, DateOfBirth, Gender) VALUES
 (@SchDemo, N'Hùng',  N'Nguyễn Văn', '2010-04-11', 1),
 (@SchDemo, N'Lan',   N'Trần Thị',   '2010-09-23', 0),
 (@SchDemo, N'Minh',  N'Lê Văn',     '2009-02-17', 1),
 (@SchBM,   N'Bảo',   N'Phạm Gia',   '2016-06-05', 1),
 (@SchBM,   N'Anh',   N'Đỗ Minh',    '2016-12-19', 0),
 (@SchBM,   N'Hân',   N'Vũ Ngọc',    '2015-03-30', 0),
 (@SchNT,   N'Long',  N'Bùi Đức',    '2014-08-08', 1),
 (@SchNT,   N'Mai',   N'Ngô Thanh',  '2014-01-25', 0),
 (@SchLQD,  N'Khánh', N'Đặng Quốc',  '2010-07-14', 1),
 (@SchLQD,  N'Trang', N'Lý Thu',     '2010-11-02', 0);

/* =====================================================================
   15) ClassEnrollment — ghi danh 10 học sinh vào lớp (tra theo tên)
   ===================================================================== */
-- Tra học sinh theo FirstName (tên gọi là duy nhất trong mỗi trường ở bộ demo này).
INSERT INTO ClassEnrollment (ClassId, StudentId)
SELECT @Cls10A1, StudentId FROM Student WHERE SchoolId = @SchDemo AND FirstName IN (N'Hùng', N'Lan');
INSERT INTO ClassEnrollment (ClassId, StudentId)
SELECT @Cls11A2, StudentId FROM Student WHERE SchoolId = @SchDemo AND FirstName = N'Minh';
INSERT INTO ClassEnrollment (ClassId, StudentId)
SELECT @Cls4A, StudentId FROM Student WHERE SchoolId = @SchBM AND FirstName IN (N'Bảo', N'Anh');
INSERT INTO ClassEnrollment (ClassId, StudentId)
SELECT @Cls5B, StudentId FROM Student WHERE SchoolId = @SchBM AND FirstName = N'Hân';
INSERT INTO ClassEnrollment (ClassId, StudentId)
SELECT @Cls6A1, StudentId FROM Student WHERE SchoolId = @SchNT AND FirstName IN (N'Long', N'Mai');
INSERT INTO ClassEnrollment (ClassId, StudentId)
SELECT @Cls10C3, StudentId FROM Student WHERE SchoolId = @SchLQD AND FirstName IN (N'Khánh', N'Trang');

/* =====================================================================
   16) Assignment — 6 phân công (GV của Schedule phải = GV của Assignment)
   ===================================================================== */
DECLARE @Asg1 INT, @Asg2 INT, @Asg3 INT, @Asg4 INT, @Asg5 INT, @Asg6 INT;

INSERT INTO Assignment (TeacherId, SchoolId, SubjectId, ClassId, AssignedByEmployeeId, StartDate, EndDate)
VALUES (@TeaDemo, @SchDemo, @SubSc, @Cls10A1, @EmpTT, '2026-01-05', '2026-06-30');
SET @Asg1 = SCOPE_IDENTITY();

INSERT INTO Assignment (TeacherId, SchoolId, SubjectId, ClassId, AssignedByEmployeeId, StartDate, EndDate)
VALUES (@TeaAn, @SchBM, @SubSc, @Cls4A, @EmpCG, '2026-02-01', '2026-07-31');
SET @Asg2 = SCOPE_IDENTITY();

INSERT INTO Assignment (TeacherId, SchoolId, SubjectId, ClassId, AssignedByEmployeeId, StartDate, EndDate)
VALUES (@TeaBich, @SchBM, @SubCD1, @Cls5B, @EmpCG, '2026-03-01', NULL);
SET @Asg3 = SCOPE_IDENTITY();

INSERT INTO Assignment (TeacherId, SchoolId, SubjectId, ClassId, AssignedByEmployeeId, StartDate, EndDate)
VALUES (@TeaChau, @SchNT, @SubPy, @Cls6A1, @EmpHD, '2026-01-15', '2026-06-15');
SET @Asg4 = SCOPE_IDENTITY();

INSERT INTO Assignment (TeacherId, SchoolId, SubjectId, ClassId, AssignedByEmployeeId, StartDate, EndDate)
VALUES (@TeaHa, @SchLQD, @SubCD2, @Cls10C3, @EmpHCM, '2026-04-01', NULL);
SET @Asg5 = SCOPE_IDENTITY();

-- Phân công đã KẾT THÚC (không gắn lớp cụ thể) — demo trạng thái COMPLETED
INSERT INTO Assignment (TeacherId, SchoolId, SubjectId, ClassId, AssignedByEmployeeId, StartDate, EndDate, Status)
VALUES (@TeaDat, @SchHB, @SubRo, NULL, @EmpDN, '2025-09-01', '2026-05-31', 'COMPLETED');
SET @Asg6 = SCOPE_IDENTITY();

/* =====================================================================
   17) Schedule — 10 buổi dạy (tạo PENDING, phần 18 sẽ duyệt qua UPDATE
       để trigger TR_Schedule_StatusLog tự sinh ScheduleStatusLog)
   ===================================================================== */
DECLARE @Sch1 BIGINT, @Sch2 BIGINT, @Sch3 BIGINT, @Sch4 BIGINT, @Sch5 BIGINT,
        @Sch6 BIGINT, @Sch7 BIGINT, @Sch8 BIGINT, @Sch9 BIGINT, @Sch10 BIGINT;

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg1, @TeaDemo, @RoomLabDemo, '2026-06-08 08:00', '2026-06-08 09:30', @UEmp1);
SET @Sch1 = SCOPE_IDENTITY();

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg1, @TeaDemo, @RoomLabDemo, '2026-06-15 08:00', '2026-06-15 09:30', @UEmp1);
SET @Sch2 = SCOPE_IDENTITY();

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg2, @TeaAn, @RoomLabBM, '2026-06-09 14:00', '2026-06-09 15:30', @UEmp2);
SET @Sch3 = SCOPE_IDENTITY();

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg2, @TeaAn, @RoomLabBM, '2026-06-16 14:00', '2026-06-16 15:30', @UEmp2);
SET @Sch4 = SCOPE_IDENTITY();

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg3, @TeaBich, @RoomP202, '2026-06-10 09:00', '2026-06-10 10:30', @UEmp2);
SET @Sch5 = SCOPE_IDENTITY();

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg3, @TeaBich, @RoomP202, '2026-06-17 09:00', '2026-06-17 10:30', @UEmp2);
SET @Sch6 = SCOPE_IDENTITY();

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg4, @TeaChau, @RoomB101, '2026-06-11 07:30', '2026-06-11 09:00', @UEmp3);
SET @Sch7 = SCOPE_IDENTITY();

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg4, @TeaChau, @RoomB101, '2026-06-13 07:30', '2026-06-13 09:00', @UEmp3);
SET @Sch8 = SCOPE_IDENTITY();

-- Buổi do AI xếp lịch (demo cột Source = 'AI')
INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, Source, CreatedByUserId)
VALUES (@Asg5, @TeaHa, @RoomD301, '2026-06-18 13:30', '2026-06-18 15:00', 'AI', @UEmp5);
SET @Sch9 = SCOPE_IDENTITY();

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, CreatedByUserId)
VALUES (@Asg6, @TeaDat, @RoomC101, '2026-05-20 08:00', '2026-05-20 09:30', @UEmp4);
SET @Sch10 = SCOPE_IDENTITY();

/* =====================================================================
   18) Duyệt lịch bằng UPDATE → trigger TỰ ghi ScheduleStatusLog (7 dòng)
       Nhớ set UpdatedBy TRƯỚC khi đổi Status (trigger đọc cột này).
   ===================================================================== */
UPDATE Schedule
SET Status = 'APPROVED', ApprovedByUserId = @UAdmin, ApprovedAt = SYSUTCDATETIME(),
    UpdatedAt = SYSUTCDATETIME(), UpdatedBy = @UAdmin
WHERE ScheduleId IN (@Sch1, @Sch2, @Sch3, @Sch5, @Sch7, @Sch10);

UPDATE Schedule
SET Status = 'REJECTED', ApprovedByUserId = @UAdmin, ApprovedAt = SYSUTCDATETIME(),
    RejectionReason = N'Trùng lịch sinh hoạt toàn trường, đề nghị dời sang tuần sau',
    UpdatedAt = SYSUTCDATETIME(), UpdatedBy = @UAdmin
WHERE ScheduleId = @Sch8;

/* =====================================================================
   19) Attendance — 6 lượt chấm công (các buổi đã dạy + 1 nghỉ phép)
   ===================================================================== */
INSERT INTO Attendance (TeacherId, ScheduleId, WorkDate, CheckIn, CheckOut, Status, CheckInMethod, ConfirmedByUserId, ConfirmedAt, Note) VALUES
 (@TeaDemo, @Sch1,  '2026-06-08', '07:55', '09:35', 'PRESENT', 'SELF',     @USch1, SYSUTCDATETIME(), NULL),
 (@TeaAn,   @Sch3,  '2026-06-09', '13:58', '15:32', 'PRESENT', 'SELF',     @USch2, SYSUTCDATETIME(), NULL),
 (@TeaBich, @Sch5,  '2026-06-10', '09:10', '10:30', 'LATE',    'SELF',     @USch2, SYSUTCDATETIME(), N'Đến muộn 10 phút do tắc đường'),
 (@TeaChau, @Sch7,  '2026-06-11', '07:28', '09:05', 'PRESENT', 'SCHOOL',   @USch3, SYSUTCDATETIME(), NULL),
 (@TeaDat,  @Sch10, '2026-05-20', '08:00', '09:30', 'PRESENT', 'EMPLOYEE', NULL,   NULL,             NULL),
 (@TeaHa,   NULL,   '2026-06-05', NULL,    NULL,    'LEAVE',   NULL,       NULL,   NULL,             N'Nghỉ phép có báo trước');

/* =====================================================================
   20) Payroll — bảng lương tháng 5/2026 cho cả 6 giáo viên
   ===================================================================== */
INSERT INTO Payroll (TeacherId, PeriodMonth, PeriodYear, BaseSalary, TaughtHours, RatePerHour, Allowance, Bonus, Deduction, Status, CreatedBy) VALUES
 (@TeaDemo, 5, 2026, 12000000, 20.0, 200000, 1000000, 500000,      0, 'PAID',      @UAdmin),
 (@TeaAn,   5, 2026, 11000000, 18.5, 180000,  800000,      0,      0, 'PAID',      @UAdmin),
 (@TeaBich, 5, 2026,  7000000, 24.0, 150000,  500000,      0, 200000, 'FINALIZED', @UAdmin),
 (@TeaChau, 5, 2026, 13000000, 16.0, 220000, 1200000, 300000,      0, 'FINALIZED', @UAdmin),
 (@TeaDat,  5, 2026,  9000000, 12.0, 170000,  600000,      0,      0, 'PAID',      @UAdmin),
 (@TeaHa,   5, 2026, 11500000, 20.0, 190000,  900000,      0,      0, 'DRAFT',     @UAdmin);

/* =====================================================================
   21) TeacherEvaluation — 5 lượt đánh giá (trường & nhân viên đánh giá)
   ===================================================================== */
INSERT INTO TeacherEvaluation (TeacherId, EvaluatorUserId, SchoolId, Score, Comment, PeriodNote) VALUES
 (@TeaDemo, @USch1, @SchDemo, 5, N'Giảng dạy nhiệt tình, học sinh rất hứng thú',       N'Học kỳ 2 / 2025-2026'),
 (@TeaAn,   @USch2, @SchBM,   5, N'Chuyên môn Robotics tốt, quản lý lớp hiệu quả',     N'Học kỳ 2 / 2025-2026'),
 (@TeaBich, @USch2, @SchBM,   4, N'Bài giảng sinh động, đôi khi đến muộn',             N'Học kỳ 2 / 2025-2026'),
 (@TeaChau, @USch3, @SchNT,   4, N'Kiến thức Python vững, cần tương tác nhiều hơn',    N'Học kỳ 2 / 2025-2026'),
 (@TeaDat,  @UEmp4, NULL,     3, N'Hoàn thành nhiệm vụ, cần cải thiện giáo án',        N'Tổng kết hợp đồng 2025-2026');

/* =====================================================================
   22) Feedback — 5 phản hồi (đủ trạng thái OPEN → CLOSED)
   ===================================================================== */
INSERT INTO Feedback (SenderUserId, Title, Content, Status, HandledByEmployeeId, Response) VALUES
 (@UTea2, N'Đề xuất bổ sung bộ kit Robotics',      N'Bộ kit hiện tại của Lab STEM Ban Mai đã cũ, đề xuất trung tâm trang bị thêm 5 bộ mới.', 'RESOLVED',    @EmpCG, N'Đã duyệt mua bổ sung 5 bộ kit, dự kiến bàn giao cuối tháng 6.'),
 (@UTea3, N'Xin đổi khung giờ dạy lớp 5B',          N'Khung giờ 9h sáng thứ 4 trùng lịch cá nhân, xin đổi sang chiều thứ 5.',                 'IN_PROGRESS', @EmpCG, NULL),
 (@UTea4, N'Phòng B101 thiếu máy chiếu',            N'Buổi dạy ngày 11/6 phải dạy chay vì máy chiếu hỏng, nhờ trung tâm làm việc với trường.', 'OPEN',       NULL,   NULL),
 (@UTea1, N'Góp ý về quy trình duyệt lịch',         N'Lịch dạy nên được duyệt trước ít nhất 3 ngày để giáo viên chủ động chuẩn bị.',          'RESOLVED',    @EmpTT, N'Đã ghi nhận, quy trình mới yêu cầu duyệt trước 3 ngày làm việc.'),
 (@UTea6, N'Hỏi về phụ cấp đi lại chi nhánh xa',    N'Em dạy ở trường cách trung tâm 15km, có được hỗ trợ phụ cấp đi lại không?',             'CLOSED',      @EmpHCM, N'Theo chính sách hiện tại, phụ cấp đi lại áp dụng từ 10km — đã cập nhật vào lương tháng 6.');

/* =====================================================================
   23) Notification — 6 thông báo tới giáo viên / trường
   ===================================================================== */
INSERT INTO Notification (RecipientUserId, Title, Content, Type, RefEntity, RefId, IsRead, ReadAt) VALUES
 (@UTea1, N'Lịch dạy đã được duyệt',        N'Buổi Scratch lớp 10A1 ngày 15/06 đã được duyệt.',            'SCHEDULE',   'Schedule',   @Sch2,  1, SYSUTCDATETIME()),
 (@UTea2, N'Lịch dạy đã được duyệt',        N'Buổi Scratch lớp 4A ngày 09/06 đã được duyệt.',              'SCHEDULE',   'Schedule',   @Sch3,  1, SYSUTCDATETIME()),
 (@UTea4, N'Lịch dạy bị từ chối',           N'Buổi Python ngày 13/06 bị từ chối: trùng lịch sinh hoạt trường.', 'SCHEDULE', 'Schedule', @Sch8,  0, NULL),
 (@UTea3, N'Bạn có phân công mới',          N'Bạn được phân công dạy Công dân số tại Trường Tiểu học Ban Mai.', 'ASSIGNMENT', 'Assignment', @Asg3, 1, SYSUTCDATETIME()),
 (@USch2, N'Lịch dạy tuần tới tại trường',  N'Trung tâm đã xếp 2 buổi dạy tại trường trong tuần 15-21/06.', 'SCHEDULE',  NULL,         NULL,   0, NULL),
 (@UTea6, N'Lịch do AI đề xuất chờ duyệt',  N'Hệ thống AI đã xếp buổi CDS02 ngày 18/06, đang chờ duyệt.',  'SCHEDULE',   'Schedule',   @Sch9,  0, NULL);

/* =====================================================================
   24) AuditLog — 5 dòng nhật ký mẫu
   ===================================================================== */
INSERT INTO AuditLog (ActorUserId, Action, Entity, EntityId, OldValue, NewValue, IpAddress) VALUES
 (@UAdmin, 'LOGIN',  NULL,       NULL,                          NULL,                       NULL,                                     '127.0.0.1'),
 (@UEmp2,  'CREATE', 'Teacher',  CAST(@TeaAn AS VARCHAR(50)),   NULL,                       N'{"fullName":"Nguyễn Văn An"}',          '192.168.1.10'),
 (@UAdmin, 'UPDATE', 'Schedule', CAST(@Sch8 AS VARCHAR(50)),    N'{"status":"PENDING"}',    N'{"status":"REJECTED"}',                 '127.0.0.1'),
 (@UEmp2,  'CREATE', 'Lesson',   NULL,                          NULL,                       N'{"title":"Scratch: Vòng lặp và điều kiện"}', '192.168.1.10'),
 (@USch2,  'LOGIN',  NULL,       NULL,                          NULL,                       NULL,                                     '203.113.5.77');

/* =====================================================================
   25) Lesson — 6 bài giảng (module mới, đủ 3 trạng thái)
   ===================================================================== */
DECLARE @Les1 INT, @Les2 INT, @Les3 INT, @Les4 INT, @Les5 INT, @Les6 INT;

INSERT INTO Lesson (SubjectId, TeacherId, BranchId, Title, Description, Content, GradeLevel, Duration, DifficultyLevel, Status, CreatedBy)
VALUES (@SubSc, @TeaDemo, @BrTT, N'Giới thiệu lập trình Scratch', N'Buổi đầu làm quen giao diện Scratch và khối lệnh chuyển động.',
        N'## Mục tiêu
- Nhận biết giao diện Scratch
- Kéo thả khối lệnh đầu tiên

## Hoạt động
1. Khởi động (10ph)
2. Thực hành theo mẫu (25ph)
3. Trình bày sản phẩm (10ph)',
        N'Lớp 4', 45, 'BASIC', 'PUBLISHED', @UEmp1);
SET @Les1 = SCOPE_IDENTITY();

INSERT INTO Lesson (SubjectId, TeacherId, BranchId, Title, Description, Content, GradeLevel, Duration, DifficultyLevel, Status, CreatedBy)
VALUES (@SubSc, @TeaAn, @BrCG, N'Scratch: Vòng lặp và điều kiện', N'Sử dụng khối lặp lại và khối nếu-thì qua trò chơi mê cung.',
        N'## Mục tiêu
- Hiểu vòng lặp repeat
- Dùng điều kiện if để xử lý va chạm', N'Lớp 4', 45, 'BASIC', 'PUBLISHED', @UEmp2);
SET @Les2 = SCOPE_IDENTITY();

INSERT INTO Lesson (SubjectId, TeacherId, BranchId, Title, Description, GradeLevel, Duration, DifficultyLevel, Status, CreatedBy)
VALUES (@SubRo, @TeaDat, @BrDN, N'Lắp ráp robot đầu tiên', N'Lắp ráp khung robot cơ bản và kết nối động cơ.', N'Lớp 6', 90, 'INTERMEDIATE', 'PUBLISHED', @UEmp4);
SET @Les3 = SCOPE_IDENTITY();

INSERT INTO Lesson (SubjectId, TeacherId, BranchId, Title, Description, GradeLevel, Duration, DifficultyLevel, Status, CreatedBy)
VALUES (@SubPy, @TeaChau, @BrHD, N'Python: Biến và kiểu dữ liệu', N'Khái niệm biến, số nguyên, chuỗi qua ví dụ tính tuổi.', N'Lớp 6', 60, 'BASIC', 'DRAFT', @UEmp3);
SET @Les4 = SCOPE_IDENTITY();

INSERT INTO Lesson (SubjectId, TeacherId, BranchId, Title, Description, GradeLevel, Duration, DifficultyLevel, Status, CreatedBy)
VALUES (@SubCD1, @TeaBich, @BrCG, N'Định danh số và dấu chân số', N'Học sinh hiểu thông tin để lại trên Internet và cách bảo vệ.', N'Lớp 5', 45, 'BASIC', 'PUBLISHED', @UEmp2);
SET @Les5 = SCOPE_IDENTITY();

-- Bài giảng không gắn GV phụ trách (TeacherId NULL) + trạng thái lưu kho
INSERT INTO Lesson (SubjectId, TeacherId, BranchId, Title, Description, GradeLevel, Duration, DifficultyLevel, Status, CreatedBy)
VALUES (@SubCD2, NULL, @BrHCM, N'Nhận diện lừa đảo trực tuyến (bản 2025)', N'Phiên bản cũ, đã thay bằng giáo trình 2026.', N'Lớp 10', 60, 'INTERMEDIATE', 'ARCHIVED', @UEmp5);
SET @Les6 = SCOPE_IDENTITY();

/* =====================================================================
   26) LessonFile — 8 file đính kèm
   ===================================================================== */
INSERT INTO LessonFile (LessonId, FileName, FileUrl, FileType, FileSizeKb, CreatedBy) VALUES
 (@Les1, N'scratch-bai1-slide.pptx',        '/uploads/lessons/scratch-bai1-slide.pptx',        'pptx', 2048, @UEmp1),
 (@Les1, N'scratch-bai1-phieu-bai-tap.pdf', '/uploads/lessons/scratch-bai1-phieu-bai-tap.pdf', 'pdf',   512, @UEmp1),
 (@Les2, N'scratch-vong-lap-slide.pptx',    '/uploads/lessons/scratch-vong-lap-slide.pptx',    'pptx', 1843, @UEmp2),
 (@Les3, N'robot-lap-rap-huong-dan.pdf',    '/uploads/lessons/robot-lap-rap-huong-dan.pdf',    'pdf',  3120, @UEmp4),
 (@Les3, N'Video hướng dẫn lắp ráp',        'https://youtu.be/demo-robot-lap-rap',             'link',  NULL, @UEmp4),
 (@Les4, N'python-bien-kieu-du-lieu.pdf',   '/uploads/lessons/python-bien-kieu-du-lieu.pdf',   'pdf',   780, @UEmp3),
 (@Les5, N'dau-chan-so-slide.pdf',          '/uploads/lessons/dau-chan-so-slide.pdf',          'pdf',   950, @UEmp2),
 (@Les6, N'lua-dao-truc-tuyen-2025.pptx',   '/uploads/lessons/lua-dao-truc-tuyen-2025.pptx',   'pptx', 2400, @UEmp5);

COMMIT TRANSACTION;
PRINT N'>>> Seed demo hoàn tất: dữ liệu mẫu đã nạp cho toàn bộ bảng nghiệp vụ.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    PRINT N'>>> LỖI khi seed — đã rollback toàn bộ. Chi tiết:';
    THROW;
END CATCH
