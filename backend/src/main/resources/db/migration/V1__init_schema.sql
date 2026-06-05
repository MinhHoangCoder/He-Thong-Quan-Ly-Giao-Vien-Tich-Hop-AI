/* =====================================================================
   TSMS - HỆ THỐNG QUẢN LÝ & ĐIỀU PHỐI GIÁO VIÊN  (PHẦN LÕI)
   ---------------------------------------------------------------------
   MÔ HÌNH: Trung tâm giáo dục ĐIỀU PHỐI giáo viên đi dạy cho các trường
            khách hàng (chuyên STEM & Công dân số).
   4 ACTOR: ADMIN · EMPLOYEE (nhân viên trung tâm) · SCHOOL (trường) · TEACHER
   DBMS   : Microsoft SQL Server 2019+
   GHI CHÚ: Phần AI nằm ở file riêng "TSMS_Schema_AI.sql" — chạy SAU file này.
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
        School tạo  TeacherRequest  (yêu cầu cần GV cho 1 môn/lớp)
            → Employee tạo  Assignment  (gán GV ↔ trường ↔ môn ↔ lớp ↔ giai đoạn)
                → từ Assignment sinh ra nhiều  Schedule  (từng BUỔI dạy cụ thể)
                    → mỗi buổi có thể có  Attendance  (chấm công)
        Schedule đổi trạng thái → tự ghi  ScheduleStatusLog  (qua trigger)

     VẬN HÀNH KHÁC: Payroll (lương), TeacherEvaluation (đánh giá),
                    Feedback (phản hồi), Notification (thông báo),
                    AuditLog (nhật ký toàn hệ thống).
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

-- CREATE DATABASE TSMS COLLATE Vietnamese_CI_AS;
-- GO
-- USE TSMS;
-- GO


/* #####################################################################
   NHÓM 1 — TỔ CHỨC & XÁC THỰC (RBAC = phân quyền theo vai trò)
   ##################################################################### */

/* ========== Bảng 1: Branch — CHI NHÁNH của trung tâm ==========
   Ý nghĩa : Trung tâm có nhiều chi nhánh. Mọi dữ liệu (nhân viên, giáo viên,
             trường) đều gắn với một chi nhánh để giới hạn quyền truy cập.
   QUAN HỆ : Là bảng GỐC, được Employee/Teacher/School tham chiếu tới.        */
CREATE TABLE Branch (
    BranchId      INT IDENTITY PRIMARY KEY,          -- mã chi nhánh (tự tăng)
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
    AppUserId     INT IDENTITY PRIMARY KEY,
    Username      VARCHAR(50)   NOT NULL,            -- tên đăng nhập
    PasswordHash  VARCHAR(255)  NOT NULL,            -- mật khẩu ĐÃ mã hóa BCrypt (KHÔNG lưu thô)
    Email         VARCHAR(100)  NOT NULL,            -- email
    FullName      NVARCHAR(150) NOT NULL,            -- tên hiển thị
    Phone         VARCHAR(20)   NULL,
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
    RoleId       INT IDENTITY PRIMARY KEY,
    Name         VARCHAR(30)   NOT NULL UNIQUE,      -- ADMIN | EMPLOYEE | SCHOOL | TEACHER
    Description  NVARCHAR(255) NULL                  -- mô tả vai trò
);

/* ========== Bảng 4: Permission — QUYỀN CHI TIẾT ==========
   Ý nghĩa : Liệt kê từng quyền nhỏ (vd: duyệt lịch, tạo giáo viên) phục vụ
             chức năng "Phân quyền" của Admin.
   QUAN HỆ : Gán cho vai trò qua RolePermission.                              */
CREATE TABLE Permission (
    PermissionId INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (RoleId)       REFERENCES Role(RoleId),
    FOREIGN KEY (PermissionId) REFERENCES Permission(PermissionId)
);

/* ========== Bảng 6: UserRole — bảng nối TÀI KHOẢN ⇄ VAI TRÒ ==========
   Ý nghĩa : Một tài khoản có thể mang nhiều vai trò (nhiều-nhiều).
   QUAN HỆ : AppUserId → AppUser,  RoleId → Role.                             */
CREATE TABLE UserRole (
    AppUserId    INT NOT NULL,                        -- → AppUser.AppUserId
    RoleId       INT NOT NULL,                        -- → Role.RoleId
    AssignedAt   DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),  -- thời điểm gán vai trò
    PRIMARY KEY (AppUserId, RoleId),
    FOREIGN KEY (AppUserId) REFERENCES AppUser(AppUserId),
    FOREIGN KEY (RoleId)    REFERENCES Role(RoleId)
);

/* ========== Bảng 7: RefreshToken — TOKEN làm mới phiên đăng nhập (JWT) ==========
   Ý nghĩa : Lưu refresh token để cấp lại access token, và cho phép THU HỒI
             (đăng xuất 1 thiết bị / tất cả thiết bị).
   QUAN HỆ : AppUserId → AppUser. Khóa BIGINT vì sinh ra rất nhiều theo lượt đăng nhập. */
CREATE TABLE RefreshToken (
    RefreshTokenId BIGINT IDENTITY PRIMARY KEY,
    AppUserId      INT          NOT NULL,             -- → AppUser.AppUserId
    TokenHash      VARCHAR(255) NOT NULL,             -- lưu HASH của token, không lưu token thô
    ExpiresAt      DATETIME2(3) NOT NULL,             -- hạn dùng
    RevokedAt      DATETIME2(3) NULL,                 -- thời điểm bị thu hồi (NULL = còn hiệu lực)
    CreatedAt      DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (AppUserId) REFERENCES AppUser(AppUserId)
);
CREATE INDEX IX_RefreshToken_AppUser ON RefreshToken(AppUserId);  -- tra token theo user
CREATE INDEX IX_RefreshToken_Hash    ON RefreshToken(TokenHash);  -- tra theo token


/* #####################################################################
   NHÓM 2 — NHÂN SỰ TRUNG TÂM & DANH MỤC MÔN
   ##################################################################### */

/* ========== Bảng 8: Employee — NHÂN VIÊN TRUNG TÂM ==========
   Ý nghĩa : Hồ sơ của tài khoản có vai trò EMPLOYEE. Nhân viên chỉ quản lý
             dữ liệu trong chi nhánh của mình.
   QUAN HỆ : AppUserId → AppUser (1-1),  BranchId → Branch.                   */
CREATE TABLE Employee (
    EmployeeId   INT IDENTITY PRIMARY KEY,
    AppUserId    INT           NOT NULL UNIQUE,       -- → AppUser (mỗi user chỉ 1 hồ sơ NV)
    BranchId     INT           NOT NULL,             -- → Branch (nhân viên thuộc chi nhánh nào)
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
    FOREIGN KEY (AppUserId) REFERENCES AppUser(AppUserId),
    FOREIGN KEY (BranchId)  REFERENCES Branch(BranchId)
);
CREATE INDEX IX_Employee_Branch ON Employee(BranchId);  -- lọc nhân viên theo chi nhánh

/* ========== Bảng 9: Subject — MÔN HỌC ==========
   Ý nghĩa : Danh mục môn (STEM, Công dân số...).
   QUAN HỆ : Nối với Teacher qua TeacherSubject; được TeacherRequest/Assignment tham chiếu. */
CREATE TABLE Subject (
    SubjectId    INT IDENTITY PRIMARY KEY,
    Code         VARCHAR(20)   NOT NULL UNIQUE,      -- mã môn, vd: STEM01, CDS01
    Name         NVARCHAR(150) NOT NULL,            -- tên môn
    Category     NVARCHAR(50)  NULL,                -- nhóm: STEM | CONG_DAN_SO | ...
    Description  NVARCHAR(500) NULL,
    Status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / DISABLED(ngừng dạy)
                 CONSTRAINT CK_Subject_Status CHECK (Status IN ('ACTIVE','DISABLED')),
    IsDeleted    BIT           NOT NULL DEFAULT 0,
    DeletedAt    DATETIME2(3)  NULL,
    DeletedBy    INT           NULL,
    CreatedAt    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy    INT           NULL,
    UpdatedAt    DATETIME2(3)  NULL,
    UpdatedBy    INT           NULL
);
CREATE INDEX IX_Subject_Name ON Subject(Name);  -- tìm môn theo tên


/* #####################################################################
   NHÓM 3 — GIÁO VIÊN & HỒ SƠ
   ##################################################################### */

/* ========== Bảng 10: Teacher — GIÁO VIÊN ==========
   Ý nghĩa : Hồ sơ của tài khoản có vai trò TEACHER.
   QUAN HỆ : AppUserId → AppUser (1-1),  BranchId → Branch.
             Là tâm điểm: được TeacherSubject/Certificate/Contract/Assignment/
             Schedule/Attendance/Payroll/TeacherEvaluation tham chiếu tới.    */
CREATE TABLE Teacher (
    TeacherId       INT IDENTITY PRIMARY KEY,
    AppUserId       INT           NOT NULL UNIQUE,    -- → AppUser (1-1)
    BranchId        INT           NOT NULL,          -- → Branch (chi nhánh quản lý GV)
    FullName        NVARCHAR(150) NOT NULL,          -- họ tên
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
    FOREIGN KEY (AppUserId) REFERENCES AppUser(AppUserId),
    FOREIGN KEY (BranchId)  REFERENCES Branch(BranchId)
);
-- CCCD là duy nhất (chỉ tính bản ghi chưa xóa mềm, và bỏ qua ô để trống)
CREATE UNIQUE INDEX UX_Teacher_IdCard ON Teacher(IdCardNo) WHERE IdCardNo IS NOT NULL AND IsDeleted = 0;
CREATE INDEX IX_Teacher_Branch   ON Teacher(BranchId);    -- lọc GV theo chi nhánh
CREATE INDEX IX_Teacher_FullName ON Teacher(FullName);    -- tìm GV theo tên

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
    FOREIGN KEY (TeacherId) REFERENCES Teacher(TeacherId),
    FOREIGN KEY (SubjectId) REFERENCES Subject(SubjectId)
);
CREATE INDEX IX_TeacherSubject_Subject ON TeacherSubject(SubjectId);  -- tìm GV theo môn

/* ========== Bảng 12: Certificate — BẰNG CẤP & CHỨNG CHỈ của giáo viên ==========
   QUAN HỆ : TeacherId → Teacher (1 GV có nhiều bằng cấp/chứng chỉ).          */
CREATE TABLE Certificate (
    CertificateId INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (TeacherId) REFERENCES Teacher(TeacherId)
);
CREATE INDEX IX_Certificate_Teacher ON Certificate(TeacherId);

/* ========== Bảng 13: Contract — HỢP ĐỒNG của giáo viên ==========
   QUAN HỆ : TeacherId → Teacher (1 GV có thể có nhiều hợp đồng theo thời gian). */
CREATE TABLE Contract (
    ContractId   INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (TeacherId) REFERENCES Teacher(TeacherId),
    CONSTRAINT CK_Contract_Dates CHECK (EndDate IS NULL OR EndDate >= StartDate)  -- ngày KT ≥ ngày BĐ
);
CREATE INDEX IX_Contract_Teacher ON Contract(TeacherId);


/* #####################################################################
   NHÓM 4 — TRƯỜNG KHÁCH HÀNG, PHÒNG, LỚP, HỌC SINH
   ##################################################################### */

/* ========== Bảng 14: School — TRƯỜNG (khách hàng) ==========
   Ý nghĩa : Trường thuê giáo viên của trung tâm. Có thể có 1 tài khoản đăng nhập riêng.
   QUAN HỆ : BranchId → Branch (chi nhánh phụ trách),  AppUserId → AppUser (1-1, tùy chọn).
             Sở hữu Room/SchoolClass/Student; tạo TeacherRequest.            */
CREATE TABLE School (
    SchoolId          INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (BranchId)  REFERENCES Branch(BranchId),
    FOREIGN KEY (AppUserId) REFERENCES AppUser(AppUserId)
);
CREATE INDEX IX_School_Branch ON School(BranchId);
CREATE INDEX IX_School_Name   ON School(Name);

/* ========== Bảng 15: Room — PHÒNG HỌC (thuộc trường) ==========
   Ý nghĩa : Phòng để xếp lịch dạy. Chỉ phòng AVAILABLE mới được đặt.
   QUAN HỆ : SchoolId → School. Được Schedule tham chiếu.                    */
CREATE TABLE Room (
    RoomId     INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (SchoolId) REFERENCES School(SchoolId)
);
-- Trong cùng 1 trường, tên phòng không trùng (tính trên bản ghi chưa xóa mềm)
CREATE UNIQUE INDEX UX_Room_School_Name ON Room(SchoolId, Name) WHERE IsDeleted = 0;

/* ========== Bảng 16: SchoolClass — LỚP HỌC (thuộc trường) ==========
   QUAN HỆ : SchoolId → School. Nối với Student qua ClassEnrollment.         */
CREATE TABLE SchoolClass (
    ClassId     INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (SchoolId) REFERENCES School(SchoolId)
);
CREATE UNIQUE INDEX UX_Class_School_Name_Year ON SchoolClass(SchoolId, Name, SchoolYear) WHERE IsDeleted = 0;

/* ========== Bảng 17: Student — HỌC SINH ==========
   Ý nghĩa : Chỉ lưu danh sách học sinh (KHÔNG điểm, KHÔNG học phí).
   QUAN HỆ : SchoolId → School. Nối với SchoolClass qua ClassEnrollment.     */
CREATE TABLE Student (
    StudentId   INT IDENTITY PRIMARY KEY,
    SchoolId    INT           NOT NULL,              -- → School.SchoolId
    FullName    NVARCHAR(150) NOT NULL,
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
    FOREIGN KEY (SchoolId) REFERENCES School(SchoolId)
);
CREATE INDEX IX_Student_School   ON Student(SchoolId);
CREATE INDEX IX_Student_FullName ON Student(FullName);

/* ========== Bảng 18: ClassEnrollment — bảng nối LỚP ⇄ HỌC SINH ==========
   Ý nghĩa : Một lớp có nhiều học sinh; một học sinh có thể vào nhiều lớp (nhiều-nhiều).
   QUAN HỆ : ClassId → SchoolClass,  StudentId → Student.                    */
CREATE TABLE ClassEnrollment (
    ClassId    INT NOT NULL,                          -- → SchoolClass.ClassId
    StudentId  INT NOT NULL,                          -- → Student.StudentId
    EnrolledAt DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),  -- thời điểm ghi danh
    PRIMARY KEY (ClassId, StudentId),
    FOREIGN KEY (ClassId)   REFERENCES SchoolClass(ClassId),
    FOREIGN KEY (StudentId) REFERENCES Student(StudentId)
);
CREATE INDEX IX_Enrollment_Student ON ClassEnrollment(StudentId);


/* #####################################################################
   NHÓM 5 — ĐIỀU PHỐI (LÕI): YÊU CẦU → PHÂN CÔNG → LỊCH DẠY
   ##################################################################### */

/* ========== Bảng 19: TeacherRequest — YÊU CẦU GIÁO VIÊN ==========
   Ý nghĩa : TRƯỜNG gửi yêu cầu cần giáo viên cho một môn/lớp. Nhân viên xử lý.
   QUAN HỆ : SchoolId → School,  SubjectId → Subject,  ClassId → SchoolClass,
             HandledByEmployeeId → Employee (nhân viên xử lý).
             Là điểm BẮT ĐẦU của luồng điều phối → dẫn tới Assignment.        */
CREATE TABLE TeacherRequest (
    RequestId           INT IDENTITY PRIMARY KEY,
    SchoolId            INT           NOT NULL,       -- → School (trường gửi yêu cầu)
    SubjectId           INT           NOT NULL,       -- → Subject (môn cần dạy)
    ClassId             INT           NULL,           -- → SchoolClass (lớp cần dạy, nếu có)
    RequestedQuantity   INT           NOT NULL DEFAULT 1,  -- số giáo viên cần
    DesiredStartDate    DATE          NULL,           -- mong muốn bắt đầu
    DesiredEndDate      DATE          NULL,
    PreferredTimeNote   NVARCHAR(255) NULL,           -- khung giờ mong muốn (ghi chú text)
    Note                NVARCHAR(500) NULL,
    Status              VARCHAR(20)   NOT NULL DEFAULT 'NEW'  -- xem chú thích dưới
                        CONSTRAINT CK_Request_Status
                        CHECK (Status IN ('NEW','IN_REVIEW','MATCHED','FULFILLED','REJECTED','CANCELLED')),
    -- NEW=mới gửi · IN_REVIEW=đang xử lý · MATCHED=đã ghép GV · FULFILLED=đã đáp ứng
    -- REJECTED=từ chối · CANCELLED=hủy
    HandledByEmployeeId INT           NULL,           -- → Employee (nhân viên phụ trách)
    IsDeleted           BIT           NOT NULL DEFAULT 0,
    DeletedAt           DATETIME2(3)  NULL,
    DeletedBy           INT           NULL,
    CreatedAt           DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy           INT           NULL,
    UpdatedAt           DATETIME2(3)  NULL,
    UpdatedBy           INT           NULL,
    FOREIGN KEY (SchoolId)            REFERENCES School(SchoolId),
    FOREIGN KEY (SubjectId)           REFERENCES Subject(SubjectId),
    FOREIGN KEY (ClassId)             REFERENCES SchoolClass(ClassId),
    FOREIGN KEY (HandledByEmployeeId) REFERENCES Employee(EmployeeId)
);
CREATE INDEX IX_Request_School ON TeacherRequest(SchoolId);
CREATE INDEX IX_Request_Status ON TeacherRequest(Status);

/* ========== Bảng 20: Assignment — PHÂN CÔNG ==========
   Ý nghĩa : Nhân viên gán một GIÁO VIÊN cho một TRƯỜNG/MÔN/LỚP trong một giai đoạn.
             LƯU Ý: một GV CÓ THỂ được phân công cho NHIỀU trường (mô hình điều phối).
   QUAN HỆ : TeacherId → Teacher,  SchoolId → School,  SubjectId → Subject,
             ClassId → SchoolClass,  RequestId → TeacherRequest (yêu cầu gốc),
             AssignedByEmployeeId → Employee.  Sinh ra nhiều Schedule.        */
CREATE TABLE Assignment (
    AssignmentId         INT IDENTITY PRIMARY KEY,
    TeacherId            INT           NOT NULL,      -- → Teacher
    SchoolId             INT           NOT NULL,      -- → School
    SubjectId            INT           NOT NULL,      -- → Subject
    ClassId              INT           NULL,          -- → SchoolClass (nếu gắn lớp cụ thể)
    RequestId            INT           NULL,          -- → TeacherRequest (yêu cầu khởi nguồn)
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
    FOREIGN KEY (TeacherId)            REFERENCES Teacher(TeacherId),
    FOREIGN KEY (SchoolId)             REFERENCES School(SchoolId),
    FOREIGN KEY (SubjectId)            REFERENCES Subject(SubjectId),
    FOREIGN KEY (ClassId)              REFERENCES SchoolClass(ClassId),
    FOREIGN KEY (RequestId)            REFERENCES TeacherRequest(RequestId),
    FOREIGN KEY (AssignedByEmployeeId) REFERENCES Employee(EmployeeId),
    CONSTRAINT CK_Assignment_Dates CHECK (EndDate IS NULL OR EndDate >= StartDate)
);
CREATE INDEX IX_Assignment_Teacher ON Assignment(TeacherId);
CREATE INDEX IX_Assignment_School  ON Assignment(SchoolId);

/* ========== Bảng 21: Schedule — LỊCH DẠY (bảng TRUNG TÂM) ==========
   Ý nghĩa : Từng BUỔI dạy cụ thể (ngày-giờ-phòng) sinh ra từ một Assignment,
             kèm quy trình duyệt PENDING → APPROVED/REJECTED/CANCELLED.
   QUAN HỆ : AssignmentId → Assignment,  TeacherId → Teacher (lưu kèm để dò
             TRÙNG LỊCH nhanh; phải = giáo viên của Assignment),
             RoomId → Room,  CreatedByUserId/ApprovedByUserId → AppUser.
   KHÓA BIGINT vì số buổi tích lũy rất lớn theo thời gian.                    */
CREATE TABLE Schedule (
    ScheduleId       BIGINT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (AssignmentId)     REFERENCES Assignment(AssignmentId),
    FOREIGN KEY (TeacherId)        REFERENCES Teacher(TeacherId),
    FOREIGN KEY (RoomId)           REFERENCES Room(RoomId),
    FOREIGN KEY (CreatedByUserId)  REFERENCES AppUser(AppUserId),
    FOREIGN KEY (ApprovedByUserId) REFERENCES AppUser(AppUserId),
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

/* ========== Bảng 22: ScheduleStatusLog — NHẬT KÝ ĐỔI TRẠNG THÁI LỊCH ==========
   Ý nghĩa : Tự động ghi lại mỗi lần Schedule đổi trạng thái (qua trigger ở cuối file).
   QUAN HỆ : ScheduleId → Schedule.  Khóa BIGINT vì số dòng lớn.             */
CREATE TABLE ScheduleStatusLog (
    LogId           BIGINT IDENTITY PRIMARY KEY,
    ScheduleId      BIGINT        NOT NULL,           -- → Schedule
    OldStatus       VARCHAR(20)   NULL,               -- trạng thái cũ
    NewStatus       VARCHAR(20)   NOT NULL,           -- trạng thái mới
    ChangedByUserId INT           NULL,               -- ai đổi
    ChangedAt       DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    Note            NVARCHAR(500) NULL,
    FOREIGN KEY (ScheduleId) REFERENCES Schedule(ScheduleId)
);
CREATE INDEX IX_StatusLog_Schedule ON ScheduleStatusLog(ScheduleId);


/* #####################################################################
   NHÓM 6 — VẬN HÀNH: CHẤM CÔNG, LƯƠNG, ĐÁNH GIÁ, PHẢN HỒI, THÔNG BÁO, AUDIT
   ##################################################################### */

/* ========== Bảng 23: Attendance — CHẤM CÔNG ==========
   Ý nghĩa : Ghi nhận giáo viên có dạy buổi đó không, giờ vào/ra.
   PHƯƠNG ÁN chống gian lận (gợi ý): GV tự check-in + TRƯỜNG xác nhận
             (CheckInMethod='SELF', ConfirmedByUserId = tài khoản trường).
   QUAN HỆ : TeacherId → Teacher,  ScheduleId → Schedule (buổi dạy),
             ConfirmedByUserId → AppUser (người xác nhận). Khóa BIGINT.       */
CREATE TABLE Attendance (
    AttendanceId      BIGINT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (TeacherId)         REFERENCES Teacher(TeacherId),
    FOREIGN KEY (ScheduleId)        REFERENCES Schedule(ScheduleId),
    FOREIGN KEY (ConfirmedByUserId) REFERENCES AppUser(AppUserId),
    CONSTRAINT CK_Attendance_Time CHECK (CheckIn IS NULL OR CheckOut IS NULL OR CheckIn < CheckOut)
);
CREATE INDEX IX_Attendance_Teacher_Date ON Attendance(TeacherId, WorkDate);

/* ========== Bảng 24: Payroll — BẢNG LƯƠNG (theo tháng) ==========
   Ý nghĩa : Lưu đủ thành phần lương để linh hoạt công thức. NetAmount tự tính.
   QUAN HỆ : TeacherId → Teacher. Mỗi GV mỗi tháng chỉ 1 dòng (ràng buộc UNIQUE). */
CREATE TABLE Payroll (
    PayrollId   INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (TeacherId) REFERENCES Teacher(TeacherId),
    CONSTRAINT UX_Payroll_Period UNIQUE (TeacherId, PeriodYear, PeriodMonth)
);

/* ========== Bảng 25: TeacherEvaluation — ĐÁNH GIÁ GIÁO VIÊN ==========
   Ý nghĩa : Trường hoặc nhân viên chấm điểm/nhận xét giáo viên.
   QUAN HỆ : TeacherId → Teacher,  EvaluatorUserId → AppUser (người đánh giá),
             SchoolId → School (nếu do trường đánh giá).                      */
CREATE TABLE TeacherEvaluation (
    EvaluationId    INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (TeacherId)       REFERENCES Teacher(TeacherId),
    FOREIGN KEY (EvaluatorUserId) REFERENCES AppUser(AppUserId),
    FOREIGN KEY (SchoolId)        REFERENCES School(SchoolId)
);
CREATE INDEX IX_Eval_Teacher ON TeacherEvaluation(TeacherId);

/* ========== Bảng 26: Feedback — PHẢN HỒI ==========
   Ý nghĩa : Người dùng (thường là GV) gửi phản hồi; nhân viên xử lý & trả lời.
   QUAN HỆ : SenderUserId → AppUser (người gửi),  HandledByEmployeeId → Employee. */
CREATE TABLE Feedback (
    FeedbackId          INT IDENTITY PRIMARY KEY,
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
    FOREIGN KEY (SenderUserId)        REFERENCES AppUser(AppUserId),
    FOREIGN KEY (HandledByEmployeeId) REFERENCES Employee(EmployeeId)
);
CREATE INDEX IX_Feedback_Status ON Feedback(Status);

/* ========== Bảng 27: Notification — THÔNG BÁO ==========
   Ý nghĩa : Thông báo gửi tới một người dùng (vd: lịch được duyệt, có yêu cầu mới).
   QUAN HỆ : RecipientUserId → AppUser (người nhận).  Khóa BIGINT vì rất nhiều. */
CREATE TABLE Notification (
    NotificationId  BIGINT IDENTITY PRIMARY KEY,
    RecipientUserId INT            NOT NULL,          -- → AppUser (người nhận)
    Title           NVARCHAR(200)  NOT NULL,
    Content         NVARCHAR(1000) NULL,
    Type            VARCHAR(30)    NULL,              -- SCHEDULE | ASSIGNMENT | REQUEST | SYSTEM...
    RefEntity       VARCHAR(30)    NULL,              -- tên bảng liên quan (để bấm vào xem)
    RefId           BIGINT         NULL,              -- id bản ghi liên quan
    IsRead          BIT            NOT NULL DEFAULT 0,-- đã đọc chưa
    ReadAt          DATETIME2(3)   NULL,
    CreatedAt       DATETIME2(3)   NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (RecipientUserId) REFERENCES AppUser(AppUserId)
);
CREATE INDEX IX_Notification_Recipient ON Notification(RecipientUserId, IsRead);

/* ========== Bảng 28: AuditLog — NHẬT KÝ HỆ THỐNG ==========
   Ý nghĩa : Ghi lại ai làm gì (tạo/sửa/xóa/đăng nhập) trên toàn hệ thống.
   QUAN HỆ : ActorUserId → AppUser (người thực hiện).  Khóa BIGINT.          */
CREATE TABLE AuditLog (
    AuditLogId  BIGINT IDENTITY PRIMARY KEY,
    ActorUserId INT           NULL,                   -- → AppUser (người thao tác)
    Action      VARCHAR(50)   NOT NULL,              -- CREATE | UPDATE | DELETE | LOGIN...
    Entity      VARCHAR(50)   NULL,                  -- tên bảng bị tác động
    EntityId    VARCHAR(50)   NULL,                  -- id bản ghi bị tác động
    OldValue    NVARCHAR(MAX) NULL,                  -- giá trị trước (JSON)
    NewValue    NVARCHAR(MAX) NULL,                  -- giá trị sau (JSON)
    IpAddress   VARCHAR(45)   NULL,                  -- IP người thao tác
    CreatedAt   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (ActorUserId) REFERENCES AppUser(AppUserId)
);
CREATE INDEX IX_Audit_Entity ON AuditLog(Entity, EntityId);
CREATE INDEX IX_Audit_Actor  ON AuditLog(ActorUserId, CreatedAt);


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
