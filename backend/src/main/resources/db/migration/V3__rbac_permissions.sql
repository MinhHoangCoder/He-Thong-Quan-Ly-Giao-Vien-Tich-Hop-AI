/* =====================================================================
   TSDMS - V3: PHÂN QUYỀN RBAC THEO PERMISSION
   ---------------------------------------------------------------------
   Mục tiêu: tách "nhân viên" thành các PHÒNG BAN (role) và cấp QUYỀN CHI
   TIẾT (permission) cho từng phòng. Sau bước này token sẽ mang theo danh
   sách mã quyền -> controller chặn bằng @PreAuthorize("hasAuthority('...')").

   Tài liệu thiết kế: docs/dev-notes/2026-06-14-backend-rbac-permission-matrix.md

   LƯU Ý FLYWAY (quy tắc nhóm):
     - KHÔNG sửa V1/V2 đã chạy (sai checksum -> cả nhóm lỗi khởi động).
     - File này CHỈ THÊM dữ liệu (không đổi cấu trúc bảng Role/Permission/
       RolePermission/UserRole — chúng đã có sẵn từ V1).
     - Mọi INSERT đều có chốt "WHERE NOT EXISTS" -> chạy lại nhiều lần vẫn an
       toàn (idempotent), và không xung đột nếu DB dựng tay bằng TSDMS_Schema.sql.

   QUY ƯỚC: gán quan hệ theo Name/Code, TUYỆT ĐỐI không hard-code Id tự tăng.
   ===================================================================== */

/* ========== 1) Thêm 4 role phòng ban (giữ nguyên 4 role cũ của V1) ========== */
INSERT INTO Role (Name, Description)
SELECT v.Name, v.Description
FROM (VALUES
    ('HR',         N'Nhân sự'),
    ('ACCOUNTANT', N'Kế toán'),
    ('ACADEMIC',   N'Đào tạo / Học vụ'),
    ('SALES',      N'Tuyển sinh / Kinh doanh')
) AS v(Name, Description)
WHERE NOT EXISTS (SELECT 1 FROM Role r WHERE r.Name = v.Name);
GO

/* ========== 2) Seed danh mục Permission (mã quyền theo quy ước MODULE_ACTION) ========== */
INSERT INTO Permission (Code, Description)
SELECT v.Code, v.Description
FROM (VALUES
    ('USER_VIEW',               N'Xem tài khoản người dùng'),
    ('USER_MANAGE',             N'Quản lý tài khoản người dùng'),
    ('ROLE_MANAGE',             N'Gán role/quyền cho nhân viên'),
    ('TEACHER_VIEW',            N'Xem hồ sơ giáo viên'),
    ('TEACHER_MANAGE',          N'Quản lý hồ sơ giáo viên'),
    ('CONTRACT_VIEW',           N'Xem hợp đồng lao động GV + chứng chỉ'),
    ('CONTRACT_MANAGE',         N'Quản lý hợp đồng lao động GV + chứng chỉ'),
    ('SERVICE_CONTRACT_VIEW',   N'Xem hợp đồng dịch vụ trường (nguồn doanh thu)'),
    ('SERVICE_CONTRACT_MANAGE', N'Quản lý hợp đồng dịch vụ trường'),
    ('ASSIGNMENT_VIEW',         N'Xem phân công giảng dạy'),
    ('ASSIGNMENT_MANAGE',       N'Phân công giảng dạy'),
    ('SCHEDULE_VIEW',           N'Xem lịch dạy'),
    ('SCHEDULE_MANAGE',         N'Xếp/sửa lịch dạy'),
    ('SCHEDULE_APPROVE',        N'Duyệt lịch dạy'),
    ('ATTENDANCE_VIEW',         N'Xem chấm công'),
    ('ATTENDANCE_MANAGE',       N'Chấm công'),
    ('PAYROLL_VIEW',            N'Xem bảng lương'),
    ('PAYROLL_MANAGE',          N'Quản lý bảng lương'),
    ('CLASS_VIEW',              N'Xem lớp học'),
    ('CLASS_MANAGE',            N'Quản lý lớp học'),
    ('STUDENT_VIEW',            N'Xem hồ sơ học sinh'),
    ('STUDENT_MANAGE',          N'Quản lý hồ sơ học sinh'),
    ('SCHOOL_VIEW',             N'Xem trường khách hàng'),
    ('SCHOOL_MANAGE',           N'Quản lý trường khách hàng'),
    ('EVALUATION_VIEW',         N'Xem đánh giá giáo viên'),
    ('EVALUATION_MANAGE',       N'Gửi/sửa đánh giá giáo viên'),
    ('LESSON_VIEW',             N'Xem kho bài giảng'),
    ('LESSON_MANAGE',           N'Quản lý kho bài giảng'),
    ('REPORT_REVENUE_VIEW',     N'Xem dashboard doanh thu'),
    ('REPORT_OPERATION_VIEW',   N'Xem báo cáo vận hành')
) AS v(Code, Description)
WHERE NOT EXISTS (SELECT 1 FROM Permission p WHERE p.Code = v.Code);
GO

/* ========== 3) Gán quyền cho từng role (ma trận RolePermission) ==========
   Cách đọc: với role <X>, cấp mọi permission có Code nằm trong danh sách.
   ADMIN KHÔNG seed ở đây — đi tắt bằng hasRole('ADMIN') ở tầng @PreAuthorize. */

/* --- HR (Nhân sự) --- */
INSERT INTO RolePermission (RoleId, PermissionId)
SELECT r.RoleId, p.PermissionId
FROM Role r JOIN Permission p ON 1 = 1
WHERE r.Name = 'HR'
  AND p.Code IN ('TEACHER_VIEW','TEACHER_MANAGE','CONTRACT_VIEW','CONTRACT_MANAGE',
                 'ASSIGNMENT_VIEW','ASSIGNMENT_MANAGE','REPORT_OPERATION_VIEW')
  AND NOT EXISTS (SELECT 1 FROM RolePermission rp
                  WHERE rp.RoleId = r.RoleId AND rp.PermissionId = p.PermissionId);
GO

/* --- ACCOUNTANT (Kế toán) --- */
INSERT INTO RolePermission (RoleId, PermissionId)
SELECT r.RoleId, p.PermissionId
FROM Role r JOIN Permission p ON 1 = 1
WHERE r.Name = 'ACCOUNTANT'
  AND p.Code IN ('ATTENDANCE_VIEW','ATTENDANCE_MANAGE','PAYROLL_VIEW','PAYROLL_MANAGE',
                 'SERVICE_CONTRACT_VIEW','REPORT_REVENUE_VIEW','REPORT_OPERATION_VIEW')
  AND NOT EXISTS (SELECT 1 FROM RolePermission rp
                  WHERE rp.RoleId = r.RoleId AND rp.PermissionId = p.PermissionId);
GO

/* --- ACADEMIC (Đào tạo / Học vụ) --- */
INSERT INTO RolePermission (RoleId, PermissionId)
SELECT r.RoleId, p.PermissionId
FROM Role r JOIN Permission p ON 1 = 1
WHERE r.Name = 'ACADEMIC'
  AND p.Code IN ('TEACHER_VIEW','ASSIGNMENT_VIEW','SCHEDULE_VIEW','SCHEDULE_MANAGE',
                 'SCHEDULE_APPROVE','ATTENDANCE_VIEW','CLASS_VIEW','CLASS_MANAGE',
                 'STUDENT_VIEW','STUDENT_MANAGE','EVALUATION_VIEW','EVALUATION_MANAGE',
                 'LESSON_VIEW','LESSON_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM RolePermission rp
                  WHERE rp.RoleId = r.RoleId AND rp.PermissionId = p.PermissionId);
GO

/* --- SALES (Tuyển sinh / Kinh doanh) --- */
INSERT INTO RolePermission (RoleId, PermissionId)
SELECT r.RoleId, p.PermissionId
FROM Role r JOIN Permission p ON 1 = 1
WHERE r.Name = 'SALES'
  AND p.Code IN ('ASSIGNMENT_VIEW','SCHOOL_VIEW','SCHOOL_MANAGE',
                 'SERVICE_CONTRACT_VIEW','SERVICE_CONTRACT_MANAGE','REPORT_REVENUE_VIEW')
  AND NOT EXISTS (SELECT 1 FROM RolePermission rp
                  WHERE rp.RoleId = r.RoleId AND rp.PermissionId = p.PermissionId);
GO

/* --- TEACHER (giáo viên — kèm lọc ownership ở tầng service) --- */
INSERT INTO RolePermission (RoleId, PermissionId)
SELECT r.RoleId, p.PermissionId
FROM Role r JOIN Permission p ON 1 = 1
WHERE r.Name = 'TEACHER'
  AND p.Code IN ('SCHEDULE_VIEW','ATTENDANCE_VIEW','EVALUATION_VIEW','LESSON_VIEW')
  AND NOT EXISTS (SELECT 1 FROM RolePermission rp
                  WHERE rp.RoleId = r.RoleId AND rp.PermissionId = p.PermissionId);
GO

/* --- SCHOOL (trường khách hàng — kèm lọc ownership ở tầng service) --- */
INSERT INTO RolePermission (RoleId, PermissionId)
SELECT r.RoleId, p.PermissionId
FROM Role r JOIN Permission p ON 1 = 1
WHERE r.Name = 'SCHOOL'
  AND p.Code IN ('SCHEDULE_VIEW','EVALUATION_VIEW','EVALUATION_MANAGE','REPORT_OPERATION_VIEW')
  AND NOT EXISTS (SELECT 1 FROM RolePermission rp
                  WHERE rp.RoleId = r.RoleId AND rp.PermissionId = p.PermissionId);
GO

/* ========== 4) Tài khoản DEMO cho từng phòng ban (để test phân quyền) ==========
   Dùng CHUNG hash của 'employee' -> mật khẩu đều là Tsdms@123 (bcrypt khớp theo
   nội dung, không phụ thuộc salt). Mỗi tài khoản 1 role phòng ban tương ứng. */
INSERT INTO AppUser (Username, PasswordHash, Email, FullName, Phone)
SELECT v.Username,
       '$2b$10$O419OLGJ23IaXz9oT9DYL.Xhk/uMJDpbHr8SKQgjgEysVakGT1NkO',
       v.Email, v.FullName, v.Phone
FROM (VALUES
    ('ketoan',    'ketoan@tsdms.local',    N'Nhân viên Kế toán',   '0900000011'),
    ('nhansu',    'nhansu@tsdms.local',    N'Nhân viên Nhân sự',   '0900000012'),
    ('daotao',    'daotao@tsdms.local',    N'Nhân viên Đào tạo',   '0900000013'),
    ('tuyensinh', 'tuyensinh@tsdms.local', N'Nhân viên Tuyển sinh','0900000014')
) AS v(Username, Email, FullName, Phone)
WHERE NOT EXISTS (SELECT 1 FROM AppUser a WHERE a.Username = v.Username);
GO

/* Gán role phòng ban cho 4 tài khoản demo (theo Username ⇄ Role.Name). */
INSERT INTO UserRole (AppUserId, RoleId)
SELECT u.AppUserId, r.RoleId
FROM AppUser u
JOIN Role r ON (u.Username = 'ketoan'    AND r.Name = 'ACCOUNTANT')
            OR (u.Username = 'nhansu'    AND r.Name = 'HR')
            OR (u.Username = 'daotao'    AND r.Name = 'ACADEMIC')
            OR (u.Username = 'tuyensinh' AND r.Name = 'SALES')
WHERE NOT EXISTS (SELECT 1 FROM UserRole ur
                  WHERE ur.AppUserId = u.AppUserId AND ur.RoleId = r.RoleId);
GO

/* Tạo bản ghi Employee cho 4 tài khoản demo (gắn vào chi nhánh trung tâm). */
INSERT INTO Employee (AppUserId, BranchId, Position)
SELECT u.AppUserId, b.BranchId,
       CASE u.Username
            WHEN 'ketoan'    THEN N'Kế toán'
            WHEN 'nhansu'    THEN N'Chuyên viên nhân sự'
            WHEN 'daotao'    THEN N'Chuyên viên đào tạo'
            WHEN 'tuyensinh' THEN N'Chuyên viên tuyển sinh'
       END
FROM AppUser u CROSS JOIN Branch b
WHERE u.Username IN ('ketoan','nhansu','daotao','tuyensinh')
  AND b.Name = N'Chi nhánh trung tâm'
  AND NOT EXISTS (SELECT 1 FROM Employee e WHERE e.AppUserId = u.AppUserId);
GO
