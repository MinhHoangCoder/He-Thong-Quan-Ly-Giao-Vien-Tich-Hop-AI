/* =====================================================================
   TSDMS — SEED 100 GIÁO VIÊN + 23 MÔN HỌC
   ---------------------------------------------------------------------
   CHẠY THẾ NÀO
     Mở file này trong SSMS (đã kết nối DB TSDMS) rồi bấm Execute. KHÔNG
     đặt vào Flyway: migration chạy tự động ở MỌI môi trường, còn đây là
     DỮ LIỆU DEMO chỉ dành cho máy dev/máy demo đồ án — đúng quy ước đã
     ghi ở đầu file database/seed/TSDMS_Seed_Demo.sql.

   FILE NÀY LÀM GÌ (theo đúng thứ tự)
     0) Dọn tài khoản vai trò TEACHER "mồ côi" — có AppUser nhưng KHÔNG có
        hồ sơ Teacher. Chỉ xóa khi tài khoản đó không còn dấu vết ở bất kỳ
        bảng nào khác (đã kiểm tra đủ 12 bảng có khóa ngoại tới AppUser).
     1) Đổi tên nhóm môn 'NLS-STEM - AI' -> 'STEM - AI' (Code STEM_AI giữ
        nguyên nên mã môn vẫn sinh theo prefix 'SA').
    1b) Đưa bộ đếm IDENTITY về đầu (chỉ khi bảng đang rỗng) để giáo viên đầu
        tiên mang Id = 1 chứ không phải một số lẻ do các lần nạp/gỡ trước đó
        để lại.
     2) Thêm 23 môn học thuộc 5 nhóm hiện có.
     3) Thêm 100 tài khoản + 100 hồ sơ giáo viên, kèm bằng cấp, hợp đồng
        và môn dạy được.

   DỮ LIỆU NÀY LÀ DỮ LIỆU GIẢ
     Họ tên, địa chỉ, trường cấp bằng và mức lương mô phỏng theo thực tế
     Việt Nam để màn hình trông đúng nghiệp vụ, NHƯNG không mô tả bất kỳ
     người thật nào. CCCD và số điện thoại là số sinh tự động, chỉ đúng
     ĐỊNH DẠNG. Email dùng đuôi @tsdms.local — tên miền nội bộ không định
     tuyến ra Internet, nên luồng "Quên mật khẩu" không thể gửi mail nhầm
     vào hòm thư của người thật.
     => CHỈ dùng ở máy dev/demo. KHÔNG nạp vào môi trường có người dùng thật.

   QUY ƯỚC ÁP DỤNG
     - Toàn bộ 100 GV thuộc 'Chi nhánh trung tâm'.
     - Tài khoản: username 'gv.<họtênkhôngdấu>', mật khẩu chung 'Tsdms@123'.
       Trạng thái tài khoản bám theo trạng thái hồ sơ: ACTIVE -> ACTIVE,
       RETIRED -> INACTIVE, SUSPENDED -> LOCKED.
     - Tra Id theo Username/Code/Name, KHÔNG hard-code Id tự tăng.
     - Chốt chặn ở đầu file: lỡ chạy 2 lần sẽ tự thoát, không tạo trùng.
     - Bọc trong TRANSACTION: lỗi giữa chừng -> rollback sạch, không để lại
       AppUser mồ côi (đúng cái bệnh mà bước 0 đang phải đi dọn).
     - File lưu UTF-8 CÓ BOM để SSMS đọc đúng tiếng Việt trong 100 hồ sơ.

   GỠ RA: chạy database/seed/TSDMS_Rollback_100GiaoVien.sql
   ===================================================================== */

USE TSDMS;
GO

/* QUOTED_IDENTIFIER phải BẬT: AppUser/Teacher/Contract có filtered index
   (UX_AppUser_Username, UX_Teacher_IdCard...), mà SQL Server từ chối mọi
   INSERT/DELETE lên bảng có filtered index khi tùy chọn này tắt (lỗi 1934).
   SSMS bật sẵn, nhưng sqlcmd thì KHÔNG — khai báo thẳng ở đây để file chạy
   được ở cả hai nơi. */
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---- CHỐT CHẶN: đã nạp rồi thì thoát, không chạy lại ---- */
IF EXISTS (SELECT 1 FROM AppUser WHERE Username = 'gv.hothanhhuong' AND IsDeleted = 0)
BEGIN
    PRINT N'>>> Bộ 100 giáo viên đã được nạp trước đó — bỏ qua, không chạy lại.';
    RETURN;
END

BEGIN TRY
BEGIN TRANSACTION;

DECLARE @Admin  INT = (SELECT Id FROM AppUser WHERE Username = 'admin' AND IsDeleted = 0);
DECLARE @Branch INT = (SELECT Id FROM Branch  WHERE Name = N'Chi nhánh trung tâm' AND IsDeleted = 0);
DECLARE @Hash   VARCHAR(255) = '$2b$10$QNvoqOIPKkrbysnPpWc5buzR/mVnKyCDL//p8jiTfl3VGTcs2XdfK';   -- BCrypt('Tsdms@123')

IF @Branch IS NULL
    THROW 50001, N'Không tìm thấy chi nhánh "Chi nhánh trung tâm" — kiểm tra lại DB trước khi seed.', 1;
IF NOT EXISTS (SELECT 1 FROM Role WHERE Name = 'TEACHER')
    THROW 50002, N'Chưa có vai trò TEACHER — chạy Flyway V1 trước khi seed.', 1;


/* =====================================================================
   0) DỌN TÀI KHOẢN TEACHER MỒ CÔI
   ---------------------------------------------------------------------
   Tài khoản có vai trò TEACHER nhưng hồ sơ Teacher đã bị xóa cứng trước
   đó. Để lại thì số "tài khoản giáo viên" và số "hồ sơ giáo viên" lệch
   nhau vĩnh viễn, và username/email bị chiếm chỗ vô ích.

   Chỉ xóa tài khoản KHÔNG còn tham chiếu ở bất kỳ đâu — liệt kê đủ 12
   bảng có khóa ngoại trỏ tới AppUser. Tài khoản nào còn dính dữ liệu
   (đã tạo lịch, đã gửi phản hồi, có nhật ký...) sẽ được giữ nguyên,
   thà thừa còn hơn xóa mất lịch sử.
   ===================================================================== */
DECLARE @Orphan TABLE (Id INT PRIMARY KEY);

INSERT INTO @Orphan (Id)
SELECT u.Id
FROM AppUser u
JOIN UserRole ur ON ur.AppUserId = u.Id
JOIN Role     r  ON r.Id = ur.RoleId AND r.Name = 'TEACHER'
WHERE NOT EXISTS (SELECT 1 FROM Teacher                t  WHERE t.AppUserId          = u.Id)
  AND NOT EXISTS (SELECT 1 FROM Employee               e  WHERE e.AppUserId          = u.Id)
  AND NOT EXISTS (SELECT 1 FROM School                 s  WHERE s.AppUserId          = u.Id)
  AND NOT EXISTS (SELECT 1 FROM RefreshToken           rt WHERE rt.AppUserId          = u.Id)
  AND NOT EXISTS (SELECT 1 FROM PasswordResetToken     pr WHERE pr.AppUserId          = u.Id)
  AND NOT EXISTS (SELECT 1 FROM Notification           n  WHERE n.RecipientUserId     = u.Id)
  AND NOT EXISTS (SELECT 1 FROM AuditLog               al WHERE al.ActorUserId        = u.Id)
  AND NOT EXISTS (SELECT 1 FROM Feedback               f  WHERE f.SenderUserId        = u.Id)
  AND NOT EXISTS (SELECT 1 FROM TeacherEvaluation      te WHERE te.EvaluatorUserId    = u.Id)
  AND NOT EXISTS (SELECT 1 FROM Schedule               sc WHERE sc.CreatedByUserId    = u.Id
                                                            OR sc.ApprovedByUserId    = u.Id)
  AND NOT EXISTS (SELECT 1 FROM Attendance             at WHERE at.ConfirmedByUserId  = u.Id)
  AND NOT EXISTS (SELECT 1 FROM AttendanceAmendRequest ar WHERE ar.ReviewedByUserId   = u.Id);

DELETE FROM UserRole WHERE AppUserId IN (SELECT Id FROM @Orphan);
DELETE FROM AppUser  WHERE Id        IN (SELECT Id FROM @Orphan);

DECLARE @OrphanCount INT = (SELECT COUNT(*) FROM @Orphan);
PRINT N'0) Đã dọn ' + CAST(@OrphanCount AS NVARCHAR(10)) + N' tài khoản TEACHER mồ côi.';


/* =====================================================================
   1) ĐỔI TÊN NHÓM MÔN 'NLS-STEM - AI' -> 'STEM - AI'
   ---------------------------------------------------------------------
   Chỉ đổi Name hiển thị. Code 'STEM_AI' GIỮ NGUYÊN vì bộ sinh mã môn
   (SubjectService#buildCodePrefix) lấy chữ cái đầu của Code để dựng
   prefix — đổi Code sẽ làm mã môn nhảy từ 'SA' sang thứ khác.
   ===================================================================== */
UPDATE SubjectCategory
SET Name = N'STEM - AI', UpdatedAt = SYSUTCDATETIME(), UpdatedBy = @Admin
WHERE Code = 'STEM_AI' AND Name <> N'STEM - AI';


/* =====================================================================
   1c) CHI NHÁNH VỀ ĐÚNG HẢI PHÒNG
   ---------------------------------------------------------------------
   Trung tâm chỉ có MỘT cơ sở, đặt tại Hải Phòng. Bản ghi Branch trước đó
   ghi 'Hà Nội' và số điện thoại đầu 024 (mã vùng Hà Nội) — để nguyên thì
   màn hình tự mâu thuẫn: 100 giáo viên ở Hải Phòng nhưng chi nhánh quản lý
   họ lại nằm ở Hà Nội.

   Địa chỉ để ở mức tên thành phố, đúng như cách bản ghi cũ chỉ ghi 'Hà Nội'
   — KHÔNG bịa số nhà/tên phố cho một doanh nghiệp có thật. 0225 là mã vùng
   điện thoại cố định của Hải Phòng; 7 số sau là chỗ trống chờ điền số thật.
   ===================================================================== */
UPDATE Branch
SET Address   = N'Hải Phòng',
    Phone     = '02250000000',
    UpdatedAt = SYSUTCDATETIME(),
    UpdatedBy = @Admin
WHERE Id = @Branch AND Address = N'Hà Nội';


/* =====================================================================
   1b) ĐƯA BỘ ĐẾM ID VỀ ĐẦU
   ---------------------------------------------------------------------
   Cột Id của các bảng này là IDENTITY. Bộ đếm IDENTITY chỉ TIẾN, không bao
   giờ lùi khi xóa dòng — nên nếu trước đó đã từng nạp rồi gỡ dữ liệu (kể cả
   chạy thử file này vài lần), giáo viên đầu tiên sẽ mang Id kiểu 149 thay vì
   1. Nhìn màn hình danh sách rất khó hiểu.

   CHỈ reseed khi bảng ĐANG RỖNG. Bảng còn dòng mà reseed về 0 là dòng mới
   sẽ đâm trùng khóa chính với dòng cũ — nên có IF NOT EXISTS chặn ở đây,
   máy nào đang có dữ liệu thật thì bước này tự bỏ qua.

   AppUser KHÔNG reseed về 0 được vì luôn còn tài khoản hệ thống (admin,
   employee, kế toán...). Chỉ dồn bộ đếm về đúng Id lớn nhất đang có để 100
   tài khoản mới nối tiếp liền mạch, không chừa khoảng trống.
   ===================================================================== */
IF NOT EXISTS (SELECT 1 FROM Teacher)     DBCC CHECKIDENT('Teacher',     RESEED, 0) WITH NO_INFOMSGS;
IF NOT EXISTS (SELECT 1 FROM Subject)     DBCC CHECKIDENT('Subject',     RESEED, 0) WITH NO_INFOMSGS;
IF NOT EXISTS (SELECT 1 FROM Certificate) DBCC CHECKIDENT('Certificate', RESEED, 0) WITH NO_INFOMSGS;
IF NOT EXISTS (SELECT 1 FROM Contract)    DBCC CHECKIDENT('Contract',    RESEED, 0) WITH NO_INFOMSGS;

DECLARE @MaxUserId INT = (SELECT ISNULL(MAX(Id), 0) FROM AppUser);
DBCC CHECKIDENT('AppUser', RESEED, @MaxUserId) WITH NO_INFOMSGS;


/* =====================================================================
   2) MÔN HỌC — 23 môn thuộc 5 nhóm hiện có
   ---------------------------------------------------------------------
   Mã môn đặt theo ĐÚNG quy ước bộ sinh tự động của SubjectService:
   prefix = chữ cái đầu các phần trong Code của nhóm, rồi số thứ tự 2 chữ số.
     TIN_HOC -> TH · TIENG_ANH -> TA · STEM_AI -> SA
     KY_NANG_SONG -> KNS · KY_NANG_SO -> KNS  (hai nhóm này TRÙNG prefix,
     nên đánh số nối tiếp KNS01..KNS07 để môn tạo sau bằng UI không bị
     trùng mã.)
   Dùng NOT EXISTS để không đụng môn đã có sẵn.

   COLLATE DATABASE_DEFAULT trên mọi cột chuỗi của bảng tạm: bảng tạm nằm ở
   tempdb nên mặc định lấy collation của SERVER (thường Latin1), trong khi
   DB TSDMS dùng Vietnamese_CI_AS. Thiếu dòng này thì mọi phép JOIN theo
   Code/Username dưới đây đều nổ lỗi 468 "Cannot resolve the collation
   conflict" — máy nào cài SQL Server theo mặc định cũng dính.
   ===================================================================== */
CREATE TABLE #Subj (
    Code    VARCHAR(20)   COLLATE DATABASE_DEFAULT PRIMARY KEY,
    Name    NVARCHAR(150) COLLATE DATABASE_DEFAULT,
    CatCode VARCHAR(50)   COLLATE DATABASE_DEFAULT,
    Descr   NVARCHAR(500) COLLATE DATABASE_DEFAULT
);

INSERT INTO #Subj (Code, Name, CatCode, Descr) VALUES
 ('TH01', N'Tin học cơ bản (GDPT 2018)', 'TIN_HOC', N'Tin học nền tảng theo Chương trình GDPT 2018, khối 3-9'),
 ('TH02', N'Soạn thảo văn bản & Trình chiếu', 'TIN_HOC', N'Kĩ năng soạn thảo, trình bày và thuyết trình bằng công cụ số'),
 ('TH03', N'Bảng tính Excel cho học sinh', 'TIN_HOC', N'Nhập liệu, công thức và biểu đồ cơ bản trên bảng tính'),
 ('TA01', N'Tiếng Anh làm quen (Lớp 1-2)', 'TIENG_ANH', N'Chương trình Tiếng Anh tự chọn lớp 1-2: nghe - nói qua trò chơi và bài hát'),
 ('TA02', N'Tiếng Anh Tiểu học 3-5 - Global Success', 'TIENG_ANH', N'Bám sát bộ Tiếng Anh Global Success (NXB Giáo dục Việt Nam), khối 3-5'),
 ('TA03', N'Tiếng Anh Tiểu học 3-5 - i-Learn Smart Start', 'TIENG_ANH', N'Bám sát bộ i-Learn Smart Start, khối 3-5'),
 ('TA04', N'Tiếng Anh THCS 6-9 - Global Success', 'TIENG_ANH', N'Bám sát bộ Tiếng Anh Global Success (NXB Giáo dục Việt Nam), khối 6-9'),
 ('TA05', N'Tiếng Anh THCS 6-9 - i-Learn Smart World', 'TIENG_ANH', N'Bám sát bộ i-Learn Smart World, khối 6-9'),
 ('TA06', N'Ngữ âm & Phonics Tiểu học', 'TIENG_ANH', N'Xây nền phát âm chuẩn cho học sinh tiểu học theo phương pháp Phonics'),
 ('SA01', N'Lập trình Scratch', 'STEM_AI', N'Lập trình kéo - thả, tư duy thuật toán cho học sinh tiểu học'),
 ('SA02', N'Lập trình robot Leanbot', 'STEM_AI', N'Lắp ráp và lập trình robot giáo dục Leanbot'),
 ('SA03', N'Lập trình robot Vincibot', 'STEM_AI', N'Điều khiển và lập trình robot Vincibot theo dự án'),
 ('SA04', N'Lập trình robot Spike Essential', 'STEM_AI', N'Lắp ráp và lập trình bộ LEGO Education SPIKE Essential'),
 ('SA05', N'Lập trình robot RoboSim', 'STEM_AI', N'Lập trình robot trên môi trường mô phỏng RoboSim'),
 ('SA06', N'Tinkering', 'STEM_AI', N'Học qua chế tác: thử - sai - cải tiến với vật liệu mở'),
 ('SA07', N'Nhập môn Trí tuệ nhân tạo', 'STEM_AI', N'Khái niệm AI, machine learning và ứng dụng ở mức phổ thông'),
 ('KNS01', N'Kĩ năng giao tiếp & thuyết trình', 'KY_NANG_SONG', N'Diễn đạt, lắng nghe và trình bày trước đám đông'),
 ('KNS02', N'Kĩ năng làm việc nhóm & lãnh đạo', 'KY_NANG_SONG', N'Phân vai, hợp tác và dẫn dắt nhóm trong hoạt động học tập'),
 ('KNS03', N'Kĩ năng tự bảo vệ & phòng chống xâm hại', 'KY_NANG_SONG', N'Nhận diện nguy cơ, quy tắc an toàn cá nhân và cách tìm trợ giúp'),
 ('KNS04', N'Kĩ năng quản lý thời gian & tài chính cá nhân', 'KY_NANG_SONG', N'Lập kế hoạch, ưu tiên công việc và thói quen chi tiêu lành mạnh'),
 ('KNS05', N'Công dân số cơ bản', 'KY_NANG_SO', N'Định danh số, ứng xử văn minh và khai thác dịch vụ công trực tuyến'),
 ('KNS06', N'An toàn trên không gian mạng', 'KY_NANG_SO', N'Nhận diện lừa đảo, bảo vệ tài khoản và thông tin cá nhân'),
 ('KNS07', N'Khai thác AI an toàn cho học sinh', 'KY_NANG_SO', N'Dùng công cụ AI có trách nhiệm, kiểm chứng thông tin và liêm chính học thuật');

INSERT INTO Subject (Code, Name, CategoryId, Description, Status, CreatedBy)
SELECT s.Code, s.Name, c.Id, s.Descr, 'ACTIVE', @Admin
FROM #Subj s
JOIN SubjectCategory c ON c.Code = s.CatCode AND c.IsDeleted = 0
WHERE NOT EXISTS (SELECT 1 FROM Subject x WHERE x.Code = s.Code AND x.IsDeleted = 0);

PRINT N'2) Đã thêm ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' môn học.';


/* =====================================================================
   3) 100 GIÁO VIÊN
   ---------------------------------------------------------------------
   Nạp toàn bộ hồ sơ vào bảng tạm #GV rồi mới INSERT sang các bảng thật
   bằng JOIN theo Username — cách này giữ đúng quy tắc "không hard-code
   Id tự tăng" mà không phải khai 100 biến.

   Ý nghĩa các cột: xem chú thích ở bảng Teacher / Contract trong
   database/schema/TSDMS_Schema.sql.
   ===================================================================== */
CREATE TABLE #GV (
    Username   VARCHAR(50)   COLLATE DATABASE_DEFAULT PRIMARY KEY,
    Email      VARCHAR(100)  COLLATE DATABASE_DEFAULT NOT NULL,
    UserStatus VARCHAR(20)   COLLATE DATABASE_DEFAULT NOT NULL,  -- ACTIVE / INACTIVE / LOCKED (bám theo Status hồ sơ)
    LastName   NVARCHAR(100) COLLATE DATABASE_DEFAULT NOT NULL,  -- họ + tên đệm
    FirstName  NVARCHAR(50)  COLLATE DATABASE_DEFAULT NOT NULL,  -- tên gọi
    Dob        DATE, Gender BIT,
    IdCard     VARCHAR(20)   COLLATE DATABASE_DEFAULT,
    Phone      VARCHAR(20)   COLLATE DATABASE_DEFAULT,
    Addr       NVARCHAR(255) COLLATE DATABASE_DEFAULT,
    HireDate   DATE,
    EmpType    VARCHAR(20)   COLLATE DATABASE_DEFAULT,           -- CO_HUU / THINH_GIANG
    Status     VARCHAR(20)   COLLATE DATABASE_DEFAULT,           -- ACTIVE / RETIRED / SUSPENDED
    Experience NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    ContractNo VARCHAR(50)   COLLATE DATABASE_DEFAULT,
    CStart DATE, CEnd DATE,
    CBase DECIMAL(18,2), CAllow DECIMAL(18,2),
    CStatus    VARCHAR(20)   COLLATE DATABASE_DEFAULT
);

INSERT INTO #GV (Username, Email, UserStatus, LastName, FirstName, Dob, Gender, IdCard, Phone, Addr,
                 HireDate, EmpType, Status, Experience, ContractNo, CStart, CEnd, CBase, CAllow, CStatus) VALUES
 ('gv.hothanhhuong', 'gv.hothanhhuong@tsdms.local', 'LOCKED', N'Hồ Thanh', N'Hương', '1983-03-12', 0, '001183583318', '0903801298', N'Số 155 phố Nguyễn Đức Cảnh, phường Lê Chân, Hải Phòng', '2018-01-25', 'THINH_GIANG', 'SUSPENDED', N'22 năm giảng dạy Lập trình Scratch và Nhập môn Trí tuệ nhân tạo cho học sinh tiểu học và THCS, trong đó 9 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV001', '2026-05-01', '2027-05-01', 6200000, 700000, 'ACTIVE'),
 ('gv.hoangphuongnhi', 'gv.hoangphuongnhi@tsdms.local', 'ACTIVE', N'Hoàng Phương', N'Nhi', '1980-09-23', 0, '042180758852', '0937870752', N'Số 13 phố Máy Tơ, phường Ngô Quyền, Hải Phòng', '2026-03-23', 'CO_HUU', 'ACTIVE', N'26 năm giảng dạy Lập trình robot Leanbot và Lập trình robot Spike Essential cho học sinh tiểu học; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2026-GV002', '2026-03-23', '2028-03-23', 13800000, 500000, 'ACTIVE'),
 ('gv.taphuhuy', 'gv.taphuhuy@tsdms.local', 'ACTIVE', N'Tạ Phú', N'Huy', '1987-01-20', 1, '022087177210', '0965379115', N'Số 73 phố Bạch Đằng, phường Hồng Bàng, Hải Phòng', '2023-06-10', 'THINH_GIANG', 'ACTIVE', N'17 năm giảng dạy Tiếng Anh làm quen (Lớp 1-2) và Ngữ âm & Phonics Tiểu học cho học sinh tiểu học, trong đó 3 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2025-GV003', '2025-12-01', '2026-12-01', 6500000, 500000, 'ACTIVE'),
 ('gv.quachnhatha', 'gv.quachnhatha@tsdms.local', 'INACTIVE', N'Quách Nhật', N'Hà', '1980-04-23', 0, '033180012945', '0340250877', N'Số 263 phố Lê Thánh Tông, phường Ngô Quyền, Hải Phòng', '2021-04-21', 'CO_HUU', 'RETIRED', N'24 năm giảng dạy Tiếng Anh Tiểu học 3-5 - Global Success cho học sinh tiểu học, trong đó 5 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2021-GV004', '2021-04-21', '2024-07-17', 14800000, 1300000, 'TERMINATED'),
 ('gv.homanhvinh', 'gv.homanhvinh@tsdms.local', 'INACTIVE', N'Hồ Mạnh', N'Vinh', '1992-02-01', 1, '042092615773', '0334409040', N'Số 258 phố Cầu Đất, phường Ngô Quyền, Hải Phòng', '2024-06-10', 'CO_HUU', 'RETIRED', N'14 năm giảng dạy Tiếng Anh Tiểu học 3-5 - i-Learn Smart Start và Tiếng Anh Tiểu học 3-5 - Global Success cho học sinh tiểu học, trong đó 2 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2024-GV005', '2024-06-10', '2026-03-07', 13800000, 800000, 'TERMINATED'),
 ('gv.trankimthao', 'gv.trankimthao@tsdms.local', 'ACTIVE', N'Trần Kim', N'Thảo', '1990-10-14', 0, '019190595192', '0325793243', N'Số 217 ngõ 100 phố Minh Khai, phường Hồng Bàng, Hải Phòng', '2019-03-14', 'CO_HUU', 'ACTIVE', N'15 năm giảng dạy Lập trình robot Vincibot và Lập trình robot Leanbot cho học sinh tiểu học và THCS, trong đó 7 năm tại trung tâm; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2019-GV006', '2019-03-14', NULL, 14100000, 1600000, 'ACTIVE'),
 ('gv.luuthedat', 'gv.luuthedat@tsdms.local', 'ACTIVE', N'Lưu Thế', N'Đạt', '1986-11-28', 1, '037086210643', '0869923920', N'Số 108 phố Nguyễn Công Trứ, phường Lê Chân, Hải Phòng', '2019-10-05', 'CO_HUU', 'ACTIVE', N'18 năm giảng dạy Tin học cơ bản (GDPT 2018) và Bảng tính Excel cho học sinh cho học sinh tiểu học và THCS, trong đó 7 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2019-GV007', '2019-10-05', NULL, 14800000, 1600000, 'ACTIVE'),
 ('gv.nguyenvantuyet', 'gv.nguyenvantuyet@tsdms.local', 'ACTIVE', N'Nguyễn Vân', N'Tuyết', '1980-01-06', 0, '034180037433', '0940165798', N'Số 14 ngõ 23 phố Hạ Lý, phường Hồng Bàng, Hải Phòng', '2018-05-28', 'THINH_GIANG', 'ACTIVE', N'22 năm giảng dạy Tiếng Anh THCS 6-9 - Global Success cho học sinh THCS, trong đó 8 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2025-GV008', '2025-09-01', '2026-09-01', 7000000, 700000, 'ACTIVE'),
 ('gv.buihaihang', 'gv.buihaihang@tsdms.local', 'ACTIVE', N'Bùi Hải', N'Hằng', '1992-06-24', 0, '019192021317', '0835763102', N'Số 62 ngõ 158 phố Lạch Tray, phường Ngô Quyền, Hải Phòng', '2020-08-05', 'THINH_GIANG', 'ACTIVE', N'12 năm giảng dạy Lập trình robot Spike Essential cho học sinh tiểu học, trong đó 6 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV009', '2026-06-01', '2027-06-01', 6500000, 600000, 'ACTIVE'),
 ('gv.lammaily', 'gv.lammaily@tsdms.local', 'ACTIVE', N'Lâm Mai', N'Ly', '1996-10-22', 0, '027196320700', '0943945243', N'Số 211 ngõ 60 phố Phú Xá, phường Hải An, Hải Phòng', '2026-05-15', 'CO_HUU', 'ACTIVE', N'8 năm giảng dạy Lập trình robot RoboSim cho học sinh THCS; từng phụ trách câu lạc bộ chế tác - Tinkering tại trường liên kết.', 'HD-2026-GV010', '2026-05-15', '2028-05-15', 13200000, 500000, 'ACTIVE'),
 ('gv.vuhuuphong', 'gv.vuhuuphong@tsdms.local', 'ACTIVE', N'Vũ Hữu', N'Phong', '1979-03-20', 1, '037079817776', '0994103090', N'Số 212 phố Bạch Đằng, phường Hồng Bàng, Hải Phòng', '2020-04-25', 'CO_HUU', 'ACTIVE', N'26 năm giảng dạy Tinkering và Nhập môn Trí tuệ nhân tạo cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2020-GV011', '2020-04-25', NULL, 14100000, 1400000, 'ACTIVE'),
 ('gv.dangthanhtien', 'gv.dangthanhtien@tsdms.local', 'ACTIVE', N'Đặng Thanh', N'Tiến', '1983-08-23', 1, '037083917860', '0969668320', N'Số 80 phố Quang Trung, phường Hồng Bàng, Hải Phòng', '2019-07-21', 'THINH_GIANG', 'ACTIVE', N'22 năm giảng dạy Tiếng Anh THCS 6-9 - i-Learn Smart World và Tiếng Anh Tiểu học 3-5 - i-Learn Smart Start cho học sinh tiểu học và THCS, trong đó 7 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2025-GV012', '2025-12-01', '2026-12-01', 6200000, 700000, 'ACTIVE'),
 ('gv.tobaomai', 'gv.tobaomai@tsdms.local', 'ACTIVE', N'Tô Bảo', N'Mai', '1985-01-09', 0, '038185441138', '0970784406', N'Số 17 ngõ 102 phố Nguyễn Lương Bằng, phường Kiến An, Hải Phòng', '2022-03-20', 'CO_HUU', 'ACTIVE', N'17 năm giảng dạy Kĩ năng giao tiếp & thuyết trình và Kĩ năng quản lý thời gian & tài chính cá nhân cho học sinh tiểu học và THCS, trong đó 4 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2022-GV013', '2022-03-20', NULL, 16300000, 1100000, 'ACTIVE'),
 ('gv.ngotantrung', 'gv.ngotantrung@tsdms.local', 'ACTIVE', N'Ngô Tấn', N'Trung', '1994-11-23', 1, '038094175062', '0989882848', N'Số 90 phố Dư Hàng, phường Lê Chân, Hải Phòng', '2024-02-15', 'CO_HUU', 'ACTIVE', N'10 năm giảng dạy Ngữ âm & Phonics Tiểu học cho học sinh tiểu học, trong đó 3 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2024-GV014', '2024-02-15', NULL, 14000000, 1000000, 'ACTIVE'),
 ('gv.phankimdiep', 'gv.phankimdiep@tsdms.local', 'ACTIVE', N'Phan Kim', N'Diệp', '1982-12-25', 0, '019182498891', '0703991084', N'Số 182 ngõ 131 phố Chợ Hàng, phường Lê Chân, Hải Phòng', '2021-04-21', 'CO_HUU', 'ACTIVE', N'23 năm giảng dạy Nhập môn Trí tuệ nhân tạo và Lập trình robot Spike Essential cho học sinh tiểu học và THCS, trong đó 5 năm tại trung tâm; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2021-GV015', '2021-04-21', NULL, 14100000, 1300000, 'ACTIVE'),
 ('gv.tobichdung', 'gv.tobichdung@tsdms.local', 'ACTIVE', N'Tô Bích', N'Dung', '1992-07-27', 0, '034192483767', '0902002095', N'Số 171 phố Bùi Viện, phường Hải An, Hải Phòng', '2023-04-07', 'THINH_GIANG', 'ACTIVE', N'12 năm giảng dạy Lập trình Scratch và Lập trình robot Vincibot cho học sinh tiểu học và THCS, trong đó 3 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV016', '2026-07-01', '2027-07-01', 6500000, 500000, 'ACTIVE'),
 ('gv.vohaihuyen', 'gv.vohaihuyen@tsdms.local', 'ACTIVE', N'Võ Hải', N'Huyền', '1990-02-14', 0, '040190276316', '0968147862', N'Số 211 ngõ 102 phố Nguyễn Tri Phương, phường Hồng Bàng, Hải Phòng', '2018-11-11', 'THINH_GIANG', 'ACTIVE', N'12 năm giảng dạy Lập trình robot Leanbot cho học sinh tiểu học, trong đó 8 năm tại trung tâm; từng phụ trách câu lạc bộ chế tác - Tinkering tại trường liên kết.', 'HD-2026-GV017', '2026-07-01', '2027-07-01', 7000000, 700000, 'ACTIVE'),
 ('gv.caokhanhoanh', 'gv.caokhanhoanh@tsdms.local', 'ACTIVE', N'Cao Khánh', N'Oanh', '1983-03-05', 0, '030183878308', '0810328971', N'Số 8 ngõ 148 phố Máy Tơ, phường Ngô Quyền, Hải Phòng', '2021-11-04', 'THINH_GIANG', 'ACTIVE', N'22 năm giảng dạy Lập trình robot Vincibot và Lập trình Scratch cho học sinh tiểu học và THCS, trong đó 5 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2025-GV018', '2025-11-01', '2026-11-01', 6200000, 600000, 'ACTIVE'),
 ('gv.hakimha', 'gv.hakimha@tsdms.local', 'ACTIVE', N'Hà Kim', N'Hà', '1982-04-26', 0, '038182573656', '0983150293', N'Số 127 ngõ 137 phố Trần Phú, phường Ngô Quyền, Hải Phòng', '2023-10-26', 'THINH_GIANG', 'ACTIVE', N'22 năm giảng dạy Công dân số cơ bản và An toàn trên không gian mạng cho học sinh tiểu học và THCS, trong đó 3 năm tại trung tâm; từng phụ trách câu lạc bộ công dân số tại trường liên kết.', 'HD-2026-GV019', '2026-07-01', '2027-07-01', 6500000, 500000, 'ACTIVE'),
 ('gv.chuvietnghia', 'gv.chuvietnghia@tsdms.local', 'LOCKED', N'Chu Việt', N'Nghĩa', '1983-02-20', 1, '042083803443', '0334561161', N'Số 175 phố Trần Thành Ngọ, phường Kiến An, Hải Phòng', '2019-05-25', 'CO_HUU', 'SUSPENDED', N'21 năm giảng dạy Tiếng Anh làm quen (Lớp 1-2) và Kĩ năng giao tiếp & thuyết trình cho học sinh tiểu học và THCS, trong đó 7 năm tại trung tâm; từng phụ trách câu lạc bộ ngữ âm - phát âm tại trường liên kết.', 'HD-2019-GV020', '2019-05-25', NULL, 14800000, 1600000, 'ACTIVE'),
 ('gv.doanthuylinh', 'gv.doanthuylinh@tsdms.local', 'ACTIVE', N'Đoàn Thúy', N'Linh', '1998-02-21', 0, '031198788569', '0838646699', N'Số 33 ngõ 113 phố Văn Cao, phường Ngô Quyền, Hải Phòng', '2020-08-13', 'CO_HUU', 'ACTIVE', N'6 năm giảng dạy Kĩ năng làm việc nhóm & lãnh đạo cho học sinh tiểu học và THCS; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2020-GV021', '2020-08-13', NULL, 12400000, 1400000, 'ACTIVE'),
 ('gv.vuthanhtrang', 'gv.vuthanhtrang@tsdms.local', 'ACTIVE', N'Vũ Thanh', N'Trang', '1994-08-26', 0, '027194085424', '0831836223', N'Số 87 ngõ 164 phố Hoàng Văn Thụ, phường Hồng Bàng, Hải Phòng', '2023-12-21', 'CO_HUU', 'ACTIVE', N'8 năm giảng dạy Lập trình robot Spike Essential và Tinkering cho học sinh tiểu học, trong đó 3 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2023-GV022', '2023-12-21', NULL, 14700000, 1000000, 'ACTIVE'),
 ('gv.vuongmaitrinh', 'gv.vuongmaitrinh@tsdms.local', 'ACTIVE', N'Vương Mai', N'Trinh', '1978-02-25', 0, '025178862772', '0972104903', N'Số 181 ngõ 139 phố Lạch Tray, phường Ngô Quyền, Hải Phòng', '2026-02-05', 'CO_HUU', 'ACTIVE', N'26 năm giảng dạy Soạn thảo văn bản & Trình chiếu và Tin học cơ bản (GDPT 2018) cho học sinh tiểu học và THCS, trong đó 1 năm tại trung tâm; từng phụ trách câu lạc bộ kĩ năng văn phòng tại trường liên kết.', 'HD-2026-GV023', '2026-02-05', '2028-02-05', 14800000, 700000, 'ACTIVE'),
 ('gv.dangmanhhai', 'gv.dangmanhhai@tsdms.local', 'ACTIVE', N'Đặng Mạnh', N'Hải', '1981-08-09', 1, '038081513532', '0841501886', N'Số 30 ngõ 50 phố Hồ Sen, phường Lê Chân, Hải Phòng', '2019-05-17', 'CO_HUU', 'ACTIVE', N'23 năm giảng dạy Lập trình robot RoboSim và Khai thác AI an toàn cho học sinh cho học sinh THCS, trong đó 7 năm tại trung tâm; từng phụ trách câu lạc bộ chế tác - Tinkering tại trường liên kết.', 'HD-2019-GV024', '2019-05-17', NULL, 14800000, 1600000, 'ACTIVE'),
 ('gv.lethutrang', 'gv.lethutrang@tsdms.local', 'ACTIVE', N'Lê Thu', N'Trang', '1989-03-21', 0, '022189298085', '0865583670', N'Số 236 phố Mê Linh, phường Lê Chân, Hải Phòng', '2021-02-08', 'CO_HUU', 'ACTIVE', N'15 năm giảng dạy Tinkering và Lập trình robot Vincibot cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2021-GV025', '2021-02-08', NULL, 14800000, 1400000, 'ACTIVE'),
 ('gv.tangochong', 'gv.tangochong@tsdms.local', 'ACTIVE', N'Tạ Ngọc', N'Hồng', '1996-01-09', 0, '036196445542', '0842636286', N'Số 214 phố Nguyễn Bỉnh Khiêm, phường Hải An, Hải Phòng', '2024-03-12', 'CO_HUU', 'ACTIVE', N'8 năm giảng dạy Tiếng Anh Tiểu học 3-5 - Global Success và Tiếng Anh Tiểu học 3-5 - i-Learn Smart Start cho học sinh tiểu học, trong đó 2 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2024-GV026', '2024-03-12', NULL, 13200000, 800000, 'ACTIVE'),
 ('gv.buiyentuyet', 'gv.buiyentuyet@tsdms.local', 'ACTIVE', N'Bùi Yến', N'Tuyết', '1985-03-21', 0, '040185286586', '0986012791', N'Số 202 phố Trần Nguyên Hãn, phường Lê Chân, Hải Phòng', '2020-07-08', 'CO_HUU', 'ACTIVE', N'19 năm giảng dạy Bảng tính Excel cho học sinh và An toàn trên không gian mạng cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; từng phụ trách câu lạc bộ Tin học ứng dụng tại trường liên kết.', 'HD-2020-GV027', '2020-07-08', NULL, 14800000, 1400000, 'ACTIVE'),
 ('gv.doanthuynhung', 'gv.doanthuynhung@tsdms.local', 'ACTIVE', N'Đoàn Thúy', N'Nhung', '1978-12-21', 0, '042178761189', '0916068713', N'Số 17 phố Chợ Hàng, phường Lê Chân, Hải Phòng', '2020-06-10', 'THINH_GIANG', 'ACTIVE', N'26 năm giảng dạy Nhập môn Trí tuệ nhân tạo và Lập trình robot RoboSim cho học sinh THCS, trong đó 6 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2026-GV028', '2026-04-01', '2027-04-01', 6500000, 600000, 'ACTIVE'),
 ('gv.tranviettu', 'gv.tranviettu@tsdms.local', 'ACTIVE', N'Trần Việt', N'Tú', '1990-05-09', 1, '030090567485', '0940583601', N'Số 132 ngõ 90 phố Nguyễn Đức Cảnh, phường Lê Chân, Hải Phòng', '2020-04-21', 'CO_HUU', 'ACTIVE', N'14 năm giảng dạy Tin học cơ bản (GDPT 2018) và Bảng tính Excel cho học sinh cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; từng phụ trách câu lạc bộ Tin học nhà trường tại trường liên kết.', 'HD-2020-GV029', '2020-04-21', NULL, 14800000, 1400000, 'ACTIVE'),
 ('gv.hangochien', 'gv.hangochien@tsdms.local', 'ACTIVE', N'Hà Ngọc', N'Hiền', '1985-09-26', 0, '025185501683', '0326207331', N'Số 256 ngõ 94 phố Hoàng Quốc Việt, phường Kiến An, Hải Phòng', '2023-01-16', 'CO_HUU', 'ACTIVE', N'21 năm giảng dạy Lập trình Scratch cho học sinh tiểu học, trong đó 4 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2023-GV030', '2023-01-16', NULL, 13800000, 1100000, 'ACTIVE'),
 ('gv.vuongtanminh', 'gv.vuongtanminh@tsdms.local', 'ACTIVE', N'Vương Tấn', N'Minh', '1987-05-17', 1, '033087254304', '0918769612', N'Số 219 phố Điện Biên Phủ, phường Hồng Bàng, Hải Phòng', '2026-01-01', 'THINH_GIANG', 'ACTIVE', N'18 năm giảng dạy Lập trình robot Leanbot cho học sinh tiểu học, trong đó 1 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2026-GV031', '2026-07-01', '2027-07-01', 6200000, 400000, 'ACTIVE'),
 ('gv.buithiha', 'gv.buithiha@tsdms.local', 'INACTIVE', N'Bùi Thị', N'Hà', '1988-03-18', 0, '042188134366', '0982934946', N'Số 245 phố Nguyễn Lương Bằng, phường Kiến An, Hải Phòng', '2022-03-09', 'CO_HUU', 'RETIRED', N'17 năm giảng dạy Kĩ năng tự bảo vệ & phòng chống xâm hại và Kĩ năng quản lý thời gian & tài chính cá nhân cho học sinh tiểu học và THCS, trong đó 4 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2022-GV032', '2022-03-09', '2024-02-15', 14100000, 1100000, 'TERMINATED'),
 ('gv.quachtuyethien', 'gv.quachtuyethien@tsdms.local', 'ACTIVE', N'Quách Tuyết', N'Hiền', '1997-02-13', 0, '031197144659', '0919430426', N'Số 167 phố Điện Biên Phủ, phường Hồng Bàng, Hải Phòng', '2026-05-19', 'THINH_GIANG', 'ACTIVE', N'5 năm giảng dạy Kĩ năng quản lý thời gian & tài chính cá nhân cho học sinh THCS; từng phụ trách câu lạc bộ giáo dục giá trị sống tại trường liên kết.', 'HD-2026-GV033', '2026-05-19', '2027-05-19', 6300000, 300000, 'ACTIVE'),
 ('gv.tranhoaimy', 'gv.tranhoaimy@tsdms.local', 'ACTIVE', N'Trần Hoài', N'My', '1995-01-28', 0, '034195441238', '0821416548', N'Số 262 phố Cầu Đất, phường Ngô Quyền, Hải Phòng', '2024-06-28', 'THINH_GIANG', 'ACTIVE', N'9 năm giảng dạy Tiếng Anh Tiểu học 3-5 - i-Learn Smart Start và Tiếng Anh THCS 6-9 - Global Success cho học sinh tiểu học và THCS, trong đó 2 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2025-GV034', '2025-10-01', '2026-10-01', 6300000, 400000, 'ACTIVE'),
 ('gv.phamdieugiang', 'gv.phamdieugiang@tsdms.local', 'ACTIVE', N'Phạm Diệu', N'Giang', '1983-04-18', 0, '037183405616', '0349681264', N'Số 48 phố Trần Thành Ngọ, phường Kiến An, Hải Phòng', '2020-02-19', 'THINH_GIANG', 'ACTIVE', N'19 năm giảng dạy Tiếng Anh THCS 6-9 - Global Success cho học sinh THCS, trong đó 6 năm tại trung tâm; từng phụ trách câu lạc bộ ngữ âm - phát âm tại trường liên kết.', 'HD-2025-GV035', '2025-12-01', '2026-12-01', 7000000, 600000, 'ACTIVE'),
 ('gv.nghiemquocnam', 'gv.nghiemquocnam@tsdms.local', 'INACTIVE', N'Nghiêm Quốc', N'Nam', '1983-12-24', 1, '030083896057', '0860436746', N'Số 100 phố Nguyễn Đức Cảnh, phường Lê Chân, Hải Phòng', '2019-02-19', 'THINH_GIANG', 'RETIRED', N'21 năm giảng dạy Kĩ năng giao tiếp & thuyết trình và Kĩ năng tự bảo vệ & phòng chống xâm hại cho học sinh tiểu học và THCS, trong đó 7 năm tại trung tâm; từng phụ trách câu lạc bộ hoạt động trải nghiệm tại trường liên kết.', 'HD-2019-GV036', '2019-02-19', '2025-06-25', 6500000, 700000, 'TERMINATED'),
 ('gv.phamthuynga', 'gv.phamthuynga@tsdms.local', 'ACTIVE', N'Phạm Thúy', N'Nga', '1989-07-23', 0, '025189821443', '0887263098', N'Số 147 phố Đà Nẵng, phường Ngô Quyền, Hải Phòng', '2020-07-21', 'CO_HUU', 'ACTIVE', N'15 năm giảng dạy Soạn thảo văn bản & Trình chiếu và Tin học cơ bản (GDPT 2018) cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; từng phụ trách câu lạc bộ Tin học ứng dụng tại trường liên kết.', 'HD-2020-GV037', '2020-07-21', NULL, 14800000, 1400000, 'ACTIVE'),
 ('gv.dinhngocnhung', 'gv.dinhngocnhung@tsdms.local', 'ACTIVE', N'Đinh Ngọc', N'Nhung', '1981-06-25', 0, '019181487025', '0936219884', N'Số 64 phố Cát Bi, phường Hải An, Hải Phòng', '2023-07-07', 'CO_HUU', 'ACTIVE', N'23 năm giảng dạy Lập trình robot Vincibot và Nhập môn Trí tuệ nhân tạo cho học sinh tiểu học và THCS, trong đó 3 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2023-GV038', '2023-07-07', NULL, 14800000, 1000000, 'ACTIVE'),
 ('gv.doyenan', 'gv.doyenan@tsdms.local', 'ACTIVE', N'Đỗ Yến', N'An', '1993-06-27', 0, '026193083262', '0823922547', N'Số 10 ngõ 123 phố Chợ Hàng, phường Lê Chân, Hải Phòng', '2026-04-05', 'CO_HUU', 'ACTIVE', N'11 năm giảng dạy Bảng tính Excel cho học sinh và Soạn thảo văn bản & Trình chiếu cho học sinh tiểu học và THCS; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV039', '2026-04-05', '2028-04-05', 14400000, 500000, 'ACTIVE'),
 ('gv.doanphuonggiang', 'gv.doanphuonggiang@tsdms.local', 'ACTIVE', N'Đoàn Phương', N'Giang', '1984-11-09', 0, '036184671122', '0329290450', N'Số 8 ngõ 144 phố Phan Đăng Lưu, phường Kiến An, Hải Phòng', '2024-08-15', 'CO_HUU', 'ACTIVE', N'20 năm giảng dạy Tin học cơ bản (GDPT 2018) và Soạn thảo văn bản & Trình chiếu cho học sinh tiểu học và THCS, trong đó 2 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2024-GV040', '2024-08-15', NULL, 14800000, 800000, 'ACTIVE'),
 ('gv.phamdinhthang', 'gv.phamdinhthang@tsdms.local', 'LOCKED', N'Phạm Đình', N'Thắng', '1999-06-22', 1, '036099091978', '0381279766', N'Số 157 ngõ 121 phố Máy Tơ, phường Ngô Quyền, Hải Phòng', '2023-12-08', 'CO_HUU', 'SUSPENDED', N'6 năm giảng dạy Tiếng Anh THCS 6-9 - i-Learn Smart World cho học sinh THCS, trong đó 3 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2023-GV041', '2023-12-08', NULL, 11700000, 1000000, 'ACTIVE'),
 ('gv.tatienvu', 'gv.tatienvu@tsdms.local', 'ACTIVE', N'Tạ Tiến', N'Vũ', '1997-02-28', 1, '036097183939', '0775033918', N'Số 262 phố Minh Khai, phường Hồng Bàng, Hải Phòng', '2026-01-16', 'CO_HUU', 'ACTIVE', N'5 năm giảng dạy Soạn thảo văn bản & Trình chiếu và Tin học cơ bản (GDPT 2018) cho học sinh tiểu học và THCS, trong đó 1 năm tại trung tâm; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2026-GV042', '2026-01-16', '2028-01-16', 13500000, 700000, 'ACTIVE'),
 ('gv.luuvanmai', 'gv.luuvanmai@tsdms.local', 'ACTIVE', N'Lưu Vân', N'Mai', '1996-09-24', 0, '034196590593', '0811454051', N'Số 168 ngõ 40 phố Lạch Tray, phường Ngô Quyền, Hải Phòng', '2021-08-14', 'THINH_GIANG', 'ACTIVE', N'10 năm giảng dạy Ngữ âm & Phonics Tiểu học và Tiếng Anh THCS 6-9 - i-Learn Smart World cho học sinh tiểu học và THCS, trong đó 5 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2026-GV043', '2026-04-01', '2027-04-01', 6000000, 600000, 'ACTIVE'),
 ('gv.nguyenducgiang', 'gv.nguyenducgiang@tsdms.local', 'ACTIVE', N'Nguyễn Đức', N'Giang', '1984-04-18', 1, '033084186494', '0357892611', N'Số 83 ngõ 57 phố Tam Bạc, phường Hồng Bàng, Hải Phòng', '2024-09-10', 'THINH_GIANG', 'ACTIVE', N'18 năm giảng dạy Lập trình robot Spike Essential và Tinkering cho học sinh tiểu học và THCS, trong đó 2 năm tại trung tâm; từng phụ trách câu lạc bộ chế tác - Tinkering tại trường liên kết.', 'HD-2025-GV044', '2025-10-01', '2026-10-01', 7000000, 400000, 'ACTIVE'),
 ('gv.buingochai', 'gv.buingochai@tsdms.local', 'ACTIVE', N'Bùi Ngọc', N'Hải', '1992-11-21', 1, '022092410627', '0346014475', N'Số 41 phố Đông Khê, phường Ngô Quyền, Hải Phòng', '2022-06-09', 'THINH_GIANG', 'ACTIVE', N'12 năm giảng dạy Tiếng Anh làm quen (Lớp 1-2) cho học sinh tiểu học, trong đó 4 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2025-GV045', '2025-09-01', '2026-09-01', 6500000, 500000, 'ACTIVE'),
 ('gv.vuthanhha', 'gv.vuthanhha@tsdms.local', 'ACTIVE', N'Vũ Thanh', N'Hà', '1996-03-12', 0, '035196116114', '0381205374', N'Số 203 phố Trần Nhân Tông, phường Kiến An, Hải Phòng', '2018-01-25', 'THINH_GIANG', 'ACTIVE', N'8 năm giảng dạy Lập trình robot RoboSim và Lập trình robot Spike Essential cho học sinh tiểu học và THCS; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2026-GV046', '2026-01-01', '2027-01-01', 6100000, 700000, 'ACTIVE'),
 ('gv.lelandung', 'gv.lelandung@tsdms.local', 'ACTIVE', N'Lê Lan', N'Dung', '1985-10-20', 0, '019185860914', '0885120444', N'Số 111 ngõ 12 phố Mê Linh, phường Lê Chân, Hải Phòng', '2026-03-09', 'THINH_GIANG', 'ACTIVE', N'19 năm giảng dạy Tinkering và Lập trình robot Spike Essential cho học sinh tiểu học và THCS; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV047', '2026-07-01', '2027-07-01', 6500000, 300000, 'ACTIVE'),
 ('gv.doanduckhai', 'gv.doanduckhai@tsdms.local', 'ACTIVE', N'Đoàn Đức', N'Khải', '1980-01-04', 1, '036080223603', '0333751548', N'Số 179 phố Hoàng Quốc Việt, phường Kiến An, Hải Phòng', '2025-09-15', 'THINH_GIANG', 'ACTIVE', N'24 năm giảng dạy Kĩ năng làm việc nhóm & lãnh đạo và Kĩ năng giao tiếp & thuyết trình cho học sinh tiểu học và THCS, trong đó 1 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2025-GV048', '2025-12-01', '2026-12-01', 6500000, 400000, 'ACTIVE'),
 ('gv.hothinhi', 'gv.hothinhi@tsdms.local', 'ACTIVE', N'Hồ Thị', N'Nhi', '1983-11-18', 0, '038183628257', '0387803705', N'Số 177 phố Đằng Hải, phường Hải An, Hải Phòng', '2018-11-28', 'CO_HUU', 'ACTIVE', N'22 năm giảng dạy Nhập môn Trí tuệ nhân tạo cho học sinh THCS, trong đó 8 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2018-GV049', '2018-11-28', NULL, 14100000, 1700000, 'ACTIVE'),
 ('gv.vothuythuy', 'gv.vothuythuy@tsdms.local', 'ACTIVE', N'Võ Thúy', N'Thủy', '1982-08-06', 0, '031182009280', '0785285126', N'Số 60 ngõ 168 phố Ngô Gia Tự, phường Hải An, Hải Phòng', '2021-01-19', 'CO_HUU', 'ACTIVE', N'24 năm giảng dạy An toàn trên không gian mạng và Công dân số cơ bản cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2021-GV050', '2021-01-19', NULL, 13800000, 1400000, 'ACTIVE'),
 ('gv.luuthunga', 'gv.luuthunga@tsdms.local', 'ACTIVE', N'Lưu Thu', N'Nga', '1981-08-25', 0, '038181363021', '0825822703', N'Số 20 phố Cát Bi, phường Hải An, Hải Phòng', '2019-01-07', 'CO_HUU', 'ACTIVE', N'23 năm giảng dạy Kĩ năng tự bảo vệ & phòng chống xâm hại và Kĩ năng quản lý thời gian & tài chính cá nhân cho học sinh tiểu học và THCS, trong đó 8 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2019-GV051', '2019-01-07', NULL, 14800000, 1700000, 'ACTIVE'),
 ('gv.buikimthu', 'gv.buikimthu@tsdms.local', 'ACTIVE', N'Bùi Kim', N'Thu', '1989-05-27', 0, '035189368306', '0782144471', N'Số 127 phố Lạch Tray, phường Ngô Quyền, Hải Phòng', '2021-04-02', 'THINH_GIANG', 'ACTIVE', N'15 năm giảng dạy Lập trình Scratch và Khai thác AI an toàn cho học sinh cho học sinh tiểu học và THCS, trong đó 5 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2026-GV052', '2026-06-01', '2027-06-01', 6500000, 600000, 'ACTIVE'),
 ('gv.lethuytuyet', 'gv.lethuytuyet@tsdms.local', 'ACTIVE', N'Lê Thúy', N'Tuyết', '1996-08-05', 0, '026196084057', '0862380939', N'Số 53 phố Cát Bi, phường Hải An, Hải Phòng', '2019-01-23', 'CO_HUU', 'ACTIVE', N'9 năm giảng dạy Tiếng Anh Tiểu học 3-5 - Global Success và Kĩ năng giao tiếp & thuyết trình cho học sinh tiểu học và THCS, trong đó 8 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2019-GV053', '2019-01-23', NULL, 12900000, 1700000, 'ACTIVE'),
 ('gv.nghiemkhanhdung', 'gv.nghiemkhanhdung@tsdms.local', 'ACTIVE', N'Nghiêm Khánh', N'Dung', '2000-12-20', 0, '034300724129', '0357552942', N'Số 75 phố Hạ Lý, phường Hồng Bàng, Hải Phòng', '2022-08-14', 'THINH_GIANG', 'ACTIVE', N'4 năm giảng dạy Kĩ năng quản lý thời gian & tài chính cá nhân cho học sinh THCS; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2026-GV054', '2026-02-01', '2027-02-01', 5300000, 500000, 'ACTIVE'),
 ('gv.ngoquocviet', 'gv.ngoquocviet@tsdms.local', 'INACTIVE', N'Ngô Quốc', N'Việt', '1999-03-02', 1, '019099912154', '0790917177', N'Số 3 ngõ 163 phố Bạch Đằng, phường Hồng Bàng, Hải Phòng', '2021-04-28', 'CO_HUU', 'RETIRED', N'5 năm giảng dạy Lập trình robot Leanbot cho học sinh tiểu học; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2021-GV055', '2021-04-28', '2026-05-20', 12000000, 1300000, 'TERMINATED'),
 ('gv.tranxuanvinh', 'gv.tranxuanvinh@tsdms.local', 'ACTIVE', N'Trần Xuân', N'Vinh', '1990-09-08', 1, '030090043528', '0386398593', N'Số 97 phố Quang Trung, phường Hồng Bàng, Hải Phòng', '2026-04-16', 'THINH_GIANG', 'ACTIVE', N'12 năm giảng dạy Lập trình robot Vincibot và Lập trình Scratch cho học sinh tiểu học và THCS; từng phụ trách câu lạc bộ STEM - Robotics tại trường liên kết.', 'HD-2026-GV056', '2026-04-16', '2027-04-16', 7000000, 300000, 'ACTIVE'),
 ('gv.phamthanhdung', 'gv.phamthanhdung@tsdms.local', 'ACTIVE', N'Phạm Thanh', N'Dũng', '1997-02-19', 1, '042097130811', '0859977463', N'Số 19 phố Ngô Gia Tự, phường Hải An, Hải Phòng', '2021-02-18', 'THINH_GIANG', 'ACTIVE', N'5 năm giảng dạy Lập trình robot Spike Essential và Lập trình robot Vincibot cho học sinh tiểu học và THCS; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2026-GV057', '2026-03-01', '2027-03-01', 6300000, 600000, 'ACTIVE'),
 ('gv.duongphumanh', 'gv.duongphumanh@tsdms.local', 'ACTIVE', N'Dương Phú', N'Mạnh', '1993-03-19', 1, '042093134069', '0842989880', N'Số 17 phố Trường Chinh, phường Kiến An, Hải Phòng', '2019-04-28', 'THINH_GIANG', 'ACTIVE', N'11 năm giảng dạy Tiếng Anh Tiểu học 3-5 - i-Learn Smart Start cho học sinh tiểu học, trong đó 7 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2025-GV058', '2025-10-01', '2026-10-01', 6500000, 700000, 'ACTIVE'),
 ('gv.duongthihang', 'gv.duongthihang@tsdms.local', 'ACTIVE', N'Dương Thị', N'Hằng', '1990-02-27', 0, '026190937533', '0350566455', N'Số 42 ngõ 184 phố Đằng Hải, phường Hải An, Hải Phòng', '2021-10-23', 'THINH_GIANG', 'ACTIVE', N'12 năm giảng dạy Kĩ năng giao tiếp & thuyết trình và Kĩ năng làm việc nhóm & lãnh đạo cho học sinh tiểu học và THCS, trong đó 5 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2025-GV059', '2025-11-01', '2026-11-01', 7000000, 600000, 'ACTIVE'),
 ('gv.lyhonganh', 'gv.lyhonganh@tsdms.local', 'ACTIVE', N'Lý Hồng', N'Anh', '1980-09-28', 0, '037180521454', '0359631487', N'Số 41 phố Nguyễn Tri Phương, phường Hồng Bàng, Hải Phòng', '2018-02-13', 'CO_HUU', 'ACTIVE', N'24 năm giảng dạy Khai thác AI an toàn cho học sinh và Công dân số cơ bản cho học sinh tiểu học và THCS, trong đó 9 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2018-GV060', '2018-02-13', NULL, 14800000, 1900000, 'ACTIVE'),
 ('gv.duongdieutuyet', 'gv.duongdieutuyet@tsdms.local', 'ACTIVE', N'Dương Diệu', N'Tuyết', '1993-12-20', 0, '027193038009', '0353634859', N'Số 191 ngõ 95 phố Bùi Viện, phường Hải An, Hải Phòng', '2020-12-23', 'CO_HUU', 'ACTIVE', N'11 năm giảng dạy Công dân số cơ bản và An toàn trên không gian mạng cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; từng phụ trách câu lạc bộ an toàn không gian mạng tại trường liên kết.', 'HD-2020-GV061', '2020-12-23', NULL, 14400000, 1400000, 'ACTIVE'),
 ('gv.tasynghia', 'gv.tasynghia@tsdms.local', 'INACTIVE', N'Tạ Sỹ', N'Nghĩa', '1979-05-06', 1, '040079169506', '0364145069', N'Số 202 ngõ 68 phố Hoàng Văn Thụ, phường Hồng Bàng, Hải Phòng', '2023-04-14', 'THINH_GIANG', 'RETIRED', N'25 năm giảng dạy Tiếng Anh THCS 6-9 - Global Success cho học sinh THCS, trong đó 3 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2023-GV062', '2023-04-14', '2026-05-24', 6500000, 500000, 'TERMINATED'),
 ('gv.hokhackien', 'gv.hokhackien@tsdms.local', 'ACTIVE', N'Hồ Khắc', N'Kiên', '1985-06-23', 1, '034085346946', '0894029597', N'Số 119 phố Cát Bi, phường Hải An, Hải Phòng', '2022-12-06', 'CO_HUU', 'ACTIVE', N'19 năm giảng dạy Lập trình robot RoboSim và Tinkering cho học sinh tiểu học và THCS, trong đó 4 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2022-GV063', '2022-12-06', NULL, 14800000, 1100000, 'ACTIVE'),
 ('gv.dokimyen', 'gv.dokimyen@tsdms.local', 'ACTIVE', N'Đỗ Kim', N'Yến', '1993-09-24', 0, '001193185298', '0934673348', N'Số 176 ngõ 79 phố Hoàng Quốc Việt, phường Kiến An, Hải Phòng', '2018-07-14', 'CO_HUU', 'ACTIVE', N'9 năm giảng dạy Kĩ năng làm việc nhóm & lãnh đạo cho học sinh tiểu học và THCS, trong đó 8 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2018-GV064', '2018-07-14', NULL, 15100000, 1700000, 'ACTIVE'),
 ('gv.duonghoaidiep', 'gv.duonghoaidiep@tsdms.local', 'ACTIVE', N'Dương Hoài', N'Diệp', '1990-11-02', 0, '027190116256', '0891211755', N'Số 209 ngõ 31 phố Cát Cụt, phường Lê Chân, Hải Phòng', '2020-11-12', 'CO_HUU', 'ACTIVE', N'14 năm giảng dạy Tinkering và Lập trình robot Vincibot cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; từng phụ trách câu lạc bộ chế tác - Tinkering tại trường liên kết.', 'HD-2020-GV065', '2020-11-12', NULL, 14800000, 1400000, 'ACTIVE'),
 ('gv.doanbaoly', 'gv.doanbaoly@tsdms.local', 'ACTIVE', N'Đoàn Bảo', N'Ly', '1997-03-06', 0, '035197935899', '0861711969', N'Số 175 ngõ 39 phố Văn Cao, phường Ngô Quyền, Hải Phòng', '2025-04-04', 'CO_HUU', 'ACTIVE', N'5 năm giảng dạy Bảng tính Excel cho học sinh cho học sinh THCS, trong đó 1 năm tại trung tâm; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2025-GV066', '2025-04-04', '2027-04-04', 13500000, 700000, 'ACTIVE'),
 ('gv.vuanhmai', 'gv.vuanhmai@tsdms.local', 'ACTIVE', N'Vũ Ánh', N'Mai', '1990-07-25', 0, '027190324152', '0395979693', N'Số 175 ngõ 36 phố Phú Xá, phường Hải An, Hải Phòng', '2023-02-03', 'THINH_GIANG', 'ACTIVE', N'15 năm giảng dạy Tin học cơ bản (GDPT 2018) cho học sinh tiểu học và THCS, trong đó 4 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV067', '2026-06-01', '2027-06-01', 6200000, 500000, 'ACTIVE'),
 ('gv.lykhacphong', 'gv.lykhacphong@tsdms.local', 'ACTIVE', N'Lý Khắc', N'Phong', '2000-11-27', 1, '030200879510', '0885782114', N'Số 171 phố Phan Đăng Lưu, phường Kiến An, Hải Phòng', '2026-06-19', 'CO_HUU', 'ACTIVE', N'4 năm giảng dạy Tiếng Anh THCS 6-9 - i-Learn Smart World và Tiếng Anh Tiểu học 3-5 - Global Success cho học sinh tiểu học và THCS; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2026-GV068', '2026-06-19', '2028-06-19', 11600000, 500000, 'ACTIVE'),
 ('gv.tranhaitram', 'gv.tranhaitram@tsdms.local', 'ACTIVE', N'Trần Hải', N'Trâm', '1995-03-27', 0, '030195570535', '0391579208', N'Số 221 phố Lê Thánh Tông, phường Ngô Quyền, Hải Phòng', '2019-03-15', 'CO_HUU', 'ACTIVE', N'7 năm giảng dạy Nhập môn Trí tuệ nhân tạo cho học sinh THCS; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2019-GV069', '2019-03-15', NULL, 14300000, 1600000, 'ACTIVE'),
 ('gv.duongkimthu', 'gv.duongkimthu@tsdms.local', 'ACTIVE', N'Dương Kim', N'Thu', '1980-10-24', 0, '025180212108', '0351838099', N'Số 227 ngõ 38 phố Đằng Hải, phường Hải An, Hải Phòng', '2024-11-07', 'CO_HUU', 'ACTIVE', N'26 năm giảng dạy Ngữ âm & Phonics Tiểu học và Tiếng Anh Tiểu học 3-5 - i-Learn Smart Start cho học sinh tiểu học và THCS, trong đó 2 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2024-GV070', '2024-11-07', NULL, 13800000, 800000, 'ACTIVE'),
 ('gv.tranvanha', 'gv.tranvanha@tsdms.local', 'ACTIVE', N'Trần Vân', N'Hà', '1979-08-07', 0, '040179859258', '0768403400', N'Số 244 ngõ 32 phố Trần Nguyên Hãn, phường Lê Chân, Hải Phòng', '2021-05-19', 'CO_HUU', 'ACTIVE', N'25 năm giảng dạy Tiếng Anh làm quen (Lớp 1-2) cho học sinh tiểu học, trong đó 5 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2021-GV071', '2021-05-19', NULL, 14800000, 1300000, 'ACTIVE'),
 ('gv.hathuylan', 'gv.hathuylan@tsdms.local', 'ACTIVE', N'Hà Thùy', N'Lan', '1990-01-27', 0, '022190016326', '0812965560', N'Số 175 ngõ 16 phố Đông Khê, phường Ngô Quyền, Hải Phòng', '2026-02-08', 'CO_HUU', 'ACTIVE', N'14 năm giảng dạy Tiếng Anh Tiểu học 3-5 - Global Success cho học sinh tiểu học, trong đó 1 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2026-GV072', '2026-02-08', '2028-02-08', 14800000, 700000, 'ACTIVE'),
 ('gv.phanphuongtrang', 'gv.phanphuongtrang@tsdms.local', 'ACTIVE', N'Phan Phương', N'Trang', '1992-05-07', 0, '042192136322', '0894166951', N'Số 184 ngõ 36 phố Trần Nguyên Hãn, phường Lê Chân, Hải Phòng', '2021-11-26', 'CO_HUU', 'ACTIVE', N'12 năm giảng dạy Soạn thảo văn bản & Trình chiếu và Bảng tính Excel cho học sinh cho học sinh THCS, trong đó 5 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2021-GV073', '2021-11-26', NULL, 14800000, 1300000, 'ACTIVE'),
 ('gv.vuongthevinh', 'gv.vuongthevinh@tsdms.local', 'ACTIVE', N'Vương Thế', N'Vinh', '1987-09-21', 1, '026087822614', '0358605463', N'Số 123 phố Cát Cụt, phường Lê Chân, Hải Phòng', '2025-08-22', 'CO_HUU', 'ACTIVE', N'17 năm giảng dạy Tiếng Anh Tiểu học 3-5 - i-Learn Smart Start và Ngữ âm & Phonics Tiểu học cho học sinh tiểu học, trong đó 1 năm tại trung tâm; từng phụ trách câu lạc bộ ngữ âm - phát âm tại trường liên kết.', 'HD-2025-GV074', '2025-08-22', '2027-08-22', 14800000, 700000, 'ACTIVE'),
 ('gv.taxuangiang', 'gv.taxuangiang@tsdms.local', 'ACTIVE', N'Tạ Xuân', N'Giang', '1994-04-06', 1, '038094703183', '0773024368', N'Số 34 phố Lê Hồng Phong, phường Hải An, Hải Phòng', '2026-02-17', 'CO_HUU', 'ACTIVE', N'10 năm giảng dạy An toàn trên không gian mạng và Khai thác AI an toàn cho học sinh cho học sinh tiểu học và THCS; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2026-GV075', '2026-02-17', '2028-02-17', 14000000, 500000, 'ACTIVE'),
 ('gv.nghiemhongmai', 'gv.nghiemhongmai@tsdms.local', 'ACTIVE', N'Nghiêm Hồng', N'Mai', '1990-08-14', 0, '022190631675', '0389361427', N'Số 85 phố Trần Phú, phường Ngô Quyền, Hải Phòng', '2023-07-22', 'THINH_GIANG', 'ACTIVE', N'14 năm giảng dạy Khai thác AI an toàn cho học sinh cho học sinh THCS, trong đó 3 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2026-GV076', '2026-07-01', '2027-07-01', 6500000, 500000, 'ACTIVE'),
 ('gv.nguyenthuyuyen', 'gv.nguyenthuyuyen@tsdms.local', 'ACTIVE', N'Nguyễn Thùy', N'Uyên', '1996-10-12', 0, '033196124371', '0844323571', N'Số 211 phố Hạ Lý, phường Hồng Bàng, Hải Phòng', '2025-10-16', 'CO_HUU', 'ACTIVE', N'10 năm giảng dạy Bảng tính Excel cho học sinh cho học sinh THCS, trong đó 1 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2025-GV077', '2025-10-16', '2027-10-16', 13000000, 700000, 'ACTIVE'),
 ('gv.voquangson', 'gv.voquangson@tsdms.local', 'ACTIVE', N'Võ Quang', N'Sơn', '1993-10-02', 1, '024093433676', '0987599968', N'Số 52 phố Lê Thánh Tông, phường Ngô Quyền, Hải Phòng', '2026-01-09', 'CO_HUU', 'ACTIVE', N'11 năm giảng dạy Lập trình Scratch và Lập trình robot RoboSim cho học sinh tiểu học và THCS, trong đó 1 năm tại trung tâm; từng phụ trách câu lạc bộ lập trình sáng tạo tại trường liên kết.', 'HD-2026-GV078', '2026-01-09', '2028-01-09', 14400000, 700000, 'ACTIVE'),
 ('gv.phanquynhlinh', 'gv.phanquynhlinh@tsdms.local', 'ACTIVE', N'Phan Quỳnh', N'Linh', '1978-11-14', 0, '036178405911', '0829500330', N'Số 202 ngõ 173 phố Đông Khê, phường Ngô Quyền, Hải Phòng', '2021-07-05', 'CO_HUU', 'ACTIVE', N'26 năm giảng dạy Tiếng Anh THCS 6-9 - Global Success cho học sinh THCS, trong đó 5 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2021-GV079', '2021-07-05', NULL, 14800000, 1300000, 'ACTIVE'),
 ('gv.trinhngocan', 'gv.trinhngocan@tsdms.local', 'INACTIVE', N'Trịnh Ngọc', N'An', '1980-08-23', 1, '022080381775', '0970752497', N'Số 4 ngõ 191 phố Trần Thành Ngọ, phường Kiến An, Hải Phòng', '2019-09-28', 'THINH_GIANG', 'RETIRED', N'25 năm giảng dạy Lập trình robot Leanbot và Lập trình Scratch cho học sinh tiểu học, trong đó 7 năm tại trung tâm; từng phụ trách câu lạc bộ lập trình sáng tạo tại trường liên kết.', 'HD-2019-GV080', '2019-09-28', '2025-08-28', 6200000, 700000, 'TERMINATED'),
 ('gv.vobichan', 'gv.vobichan@tsdms.local', 'ACTIVE', N'Võ Bích', N'An', '1991-10-21', 0, '030191445276', '0585268571', N'Số 145 phố Hoàng Văn Thụ, phường Hồng Bàng, Hải Phòng', '2026-04-27', 'THINH_GIANG', 'ACTIVE', N'13 năm giảng dạy Tiếng Anh THCS 6-9 - i-Learn Smart World và Tiếng Anh Tiểu học 3-5 - Global Success cho học sinh tiểu học và THCS; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2026-GV081', '2026-04-27', '2027-04-27', 6500000, 300000, 'ACTIVE'),
 ('gv.vuhoaitrang', 'gv.vuhoaitrang@tsdms.local', 'ACTIVE', N'Vũ Hoài', N'Trang', '1980-01-17', 0, '030180427166', '0385794737', N'Số 48 phố Văn Cao, phường Ngô Quyền, Hải Phòng', '2026-02-18', 'THINH_GIANG', 'ACTIVE', N'22 năm giảng dạy Kĩ năng tự bảo vệ & phòng chống xâm hại cho học sinh tiểu học và THCS; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV082', '2026-02-18', '2027-02-18', 7000000, 300000, 'ACTIVE'),
 ('gv.hoangdieuthuy', 'gv.hoangdieuthuy@tsdms.local', 'ACTIVE', N'Hoàng Diệu', N'Thủy', '1981-08-21', 0, '026181572724', '0361981121', N'Số 97 phố Hạ Lý, phường Hồng Bàng, Hải Phòng', '2020-08-18', 'THINH_GIANG', 'ACTIVE', N'23 năm giảng dạy Tin học cơ bản (GDPT 2018) và Soạn thảo văn bản & Trình chiếu cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; từng phụ trách câu lạc bộ Tin học nhà trường tại trường liên kết.', 'HD-2026-GV083', '2026-03-01', '2027-03-01', 6500000, 600000, 'ACTIVE'),
 ('gv.dangtheviet', 'gv.dangtheviet@tsdms.local', 'ACTIVE', N'Đặng Thế', N'Việt', '1983-11-02', 1, '042083384226', '0816026695', N'Số 144 phố Điện Biên Phủ, phường Hồng Bàng, Hải Phòng', '2018-01-02', 'THINH_GIANG', 'ACTIVE', N'21 năm giảng dạy Kĩ năng quản lý thời gian & tài chính cá nhân và Kĩ năng làm việc nhóm & lãnh đạo cho học sinh tiểu học và THCS, trong đó 9 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2026-GV084', '2026-06-01', '2027-06-01', 6500000, 700000, 'ACTIVE'),
 ('gv.haquynhhue', 'gv.haquynhhue@tsdms.local', 'ACTIVE', N'Hà Quỳnh', N'Huệ', '1989-06-18', 0, '036189423301', '0945677027', N'Số 71 phố Cát Cụt, phường Lê Chân, Hải Phòng', '2022-02-06', 'CO_HUU', 'ACTIVE', N'13 năm giảng dạy Công dân số cơ bản cho học sinh tiểu học và THCS, trong đó 5 năm tại trung tâm; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2022-GV085', '2022-02-06', NULL, 16300000, 1300000, 'ACTIVE'),
 ('gv.thaigiaanh', 'gv.thaigiaanh@tsdms.local', 'ACTIVE', N'Thái Gia', N'Anh', '1995-08-09', 0, '038195783763', '0973216702', N'Số 204 phố Phú Xá, phường Hải An, Hải Phòng', '2021-01-24', 'CO_HUU', 'ACTIVE', N'9 năm giảng dạy Soạn thảo văn bản & Trình chiếu và Tin học cơ bản (GDPT 2018) cho học sinh tiểu học và THCS, trong đó 6 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2021-GV086', '2021-01-24', NULL, 13600000, 1400000, 'ACTIVE'),
 ('gv.hathihang', 'gv.hathihang@tsdms.local', 'ACTIVE', N'Hà Thị', N'Hằng', '1984-09-10', 0, '035184176031', '0815450346', N'Số 141 ngõ 4 phố Tô Hiệu, phường Lê Chân, Hải Phòng', '2026-01-16', 'CO_HUU', 'ACTIVE', N'22 năm giảng dạy Ngữ âm & Phonics Tiểu học và Tiếng Anh THCS 6-9 - Global Success cho học sinh tiểu học và THCS, trong đó 1 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2026-GV087', '2026-01-16', '2028-01-16', 13800000, 700000, 'ACTIVE'),
 ('gv.quachdinhkhanh', 'gv.quachdinhkhanh@tsdms.local', 'ACTIVE', N'Quách Đình', N'Khánh', '1982-01-12', 1, '036082958593', '0971018320', N'Số 107 phố Nguyễn Công Trứ, phường Lê Chân, Hải Phòng', '2024-10-16', 'THINH_GIANG', 'ACTIVE', N'20 năm giảng dạy An toàn trên không gian mạng và Khai thác AI an toàn cho học sinh cho học sinh tiểu học và THCS, trong đó 2 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV088', '2026-04-01', '2027-04-01', 7000000, 400000, 'ACTIVE'),
 ('gv.vodinhkhanh', 'gv.vodinhkhanh@tsdms.local', 'ACTIVE', N'Võ Đình', N'Khánh', '1983-10-19', 1, '022083874285', '0907489262', N'Số 11 ngõ 35 phố Trần Thành Ngọ, phường Kiến An, Hải Phòng', '2023-07-02', 'CO_HUU', 'ACTIVE', N'19 năm giảng dạy Kĩ năng giao tiếp & thuyết trình và Kĩ năng quản lý thời gian & tài chính cá nhân cho học sinh tiểu học và THCS, trong đó 3 năm tại trung tâm; từng phụ trách câu lạc bộ giáo dục giá trị sống tại trường liên kết.', 'HD-2023-GV089', '2023-07-02', NULL, 16300000, 1000000, 'ACTIVE'),
 ('gv.voanhphuong', 'gv.voanhphuong@tsdms.local', 'ACTIVE', N'Võ Ánh', N'Phương', '1995-09-16', 0, '024195930089', '0760747759', N'Số 228 phố Trần Nhân Tông, phường Kiến An, Hải Phòng', '2019-10-21', 'THINH_GIANG', 'ACTIVE', N'9 năm giảng dạy Lập trình robot Vincibot và Lập trình robot Spike Essential cho học sinh tiểu học và THCS, trong đó 7 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2025-GV090', '2025-12-01', '2026-12-01', 6300000, 700000, 'ACTIVE'),
 ('gv.thaicongvu', 'gv.thaicongvu@tsdms.local', 'ACTIVE', N'Thái Công', N'Vũ', '1988-12-07', 1, '026088345778', '0363308766', N'Số 248 phố Cát Bi, phường Hải An, Hải Phòng', '2026-02-12', 'CO_HUU', 'ACTIVE', N'16 năm giảng dạy Kĩ năng làm việc nhóm & lãnh đạo và Kĩ năng quản lý thời gian & tài chính cá nhân cho học sinh tiểu học và THCS, trong đó 1 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2026-GV091', '2026-02-12', '2028-02-12', 14800000, 700000, 'ACTIVE'),
 ('gv.hovanquang', 'gv.hovanquang@tsdms.local', 'ACTIVE', N'Hồ Văn', N'Quang', '1986-04-16', 1, '033086423006', '0821523909', N'Số 184 phố Lê Hồng Phong, phường Hải An, Hải Phòng', '2021-09-13', 'CO_HUU', 'ACTIVE', N'18 năm giảng dạy Bảng tính Excel cho học sinh và Công dân số cơ bản cho học sinh tiểu học và THCS, trong đó 5 năm tại trung tâm; quen soạn giảng theo Chương trình GDPT 2018.', 'HD-2021-GV092', '2021-09-13', NULL, 14800000, 1300000, 'ACTIVE'),
 ('gv.lengocthinh', 'gv.lengocthinh@tsdms.local', 'ACTIVE', N'Lê Ngọc', N'Thịnh', '1989-10-27', 1, '034089551349', '0337045296', N'Số 92 phố Hoàng Văn Thụ, phường Hồng Bàng, Hải Phòng', '2023-10-04', 'THINH_GIANG', 'ACTIVE', N'15 năm giảng dạy Tiếng Anh làm quen (Lớp 1-2) cho học sinh tiểu học, trong đó 3 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2026-GV093', '2026-05-01', '2027-05-01', 6500000, 500000, 'ACTIVE'),
 ('gv.dinhminhquyen', 'gv.dinhminhquyen@tsdms.local', 'ACTIVE', N'Đinh Minh', N'Quyên', '1992-06-19', 0, '038192627350', '0893392128', N'Số 62 phố Nguyễn Lương Bằng, phường Kiến An, Hải Phòng', '2024-05-17', 'CO_HUU', 'ACTIVE', N'12 năm giảng dạy Khai thác AI an toàn cho học sinh và Công dân số cơ bản cho học sinh tiểu học và THCS, trong đó 2 năm tại trung tâm; từng phụ trách câu lạc bộ khai thác AI có trách nhiệm tại trường liên kết.', 'HD-2024-GV094', '2024-05-17', NULL, 14800000, 800000, 'ACTIVE'),
 ('gv.caongochoang', 'gv.caongochoang@tsdms.local', 'ACTIVE', N'Cao Ngọc', N'Hoàng', '1984-01-06', 1, '025084695249', '0580386211', N'Số 249 ngõ 70 phố Lê Hồng Phong, phường Hải An, Hải Phòng', '2024-07-15', 'CO_HUU', 'ACTIVE', N'18 năm giảng dạy Lập trình robot Spike Essential và Lập trình robot Vincibot cho học sinh tiểu học và THCS, trong đó 2 năm tại trung tâm; đã hướng dẫn học sinh dự thi cấp thành phố.', 'HD-2024-GV095', '2024-07-15', NULL, 16300000, 800000, 'ACTIVE'),
 ('gv.trinhyenhong', 'gv.trinhyenhong@tsdms.local', 'ACTIVE', N'Trịnh Yến', N'Hồng', '1991-02-03', 0, '034191911025', '0360279628', N'Số 224 ngõ 41 phố Đông Khê, phường Ngô Quyền, Hải Phòng', '2026-02-03', 'CO_HUU', 'ACTIVE', N'11 năm giảng dạy Tin học cơ bản (GDPT 2018) và Bảng tính Excel cho học sinh cho học sinh tiểu học và THCS, trong đó 1 năm tại trung tâm; phối hợp tốt với giáo viên chủ nhiệm trong hoạt động trải nghiệm.', 'HD-2026-GV096', '2026-02-03', '2028-02-03', 15900000, 700000, 'ACTIVE'),
 ('gv.vuonghuuhuy', 'gv.vuonghuuhuy@tsdms.local', 'ACTIVE', N'Vương Hữu', N'Huy', '2000-11-28', 1, '019200806889', '0347859414', N'Số 37 phố Lạch Tray, phường Ngô Quyền, Hải Phòng', '2023-05-24', 'CO_HUU', 'ACTIVE', N'4 năm giảng dạy Lập trình robot RoboSim và Lập trình robot Spike Essential cho học sinh tiểu học và THCS, trong đó 3 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2023-GV097', '2023-05-24', NULL, 11600000, 1000000, 'ACTIVE'),
 ('gv.phanhoaimai', 'gv.phanhoaimai@tsdms.local', 'ACTIVE', N'Phan Hoài', N'Mai', '1999-11-05', 0, '019199241717', '0948897562', N'Số 210 ngõ 77 phố Lê Lợi, phường Ngô Quyền, Hải Phòng', '2023-11-12', 'CO_HUU', 'ACTIVE', N'6 năm giảng dạy Kĩ năng tự bảo vệ & phòng chống xâm hại và Kĩ năng quản lý thời gian & tài chính cá nhân cho học sinh tiểu học và THCS, trong đó 3 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2023-GV098', '2023-11-12', NULL, 11700000, 1000000, 'ACTIVE'),
 ('gv.hoanghailinh', 'gv.hoanghailinh@tsdms.local', 'ACTIVE', N'Hoàng Hải', N'Linh', '1982-09-09', 0, '027182019426', '0860510236', N'Số 59 ngõ 197 phố Ngô Gia Tự, phường Hải An, Hải Phòng', '2024-04-05', 'THINH_GIANG', 'ACTIVE', N'22 năm giảng dạy Tiếng Anh Tiểu học 3-5 - Global Success và Tiếng Anh làm quen (Lớp 1-2) cho học sinh tiểu học và THCS, trong đó 2 năm tại trung tâm; có kinh nghiệm dạy lớp đông trên 35 học sinh.', 'HD-2025-GV099', '2025-10-01', '2026-10-01', 6500000, 400000, 'ACTIVE'),
 ('gv.maithuyhanh', 'gv.maithuyhanh@tsdms.local', 'ACTIVE', N'Mai Thúy', N'Hạnh', '1991-05-27', 0, '022191866599', '0946130014', N'Số 24 phố Hồ Sen, phường Lê Chân, Hải Phòng', '2020-08-18', 'THINH_GIANG', 'ACTIVE', N'11 năm giảng dạy Tinkering cho học sinh tiểu học, trong đó 6 năm tại trung tâm; tham gia bồi dưỡng đội tuyển học sinh giỏi cấp quận.', 'HD-2025-GV100', '2025-10-01', '2026-10-01', 7000000, 600000, 'ACTIVE');

/* ---- 3a) Tài khoản đăng nhập ---- */
INSERT INTO AppUser (Username, PasswordHash, Email, Status, CreatedBy)
SELECT g.Username, @Hash, g.Email, g.UserStatus, @Admin
FROM #GV g;

/* ---- 3b) Gán vai trò TEACHER ---- */
INSERT INTO UserRole (AppUserId, RoleId)
SELECT u.Id, r.Id
FROM #GV g
JOIN AppUser u ON u.Username = g.Username
JOIN Role    r ON r.Name = 'TEACHER';

/* ---- 3c) Hồ sơ giáo viên ---- */
INSERT INTO Teacher (AppUserId, BranchId, FirstName, LastName, DateOfBirth, Gender, IdCardNo,
                     Phone, Address, HireDate, EmploymentType, TeachingExperience, Status, CreatedBy)
SELECT u.Id, @Branch, g.FirstName, g.LastName, g.Dob, g.Gender, g.IdCard,
       g.Phone, g.Addr, g.HireDate, g.EmpType, g.Experience, g.Status, @Admin
FROM #GV g
JOIN AppUser u ON u.Username = g.Username;

PRINT N'3) Đã thêm ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' hồ sơ giáo viên.';

/* ---- 3d) Hợp đồng — mỗi GV đúng 1 bản (ràng buộc UX_Contract_OneActivePerTeacher) ---- */
INSERT INTO Contract (TeacherId, ContractNo, StartDate, EndDate, BaseSalary, Allowance, Status, CreatedBy)
SELECT t.Id, g.ContractNo, g.CStart, g.CEnd, g.CBase, g.CAllow, g.CStatus, @Admin
FROM #GV g
JOIN AppUser u ON u.Username  = g.Username
JOIN Teacher t ON t.AppUserId = u.Id;


/* =====================================================================
   4) BẰNG CẤP & CHỨNG CHỈ
   ---------------------------------------------------------------------
   Bằng chính lưu theo ĐÚNG định dạng màn hình đang dùng: "Trình độ -
   Chuyên ngành" (xem degreeName() trong TeacherListPage.vue). Chứng chỉ
   nghề để tên tự nhiên. Ngày cấp luôn nằm sau ngày sinh và khớp với
   tuổi tốt nghiệp; chứng chỉ có thời hạn thì ExpiryDate = ngày cấp + hạn.
   KHÔNG đính kèm file PDF — FileUrl để NULL.
   ===================================================================== */
CREATE TABLE #Cert (
    Username   VARCHAR(50)   COLLATE DATABASE_DEFAULT,
    Name       NVARCHAR(200) COLLATE DATABASE_DEFAULT,
    Issuer     NVARCHAR(200) COLLATE DATABASE_DEFAULT,
    IssueDate  DATE,
    ExpiryDate DATE
);

INSERT INTO #Cert (Username, Name, Issuer, IssueDate, ExpiryDate) VALUES
 ('gv.hothanhhuong', N'Cao đẳng - Cơ điện tử', N'Cao đẳng Sư phạm Trung ương', '2004-09-20', NULL),
 ('gv.hothanhhuong', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2006-04-20', NULL),
 ('gv.hoangphuongnhi', N'Trung cấp - Kỹ thuật điện tử - viễn thông', N'Trung cấp nghề Hải Phòng', '2000-09-24', NULL),
 ('gv.hoangphuongnhi', N'Chứng chỉ Machine Learning cơ bản', N'ĐH Bách khoa Hà Nội', '2026-07-12', NULL),
 ('gv.taphuhuy', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Ngoại ngữ - ĐHQG Hà Nội', '2009-06-17', NULL),
 ('gv.taphuhuy', N'Chứng chỉ IELTS 7.0', N'Hội đồng Anh (British Council)', '2024-04-21', '2026-04-21'),
 ('gv.quachnhatha', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Hải Phòng', '2002-09-24', NULL),
 ('gv.quachnhatha', N'Chứng chỉ TESOL', N'Cambridge Assessment English', '2005-07-09', NULL),
 ('gv.quachnhatha', N'Chứng chỉ IELTS 7.5', N'IDP Education', '2018-05-08', '2020-05-08'),
 ('gv.homanhvinh', N'Trung cấp - Ngôn ngữ Anh', N'Trung cấp nghề Hải Phòng', '2012-09-10', NULL),
 ('gv.homanhvinh', N'Chứng chỉ IELTS 7.0', N'Hội đồng Anh (British Council)', '2015-03-02', '2017-03-02'),
 ('gv.trankimthao', N'Cao đẳng - Cơ điện tử', N'Cao đẳng Công nghệ và Thương mại Hà Nội', '2011-06-14', NULL),
 ('gv.trankimthao', N'Chứng nhận huấn luyện viên Robotics Leanbot', N'Braintech Việt Nam', '2019-08-24', '2021-08-24'),
 ('gv.luuthedat', N'Cử nhân - Sư phạm Tin học', N'ĐH Giáo dục - ĐHQG Hà Nội', '2008-06-11', NULL),
 ('gv.nguyenvantuyet', N'Thạc sỹ - Ngôn ngữ Anh', N'ĐH Thủ đô Hà Nội', '2004-06-21', NULL),
 ('gv.nguyenvantuyet', N'Chứng nhận tập huấn SGK Tiếng Anh (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2020-11-22', NULL),
 ('gv.buihaihang', N'Cử nhân - Kỹ thuật điều khiển & tự động hóa', N'ĐH Giao thông vận tải', '2014-06-25', NULL),
 ('gv.buihaihang', N'Chứng nhận huấn luyện viên Robotics Leanbot', N'Braintech Việt Nam', '2019-08-26', '2021-08-26'),
 ('gv.buihaihang', N'Chứng chỉ LEGO Education SPIKE Trainer', N'LEGO Education', '2020-11-04', '2023-11-04'),
 ('gv.lammaily', N'Cử nhân - Kỹ thuật điện tử - viễn thông', N'ĐH Hàng hải Việt Nam', '2018-06-14', NULL),
 ('gv.vuhuuphong', N'Cao đẳng - Khoa học máy tính', N'Cao đẳng Hàng hải I', '2000-06-15', NULL),
 ('gv.vuhuuphong', N'Chứng nhận giáo viên hướng dẫn cuộc thi Robothon', N'STEAM for Vietnam', '2020-07-07', NULL),
 ('gv.dangthanhtien', N'Cao đẳng - Sư phạm Tiếng Anh', N'Cao đẳng Công nghệ và Thương mại Hà Nội', '2004-09-11', NULL),
 ('gv.tobaomai', N'Thạc sỹ - Tâm lý học', N'ĐH Hải Phòng', '2009-06-12', NULL),
 ('gv.ngotantrung', N'Cử nhân - Ngôn ngữ Anh', N'ĐH Hải Phòng', '2016-06-15', NULL),
 ('gv.ngotantrung', N'Chứng nhận tập huấn SGK Tiếng Anh (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2025-10-27', NULL),
 ('gv.phankimdiep', N'Cao đẳng - Công nghệ thông tin', N'Cao đẳng Công nghệ và Thương mại Hà Nội', '2003-06-15', NULL),
 ('gv.phankimdiep', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2015-07-03', NULL),
 ('gv.tobichdung', N'Cử nhân - Cơ điện tử', N'ĐH Hàng hải Việt Nam', '2014-06-17', NULL),
 ('gv.vohaihuyen', N'Thạc sỹ - Công nghệ thông tin', N'ĐH Bách khoa Hà Nội', '2014-06-18', NULL),
 ('gv.caokhanhoanh', N'Cao đẳng - Khoa học máy tính', N'Cao đẳng Hàng hải I', '2004-06-12', NULL),
 ('gv.hakimha', N'Cử nhân - Khoa học máy tính', N'ĐH Công nghệ - ĐHQG Hà Nội', '2004-06-16', NULL),
 ('gv.chuvietnghia', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Sư phạm Hà Nội', '2005-06-26', NULL),
 ('gv.chuvietnghia', N'Chứng chỉ IELTS 7.5', N'IDP Education', '2014-07-07', '2016-07-07'),
 ('gv.doanthuylinh', N'Cử nhân - Giáo dục học', N'ĐH Sư phạm Hà Nội', '2020-06-26', NULL),
 ('gv.doanthuylinh', N'Chứng nhận tập huấn Phòng chống xâm hại trẻ em', N'Cục Trẻ em - Bộ LĐ-TB&XH', '2021-08-14', '2024-08-14'),
 ('gv.vuthanhtrang', N'Thạc sỹ - Công nghệ thông tin', N'Học viện Công nghệ Bưu chính Viễn thông', '2018-09-22', NULL),
 ('gv.vuthanhtrang', N'Chứng nhận huấn luyện viên Robotics Leanbot', N'Braintech Việt Nam', '2019-05-19', '2021-05-19'),
 ('gv.vuongmaitrinh', N'Cử nhân - Khoa học máy tính', N'ĐH Công nghệ - ĐHQG Hà Nội', '2000-09-19', NULL),
 ('gv.vuongmaitrinh', N'Chứng chỉ MOS Word Specialist', N'IIG Việt Nam', '2007-01-19', '2010-01-19'),
 ('gv.dangmanhhai', N'Cử nhân - Công nghệ thông tin', N'Học viện Công nghệ Bưu chính Viễn thông', '2003-09-26', NULL),
 ('gv.dangmanhhai', N'Chứng chỉ Machine Learning cơ bản', N'ĐH Bách khoa Hà Nội', '2019-09-27', NULL),
 ('gv.lethutrang', N'Cử nhân - Kỹ thuật điện tử - viễn thông', N'ĐH Hàng hải Việt Nam', '2011-09-26', NULL),
 ('gv.tangochong', N'Cử nhân - Ngôn ngữ học ứng dụng', N'ĐH Hà Nội', '2018-09-28', NULL),
 ('gv.tangochong', N'Chứng nhận tập huấn SGK Tiếng Anh (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2024-11-25', NULL),
 ('gv.buiyentuyet', N'Cử nhân - Kỹ thuật phần mềm', N'ĐH Công nghiệp Hà Nội', '2007-06-16', NULL),
 ('gv.buiyentuyet', N'Chứng nhận tập huấn SGK Tin học (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2020-06-17', NULL),
 ('gv.doanthuynhung', N'Cử nhân - Kỹ thuật điện tử - viễn thông', N'ĐH Bách khoa Hà Nội', '2000-09-21', NULL),
 ('gv.doanthuynhung', N'Chứng chỉ AI for Everyone', N'Google for Education', '2020-02-16', '2023-02-16'),
 ('gv.doanthuynhung', N'Chứng chỉ Machine Learning cơ bản', N'ĐH Bách khoa Hà Nội', '2020-02-12', NULL),
 ('gv.tranviettu', N'Cử nhân - Khoa học máy tính', N'ĐH Bách khoa Hà Nội', '2012-06-20', NULL),
 ('gv.hangochien', N'Trung cấp - Cơ điện tử', N'Trung cấp nghề Cơ khí I Hà Nội', '2005-06-11', NULL),
 ('gv.hangochien', N'Chứng nhận huấn luyện viên Robotics Leanbot', N'Braintech Việt Nam', '2022-09-25', '2024-09-25'),
 ('gv.hangochien', N'Chứng chỉ AI for Everyone', N'Google for Education', '2021-05-28', '2024-05-28'),
 ('gv.vuongtanminh', N'Cao đẳng - Kỹ thuật điện tử - viễn thông', N'Cao đẳng Công nghiệp Hải Phòng', '2008-06-22', NULL),
 ('gv.vuongtanminh', N'Chứng chỉ Machine Learning cơ bản', N'ĐH Bách khoa Hà Nội', '2026-02-26', NULL),
 ('gv.vuongtanminh', N'Chứng nhận huấn luyện viên Robotics Leanbot', N'Braintech Việt Nam', '2025-01-17', '2027-01-17'),
 ('gv.buithiha', N'Cao đẳng - Quản lý giáo dục', N'Cao đẳng Công nghệ và Thương mại Hà Nội', '2009-06-13', NULL),
 ('gv.quachtuyethien', N'Thạc sỹ - Sư phạm Giáo dục công dân', N'ĐH Sư phạm Hà Nội 2', '2021-06-24', NULL),
 ('gv.tranhoaimy', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Sư phạm Hà Nội 2', '2017-09-27', NULL),
 ('gv.tranhoaimy', N'Chứng chỉ TKT (Teaching Knowledge Test)', N'Cambridge Assessment English', '2021-05-01', NULL),
 ('gv.phamdieugiang', N'Thạc sỹ - Sư phạm Tiếng Anh', N'ĐH Sư phạm Hà Nội 2', '2007-09-24', NULL),
 ('gv.phamdieugiang', N'Chứng chỉ TKT (Teaching Knowledge Test)', N'Cambridge Assessment English', '2016-01-13', NULL),
 ('gv.nghiemquocnam', N'Cử nhân - Giáo dục học', N'ĐH Giáo dục - ĐHQG Hà Nội', '2005-06-25', NULL),
 ('gv.nghiemquocnam', N'Chứng nhận tập huấn Phòng chống xâm hại trẻ em', N'Cục Trẻ em - Bộ LĐ-TB&XH', '2020-10-08', '2023-10-08'),
 ('gv.phamthuynga', N'Cử nhân - Sư phạm Tin học', N'ĐH Sư phạm Hà Nội 2', '2011-06-20', NULL),
 ('gv.phamthuynga', N'Chứng nhận tập huấn SGK Tin học (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2020-11-06', NULL),
 ('gv.dinhngocnhung', N'Cử nhân - Công nghệ thông tin', N'ĐH Công nghiệp Hà Nội', '2003-06-12', NULL),
 ('gv.dinhngocnhung', N'Chứng nhận huấn luyện viên Robotics Leanbot', N'Braintech Việt Nam', '2019-03-10', '2021-03-10'),
 ('gv.doyenan', N'Cử nhân - Khoa học máy tính', N'ĐH Bách khoa Hà Nội', '2015-06-27', NULL),
 ('gv.doanphuonggiang', N'Cử nhân - Hệ thống thông tin', N'Học viện Công nghệ Bưu chính Viễn thông', '2006-09-16', NULL),
 ('gv.doanphuonggiang', N'Chứng chỉ MOS Word Specialist', N'IIG Việt Nam', '2016-01-04', '2019-01-04'),
 ('gv.phamdinhthang', N'Cao đẳng - Ngôn ngữ học ứng dụng', N'Cao đẳng nghề Công nghệ cao Hà Nội', '2020-06-15', NULL),
 ('gv.phamdinhthang', N'Chứng nhận tập huấn SGK Tiếng Anh (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2021-02-11', NULL),
 ('gv.tatienvu', N'Thạc sỹ - Hệ thống thông tin', N'Học viện Công nghệ Bưu chính Viễn thông', '2021-06-12', NULL),
 ('gv.luuvanmai', N'Trung cấp - Ngôn ngữ học ứng dụng', N'Trung cấp nghề Hải Phòng', '2016-09-10', NULL),
 ('gv.luuvanmai', N'Chứng chỉ TESOL', N'Cambridge Assessment English', '2019-11-10', NULL),
 ('gv.nguyenducgiang', N'Thạc sỹ - Kỹ thuật điều khiển & tự động hóa', N'ĐH Giao thông vận tải', '2008-06-18', NULL),
 ('gv.buingochai', N'Cử nhân - Ngôn ngữ Anh', N'ĐH Hải Phòng', '2014-06-20', NULL),
 ('gv.buingochai', N'Chứng chỉ IELTS 7.0', N'Hội đồng Anh (British Council)', '2019-03-11', '2021-03-11'),
 ('gv.buingochai', N'Chứng chỉ TESOL', N'Cambridge Assessment English', '2023-08-26', NULL),
 ('gv.vuthanhha', N'Cử nhân - Cơ điện tử', N'ĐH Công nghiệp Hà Nội', '2018-06-28', NULL),
 ('gv.vuthanhha', N'Chứng nhận huấn luyện viên Robotics Leanbot', N'Braintech Việt Nam', '2019-07-24', '2021-07-24'),
 ('gv.vuthanhha', N'Chứng chỉ Machine Learning cơ bản', N'ĐH Bách khoa Hà Nội', '2019-03-22', NULL),
 ('gv.lelandung', N'Cử nhân - Khoa học máy tính', N'ĐH Bách khoa Hà Nội', '2007-06-26', NULL),
 ('gv.lelandung', N'Chứng chỉ LEGO Education SPIKE Trainer', N'LEGO Education', '2024-11-22', '2027-11-22'),
 ('gv.lelandung', N'Chứng chỉ AI for Everyone', N'Google for Education', '2021-02-08', '2024-02-08'),
 ('gv.doanduckhai', N'Cử nhân - Giáo dục học', N'ĐH Sư phạm Hà Nội', '2002-09-19', NULL),
 ('gv.doanduckhai', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2011-04-11', NULL),
 ('gv.hothinhi', N'Cao đẳng - Kỹ thuật điều khiển & tự động hóa', N'Cao đẳng Công nghệ và Thương mại Hà Nội', '2004-06-12', NULL),
 ('gv.hothinhi', N'Chứng chỉ LEGO Education SPIKE Trainer', N'LEGO Education', '2020-03-16', '2023-03-16'),
 ('gv.vothuythuy', N'Trung cấp - Sư phạm Tin học', N'Trung cấp nghề Hải Phòng', '2002-09-27', NULL),
 ('gv.vothuythuy', N'Chứng nhận tập huấn Công dân số', N'Cục Chuyển đổi số quốc gia', '2021-02-10', NULL),
 ('gv.luuthunga', N'Cử nhân - Tâm lý học', N'ĐH Sư phạm Hà Nội', '2003-06-14', NULL),
 ('gv.luuthunga', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2019-10-22', NULL),
 ('gv.buikimthu', N'Cử nhân - Kỹ thuật điện tử - viễn thông', N'ĐH Hàng hải Việt Nam', '2011-06-13', NULL),
 ('gv.lethuytuyet', N'Cao đẳng - Ngôn ngữ Anh', N'Cao đẳng Hàng hải I', '2017-06-10', NULL),
 ('gv.lethuytuyet', N'Chứng chỉ TESOL', N'Cambridge Assessment English', '2020-03-24', NULL),
 ('gv.nghiemkhanhdung', N'Cử nhân - Tâm lý học', N'ĐH Khoa học Xã hội và Nhân văn - ĐHQG Hà Nội', '2022-06-16', NULL),
 ('gv.ngoquocviet', N'Cử nhân - Khoa học máy tính', N'ĐH Bách khoa Hà Nội', '2021-09-19', NULL),
 ('gv.ngoquocviet', N'Chứng chỉ Machine Learning cơ bản', N'ĐH Bách khoa Hà Nội', '2021-11-05', NULL),
 ('gv.tranxuanvinh', N'Thạc sỹ - Khoa học máy tính', N'ĐH Công nghệ - ĐHQG Hà Nội', '2014-09-22', NULL),
 ('gv.tranxuanvinh', N'Chứng chỉ LEGO Education SPIKE Trainer', N'LEGO Education', '2020-07-16', '2023-07-16'),
 ('gv.phamthanhdung', N'Thạc sỹ - Khoa học máy tính', N'ĐH Công nghệ - ĐHQG Hà Nội', '2021-06-26', NULL),
 ('gv.phamthanhdung', N'Chứng chỉ LEGO Education SPIKE Trainer', N'LEGO Education', '2021-01-16', '2024-01-16'),
 ('gv.duongphumanh', N'Cử nhân - Ngôn ngữ Anh', N'ĐH Thủ đô Hà Nội', '2015-06-26', NULL),
 ('gv.duongphumanh', N'Chứng chỉ TKT (Teaching Knowledge Test)', N'Cambridge Assessment English', '2016-01-16', NULL),
 ('gv.duongthihang', N'Thạc sỹ - Tâm lý học', N'ĐH Hải Phòng', '2014-06-15', NULL),
 ('gv.duongthihang', N'Chứng chỉ Sơ cấp cứu ban đầu', N'Hội Chữ thập đỏ Việt Nam', '2021-08-27', '2023-08-27'),
 ('gv.lyhonganh', N'Cử nhân - Công nghệ thông tin', N'ĐH Bách khoa Hà Nội', '2002-06-10', NULL),
 ('gv.lyhonganh', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2013-08-20', NULL),
 ('gv.duongdieutuyet', N'Cử nhân - Công nghệ thông tin', N'ĐH Hàng hải Việt Nam', '2015-06-20', NULL),
 ('gv.duongdieutuyet', N'Chứng nhận tập huấn Công dân số', N'Cục Chuyển đổi số quốc gia', '2021-12-14', NULL),
 ('gv.tasynghia', N'Cử nhân - Ngôn ngữ Anh', N'Học viện Ngoại giao', '2001-06-13', NULL),
 ('gv.tasynghia', N'Chứng chỉ TKT (Teaching Knowledge Test)', N'Cambridge Assessment English', '2017-02-14', NULL),
 ('gv.hokhackien', N'Cử nhân - Công nghệ thông tin', N'ĐH Hàng hải Việt Nam', '2007-09-22', NULL),
 ('gv.dokimyen', N'Thạc sỹ - Công tác xã hội', N'Học viện Phụ nữ Việt Nam', '2017-09-11', NULL),
 ('gv.dokimyen', N'Chứng chỉ Kĩ năng sống cho trẻ em', N'Trung ương Đoàn TNCS Hồ Chí Minh', '2018-03-23', NULL),
 ('gv.duonghoaidiep', N'Cử nhân - Khoa học máy tính', N'ĐH Khoa học Tự nhiên - ĐHQG Hà Nội', '2012-06-11', NULL),
 ('gv.duonghoaidiep', N'Chứng chỉ AI for Everyone', N'Google for Education', '2021-06-04', '2024-06-04'),
 ('gv.doanbaoly', N'Thạc sỹ - Hệ thống thông tin', N'Học viện Công nghệ Bưu chính Viễn thông', '2021-06-11', NULL),
 ('gv.vuanhmai', N'Cao đẳng - Sư phạm Tin học', N'Cao đẳng Hàng hải I', '2011-06-21', NULL),
 ('gv.lykhacphong', N'Cử nhân - Ngôn ngữ Anh', N'Học viện Ngoại giao', '2022-09-11', NULL),
 ('gv.lykhacphong', N'Chứng nhận tập huấn SGK Tiếng Anh (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2024-09-19', NULL),
 ('gv.tranhaitram', N'Thạc sỹ - Trí tuệ nhân tạo', N'ĐH Bách khoa Hà Nội', '2019-09-26', NULL),
 ('gv.tranhaitram', N'Chứng nhận giáo viên hướng dẫn cuộc thi Robothon', N'STEAM for Vietnam', '2020-06-12', NULL),
 ('gv.duongkimthu', N'Trung cấp - Ngôn ngữ Anh', N'Trung cấp Kinh tế - Kỹ thuật Hà Nội', '2000-06-26', NULL),
 ('gv.duongkimthu', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2002-01-07', NULL),
 ('gv.tranvanha', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Giáo dục - ĐHQG Hà Nội', '2001-06-15', NULL),
 ('gv.tranvanha', N'Chứng chỉ IELTS 7.5', N'IDP Education', '2009-09-12', '2011-09-12'),
 ('gv.hathuylan', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Hải Phòng', '2012-06-13', NULL),
 ('gv.hathuylan', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2017-01-12', NULL),
 ('gv.hathuylan', N'Chứng chỉ TOEIC 900', N'IIG Việt Nam', '2026-01-04', '2028-01-04'),
 ('gv.phanphuongtrang', N'Cử nhân - Kỹ thuật phần mềm', N'ĐH Công nghiệp Hà Nội', '2014-06-27', NULL),
 ('gv.vuongthevinh', N'Cử nhân - Ngôn ngữ Anh', N'ĐH Khoa học Xã hội và Nhân văn - ĐHQG Hà Nội', '2009-06-23', NULL),
 ('gv.vuongthevinh', N'Chứng chỉ TKT (Teaching Knowledge Test)', N'Cambridge Assessment English', '2021-03-02', NULL),
 ('gv.vuongthevinh', N'Chứng chỉ TESOL', N'Cambridge Assessment English', '2026-07-13', NULL),
 ('gv.taxuangiang', N'Cử nhân - An toàn thông tin', N'Học viện Kỹ thuật Mật mã', '2016-06-21', NULL),
 ('gv.nghiemhongmai', N'Cử nhân - Sư phạm Tin học', N'ĐH Giáo dục - ĐHQG Hà Nội', '2012-09-18', NULL),
 ('gv.nghiemhongmai', N'Chứng chỉ An toàn thông tin cơ bản', N'Trung tâm VNCERT/CC', '2019-01-18', '2022-01-18'),
 ('gv.nguyenthuyuyen', N'Trung cấp - Công nghệ thông tin', N'Trung cấp nghề Cơ khí I Hà Nội', '2016-09-22', NULL),
 ('gv.nguyenthuyuyen', N'Chứng nhận tập huấn SGK Tin học (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2026-03-12', NULL),
 ('gv.voquangson', N'Cử nhân - Cơ điện tử', N'ĐH Hàng hải Việt Nam', '2015-06-24', NULL),
 ('gv.voquangson', N'Chứng chỉ LEGO Education SPIKE Trainer', N'LEGO Education', '2021-11-07', '2024-11-07'),
 ('gv.phanquynhlinh', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Giáo dục - ĐHQG Hà Nội', '2000-09-26', NULL),
 ('gv.phanquynhlinh', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2000-02-06', NULL),
 ('gv.trinhngocan', N'Cao đẳng - Công nghệ thông tin', N'Cao đẳng Hàng hải I', '2001-06-26', NULL),
 ('gv.vobichan', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Ngoại ngữ - ĐHQG Hà Nội', '2013-06-10', NULL),
 ('gv.vuhoaitrang', N'Thạc sỹ - Sư phạm Giáo dục công dân', N'ĐH Khoa học Xã hội và Nhân văn - ĐHQG Hà Nội', '2004-06-24', NULL),
 ('gv.vuhoaitrang', N'Chứng chỉ Tham vấn học đường', N'ĐH Giáo dục - ĐHQG Hà Nội', '2026-01-09', NULL),
 ('gv.hoangdieuthuy', N'Cử nhân - Sư phạm Tin học', N'ĐH Sư phạm Hà Nội 2', '2003-06-11', NULL),
 ('gv.hoangdieuthuy', N'Chứng chỉ MOS Excel Specialist', N'IIG Việt Nam', '2015-12-02', '2018-12-02'),
 ('gv.dangtheviet', N'Cử nhân - Tâm lý học', N'Học viện Quản lý Giáo dục', '2005-06-23', NULL),
 ('gv.dangtheviet', N'Chứng chỉ Sơ cấp cứu ban đầu', N'Hội Chữ thập đỏ Việt Nam', '2005-12-28', '2007-12-28'),
 ('gv.haquynhhue', N'Thạc sỹ - Công nghệ thông tin', N'ĐH Công nghệ - ĐHQG Hà Nội', '2013-06-24', NULL),
 ('gv.haquynhhue', N'Chứng chỉ CCNA Cyber Ops', N'Cisco Networking Academy', '2019-04-04', '2022-04-04'),
 ('gv.thaigiaanh', N'Cử nhân - Sư phạm Tin học', N'ĐH Sư phạm Hà Nội 2', '2017-09-24', NULL),
 ('gv.thaigiaanh', N'Chứng nhận tập huấn SGK Tin học (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2021-11-25', NULL),
 ('gv.hathihang', N'Trung cấp - Sư phạm Tiếng Anh', N'Trung cấp Kinh tế - Kỹ thuật Hà Nội', '2004-06-23', NULL),
 ('gv.hathihang', N'Chứng chỉ TOEIC 900', N'IIG Việt Nam', '2013-04-25', '2015-04-25'),
 ('gv.quachdinhkhanh', N'Thạc sỹ - Khoa học máy tính', N'ĐH Công nghệ - ĐHQG Hà Nội', '2006-06-16', NULL),
 ('gv.quachdinhkhanh', N'Chứng nhận tập huấn Công dân số', N'Cục Chuyển đổi số quốc gia', '2025-08-01', NULL),
 ('gv.vodinhkhanh', N'Thạc sỹ - Công tác xã hội', N'ĐH Khoa học Xã hội và Nhân văn - ĐHQG Hà Nội', '2007-06-28', NULL),
 ('gv.vodinhkhanh', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2011-09-17', NULL),
 ('gv.voanhphuong', N'Cử nhân - Cơ điện tử', N'ĐH Bách khoa Hà Nội', '2017-06-24', NULL),
 ('gv.thaicongvu', N'Cử nhân - Tâm lý học', N'Học viện Quản lý Giáo dục', '2010-06-14', NULL),
 ('gv.thaicongvu', N'Chứng chỉ Sơ cấp cứu ban đầu', N'Hội Chữ thập đỏ Việt Nam', '2015-08-25', '2017-08-25'),
 ('gv.hovanquang', N'Cử nhân - Khoa học máy tính', N'ĐH Bách khoa Hà Nội', '2008-09-12', NULL),
 ('gv.hovanquang', N'Chứng nhận tập huấn SGK Tin học (GDPT 2018)', N'NXB Giáo dục Việt Nam', '2020-05-07', NULL),
 ('gv.lengocthinh', N'Cử nhân - Ngôn ngữ học ứng dụng', N'ĐH Hà Nội', '2011-06-11', NULL),
 ('gv.lengocthinh', N'Chứng chỉ IELTS 7.5', N'IDP Education', '2015-07-02', '2017-07-02'),
 ('gv.dinhminhquyen', N'Cử nhân - An toàn thông tin', N'ĐH Bách khoa Hà Nội', '2014-06-28', NULL),
 ('gv.caongochoang', N'Thạc sỹ - Công nghệ thông tin', N'ĐH Hàng hải Việt Nam', '2008-09-17', NULL),
 ('gv.caongochoang', N'Chứng chỉ AI for Everyone', N'Google for Education', '2024-01-24', '2027-01-24'),
 ('gv.trinhyenhong', N'Thạc sỹ - Công nghệ thông tin', N'ĐH Hàng hải Việt Nam', '2015-06-14', NULL),
 ('gv.vuonghuuhuy', N'Cử nhân - Trí tuệ nhân tạo', N'ĐH Bách khoa Hà Nội', '2022-06-26', NULL),
 ('gv.vuonghuuhuy', N'Chứng nhận giáo viên hướng dẫn cuộc thi Robothon', N'STEAM for Vietnam', '2024-10-06', NULL),
 ('gv.vuonghuuhuy', N'Chứng chỉ AI for Everyone', N'Google for Education', '2022-04-25', '2025-04-25'),
 ('gv.phanhoaimai', N'Cao đẳng - Tâm lý học', N'Cao đẳng Sư phạm Trung ương', '2020-06-21', NULL),
 ('gv.phanhoaimai', N'Chứng chỉ nghiệp vụ sư phạm', N'ĐH Hải Phòng', '2022-09-21', NULL),
 ('gv.hoanghailinh', N'Cử nhân - Sư phạm Tiếng Anh', N'ĐH Thủ đô Hà Nội', '2004-09-10', NULL),
 ('gv.maithuyhanh', N'Thạc sỹ - Kỹ thuật điều khiển & tự động hóa', N'ĐH Điện lực', '2015-09-23', NULL),
 ('gv.maithuyhanh', N'Chứng chỉ AI for Everyone', N'Google for Education', '2020-03-25', '2023-03-25');

INSERT INTO Certificate (TeacherId, Name, Issuer, IssueDate, ExpiryDate, CreatedBy)
SELECT t.Id, c.Name, c.Issuer, c.IssueDate, c.ExpiryDate, @Admin
FROM #Cert c
JOIN AppUser u ON u.Username  = c.Username
JOIN Teacher t ON t.AppUserId = u.Id;

PRINT N'4) Đã thêm ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' bằng cấp / chứng chỉ.';


/* =====================================================================
   5) MÔN DẠY ĐƯỢC (TeacherSubject)
   ---------------------------------------------------------------------
   Mỗi GV 1-3 môn, chọn trong đúng nhóm chuyên môn khớp với bằng cấp —
   GV bằng Sư phạm Tiếng Anh sẽ không bị gán dạy Robotics. Một số ít GV
   dạy chéo nhóm ở các cặp hợp lý (Tin học <-> Kĩ năng số <-> STEM-AI).

   ProficiencyLevel (1-5) dùng cho AI matching sau này: môn chính cao
   nhất rồi giảm dần, cộng thêm theo thâm niên và trình độ Thạc sỹ.
   ===================================================================== */
CREATE TABLE #TS (
    Username VARCHAR(50) COLLATE DATABASE_DEFAULT,
    SubjCode VARCHAR(20) COLLATE DATABASE_DEFAULT,
    Prof     TINYINT
);

INSERT INTO #TS (Username, SubjCode, Prof) VALUES
 ('gv.hothanhhuong', 'SA01', 5),
 ('gv.hothanhhuong', 'SA07', 4),
 ('gv.hoangphuongnhi', 'SA02', 4),
 ('gv.hoangphuongnhi', 'SA04', 3),
 ('gv.taphuhuy', 'TA01', 4),
 ('gv.taphuhuy', 'TA06', 3),
 ('gv.quachnhatha', 'TA02', 5),
 ('gv.homanhvinh', 'TA03', 4),
 ('gv.homanhvinh', 'TA02', 3),
 ('gv.trankimthao', 'SA03', 5),
 ('gv.trankimthao', 'SA02', 4),
 ('gv.luuthedat', 'TH01', 5),
 ('gv.luuthedat', 'TH03', 4),
 ('gv.nguyenvantuyet', 'TA04', 5),
 ('gv.buihaihang', 'SA04', 5),
 ('gv.lammaily', 'SA05', 4),
 ('gv.vuhuuphong', 'SA06', 5),
 ('gv.vuhuuphong', 'SA07', 4),
 ('gv.vuhuuphong', 'SA05', 3),
 ('gv.dangthanhtien', 'TA05', 5),
 ('gv.dangthanhtien', 'TA03', 4),
 ('gv.tobaomai', 'KNS01', 5),
 ('gv.tobaomai', 'KNS04', 4),
 ('gv.tobaomai', 'KNS03', 3),
 ('gv.ngotantrung', 'TA06', 4),
 ('gv.phankimdiep', 'SA07', 5),
 ('gv.phankimdiep', 'SA04', 4),
 ('gv.tobichdung', 'SA01', 4),
 ('gv.tobichdung', 'SA03', 3),
 ('gv.vohaihuyen', 'SA02', 5),
 ('gv.caokhanhoanh', 'SA03', 5),
 ('gv.caokhanhoanh', 'SA01', 4),
 ('gv.hakimha', 'KNS05', 4),
 ('gv.hakimha', 'KNS06', 3),
 ('gv.chuvietnghia', 'TA01', 5),
 ('gv.chuvietnghia', 'KNS01', 4),
 ('gv.doanthuylinh', 'KNS02', 5),
 ('gv.vuthanhtrang', 'SA04', 5),
 ('gv.vuthanhtrang', 'SA06', 3),
 ('gv.vuongmaitrinh', 'TH02', 4),
 ('gv.vuongmaitrinh', 'TH01', 3),
 ('gv.vuongmaitrinh', 'KNS06', 2),
 ('gv.dangmanhhai', 'SA05', 5),
 ('gv.dangmanhhai', 'KNS07', 4),
 ('gv.lethutrang', 'SA06', 5),
 ('gv.lethutrang', 'SA03', 4),
 ('gv.lethutrang', 'SA05', 3),
 ('gv.tangochong', 'TA02', 4),
 ('gv.tangochong', 'TA03', 3),
 ('gv.buiyentuyet', 'TH03', 5),
 ('gv.buiyentuyet', 'KNS06', 4),
 ('gv.doanthuynhung', 'SA07', 5),
 ('gv.doanthuynhung', 'SA05', 4),
 ('gv.tranviettu', 'TH01', 5),
 ('gv.tranviettu', 'TH03', 4),
 ('gv.tranviettu', 'KNS06', 3),
 ('gv.hangochien', 'SA01', 5),
 ('gv.vuongtanminh', 'SA02', 4),
 ('gv.buithiha', 'KNS03', 5),
 ('gv.buithiha', 'KNS04', 4),
 ('gv.buithiha', 'KNS05', 3),
 ('gv.quachtuyethien', 'KNS04', 5),
 ('gv.tranhoaimy', 'TA03', 4),
 ('gv.tranhoaimy', 'TA04', 3),
 ('gv.phamdieugiang', 'TA04', 5),
 ('gv.nghiemquocnam', 'KNS01', 5),
 ('gv.nghiemquocnam', 'KNS03', 4),
 ('gv.phamthuynga', 'TH02', 5),
 ('gv.phamthuynga', 'TH01', 4),
 ('gv.dinhngocnhung', 'SA03', 4),
 ('gv.dinhngocnhung', 'SA07', 3),
 ('gv.dinhngocnhung', 'SA01', 2),
 ('gv.doyenan', 'TH03', 4),
 ('gv.doyenan', 'TH02', 3),
 ('gv.doyenan', 'SA01', 2),
 ('gv.doanphuonggiang', 'TH01', 4),
 ('gv.doanphuonggiang', 'TH02', 3),
 ('gv.phamdinhthang', 'TA05', 4),
 ('gv.tatienvu', 'TH02', 5),
 ('gv.tatienvu', 'TH01', 3),
 ('gv.luuvanmai', 'TA06', 5),
 ('gv.luuvanmai', 'TA05', 4),
 ('gv.luuvanmai', 'TA01', 3),
 ('gv.nguyenducgiang', 'SA04', 5),
 ('gv.nguyenducgiang', 'SA06', 3),
 ('gv.nguyenducgiang', 'TH01', 2),
 ('gv.buingochai', 'TA01', 5),
 ('gv.vuthanhha', 'SA05', 5),
 ('gv.vuthanhha', 'SA04', 4),
 ('gv.lelandung', 'SA06', 4),
 ('gv.lelandung', 'SA04', 3),
 ('gv.lelandung', 'SA05', 2),
 ('gv.doanduckhai', 'KNS02', 4),
 ('gv.doanduckhai', 'KNS01', 3),
 ('gv.hothinhi', 'SA07', 5),
 ('gv.vothuythuy', 'KNS06', 5),
 ('gv.vothuythuy', 'KNS05', 4),
 ('gv.luuthunga', 'KNS03', 5),
 ('gv.luuthunga', 'KNS04', 4),
 ('gv.buikimthu', 'SA01', 5),
 ('gv.buikimthu', 'KNS07', 4),
 ('gv.lethuytuyet', 'TA02', 5),
 ('gv.lethuytuyet', 'KNS01', 4),
 ('gv.nghiemkhanhdung', 'KNS04', 5),
 ('gv.ngoquocviet', 'SA02', 5),
 ('gv.tranxuanvinh', 'SA03', 5),
 ('gv.tranxuanvinh', 'SA01', 3),
 ('gv.phamthanhdung', 'SA04', 5),
 ('gv.phamthanhdung', 'SA03', 4),
 ('gv.duongphumanh', 'TA03', 5),
 ('gv.duongthihang', 'KNS01', 5),
 ('gv.duongthihang', 'KNS02', 4),
 ('gv.lyhonganh', 'KNS07', 5),
 ('gv.lyhonganh', 'KNS05', 4),
 ('gv.duongdieutuyet', 'KNS05', 5),
 ('gv.duongdieutuyet', 'KNS06', 4),
 ('gv.tasynghia', 'TA04', 4),
 ('gv.hokhackien', 'SA05', 5),
 ('gv.hokhackien', 'SA06', 4),
 ('gv.dokimyen', 'KNS02', 5),
 ('gv.duonghoaidiep', 'SA06', 5),
 ('gv.duonghoaidiep', 'SA03', 4),
 ('gv.doanbaoly', 'TH03', 5),
 ('gv.vuanhmai', 'TH01', 5),
 ('gv.lykhacphong', 'TA05', 4),
 ('gv.lykhacphong', 'TA02', 3),
 ('gv.tranhaitram', 'SA07', 5),
 ('gv.duongkimthu', 'TA06', 4),
 ('gv.duongkimthu', 'TA03', 3),
 ('gv.duongkimthu', 'KNS01', 2),
 ('gv.tranvanha', 'TA01', 5),
 ('gv.hathuylan', 'TA02', 4),
 ('gv.phanphuongtrang', 'TH02', 5),
 ('gv.phanphuongtrang', 'TH03', 4),
 ('gv.vuongthevinh', 'TA03', 4),
 ('gv.vuongthevinh', 'TA06', 3),
 ('gv.vuongthevinh', 'TA02', 2),
 ('gv.taxuangiang', 'KNS06', 4),
 ('gv.taxuangiang', 'KNS07', 3),
 ('gv.taxuangiang', 'SA07', 2),
 ('gv.nghiemhongmai', 'KNS07', 4),
 ('gv.nguyenthuyuyen', 'TH03', 4),
 ('gv.voquangson', 'SA01', 4),
 ('gv.voquangson', 'SA05', 3),
 ('gv.phanquynhlinh', 'TA04', 5),
 ('gv.trinhngocan', 'SA02', 5),
 ('gv.trinhngocan', 'SA01', 4),
 ('gv.vobichan', 'TA05', 4),
 ('gv.vobichan', 'TA02', 3),
 ('gv.vobichan', 'TA03', 2),
 ('gv.vuhoaitrang', 'KNS03', 5),
 ('gv.hoangdieuthuy', 'TH01', 5),
 ('gv.hoangdieuthuy', 'TH02', 4),
 ('gv.dangtheviet', 'KNS04', 5),
 ('gv.dangtheviet', 'KNS02', 4),
 ('gv.dangtheviet', 'KNS05', 3),
 ('gv.haquynhhue', 'KNS05', 5),
 ('gv.thaigiaanh', 'TH02', 5),
 ('gv.thaigiaanh', 'TH01', 4),
 ('gv.thaigiaanh', 'TH03', 3),
 ('gv.hathihang', 'TA06', 4),
 ('gv.hathihang', 'TA04', 3),
 ('gv.quachdinhkhanh', 'KNS06', 5),
 ('gv.quachdinhkhanh', 'KNS07', 3),
 ('gv.vodinhkhanh', 'KNS01', 5),
 ('gv.vodinhkhanh', 'KNS04', 3),
 ('gv.voanhphuong', 'SA03', 5),
 ('gv.voanhphuong', 'SA04', 4),
 ('gv.voanhphuong', 'SA07', 3),
 ('gv.thaicongvu', 'KNS02', 4),
 ('gv.thaicongvu', 'KNS04', 3),
 ('gv.thaicongvu', 'KNS03', 2),
 ('gv.hovanquang', 'TH03', 5),
 ('gv.hovanquang', 'KNS05', 4),
 ('gv.lengocthinh', 'TA01', 4),
 ('gv.dinhminhquyen', 'KNS07', 4),
 ('gv.dinhminhquyen', 'KNS05', 3),
 ('gv.dinhminhquyen', 'KNS06', 2),
 ('gv.caongochoang', 'SA04', 5),
 ('gv.caongochoang', 'SA03', 3),
 ('gv.trinhyenhong', 'TH01', 5),
 ('gv.trinhyenhong', 'TH03', 3),
 ('gv.vuonghuuhuy', 'SA05', 4),
 ('gv.vuonghuuhuy', 'SA04', 3),
 ('gv.phanhoaimai', 'KNS03', 4),
 ('gv.phanhoaimai', 'KNS04', 3),
 ('gv.hoanghailinh', 'TA02', 4),
 ('gv.hoanghailinh', 'TA01', 3),
 ('gv.hoanghailinh', 'KNS01', 2),
 ('gv.maithuyhanh', 'SA06', 5);

INSERT INTO TeacherSubject (TeacherId, SubjectId, ProficiencyLevel)
SELECT t.Id, s.Id, ts.Prof
FROM #TS ts
JOIN AppUser u ON u.Username  = ts.Username
JOIN Teacher t ON t.AppUserId = u.Id
JOIN Subject s ON s.Code = ts.SubjCode AND s.IsDeleted = 0;

PRINT N'5) Đã gán ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' liên kết giáo viên - môn học.';


/* ---- Dọn bảng tạm & chốt giao dịch ---- */
DROP TABLE #Subj; DROP TABLE #GV; DROP TABLE #Cert; DROP TABLE #TS;

COMMIT TRANSACTION;

PRINT N'';
PRINT N'>>> XONG. Đăng nhập thử: gv.hothanhhuong / Tsdms@123';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DROP TABLE IF EXISTS #Subj;
    DROP TABLE IF EXISTS #GV;
    DROP TABLE IF EXISTS #Cert;
    DROP TABLE IF EXISTS #TS;
    PRINT N'!!! LỖI — đã rollback toàn bộ, DB giữ nguyên như trước khi chạy.';
    THROW;
END CATCH
GO
