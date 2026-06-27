/* =====================================================================
   TSDMS - HỆ THỐNG QUẢN LÝ & ĐIỀU PHỐI GIÁO VIÊN  (PHẦN LÕI)
   ---------------------------------------------------------------------
   MÔ HÌNH: Trung tâm giáo dục ĐIỀU PHỐI giáo viên đi dạy cho các trường
            khách hàng (chuyên STEM & Công dân số).
   4 ACTOR: ADMIN · EMPLOYEE (nhân viên trung tâm) · SCHOOL (trường) · TEACHER
   DBMS   : Microsoft SQL Server 2019+
   GHI CHÚ: Phần AI nằm ở file riêng "TSDMS_Schema_AI.sql" — chạy SAU file này.
   ---------------------------------------------------------------------
   BẢN ĐỒ QUAN HỆ (đọc nhanh):

     Branch (CHI NHÁNH) là gốc tổ chức.
        ├── Employee  (nhân viên trung tâm)  — mỗi NV thuộc 1 chi nhánh
        ├── Teacher   (giáo viên)            — mỗi GV thuộc 1 chi nhánh
        └── School    (trường khách hàng)    — mỗi trường do 1 chi nhánh quản lý

     AppUser (TÀI KHOẢN đăng nhập) là điểm xác thực chung.
        Employee / Teacher / School  mỗi loại nối 1-1 tới một AppUser.
        UserRole gán vai trò (ADMIN/EMPLOYEE/SCHOOL/TEACHER) cho AppUser.

     Teacher  ── TeacherSubject ──  Subject     (1 GV dạy được nhiều môn)
     Teacher  ── Certificate / Contract          (bằng cấp, hợp đồng của GV)

     School   ── Room / SchoolClass / Student     (phòng, lớp, học sinh của trường)
     SchoolClass ── ClassEnrollment ── Student    (1 lớp có nhiều học sinh)

     LUỒNG ĐIỀU PHỐI (quan trọng nhất):
        Employee (TRUNG TÂM) tạo  Assignment  (gán GV ↔ trường ↔ môn ↔ lớp ↔ giai đoạn)
            → từ Assignment sinh ra nhiều  Schedule  (từng BUỔI dạy cụ thể)
                → mỗi buổi có thể có  Attendance  (chấm công)
        Schedule đổi trạng thái → tự ghi  ScheduleStatusLog  (qua trigger)
        (Trung tâm TOÀN QUYỀN phân công; TRƯỜNG chỉ XEM thống kê/báo cáo.)

     VẬN HÀNH KHÁC: Payroll (lương), TeacherEvaluation (đánh giá),
                    Feedback (phản hồi), Notification (thông báo),
                    AuditLog (nhật ký toàn hệ thống).

     BÀI GIẢNG    : Lesson (nhân viên trung tâm soạn/quản lý) ── LessonFile
                    (file đính kèm). Gắn Subject/Branch/Teacher; trường & GV chỉ XEM.
   ---------------------------------------------------------------------
   QUY ƯỚC KIỂU DỮ LIỆU:
     - INT   : khóa của bảng master/danh mục (User, Teacher, School... ≤ 2,1 tỷ là thừa đủ)
     - BIGINT: khóa của bảng LOG/SỰ KIỆN tích lũy nhanh (Schedule, Attendance,
               Notification, AuditLog, RefreshToken) — phòng vượt 2,1 tỷ dòng
     - DATETIME2(3) lưu giờ UTC (DEFAULT SYSUTCDATETIME()); DATE/TIME(0) cho ngày/giờ
     - DECIMAL(18,2) cho tiền; NVARCHAR cho tiếng Việt; VARCHAR cho mã/email/username
     - Soft delete : IsDeleted + DeletedAt + DeletedBy (xóa MỀM, giữ lại dữ liệu)
     - Audit       : CreatedAt/CreatedBy/UpdatedAt/UpdatedBy (ai tạo/sửa, lúc nào)
     - Trạng thái  : VARCHAR ngắn + ràng buộc CHECK (khớp 1-1 với Enum trong Java)
   ===================================================================== */

/* Bấm Execute là chạy trọn gói: tạo DB (nếu chưa có) -> chuyển sang dùng DB
   -> tạo bảng theo đúng thứ tự phụ thuộc -> seed. Các 'GO' tách BATCH cho SQL Server. */
IF DB_ID('TSDMS') IS NULL
    CREATE DATABASE TSDMS COLLATE Vietnamese_CI_AS;
GO
USE TSDMS;
GO


/* #####################################################################
   NHÓM 1 — TỔ CHỨC & XÁC THỰC (RBAC = phân quyền theo vai trò)
   ##################################################################### */

/* ========== Bảng 1: Branch — CHI NHÁNH của trung tâm ==========
   Ý nghĩa : Trung tâm có nhiều chi nhánh. Mọi dữ liệu (nhân viên, giáo viên,
             trường) đều gắn với một chi nhánh để giới hạn quyền truy cập.
   QUAN HỆ : Là bảng GỐC, được Employee/Teacher/School tham chiếu tới.        */
CREATE TABLE Branch (
    Id            INT IDENTITY PRIMARY KEY,          -- mã chi nhánh (tự tăng)
    Name          NVARCHAR(150) NOT NULL,            -- tên chi nhánh
    Address       NVARCHAR(255) NULL,                -- địa chỉ
    Phone         VARCHAR(20)   NULL,                -- số điện thoại
    Status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE=hoạt động, INACTIVE=ngừng
                  CONSTRAINT CK_Branch_Status CHECK (Status IN ('ACTIVE','INACTIVE')),
    IsDeleted     BIT           NOT NULL DEFAULT 0,  -- 1 = đã xóa mềm
    DeletedAt     DATETIME2(3)  NULL,                -- thời điểm xóa mềm
    DeletedBy     INT           NULL,                -- ai xóa
    CreatedAt     DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),  -- thời điểm tạo
    CreatedBy     INT           NULL,                -- ai tạo
    UpdatedAt     DATETIME2(3)  NULL,                -- thời điểm sửa gần nhất
    UpdatedBy     INT           NULL                 -- ai sửa
);

/* ========== Bảng 2: AppUser — TÀI KHOẢN đăng nhập ==========
   Ý nghĩa : Mọi người dùng (admin/nhân viên/trường/giáo viên) đều có 1 tài khoản ở đây.
   QUAN HỆ : Là "trung tâm xác thực". Employee/Teacher/School nối 1-1 tới bảng này;
             UserRole gán vai trò; RefreshToken lưu phiên đăng nhập.               */
CREATE TABLE AppUser (
    Id            INT IDENTITY PRIMARY KEY,
    Username      VARCHAR(50)   NOT NULL,            -- tên đăng nhập
    PasswordHash  VARCHAR(255)  NOT NULL,            -- mật khẩu ĐÃ mã hóa BCrypt (KHÔNG lưu thô)
    Email         VARCHAR(100)  NOT NULL,            -- email (đăng nhập + khôi phục mật khẩu)
    -- Họ tên & SĐT đã chuyển về bảng tác nhân (Teacher/Employee/School) — AppUser chỉ giữ định danh.
    Status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / INACTIVE / LOCKED(bị khóa)
                  CONSTRAINT CK_AppUser_Status CHECK (Status IN ('ACTIVE','INACTIVE','LOCKED')),
    LastLoginAt   DATETIME2(3)  NULL,                -- lần đăng nhập gần nhất
    IsDeleted     BIT           NOT NULL DEFAULT 0,
    DeletedAt     DATETIME2(3)  NULL,
    DeletedBy     INT           NULL,
    CreatedAt     DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy     INT           NULL,
    UpdatedAt     DATETIME2(3)  NULL,
    UpdatedBy     INT           NULL
);
-- Username/Email là DUY NHẤT, nhưng chỉ tính trên các bản ghi CHƯA xóa mềm
-- (cho phép dùng lại username/email của tài khoản đã bị xóa).
CREATE UNIQUE INDEX UX_AppUser_Username ON AppUser(Username) WHERE IsDeleted = 0;
CREATE UNIQUE INDEX UX_AppUser_Email    ON AppUser(Email)    WHERE IsDeleted = 0;

/* ========== Bảng 3: Role — VAI TRÒ ==========
   Ý nghĩa : Danh mục 4 vai trò cố định của hệ thống.
   QUAN HỆ : Nối với AppUser qua UserRole; nối với Permission qua RolePermission. */
CREATE TABLE Role (
    Id           INT IDENTITY PRIMARY KEY,
    Name         VARCHAR(30)   NOT NULL UNIQUE,      -- ADMIN | EMPLOYEE | SCHOOL | TEACHER
    Description  NVARCHAR(255) NULL                  -- mô tả vai trò
);

/* ========== Bảng 4: Permission — QUYỀN CHI TIẾT ==========
   Ý nghĩa : Liệt kê từng quyền nhỏ (vd: duyệt lịch, tạo giáo viên) phục vụ
             chức năng "Phân quyền" của Admin.
   QUAN HỆ : Gán cho vai trò qua RolePermission.                              */
CREATE TABLE Permission (
    Id           INT IDENTITY PRIMARY KEY,
    Code         VARCHAR(100)  NOT NULL UNIQUE,      -- mã quyền, vd: SCHEDULE_APPROVE
    Description  NVARCHAR(255) NULL
);

/* ========== Bảng 5: RolePermission — bảng nối VAI TRÒ ⇄ QUYỀN ==========
   Ý nghĩa : Một vai trò có nhiều quyền; một quyền thuộc nhiều vai trò (nhiều-nhiều).
   QUAN HỆ : RoleId → Role,  PermissionId → Permission.                       */
CREATE TABLE RolePermission (
    RoleId       INT NOT NULL,                       -- → Role.RoleId
    PermissionId INT NOT NULL,                       -- → Permission.PermissionId
    PRIMARY KEY (RoleId, PermissionId),              -- khóa kép: tránh trùng cặp
    FOREIGN KEY (RoleId)       REFERENCES Role(Id),
    FOREIGN KEY (PermissionId) REFERENCES Permission(Id)
);

/* ========== Bảng 6: UserRole — bảng nối TÀI KHOẢN ⇄ VAI TRÒ ==========
   Ý nghĩa : Một tài khoản có thể mang nhiều vai trò (nhiều-nhiều).
   QUAN HỆ : AppUserId → AppUser,  RoleId → Role.                             */
CREATE TABLE UserRole (
    AppUserId    INT NOT NULL,                        -- → AppUser.AppUserId
    RoleId       INT NOT NULL,                        -- → Role.RoleId
    AssignedAt   DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),  -- thời điểm gán vai trò
    PRIMARY KEY (AppUserId, RoleId),
    FOREIGN KEY (AppUserId) REFERENCES AppUser(Id),
    FOREIGN KEY (RoleId)    REFERENCES Role(Id)
);

/* ========== Bảng 7: RefreshToken — TOKEN làm mới phiên đăng nhập (JWT) ==========
   Ý nghĩa : Lưu refresh token để cấp lại access token, và cho phép THU HỒI
             (đăng xuất 1 thiết bị / tất cả thiết bị).
   QUAN HỆ : AppUserId → AppUser. Khóa BIGINT vì sinh ra rất nhiều theo lượt đăng nhập. */
CREATE TABLE RefreshToken (
    Id             BIGINT IDENTITY PRIMARY KEY,
    AppUserId      INT          NOT NULL,             -- → AppUser.AppUserId
    TokenHash      VARCHAR(255) NOT NULL,             -- lưu HASH của token, không lưu token thô
    ExpiresAt      DATETIME2(3) NOT NULL,             -- hạn dùng
    RevokedAt      DATETIME2(3) NULL,                 -- thời điểm bị thu hồi (NULL = còn hiệu lực)
    CreatedAt      DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (AppUserId) REFERENCES AppUser(Id)
);
CREATE INDEX IX_RefreshToken_AppUser ON RefreshToken(AppUserId);  -- tra token theo user
CREATE INDEX IX_RefreshToken_Hash    ON RefreshToken(TokenHash);  -- tra theo token

/* ========== Bảng 7b: PasswordResetToken — TOKEN đặt lại mật khẩu (QUÊN MẬT KHẨU) ==========
   Ý nghĩa : Khi user bấm "quên mật khẩu", hệ thống sinh 1 token, gửi link qua email.
             Ở DB chỉ lưu HASH của token (không lưu token thô), có hạn dùng NGẮN và cột
             UsedAt để token chỉ dùng được MỘT lần.
   QUAN HỆ : AppUserId → AppUser.                                                          */
CREATE TABLE PasswordResetToken (
    Id                   BIGINT IDENTITY PRIMARY KEY,
    AppUserId   INT          NOT NULL,                -- → AppUser.AppUserId
    TokenHash   VARCHAR(255) NOT NULL,               -- HASH của token reset (SHA-256)
    ExpiresAt   DATETIME2(3) NOT NULL,               -- hạn dùng (vd 30 phút)
    UsedAt      DATETIME2(3) NULL,                   -- đã dùng lúc nào (NULL = chưa dùng)
    CreatedAt   DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (AppUserId) REFERENCES AppUser(Id)
);
CREATE INDEX IX_PwdReset_Hash    ON PasswordResetToken(TokenHash);  -- tra theo token
CREATE INDEX IX_PwdReset_AppUser ON PasswordResetToken(AppUserId);  -- tra theo user


/* #####################################################################
   NHÓM 2 — NHÂN SỰ TRUNG TÂM & DANH MỤC MÔN
   ##################################################################### */

/* ========== Bảng 8: Employee — NHÂN VIÊN TRUNG TÂM ==========
   Ý nghĩa : Hồ sơ của tài khoản có vai trò EMPLOYEE. Nhân viên chỉ quản lý
             dữ liệu trong chi nhánh của mình.
   QUAN HỆ : AppUserId → AppUser (1-1),  BranchId → Branch.                   */
CREATE TABLE Employee (
    Id           INT IDENTITY PRIMARY KEY,
    AppUserId    INT           NOT NULL UNIQUE,       -- → AppUser (mỗi user chỉ 1 hồ sơ NV)
    BranchId     INT           NOT NULL,             -- → Branch (nhân viên thuộc chi nhánh nào)
    FirstName    NVARCHAR(50)  NOT NULL,             -- Tên gọi (given name)
    LastName     NVARCHAR(100) NOT NULL,             -- Họ và tên đệm (family name)
    Phone        VARCHAR(20)   NULL,                 -- SĐT liên hệ
    Position     NVARCHAR(100) NULL,                 -- chức vụ
    Status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                 CONSTRAINT CK_Employee_Status CHECK (Status IN ('ACTIVE','INACTIVE')),
    IsDeleted    BIT           NOT NULL DEFAULT 0,
    DeletedAt    DATETIME2(3)  NULL,
    DeletedBy    INT           NULL,
    CreatedAt    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy    INT           NULL,
    UpdatedAt    DATETIME2(3)  NULL,
    UpdatedBy    INT           NULL,
    FOREIGN KEY (AppUserId) REFERENCES AppUser(Id),
    FOREIGN KEY (BranchId)  REFERENCES Branch(Id)
);
CREATE INDEX IX_Employee_Branch    ON Employee(BranchId);             -- lọc nhân viên theo chi nhánh
CREATE INDEX IX_Employee_FirstName ON Employee(FirstName, LastName);  -- sắp xếp theo TÊN

/* ========== Bảng 8b: SubjectCategory — NHÓM MÔN (lookup, thêm ở V8) ==========
   Ý nghĩa : Danh mục 4 nhóm môn chính thức (Tin học, Tiếng Anh, STEM - AI,
             Kĩ năng sống) — thay cột text Subject.Category, dùng chung với Lesson.
   QUAN HỆ : Được Subject tham chiếu qua FK CategoryId.                            */
CREATE TABLE SubjectCategory (
    Id          INT IDENTITY PRIMARY KEY,
    Code        VARCHAR(50)   NOT NULL UNIQUE,      -- mã nhóm (khớp Enum Java): TIN_HOC | TIENG_ANH | STEM_AI | KY_NANG_SONG
    Name        NVARCHAR(100) NOT NULL,            -- tên hiển thị: Tin học | Tiếng Anh | STEM - AI | Kĩ năng sống
    Description NVARCHAR(500) NULL,
    Status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / DISABLED(ngừng dùng)
                CONSTRAINT CK_SubjectCategory_Status CHECK (Status IN ('ACTIVE','DISABLED')),
    IsDeleted   BIT           NOT NULL DEFAULT 0,
    DeletedAt   DATETIME2(3)  NULL,
    DeletedBy   INT           NULL,
    CreatedAt   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy   INT           NULL,
    UpdatedAt   DATETIME2(3)  NULL,
    UpdatedBy   INT           NULL
);

/* ========== Bảng 9: Subject — MÔN HỌC ==========
   Ý nghĩa : Môn học cụ thể (Lập trình Scratch, Robotics...).
   QUAN HỆ : CategoryId → SubjectCategory (nhóm môn); nối với Teacher qua
             TeacherSubject; được Assignment tham chiếu.                          */
CREATE TABLE Subject (
    Id           INT IDENTITY PRIMARY KEY,
    Code         VARCHAR(20)   NOT NULL UNIQUE,      -- mã môn, vd: STEM01, CDS01
    Name         NVARCHAR(150) NOT NULL,            -- tên môn
    CategoryId   INT           NULL,                -- → SubjectCategory (nhóm môn)
    Description  NVARCHAR(500) NULL,
    Status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / DISABLED(ngừng dạy)
                 CONSTRAINT CK_Subject_Status CHECK (Status IN ('ACTIVE','DISABLED')),
    IsDeleted    BIT           NOT NULL DEFAULT 0,
    DeletedAt    DATETIME2(3)  NULL,
    DeletedBy    INT           NULL,
    CreatedAt    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy    INT           NULL,
    UpdatedAt    DATETIME2(3)  NULL,
    UpdatedBy    INT           NULL,
    CONSTRAINT FK_Subject_Category FOREIGN KEY (CategoryId) REFERENCES SubjectCategory(Id)
);
CREATE INDEX IX_Subject_Name     ON Subject(Name);        -- tìm môn theo tên
CREATE INDEX IX_Subject_Category ON Subject(CategoryId);  -- lọc môn theo nhóm


/* #####################################################################
   NHÓM 3 — GIÁO VIÊN & HỒ SƠ
   ##################################################################### */

/* ========== Bảng 10: Teacher — GIÁO VIÊN ==========
   Ý nghĩa : Hồ sơ của tài khoản có vai trò TEACHER.
   QUAN HỆ : AppUserId → AppUser (1-1),  BranchId → Branch.
             Là tâm điểm: được TeacherSubject/Certificate/Contract/Assignment/
             Schedule/Attendance/Payroll/TeacherEvaluation tham chiếu tới.    */
CREATE TABLE Teacher (
    Id              INT IDENTITY PRIMARY KEY,
    AppUserId       INT           NOT NULL UNIQUE,    -- → AppUser (1-1)
    BranchId        INT           NOT NULL,          -- → Branch (chi nhánh quản lý GV)
    -- Tách họ tên (quy ước Tây): FirstName = tên gọi, LastName = họ + tên đệm.
    FirstName       NVARCHAR(50)  NOT NULL,          -- Tên gọi (vd "A" trong "Trần Nguyễn Văn A")
    LastName        NVARCHAR(100) NOT NULL,          -- Họ và tên đệm (vd "Trần Nguyễn Văn")
    DateOfBirth     DATE          NULL,              -- ngày sinh
    Gender          BIT           NULL,              -- 1 = Nam, 0 = Nữ
    IdCardNo        VARCHAR(20)   NULL,              -- số CCCD
    Phone           VARCHAR(20)   NULL,
    Address         NVARCHAR(255) NULL,
    HireDate        DATE          NULL,              -- ngày vào làm
    EmploymentType  VARCHAR(20)   NULL               -- FULL_TIME / PART_TIME / CONTRACT
                    CONSTRAINT CK_Teacher_EmpType CHECK (EmploymentType IN ('FULL_TIME','PART_TIME','CONTRACT')),
    Status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / RETIRED(nghỉ) / SUSPENDED(tạm dừng)
                    CONSTRAINT CK_Teacher_Status CHECK (Status IN ('ACTIVE','RETIRED','SUSPENDED')),
    IsDeleted       BIT           NOT NULL DEFAULT 0,
    DeletedAt       DATETIME2(3)  NULL,
    DeletedBy       INT           NULL,
    CreatedAt       DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy       INT           NULL,
    UpdatedAt       DATETIME2(3)  NULL,
    UpdatedBy       INT           NULL,
    FOREIGN KEY (AppUserId) REFERENCES AppUser(Id),
    FOREIGN KEY (BranchId)  REFERENCES Branch(Id)
);
-- CCCD là duy nhất (chỉ tính bản ghi chưa xóa mềm, và bỏ qua ô để trống)
CREATE UNIQUE INDEX UX_Teacher_IdCard ON Teacher(IdCardNo) WHERE IdCardNo IS NOT NULL AND IsDeleted = 0;
CREATE INDEX IX_Teacher_Branch    ON Teacher(BranchId);             -- lọc GV theo chi nhánh
CREATE INDEX IX_Teacher_FirstName ON Teacher(FirstName, LastName);  -- sắp xếp theo TÊN

/* ========== Bảng 11: TeacherSubject — bảng nối GIÁO VIÊN ⇄ MÔN dạy được ==========
   Ý nghĩa : Một GV dạy được nhiều môn; một môn nhiều GV dạy (nhiều-nhiều).
             Cột ProficiencyLevel dùng cho AI gợi ý ghép GV phù hợp về sau.
   QUAN HỆ : TeacherId → Teacher,  SubjectId → Subject.                       */
CREATE TABLE TeacherSubject (
    TeacherId        INT NOT NULL,                    -- → Teacher.TeacherId
    SubjectId        INT NOT NULL,                    -- → Subject.SubjectId
    ProficiencyLevel TINYINT NULL                     -- mức thành thạo 1..5 (cho AI matching)
                     CONSTRAINT CK_TS_Prof CHECK (ProficiencyLevel BETWEEN 1 AND 5),
    PRIMARY KEY (TeacherId, SubjectId),
    FOREIGN KEY (TeacherId) REFERENCES Teacher(Id),
    FOREIGN KEY (SubjectId) REFERENCES Subject(Id)
);
CREATE INDEX IX_TeacherSubject_Subject ON TeacherSubject(SubjectId);  -- tìm GV theo môn

/* ========== Bảng 12: Certificate — BẰNG CẤP & CHỨNG CHỈ của giáo viên ==========
   QUAN HỆ : TeacherId → Teacher (1 GV có nhiều bằng cấp/chứng chỉ).          */
CREATE TABLE Certificate (
    Id            INT IDENTITY PRIMARY KEY,
    TeacherId     INT           NOT NULL,             -- → Teacher.TeacherId
    Name          NVARCHAR(200) NOT NULL,            -- tên bằng/chứng chỉ
    Issuer        NVARCHAR(200) NULL,                -- nơi cấp
    IssueDate     DATE          NULL,                -- ngày cấp
    ExpiryDate    DATE          NULL,                -- ngày hết hạn (nếu có)
    FileUrl       VARCHAR(500)  NULL,                -- link file scan đã upload
    IsDeleted     BIT           NOT NULL DEFAULT 0,
    DeletedAt     DATETIME2(3)  NULL,
    DeletedBy     INT           NULL,
    CreatedAt     DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy     INT           NULL,
    UpdatedAt     DATETIME2(3)  NULL,
    UpdatedBy     INT           NULL,
    FOREIGN KEY (TeacherId) REFERENCES Teacher(Id)
);
CREATE INDEX IX_Certificate_Teacher ON Certificate(TeacherId);

/* ========== Bảng 13: Contract — HỢP ĐỒNG của giáo viên ==========
   QUAN HỆ : TeacherId → Teacher (1 GV có thể có nhiều hợp đồng theo thời gian). */
CREATE TABLE Contract (
    Id           INT IDENTITY PRIMARY KEY,
    TeacherId    INT           NOT NULL,             -- → Teacher.TeacherId
    ContractNo   VARCHAR(50)   NOT NULL UNIQUE,      -- số hợp đồng (duy nhất)
    StartDate    DATE          NOT NULL,             -- ngày bắt đầu
    EndDate      DATE          NULL,                 -- ngày kết thúc (NULL = vô thời hạn)
    BaseSalary   DECIMAL(18,2) NULL,                 -- lương cơ bản theo HĐ
    Allowance    DECIMAL(18,2) NULL,                 -- phụ cấp theo HĐ
    Status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / EXPIRED(hết hạn) / TERMINATED(chấm dứt)
                 CONSTRAINT CK_Contract_Status CHECK (Status IN ('ACTIVE','EXPIRED','TERMINATED')),
    FileUrl      VARCHAR(500)  NULL,
    IsDeleted    BIT           NOT NULL DEFAULT 0,
    DeletedAt    DATETIME2(3)  NULL,
    DeletedBy    INT           NULL,
    CreatedAt    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy    INT           NULL,
    UpdatedAt    DATETIME2(3)  NULL,
    UpdatedBy    INT           NULL,
    FOREIGN KEY (TeacherId) REFERENCES Teacher(Id),
    CONSTRAINT CK_Contract_Dates CHECK (EndDate IS NULL OR EndDate >= StartDate)  -- ngày KT ≥ ngày BĐ
);
-- 1-1: mỗi GV tối đa 1 hợp đồng CHƯA xóa mềm (theo yêu cầu GVHD).
CREATE UNIQUE INDEX UX_Contract_OneActivePerTeacher ON Contract(TeacherId) WHERE IsDeleted = 0;


/* #####################################################################
   NHÓM 4 — TRƯỜNG KHÁCH HÀNG, PHÒNG, LỚP, HỌC SINH
   ##################################################################### */

/* ========== Bảng 14: School — TRƯỜNG (khách hàng) ==========
   Ý nghĩa : Trường thuê giáo viên của trung tâm. Có thể có 1 tài khoản đăng nhập riêng.
   QUAN HỆ : BranchId → Branch (chi nhánh phụ trách),  AppUserId → AppUser (1-1, tùy chọn).
             Sở hữu Room/SchoolClass/Student.                                */
CREATE TABLE School (
    Id                INT IDENTITY PRIMARY KEY,
    BranchId          INT           NOT NULL,         -- → Branch (chi nhánh phụ trách trường)
    Name              NVARCHAR(200) NOT NULL,
    Address           NVARCHAR(255) NULL,
    Phone             VARCHAR(20)   NULL,
    Email             VARCHAR(100)  NULL,
    ContactPerson     NVARCHAR(150) NULL,            -- người liên hệ phía trường
    AppUserId         INT           NULL UNIQUE,     -- → AppUser (tài khoản trường, nếu có)
    ContractStartDate DATE          NULL,            -- ngày bắt đầu hợp đồng dịch vụ
    ContractEndDate   DATE          NULL,            -- ngày hết hạn (dùng để cảnh báo/khóa)
    Status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / INACTIVE / EXPIRED
                      CONSTRAINT CK_School_Status CHECK (Status IN ('ACTIVE','INACTIVE','EXPIRED')),
    IsDeleted         BIT           NOT NULL DEFAULT 0,
    DeletedAt         DATETIME2(3)  NULL,
    DeletedBy         INT           NULL,
    CreatedAt         DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy         INT           NULL,
    UpdatedAt         DATETIME2(3)  NULL,
    UpdatedBy         INT           NULL,
    FOREIGN KEY (BranchId)  REFERENCES Branch(Id),
    FOREIGN KEY (AppUserId) REFERENCES AppUser(Id)
);
CREATE INDEX IX_School_Branch ON School(BranchId);
CREATE INDEX IX_School_Name   ON School(Name);

/* ========== Bảng 15: Room — PHÒNG HỌC (thuộc trường) ==========
   Ý nghĩa : Phòng để xếp lịch dạy. Chỉ phòng AVAILABLE mới được đặt.
   QUAN HỆ : SchoolId → School. Được Schedule tham chiếu.                    */
CREATE TABLE Room (
    Id         INT IDENTITY PRIMARY KEY,
    SchoolId   INT           NOT NULL,               -- → School.SchoolId
    Name       NVARCHAR(50)  NOT NULL,              -- tên phòng, vd: A101
    Building   NVARCHAR(50)  NULL,                  -- tòa nhà
    Floor      VARCHAR(10)   NULL,                  -- tầng
    Type       VARCHAR(20)   NULL                    -- LAB / CLASSROOM / HALL
               CONSTRAINT CK_Room_Type CHECK (Type IN ('LAB','CLASSROOM','HALL')),
    Capacity   INT           NULL CONSTRAINT CK_Room_Capacity CHECK (Capacity IS NULL OR Capacity > 0),  -- sức chứa
    Status     VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE'  -- AVAILABLE / MAINTENANCE / DISABLED
               CONSTRAINT CK_Room_Status CHECK (Status IN ('AVAILABLE','MAINTENANCE','DISABLED')),
    IsDeleted  BIT           NOT NULL DEFAULT 0,
    DeletedAt  DATETIME2(3)  NULL,
    DeletedBy  INT           NULL,
    CreatedAt  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy  INT           NULL,
    UpdatedAt  DATETIME2(3)  NULL,
    UpdatedBy  INT           NULL,
    FOREIGN KEY (SchoolId) REFERENCES School(Id)
);
-- Trong cùng 1 trường, tên phòng không trùng (tính trên bản ghi chưa xóa mềm)
CREATE UNIQUE INDEX UX_Room_School_Name ON Room(SchoolId, Name) WHERE IsDeleted = 0;

/* ========== Bảng 16: SchoolClass — LỚP HỌC (thuộc trường) ==========
   QUAN HỆ : SchoolId → School. Nối với Student qua ClassEnrollment.         */
CREATE TABLE SchoolClass (
    Id          INT IDENTITY PRIMARY KEY,
    SchoolId    INT           NOT NULL,              -- → School.SchoolId
    Name        NVARCHAR(100) NOT NULL,            -- tên lớp
    GradeLevel  NVARCHAR(50)  NULL,                -- khối
    SchoolYear  VARCHAR(20)   NOT NULL,            -- năm học, vd: 2025-2026
    Status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                CONSTRAINT CK_Class_Status CHECK (Status IN ('ACTIVE','INACTIVE')),
    IsDeleted   BIT           NOT NULL DEFAULT 0,
    DeletedAt   DATETIME2(3)  NULL,
    DeletedBy   INT           NULL,
    CreatedAt   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy   INT           NULL,
    UpdatedAt   DATETIME2(3)  NULL,
    UpdatedBy   INT           NULL,
    FOREIGN KEY (SchoolId) REFERENCES School(Id)
);
CREATE UNIQUE INDEX UX_Class_School_Name_Year ON SchoolClass(SchoolId, Name, SchoolYear) WHERE IsDeleted = 0;

/* ========== Bảng 17: Student — HỌC SINH ==========
   Ý nghĩa : Chỉ lưu danh sách học sinh (KHÔNG điểm, KHÔNG học phí).
   QUAN HỆ : SchoolId → School. Nối với SchoolClass qua ClassEnrollment.     */
CREATE TABLE Student (
    Id          INT IDENTITY PRIMARY KEY,
    SchoolId    INT           NOT NULL,              -- → School.SchoolId
    FirstName   NVARCHAR(50)  NOT NULL,              -- Tên gọi (given name)
    LastName    NVARCHAR(100) NOT NULL,              -- Họ và tên đệm (family name)
    DateOfBirth DATE          NULL,
    Gender      BIT           NULL,                 -- 1 = Nam, 0 = Nữ
    Status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                CONSTRAINT CK_Student_Status CHECK (Status IN ('ACTIVE','INACTIVE')),
    IsDeleted   BIT           NOT NULL DEFAULT 0,
    DeletedAt   DATETIME2(3)  NULL,
    DeletedBy   INT           NULL,
    CreatedAt   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy   INT           NULL,
    UpdatedAt   DATETIME2(3)  NULL,
    UpdatedBy   INT           NULL,
    FOREIGN KEY (SchoolId) REFERENCES School(Id)
);
CREATE INDEX IX_Student_School    ON Student(SchoolId);
CREATE INDEX IX_Student_FirstName ON Student(FirstName, LastName);  -- sắp xếp theo TÊN

/* ========== Bảng 18: ClassEnrollment — bảng nối LỚP ⇄ HỌC SINH ==========
   Ý nghĩa : Một lớp có nhiều học sinh; một học sinh có thể vào nhiều lớp (nhiều-nhiều).
   QUAN HỆ : ClassId → SchoolClass,  StudentId → Student.                    */
CREATE TABLE ClassEnrollment (
    ClassId    INT NOT NULL,                          -- → SchoolClass.ClassId
    StudentId  INT NOT NULL,                          -- → Student.StudentId
    EnrolledAt DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),  -- thời điểm ghi danh
    PRIMARY KEY (ClassId, StudentId),
    FOREIGN KEY (ClassId)   REFERENCES SchoolClass(Id),
    FOREIGN KEY (StudentId) REFERENCES Student(Id)
);
CREATE INDEX IX_Enrollment_Student ON ClassEnrollment(StudentId);


/* #####################################################################
   NHÓM 5 — ĐIỀU PHỐI (LÕI): PHÂN CÔNG → LỊCH DẠY
   ##################################################################### */

/* ========== Bảng 19: Assignment — PHÂN CÔNG ==========
   Ý nghĩa : Nhân viên TRUNG TÂM gán một GIÁO VIÊN cho một TRƯỜNG/MÔN/LỚP trong một giai đoạn.
             Trung tâm TOÀN QUYỀN quyết định (KHÔNG xuất phát từ yêu cầu của trường).
             LƯU Ý: một GV CÓ THỂ được phân công cho NHIỀU trường (mô hình điều phối).
   QUAN HỆ : TeacherId → Teacher,  SchoolId → School,  SubjectId → Subject,
             ClassId → SchoolClass,
             AssignedByEmployeeId → Employee.  Sinh ra nhiều Schedule.        */
CREATE TABLE Assignment (
    Id                   INT IDENTITY PRIMARY KEY,
    TeacherId            INT           NOT NULL,      -- → Teacher
    SchoolId             INT           NOT NULL,      -- → School
    SubjectId            INT           NOT NULL,      -- → Subject
    ClassId              INT           NULL,          -- → SchoolClass (nếu gắn lớp cụ thể)
    AssignedByEmployeeId INT           NULL,          -- → Employee (ai phân công)
    StartDate            DATE          NOT NULL,      -- giai đoạn bắt đầu
    EndDate              DATE          NULL,
    Status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / COMPLETED / CANCELLED
                         CONSTRAINT CK_Assignment_Status CHECK (Status IN ('ACTIVE','COMPLETED','CANCELLED')),
    IsDeleted            BIT           NOT NULL DEFAULT 0,
    DeletedAt            DATETIME2(3)  NULL,
    DeletedBy            INT           NULL,
    CreatedAt            DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy            INT           NULL,
    UpdatedAt            DATETIME2(3)  NULL,
    UpdatedBy            INT           NULL,
    FOREIGN KEY (TeacherId)            REFERENCES Teacher(Id),
    FOREIGN KEY (SchoolId)             REFERENCES School(Id),
    FOREIGN KEY (SubjectId)            REFERENCES Subject(Id),
    FOREIGN KEY (ClassId)              REFERENCES SchoolClass(Id),
    FOREIGN KEY (AssignedByEmployeeId) REFERENCES Employee(Id),
    CONSTRAINT CK_Assignment_Dates CHECK (EndDate IS NULL OR EndDate >= StartDate)
);
CREATE INDEX IX_Assignment_Teacher ON Assignment(TeacherId);
CREATE INDEX IX_Assignment_School  ON Assignment(SchoolId);

/* ========== Bảng 20: Schedule — LỊCH DẠY (bảng TRUNG TÂM) ==========
   Ý nghĩa : Từng BUỔI dạy cụ thể (ngày-giờ-phòng) sinh ra từ một Assignment,
             kèm quy trình duyệt PENDING → APPROVED/REJECTED/CANCELLED.
   QUAN HỆ : AssignmentId → Assignment,  TeacherId → Teacher (lưu kèm để dò
             TRÙNG LỊCH nhanh; phải = giáo viên của Assignment),
             RoomId → Room,  CreatedByUserId/ApprovedByUserId → AppUser.
   KHÓA BIGINT vì số buổi tích lũy rất lớn theo thời gian.                    */
CREATE TABLE Schedule (
    Id               BIGINT IDENTITY PRIMARY KEY,
    AssignmentId     INT           NOT NULL,          -- → Assignment
    TeacherId        INT           NOT NULL,          -- → Teacher (dò trùng lịch GV)
    RoomId           INT           NULL,              -- → Room (phòng dạy)
    StartTime        DATETIME2(0)  NOT NULL,          -- giờ bắt đầu
    EndTime          DATETIME2(0)  NOT NULL,          -- giờ kết thúc
    Status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'  -- PENDING / APPROVED / REJECTED / CANCELLED
                     CONSTRAINT CK_Schedule_Status CHECK (Status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    Source           VARCHAR(10)   NOT NULL DEFAULT 'MANUAL'   -- MANUAL=nhập tay, AI=do AI xếp
                     CONSTRAINT CK_Schedule_Source CHECK (Source IN ('MANUAL','AI')),
    CreatedByUserId  INT           NULL,              -- → AppUser (người tạo lịch)
    ApprovedByUserId INT           NULL,              -- → AppUser (người duyệt/từ chối)
    ApprovedAt       DATETIME2(3)  NULL,              -- thời điểm duyệt
    RejectionReason  NVARCHAR(500) NULL,              -- lý do từ chối
    IsDeleted        BIT           NOT NULL DEFAULT 0,
    DeletedAt        DATETIME2(3)  NULL,
    DeletedBy        INT           NULL,
    CreatedAt        DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt        DATETIME2(3)  NULL,
    UpdatedBy        INT           NULL,              -- dùng cho trigger ghi log (ai đổi trạng thái)
    FOREIGN KEY (AssignmentId)     REFERENCES Assignment(Id),
    FOREIGN KEY (TeacherId)        REFERENCES Teacher(Id),
    FOREIGN KEY (RoomId)           REFERENCES Room(Id),
    FOREIGN KEY (CreatedByUserId)  REFERENCES AppUser(Id),
    FOREIGN KEY (ApprovedByUserId) REFERENCES AppUser(Id),
    CONSTRAINT CK_Schedule_Time CHECK (StartTime < EndTime)  -- giờ bắt đầu phải trước giờ kết thúc
);
-- Index để kiểm tra TRÙNG LỊCH giáo viên / TRÙNG PHÒNG theo khoảng thời gian thật nhanh:
CREATE INDEX IX_Schedule_Teacher_Time ON Schedule(TeacherId, StartTime, EndTime) INCLUDE (Status) WHERE IsDeleted = 0;
CREATE INDEX IX_Schedule_Room_Time    ON Schedule(RoomId,    StartTime, EndTime) INCLUDE (Status) WHERE IsDeleted = 0 AND RoomId IS NOT NULL;
CREATE INDEX IX_Schedule_Assignment   ON Schedule(AssignmentId);
CREATE INDEX IX_Schedule_Status       ON Schedule(Status);
/* LƯU Ý: SQL Server không có ràng buộc "chống chồng lấn thời gian" sẵn như PostgreSQL,
   nên quy tắc không trùng lịch được kiểm tra ở tầng Service (Java) bằng truy vấn dùng
   các index ở trên. */

/* ========== Bảng 21: ScheduleStatusLog — NHẬT KÝ ĐỔI TRẠNG THÁI LỊCH ==========
   Ý nghĩa : Tự động ghi lại mỗi lần Schedule đổi trạng thái (qua trigger ở cuối file).
   QUAN HỆ : ScheduleId → Schedule.  Khóa BIGINT vì số dòng lớn.             */
CREATE TABLE ScheduleStatusLog (
    Id              BIGINT IDENTITY PRIMARY KEY,
    ScheduleId      BIGINT        NOT NULL,           -- → Schedule
    OldStatus       VARCHAR(20)   NULL,               -- trạng thái cũ
    NewStatus       VARCHAR(20)   NOT NULL,           -- trạng thái mới
    ChangedByUserId INT           NULL,               -- ai đổi
    ChangedAt       DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    Note            NVARCHAR(500) NULL,
    FOREIGN KEY (ScheduleId) REFERENCES Schedule(Id)
);
CREATE INDEX IX_StatusLog_Schedule ON ScheduleStatusLog(ScheduleId);


/* #####################################################################
   NHÓM 6 — VẬN HÀNH: CHẤM CÔNG, LƯƠNG, ĐÁNH GIÁ, PHẢN HỒI, THÔNG BÁO, AUDIT
   ##################################################################### */

/* ========== Bảng 22: Attendance — CHẤM CÔNG ==========
   Ý nghĩa : Ghi nhận giáo viên có dạy buổi đó không, giờ vào/ra.
   PHƯƠNG ÁN chống gian lận (gợi ý): GV tự check-in + TRƯỜNG xác nhận
             (CheckInMethod='SELF', ConfirmedByUserId = tài khoản trường).
   QUAN HỆ : TeacherId → Teacher,  ScheduleId → Schedule (buổi dạy),
             ConfirmedByUserId → AppUser (người xác nhận). Khóa BIGINT.       */
CREATE TABLE Attendance (
    Id                BIGINT IDENTITY PRIMARY KEY,
    TeacherId         INT           NOT NULL,         -- → Teacher
    ScheduleId        BIGINT        NULL,             -- → Schedule (gắn buổi dạy, nếu chấm theo buổi)
    WorkDate          DATE          NOT NULL,         -- ngày làm việc
    CheckIn           TIME(0)       NULL,             -- giờ vào
    CheckOut          TIME(0)       NULL,             -- giờ ra
    Status            VARCHAR(20)   NOT NULL DEFAULT 'PRESENT'  -- PRESENT/ABSENT/LATE/LEAVE(nghỉ phép)
                      CONSTRAINT CK_Attendance_Status CHECK (Status IN ('PRESENT','ABSENT','LATE','LEAVE')),
    CheckInMethod     VARCHAR(20)   NULL              -- AI ai/cái gì ghi nhận: SELF/EMPLOYEE/SCHOOL/DEVICE
                      CONSTRAINT CK_Attendance_Method CHECK (CheckInMethod IN ('SELF','EMPLOYEE','SCHOOL','DEVICE')),
    ConfirmedByUserId INT           NULL,             -- → AppUser (người XÁC NHẬN, vd: tài khoản trường)
    ConfirmedAt       DATETIME2(3)  NULL,             -- thời điểm xác nhận
    Note              NVARCHAR(255) NULL,
    CreatedAt         DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy         INT           NULL,
    FOREIGN KEY (TeacherId)         REFERENCES Teacher(Id),
    FOREIGN KEY (ScheduleId)        REFERENCES Schedule(Id),
    FOREIGN KEY (ConfirmedByUserId) REFERENCES AppUser(Id),
    CONSTRAINT CK_Attendance_Time CHECK (CheckIn IS NULL OR CheckOut IS NULL OR CheckIn < CheckOut)
);
CREATE INDEX IX_Attendance_Teacher_Date ON Attendance(TeacherId, WorkDate);

/* ========== Bảng 23: Payroll — BẢNG LƯƠNG (theo tháng) ==========
   Ý nghĩa : Lưu đủ thành phần lương để linh hoạt công thức. NetAmount tự tính.
   QUAN HỆ : TeacherId → Teacher. Mỗi GV mỗi tháng chỉ 1 dòng (ràng buộc UNIQUE). */
CREATE TABLE Payroll (
    Id          INT IDENTITY PRIMARY KEY,
    TeacherId   INT           NOT NULL,               -- → Teacher
    PeriodMonth TINYINT       NOT NULL CONSTRAINT CK_Payroll_Month CHECK (PeriodMonth BETWEEN 1 AND 12),  -- tháng
    PeriodYear  SMALLINT      NOT NULL,               -- năm
    BaseSalary  DECIMAL(18,2) NOT NULL DEFAULT 0,     -- lương cứng
    TaughtHours DECIMAL(9,2)  NULL,                   -- số giờ dạy thực tế (nếu trả theo giờ)
    RatePerHour DECIMAL(18,2) NULL,                   -- đơn giá/giờ
    Allowance   DECIMAL(18,2) NOT NULL DEFAULT 0,     -- phụ cấp
    Bonus       DECIMAL(18,2) NOT NULL DEFAULT 0,     -- thưởng
    Deduction   DECIMAL(18,2) NOT NULL DEFAULT 0,     -- khấu trừ
    -- Lương thực nhận = lương cứng + (giờ dạy × đơn giá) + phụ cấp + thưởng − khấu trừ
    NetAmount   AS (BaseSalary + ISNULL(TaughtHours * RatePerHour, 0) + Allowance + Bonus - Deduction) PERSISTED,
    Status      VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'  -- DRAFT(nháp)/FINALIZED(chốt)/PAID(đã trả)
                CONSTRAINT CK_Payroll_Status CHECK (Status IN ('DRAFT','FINALIZED','PAID')),
    CreatedAt   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy   INT           NULL,
    UpdatedAt   DATETIME2(3)  NULL,
    UpdatedBy   INT           NULL,
    FOREIGN KEY (TeacherId) REFERENCES Teacher(Id),
    CONSTRAINT UX_Payroll_Period UNIQUE (TeacherId, PeriodYear, PeriodMonth)
);

/* ========== Bảng 24: TeacherEvaluation — ĐÁNH GIÁ GIÁO VIÊN ==========
   Ý nghĩa : Trường hoặc nhân viên chấm điểm/nhận xét giáo viên.
   QUAN HỆ : TeacherId → Teacher,  EvaluatorUserId → AppUser (người đánh giá),
             SchoolId → School (nếu do trường đánh giá).                      */
CREATE TABLE TeacherEvaluation (
    Id              INT IDENTITY PRIMARY KEY,
    TeacherId       INT            NOT NULL,          -- → Teacher (được đánh giá)
    EvaluatorUserId INT            NOT NULL,          -- → AppUser (người đánh giá)
    SchoolId        INT            NULL,              -- → School (nếu trường đánh giá)
    Score           TINYINT        NULL CONSTRAINT CK_Eval_Score CHECK (Score BETWEEN 1 AND 5),  -- điểm 1..5
    Comment         NVARCHAR(1000) NULL,
    PeriodNote      NVARCHAR(50)   NULL,              -- kỳ đánh giá (ghi chú)
    IsDeleted       BIT            NOT NULL DEFAULT 0,
    DeletedAt       DATETIME2(3)   NULL,
    DeletedBy       INT            NULL,
    CreatedAt       DATETIME2(3)   NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy       INT            NULL,
    UpdatedAt       DATETIME2(3)   NULL,
    UpdatedBy       INT            NULL,
    FOREIGN KEY (TeacherId)       REFERENCES Teacher(Id),
    FOREIGN KEY (EvaluatorUserId) REFERENCES AppUser(Id),
    FOREIGN KEY (SchoolId)        REFERENCES School(Id)
);
CREATE INDEX IX_Eval_Teacher ON TeacherEvaluation(TeacherId);

/* ========== Bảng 25: Feedback — PHẢN HỒI ==========
   Ý nghĩa : Người dùng (thường là GV) gửi phản hồi; nhân viên xử lý & trả lời.
   QUAN HỆ : SenderUserId → AppUser (người gửi),  HandledByEmployeeId → Employee. */
CREATE TABLE Feedback (
    Id                  INT IDENTITY PRIMARY KEY,
    SenderUserId        INT            NOT NULL,      -- → AppUser (người gửi)
    Title               NVARCHAR(200)  NOT NULL,
    Content             NVARCHAR(2000) NOT NULL,
    Status              VARCHAR(20)    NOT NULL DEFAULT 'OPEN'  -- OPEN/IN_PROGRESS/RESOLVED/CLOSED
                        CONSTRAINT CK_Feedback_Status CHECK (Status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    HandledByEmployeeId INT            NULL,          -- → Employee (người xử lý)
    Response            NVARCHAR(2000) NULL,          -- nội dung trả lời
    CreatedAt           DATETIME2(3)   NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy           INT            NULL,
    UpdatedAt           DATETIME2(3)   NULL,
    UpdatedBy           INT            NULL,
    FOREIGN KEY (SenderUserId)        REFERENCES AppUser(Id),
    FOREIGN KEY (HandledByEmployeeId) REFERENCES Employee(Id)
);
CREATE INDEX IX_Feedback_Status ON Feedback(Status);

/* ========== Bảng 26: Notification — THÔNG BÁO ==========
   Ý nghĩa : Thông báo gửi tới một người dùng (vd: lịch được duyệt, có phân công mới).
   QUAN HỆ : RecipientUserId → AppUser (người nhận).  Khóa BIGINT vì rất nhiều. */
CREATE TABLE Notification (
    Id              BIGINT IDENTITY PRIMARY KEY,
    RecipientUserId INT            NOT NULL,          -- → AppUser (người nhận)
    Title           NVARCHAR(200)  NOT NULL,
    Content         NVARCHAR(1000) NULL,
    Type            VARCHAR(30)    NULL,              -- SCHEDULE | ASSIGNMENT | SYSTEM...
    RefEntity       VARCHAR(30)    NULL,              -- tên bảng liên quan (để bấm vào xem)
    RefId           BIGINT         NULL,              -- id bản ghi liên quan
    IsRead          BIT            NOT NULL DEFAULT 0,-- đã đọc chưa
    ReadAt          DATETIME2(3)   NULL,
    CreatedAt       DATETIME2(3)   NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (RecipientUserId) REFERENCES AppUser(Id)
);
CREATE INDEX IX_Notification_Recipient ON Notification(RecipientUserId, IsRead);

/* ========== Bảng 27: AuditLog — NHẬT KÝ HỆ THỐNG ==========
   Ý nghĩa : Ghi lại ai làm gì (tạo/sửa/xóa/đăng nhập) trên toàn hệ thống.
   QUAN HỆ : ActorUserId → AppUser (người thực hiện).  Khóa BIGINT.          */
CREATE TABLE AuditLog (
    Id          BIGINT IDENTITY PRIMARY KEY,
    ActorUserId INT           NULL,                   -- → AppUser (người thao tác)
    Action      VARCHAR(50)   NOT NULL,              -- CREATE | UPDATE | DELETE | LOGIN...
    Entity      VARCHAR(50)   NULL,                  -- tên bảng bị tác động
    EntityId    VARCHAR(50)   NULL,                  -- id bản ghi bị tác động
    OldValue    NVARCHAR(MAX) NULL,                  -- giá trị trước (JSON)
    NewValue    NVARCHAR(MAX) NULL,                  -- giá trị sau (JSON)
    IpAddress   VARCHAR(45)   NULL,                  -- IP người thao tác
    CreatedAt   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (ActorUserId) REFERENCES AppUser(Id)
);
CREATE INDEX IX_Audit_Entity ON AuditLog(Entity, EntityId);
CREATE INDEX IX_Audit_Actor  ON AuditLog(ActorUserId, CreatedAt);


/* #####################################################################
   NHÓM 7 — BÀI GIẢNG (LESSON MANAGEMENT)
   ##################################################################### */

/* ========== Bảng 28: Lesson — BÀI GIẢNG ==========
   Ý nghĩa : Nhân viên trung tâm tạo/sửa/xóa bài giảng, gắn với môn học,
             chi nhánh và (tùy chọn) giáo viên phụ trách soạn bài.
             Trường và giáo viên chỉ được XEM bài giảng đã PUBLISHED.
   QUAN HỆ : SubjectId → Subject (bài giảng thuộc môn nào),
             TeacherId → Teacher (GV phụ trách soạn, có thể NULL),
             BranchId  → Branch  (chi nhánh quản lý).
             Một bài giảng có nhiều LessonFile (file đính kèm).             */
CREATE TABLE Lesson (
    Id              INT IDENTITY PRIMARY KEY,
    SubjectId       INT            NOT NULL,          -- → Subject
    TeacherId       INT            NULL,              -- → Teacher (GV phụ trách, nếu có)
    BranchId        INT            NOT NULL,          -- → Branch (chi nhánh quản lý)
    Title           NVARCHAR(300)  NOT NULL,          -- tiêu đề bài giảng
    Description     NVARCHAR(2000) NULL,              -- mô tả / tóm tắt
    Content         NVARCHAR(MAX)  NULL,              -- nội dung chi tiết (rich text / markdown)
    GradeLevel      NVARCHAR(50)   NULL,              -- khối lớp áp dụng (vd: Lớp 6, Lớp 10)
    Duration        INT            NULL               -- thời lượng dạy dự kiến (PHÚT)
                    CONSTRAINT CK_Lesson_Duration CHECK (Duration IS NULL OR Duration > 0),
    DifficultyLevel VARCHAR(20)    NULL               -- BASIC / INTERMEDIATE / ADVANCED
                    CONSTRAINT CK_Lesson_Difficulty CHECK (DifficultyLevel IN ('BASIC','INTERMEDIATE','ADVANCED')),
    Status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT'  -- DRAFT(nháp) / PUBLISHED(đã đăng) / ARCHIVED(lưu kho)
                    CONSTRAINT CK_Lesson_Status CHECK (Status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    IsDeleted       BIT            NOT NULL DEFAULT 0,
    DeletedAt       DATETIME2(3)   NULL,
    DeletedBy       INT            NULL,
    CreatedAt       DATETIME2(3)   NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy       INT            NULL,
    UpdatedAt       DATETIME2(3)   NULL,
    UpdatedBy       INT            NULL,
    FOREIGN KEY (SubjectId) REFERENCES Subject(Id),
    FOREIGN KEY (TeacherId) REFERENCES Teacher(Id),
    FOREIGN KEY (BranchId)  REFERENCES Branch(Id)
);
CREATE INDEX IX_Lesson_Subject ON Lesson(SubjectId);   -- lọc bài giảng theo môn
CREATE INDEX IX_Lesson_Teacher ON Lesson(TeacherId);   -- lọc theo GV phụ trách
-- Gộp (BranchId, Status) vào MỘT index: phục vụ cả "lọc theo chi nhánh"
-- lẫn "lọc theo chi nhánh + trạng thái" (màn danh sách bài giảng hay dùng nhất).
CREATE INDEX IX_Lesson_Branch_Status ON Lesson(BranchId, Status) WHERE IsDeleted = 0;
CREATE INDEX IX_Lesson_Title ON Lesson(Title) WHERE IsDeleted = 0;  -- tìm theo tiêu đề

/* ========== Bảng 29: LessonFile — FILE ĐÍNH KÈM của bài giảng ==========
   Ý nghĩa : Một bài giảng có thể có nhiều file (PDF, PPTX, link video...).
   QUAN HỆ : LessonId → Lesson (nhiều-1).                                   */
CREATE TABLE LessonFile (
    Id           INT IDENTITY PRIMARY KEY,
    LessonId     INT           NOT NULL,              -- → Lesson
    FileName     NVARCHAR(255) NOT NULL,              -- tên file hiển thị
    FileUrl      VARCHAR(500)  NOT NULL,              -- đường dẫn / URL lưu trữ
    FileType     VARCHAR(50)   NULL,                  -- pdf / pptx / mp4 / link ...
    FileSizeKb   INT           NULL                   -- kích thước (KB)
                 CONSTRAINT CK_LessonFile_Size CHECK (FileSizeKb IS NULL OR FileSizeKb > 0),
    IsDeleted    BIT           NOT NULL DEFAULT 0,
    DeletedAt    DATETIME2(3)  NULL,
    DeletedBy    INT           NULL,
    CreatedAt    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy    INT           NULL,
    UpdatedAt    DATETIME2(3)  NULL,
    UpdatedBy    INT           NULL,
    FOREIGN KEY (LessonId) REFERENCES Lesson(Id)
);
CREATE INDEX IX_LessonFile_Lesson ON LessonFile(LessonId) WHERE IsDeleted = 0;


/* #####################################################################
   TRIGGER — tự động ghi LOG mỗi khi Schedule đổi trạng thái
   (Service nhớ set cột UpdatedBy = id người thao tác trước khi UPDATE)
   ##################################################################### */
GO
CREATE TRIGGER TR_Schedule_StatusLog
ON Schedule
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    INSERT INTO ScheduleStatusLog (ScheduleId, OldStatus, NewStatus, ChangedByUserId, ChangedAt)
    SELECT i.ScheduleId, d.Status, i.Status, i.UpdatedBy, SYSUTCDATETIME()
    FROM inserted i
    JOIN deleted  d ON i.ScheduleId = d.ScheduleId
    WHERE i.Status <> d.Status;   -- chỉ ghi khi trạng thái THỰC SỰ thay đổi
END;
GO

/* #####################################################################
   SEED — tạo sẵn 4 vai trò mặc định
   ##################################################################### */
INSERT INTO Role (Name, Description) VALUES
 ('ADMIN',    N'Quản trị toàn hệ thống'),
 ('EMPLOYEE', N'Nhân viên trung tâm (giới hạn theo chi nhánh)'),
 ('SCHOOL',   N'Tài khoản trường khách hàng'),
 ('TEACHER',  N'Giáo viên');
GO

/* #####################################################################
   SEED — 4 tài khoản mẫu đại diện 4 ACTOR  (mật khẩu chung: Tsdms@123)
   Quy ước: gán quan hệ theo Username/Name (KHÔNG hard-code Id tự tăng).
   ##################################################################### */

-- 1 chi nhánh gốc để gắn nhân viên/giáo viên/trường
INSERT INTO Branch (Name, Address, Phone) VALUES
 (N'Chi nhánh trung tâm', N'Hà Nội', '0240000000');
GO

-- 4 tài khoản đăng nhập (chỉ định danh). PasswordHash = BCrypt('Tsdms@123').
INSERT INTO AppUser (Username, PasswordHash, Email) VALUES
 ('admin',    '$2b$10$ohMPL3S6/wcDn/t4v17ASuo1v6vfwB.ZVVH/g/OpNWahstMRKt20m', 'admin@tsdms.local'),
 ('employee', '$2b$10$O419OLGJ23IaXz9oT9DYL.Xhk/uMJDpbHr8SKQgjgEysVakGT1NkO', 'employee@tsdms.local'),
 ('school',   '$2b$10$bj4N5E1LwiGBEZIkgfAFmuCZgE2unPmyQr9kjwBrOeradCNmKAvWm', 'school@tsdms.local'),
 ('teacher',  '$2b$10$QNvoqOIPKkrbysnPpWc5buzR/mVnKyCDL//p8jiTfl3VGTcs2XdfK', 'teacher@tsdms.local');
GO

-- Gán vai trò cho từng tài khoản (tra theo tên, không phụ thuộc Id)
INSERT INTO UserRole (AppUserId, RoleId)
SELECT u.AppUserId, r.RoleId
FROM AppUser u
JOIN Role r ON (u.Username = 'admin'    AND r.Name = 'ADMIN')
            OR (u.Username = 'employee' AND r.Name = 'EMPLOYEE')
            OR (u.Username = 'school'   AND r.Name = 'SCHOOL')
            OR (u.Username = 'teacher'  AND r.Name = 'TEACHER');
GO

-- Hồ sơ Nhân viên (1-1 với AppUser 'employee', gắn chi nhánh)
INSERT INTO Employee (AppUserId, BranchId, FirstName, LastName, Phone, Position)
SELECT u.AppUserId, b.BranchId, N'Tâm', N'Nhân Viên Trung', '0900000002', N'Điều phối viên'
FROM AppUser u CROSS JOIN Branch b
WHERE u.Username = 'employee' AND b.Name = N'Chi nhánh trung tâm';
GO

-- Hồ sơ Giáo viên (1-1 với AppUser 'teacher')
INSERT INTO Teacher (AppUserId, BranchId, FirstName, LastName, EmploymentType)
SELECT u.AppUserId, b.BranchId, N'Demo', N'Giáo Viên', 'FULL_TIME'
FROM AppUser u CROSS JOIN Branch b
WHERE u.Username = 'teacher' AND b.Name = N'Chi nhánh trung tâm';
GO

-- Hồ sơ Trường (1-1 với AppUser 'school')
INSERT INTO School (BranchId, Name, AppUserId, ContactPerson)
SELECT b.BranchId, N'Trường THPT Demo', u.AppUserId, N'Thầy Hiệu trưởng'
FROM AppUser u CROSS JOIN Branch b
WHERE u.Username = 'school' AND b.Name = N'Chi nhánh trung tâm';
GO
