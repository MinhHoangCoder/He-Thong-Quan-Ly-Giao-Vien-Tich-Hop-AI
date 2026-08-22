/* =====================================================================
   TSDMS — SEED DỮ LIỆU LỚN CHO DEMO (đợt 2026-08)

   CHẠY THẾ NÀO
     Mở file trong SSMS (đã kết nối DB TSDMS) rồi Execute. Hoặc:
       sqlcmd -S localhost -E -d TSDMS -i database/seed/TSDMS_Seed_2026_DuLieuLon.sql
     KHÔNG đặt vào Flyway: migration chạy ở MỌI môi trường, còn đây là dữ
     liệu demo chỉ dành cho máy dev/máy demo đồ án.

     CẢNH BÁO: file này XÓA TOÀN BỘ phân công / lịch dạy / chấm công / bảng
     lương đang có rồi sinh lại. Sao lưu trước khi chạy:
       BACKUP DATABASE TSDMS TO DISK = N'D:\TSDMS_truoc_seed.bak' WITH INIT;

   SINH RA GÌ (số liệu thực đo sau khi chạy)
     150 giáo viên — 30 cơ hữu / 120 thỉnh giảng, trong đó 10 người đã nghỉ
     việc hoặc tạm đình chỉ nên không nhận phân công mới.
     27 trường đang hợp tác · 864 lớp · 444 phiếu phân công · 4.749 ô thời
     khóa biểu · 86.745 buổi dạy · 55.125 bản ghi chấm công · 1.400 phiếu
     lương trải đủ ba trạng thái Nháp / Đã chốt / Đã trả.
     Chạy hết khoảng 60 giây trên máy dev.

   GIAI ĐOẠN
     Học kỳ 1 năm học 2025-2026 : 08/09/2025 -> 16/01/2026
     Học kỳ 2 năm học 2025-2026 : 02/02/2026 -> 29/05/2026
     Học kỳ 1 năm học 2026-2027 : 17/08/2026 -> 15/01/2027   (đang chạy)

     Tựu trường 17/08 theo đúng lịch Hải Phòng, không phải ngày bịa ra cho
     tiện. Nếu để năm học mới bắt đầu 05/09 thì cuối tháng 8 rơi trúng lỗ
     nghỉ hè: mở phần mềm lên, mọi màn "tuần này / tháng này" trống trơn và
     người xem tưởng hệ thống chưa có dữ liệu.

   MÔ HÌNH XẾP LỊCH — vì sao chia như thế này
     Đơn vị phân việc là LÀN: một giáo viên phụ trách trọn một buổi (sáng
     hoặc chiều) tại một trường trong một ngày.
       - buổi sáng  : 1 làn mỗi trường
       - buổi chiều : 2 làn ở trường tiểu học, 1 làn ở THCS
     Chiều nhiều hơn sáng vì trung tâm dạy môn phụ (STEM, kỹ năng số) — nhà
     trường giữ buổi sáng cho môn chính khóa. Kết quả: 900 tiết chiều so với
     675 tiết sáng mỗi tuần.
     Làn giao cho CƠ HỮU thì một người dạy trọn buổi (19-20 tiết/tuần); làn
     giao cho THỈNH GIẢNG thì cắt đôi, mỗi người 2-3 tiết (6-12 tiết/tuần).

   VÌ SAO KHÔNG BAO GIỜ TRÙNG LỊCH
     Cell = (ngày, buổi) — có đúng 10 cell trong một tuần. Trong một cell,
     các phần việc được đánh số liên tiếp rồi lấy giáo viên bằng phép chia dư
     trên danh sách. Số phần việc của một cell (nhiều nhất 54) LUÔN nhỏ hơn
     số giáo viên trong danh sách (110-120), nên phép chia dư không thể trả
     về cùng một người hai lần trong cùng một cell. Đó là toàn bộ chứng minh
     cho luật "một giáo viên không ở hai nơi cùng lúc" — không cần vòng lặp
     dò trùng nào. Phía lớp cũng vậy: hai làn cùng trường cùng tiết lệch nhau
     5 bậc trong danh sách lớp, mà trường ít lớp nhất cũng có 12 lớp.
     PHẦN 13 đếm lại cả hai luật, phải ra 0.

   DỮ LIỆU NÀY LÀ DỮ LIỆU GIẢ
     Họ tên, CCCD, số điện thoại, địa chỉ đều sinh tự động và chỉ đúng ĐỊNH
     DẠNG, không mô tả người thật nào. Email dùng đuôi @tsdms.local — tên
     miền nội bộ không định tuyến ra Internet nên luồng Quên mật khẩu không
     thể gửi nhầm vào hòm thư của ai.
     => CHỈ dùng ở máy dev/demo. KHÔNG nạp vào môi trường có người dùng thật.

   CHẠY LẠI ĐƯỢC: phần trường/lớp/giáo viên có chốt chặn NOT EXISTS nên
   không tạo trùng; phần điều phối xóa sạch rồi sinh lại từ đầu.

   GỠ RA: chạy database/seed/TSDMS_Rollback_2026_DuLieuLon.sql
   ===================================================================== */

USE TSDMS;
GO

/* QUOTED_IDENTIFIER phải BẬT: nhiều bảng có filtered index (UX_AppUser_Username,
   UX_Class_School_Name_Year...), mà SQL Server từ chối mọi INSERT lên bảng có
   filtered index khi tùy chọn này tắt (lỗi 1934). SSMS bật sẵn, sqlcmd thì
   KHÔNG — khai ở đây để file chạy được ở cả hai nơi. */
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO


/* =====================================================================
   PHẦN 0 — DỌN BẢNG SAO LƯU TẠM CỦA CÁC ĐỢT SEED CŨ

   Các đợt seed trước để lại hàng chục bảng zz_bak*_<Tên> (~45.000 dòng).
   Không entity nào map tới chúng, không câu query nào đọc chúng, và chúng
   không có trong database/schema/TSDMS_Schema.sql — mở SSMS ra chỉ thấy một
   danh sách bảng dài gấp đôi thực tế.
   ===================================================================== */
PRINT N'PHẦN 0 — dọn bảng sao lưu tạm của các đợt seed cũ';
GO

DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'DROP TABLE dbo.' + QUOTENAME(name) + N';' + CHAR(10)
  FROM sys.tables WHERE name LIKE 'zz[_]bak%';
IF LEN(@sql) > 0 EXEC sp_executesql @sql;
GO


/* =====================================================================
   PHẦN 1 — MỞ THÊM TRƯỜNG, SEED LỚP VÀ PHÒNG HỌC
   ===================================================================== */
PRINT N'PHẦN 1 — trường, lớp, phòng học';
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');

/* ---- 1.0 CỘT MỐC cho file rollback ----
   Ghi lại Id lớn nhất của từng bảng TRƯỚC khi chèn dòng nào. File rollback
   chỉ xóa những dòng có Id LỚN HƠN mốc, nên nó không thể đụng nhầm vào dữ
   liệu có sẵn. Bản đầu tiên đoán theo CreatedAt và đã xóa lố hai lớp vốn
   được tạo cùng ngày chạy seed — đoán theo thời gian là không đủ chặt.

   Bảng này CỐ Ý sống sót sau khi seed chạy xong (khác các bảng seed_* khác
   bị dọn ở PHẦN 13): rollback chạy ở phiên khác, có khi vài ngày sau. */
IF OBJECT_ID('dbo.seed_2026_Moc', 'U') IS NULL
CREATE TABLE seed_2026_Moc (Bang SYSNAME PRIMARY KEY, MocId INT NOT NULL, GhiLuc DATETIME2(3) NOT NULL);

DELETE FROM seed_2026_Moc;
INSERT INTO seed_2026_Moc (Bang, MocId, GhiLuc) VALUES
 ('SchoolClass', ISNULL((SELECT MAX(Id) FROM SchoolClass), 0), @now),
 ('Room',        ISNULL((SELECT MAX(Id) FROM Room),        0), @now),
 ('AppUser',     ISNULL((SELECT MAX(Id) FROM AppUser),     0), @now),
 ('Teacher',     ISNULL((SELECT MAX(Id) FROM Teacher),     0), @now),
 ('Contract',    ISNULL((SELECT MAX(Id) FROM Contract),    0), @now);

/* ---- 1.1 Mở lại các trường đang NGỪNG HỢP TÁC và chưa có lớp nào ----
   Đây là các trường đã có sẵn khung tiết và phòng học nhưng chưa từng mở lớp.
   Các trường HẾT HẠN HỢP ĐỒNG (Status = 'EXPIRED') CỐ Ý để nguyên: chúng là
   dữ liệu để thử hai chốt chặn "trường ngừng hợp tác" và "trường chưa có
   lớp" — không có chúng thì hai luật đó không demo được. */
UPDATE School
   SET Status            = 'ACTIVE',
       ContractStartDate = '2025-08-01',
       ContractEndDate   = '2029-06-30',
       UpdatedAt         = @now,
       UpdatedBy         = @admin
 WHERE IsDeleted = 0
   AND Status = 'INACTIVE'
   AND NOT EXISTS (SELECT 1 FROM SchoolClass c WHERE c.SchoolId = School.Id AND c.IsDeleted = 0);

/* ---- 1.2 Gia hạn hợp đồng đã hết hạn của trường đang dạy ----
   School.effectiveStatus() suy trạng thái theo NGÀY: trường ACTIVE mà
   ContractEndDate đã qua vẫn bị coi là hết hạn và KHÔNG nhận phân công mới.
   Không gia hạn thì lịch năm học mới không xếp được vào các trường này, dù
   trên màn hình chúng vẫn hiện "Đang hợp tác". */
UPDATE School
   SET ContractEndDate = '2029-06-30',
       UpdatedAt       = @now,
       UpdatedBy       = @admin
 WHERE IsDeleted = 0
   AND Status = 'ACTIVE'
   AND ContractEndDate < '2027-07-01'
   AND EXISTS (SELECT 1 FROM SchoolClass c WHERE c.SchoolId = School.Id AND c.IsDeleted = 0);

/* ---- 1.3 Seed lớp cho mọi trường ACTIVE còn thiếu ----
   Cấp học suy từ SỐ TIẾT trong khung tiết của trường: khung tiểu học 10 tiết
   (5 sáng + 5 chiều), khung THCS 9 tiết (5 + 4) — đúng bộ khung chuẩn ở
   PeriodService.applyStandardFrame. Suy từ dữ liệu thay vì liệt kê Id để
   thêm trường sau này không phải sửa file. */
;WITH Truong AS (
    SELECT s.Id AS SchoolId,
           CASE WHEN (SELECT COUNT(*) FROM Period p WHERE p.SchoolId = s.Id AND p.IsDeleted = 0) >= 10
                THEN 1 ELSE 0 END AS TieuHoc
      FROM School s
     WHERE s.IsDeleted = 0 AND s.Status = 'ACTIVE'
),
Khoi AS (
    SELECT t.SchoolId, k.n AS GradeLevel
      FROM Truong t
     CROSS APPLY (SELECT v.number AS n FROM master.dbo.spt_values v
                   WHERE v.type = 'P' AND v.number BETWEEN 1 AND 9) k
     WHERE (t.TieuHoc = 1 AND k.n BETWEEN 1 AND 5)
        OR (t.TieuHoc = 0 AND k.n BETWEEN 6 AND 9)
),
Nam AS (SELECT '2025-2026' AS y UNION ALL SELECT '2026-2027'),
LopCan AS (
    SELECT kh.SchoolId,
           CONCAT(kh.GradeLevel, 'A', i.n)     AS Name,
           CAST(kh.GradeLevel AS NVARCHAR(10)) AS GradeLevel,
           n.y                                 AS SchoolYear
      FROM Khoi kh
     CROSS JOIN Nam n
     CROSS APPLY (SELECT v.number AS n FROM master.dbo.spt_values v
                   WHERE v.type = 'P' AND v.number BETWEEN 1 AND 3) i
)
INSERT INTO SchoolClass (SchoolId, Name, GradeLevel, SchoolYear, Status, IsDeleted, CreatedAt, CreatedBy)
SELECT l.SchoolId, l.Name, l.GradeLevel, l.SchoolYear, 'ACTIVE', 0, @now, @admin
  FROM LopCan l
 WHERE NOT EXISTS (SELECT 1 FROM SchoolClass c
                    WHERE c.SchoolId = l.SchoolId AND c.Name = l.Name
                      AND c.SchoolYear = l.SchoolYear AND c.IsDeleted = 0);

/* ---- 1.4 Phòng học cho trường còn thiếu ---- */
;WITH ThieuPhong AS (
    SELECT s.Id AS SchoolId FROM School s
     WHERE s.IsDeleted = 0 AND s.Status = 'ACTIVE'
       AND (SELECT COUNT(*) FROM Room r WHERE r.SchoolId = s.Id AND r.IsDeleted = 0) < 4
)
INSERT INTO Room (SchoolId, Name, Building, Floor, Type, Capacity, Status, IsDeleted, CreatedAt, CreatedBy)
SELECT t.SchoolId, CONCAT(N'Phòng ', i.n), N'Nhà A', CAST(((i.n - 1) / 2 + 1) AS VARCHAR(10)),
       CASE WHEN i.n % 2 = 0 THEN 'LAB' ELSE 'CLASSROOM' END, 35, 'AVAILABLE', 0, @now, @admin
  FROM ThieuPhong t
 CROSS APPLY (SELECT v.number AS n FROM master.dbo.spt_values v
               WHERE v.type = 'P' AND v.number BETWEEN 1 AND 4) i;
GO


/* =====================================================================
   PHẦN 2 — NÂNG LÊN 150 GIÁO VIÊN, TỈ LỆ 30 CƠ HỮU / 120 THỈNH GIẢNG
   ===================================================================== */
PRINT N'PHẦN 2 — giáo viên, hợp đồng, môn dạy được';
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');
DECLARE @hash VARCHAR(255) = (SELECT TOP 1 PasswordHash FROM AppUser WHERE Username = 'admin');
DECLARE @roleTeacher INT = (SELECT Id FROM Role WHERE Name = 'TEACHER');

/* ---- 2.1 Danh sách 50 giáo viên bổ sung ----
   Username bỏ dấu để gõ được trên mọi bàn phím. Mật khẩu dùng chung với các
   tài khoản seed cũ (Tsdms@123) — lấy trực tiếp chuỗi băm của tài khoản
   admin thay vì ghi cứng, để đổi thuật toán băm không phải sửa file này. */
DECLARE @gv TABLE (rn INT IDENTITY(1,1), LastName NVARCHAR(100), FirstName NVARCHAR(100), UserName VARCHAR(100));
INSERT INTO @gv (LastName, FirstName, UserName) VALUES
(N'Đặng Minh',    N'Khoa',   'gv.dangminhkhoa'),   (N'Đặng Thu',     N'Trang',  'gv.dangthutrang'),
(N'Hoàng Bảo',    N'Long',   'gv.hoangbaolong'),   (N'Hoàng Diệu',   N'Linh',   'gv.hoangdieulinh'),
(N'Lâm Quốc',     N'Đạt',    'gv.lamquocdat'),     (N'Lâm Thanh',    N'Vân',    'gv.lamthanhvan'),
(N'Lương Đức',    N'Duy',    'gv.luongducduy'),    (N'Lương Hà',     N'My',     'gv.luonghamy'),
(N'Mai Anh',      N'Tuấn',   'gv.maianhtuan'),     (N'Mai Phương',   N'Thảo',   'gv.maiphuongthao'),
(N'Ngô Gia',      N'Bảo',    'gv.ngogiabao'),      (N'Ngô Khánh',    N'Chi',    'gv.ngokhanhchi'),
(N'Nguyễn Đăng',  N'Khôi',   'gv.nguyendangkhoi'), (N'Nguyễn Hải',   N'Yến',    'gv.nguyenhaiyen'),
(N'Nguyễn Trọng', N'Nghĩa',  'gv.nguyentrongnghia'),(N'Nguyễn Tuệ',  N'Minh',   'gv.nguyentueminh'),
(N'Phạm Đình',    N'Phúc',   'gv.phamdinhphuc'),   (N'Phạm Lan',     N'Hương',  'gv.phamlanhuong'),
(N'Phạm Quang',   N'Vinh',   'gv.phamquangvinh'),  (N'Phạm Tuyết',   N'Nhung',  'gv.phamtuyetnhung'),
(N'Phan Bá',      N'Thắng',  'gv.phanbathang'),    (N'Phan Ngọc',    N'Ánh',    'gv.phanngocanh'),
(N'Quách Hữu',    N'Nam',    'gv.quachhuunam'),    (N'Quách Thùy',   N'Dương',  'gv.quachthuyduong'),
(N'Tạ Công',      N'Hoàng',  'gv.taconghoang'),    (N'Tạ Mỹ',        N'Hạnh',   'gv.tamyhanh'),
(N'Thái Văn',     N'Cường',  'gv.thaivancuong'),   (N'Thái Xuân',    N'Mai',    'gv.thaixuanmai'),
(N'Trần Đắc',     N'Kiên',   'gv.trandackien'),    (N'Trần Hồng',    N'Nhung',  'gv.tranhongnhung'),
(N'Trần Mạnh',    N'Hùng',   'gv.tranmanhhung'),   (N'Trần Tố',      N'Uyên',   'gv.trantouyen'),
(N'Trịnh Bá',     N'Lộc',    'gv.trinhbaloc'),     (N'Trịnh Diệu',   N'Thúy',   'gv.trinhdieuthuy'),
(N'Trương Hoài',  N'Phong',  'gv.truonghoaiphong'),(N'Trương Kiều',  N'Oanh',   'gv.truongkieuoanh'),
(N'Văn Đức',      N'Toàn',   'gv.vanductoan'),     (N'Văn Thị',      N'Loan',   'gv.vanthiloan'),
(N'Vũ Đình',      N'Sơn',    'gv.vudinhson'),      (N'Vũ Hạ',        N'Vy',     'gv.vuhavy'),
(N'Vương Chí',    N'Thành',  'gv.vuongchithanh'),  (N'Vương Tâm',    N'Như',    'gv.vuongtamnhu'),
(N'Bạch Đông',    N'Quân',   'gv.bachdongquan'),   (N'Bạch Hạnh',    N'Nguyên', 'gv.bachhanhnguyen'),
(N'Chu Kiến',     N'An',     'gv.chukienan'),      (N'Chu Ngân',     N'Hà',     'gv.chunganha'),
(N'Dương Bảo',    N'Trung',  'gv.duongbaotrung'),  (N'Dương Cẩm',    N'Tú',     'gv.duongcamtu'),
(N'Hà Sỹ',        N'Trường', 'gv.hasytruong'),     (N'Hà Yến',       N'Ngọc',   'gv.hayenngoc');

/* ---- 2.2 Tài khoản đăng nhập ---- */
INSERT INTO AppUser (Username, PasswordHash, Email, Status, IsDeleted, CreatedAt, CreatedBy)
SELECT g.UserName, @hash, g.UserName + '@tsdms.local', 'ACTIVE', 0, @now, @admin
  FROM @gv g
 WHERE NOT EXISTS (SELECT 1 FROM AppUser u WHERE u.Username = g.UserName AND u.IsDeleted = 0);

INSERT INTO UserRole (AppUserId, RoleId, AssignedAt)
SELECT u.Id, @roleTeacher, @now
  FROM AppUser u JOIN @gv g ON g.UserName = u.Username
 WHERE NOT EXISTS (SELECT 1 FROM UserRole r WHERE r.AppUserId = u.Id AND r.RoleId = @roleTeacher);

/* ---- 2.3 Hồ sơ giáo viên ----
   Ngày vào làm rải đều 2019 -> 2026 để cột thâm niên trên màn Giáo viên có
   dải giá trị thật, không phải 50 người cùng một ngày. */
INSERT INTO Teacher (AppUserId, BranchId, DateOfBirth, Gender, IdCardNo, Phone, Address,
                     HireDate, EmploymentType, Status, IsDeleted, CreatedAt, CreatedBy,
                     FirstName, LastName, TeachingExperience)
SELECT u.Id, 1,
       DATEADD(DAY, (g.rn * 37) % 3650, '1988-01-01'),
       g.rn % 2,
       RIGHT('000000000000' + CAST(31000000000 + g.rn * 971 AS VARCHAR(12)), 12),
       '09' + RIGHT('00000000' + CAST(41000000 + g.rn * 613 AS VARCHAR(8)), 8),
       CONCAT(N'Số ', (g.rn * 7) % 200 + 1, N' đường Lạch Tray, Ngô Quyền, Hải Phòng'),
       DATEADD(DAY, (g.rn * 53) % 2555, '2019-08-01'),
       'THINH_GIANG', 'ACTIVE', 0, @now, @admin,
       g.FirstName, g.LastName,
       CONCAT(N'Đã dạy ', 2 + g.rn % 9, N' năm ở các trung tâm STEM / kỹ năng số.')
  FROM @gv g JOIN AppUser u ON u.Username = g.UserName
 WHERE NOT EXISTS (SELECT 1 FROM Teacher t WHERE t.AppUserId = u.Id);
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');

/* ---- 2.4 Chốt tỉ lệ 30 cơ hữu / 120 thỉnh giảng ----
   CƠ HỮU = 30 người VÀO LÀM SỚM NHẤT. Chọn theo thâm niên chứ không bốc
   ngẫu nhiên: trung tâm giữ biên chế cho người gắn bó lâu, và khi bị hỏi
   "sao người này là cơ hữu" thì có câu trả lời đọc thẳng từ dữ liệu. */
UPDATE Teacher SET EmploymentType = 'THINH_GIANG', UpdatedAt = @now, UpdatedBy = @admin
 WHERE IsDeleted = 0;

;WITH CoHuu AS (
    SELECT TOP 30 Id FROM Teacher
     WHERE IsDeleted = 0 AND Status = 'ACTIVE'
     ORDER BY HireDate ASC, Id ASC
)
UPDATE Teacher SET EmploymentType = 'CO_HUU', UpdatedAt = @now, UpdatedBy = @admin
 WHERE Id IN (SELECT Id FROM CoHuu);

/* ---- 2.5 Hợp đồng ----
   Thỉnh giảng KHÔNG có lương cứng: PayrollService.baseSalaryOf chỉ đọc
   Contract.BaseSalary cho giáo viên CƠ HỮU. Để số khác 0 ở hợp đồng thỉnh
   giảng là dữ liệu nói dối — màn Hợp đồng ghi 6,5 triệu mà phiếu lương
   không bao giờ cộng vào, và không màn hình nào giải thích vì sao. */
INSERT INTO Contract (TeacherId, ContractNo, StartDate, EndDate, BaseSalary, Allowance,
                      Status, IsDeleted, CreatedAt, CreatedBy)
SELECT t.Id,
       CONCAT('HD-2025-GV', RIGHT('000' + CAST(t.Id AS VARCHAR(4)), 3)),
       '2025-08-01', '2027-07-31', 0, 0, 'ACTIVE', 0, @now, @admin
  FROM Teacher t
 WHERE t.IsDeleted = 0
   AND NOT EXISTS (SELECT 1 FROM Contract c WHERE c.TeacherId = t.Id AND c.IsDeleted = 0);

UPDATE c
   SET c.BaseSalary = CASE WHEN t.EmploymentType = 'CO_HUU' THEN 6500000 ELSE 0 END,
       c.Allowance  = CASE WHEN t.EmploymentType = 'CO_HUU' THEN  800000 ELSE 0 END,
       c.UpdatedAt  = @now, c.UpdatedBy = @admin
  FROM Contract c JOIN Teacher t ON t.Id = c.TeacherId
 WHERE c.IsDeleted = 0;

/* ---- 2.6 Môn dạy được ----
   Mỗi giáo viên ít nhất một môn, chia đều 23 môn. Giáo viên không có môn nào
   thì phiếu phân công không có môn để ghi, mà cột Assignment.SubjectId là
   NOT NULL. */
INSERT INTO TeacherSubject (TeacherId, SubjectId, ProficiencyLevel)
SELECT x.TeacherId, x.SubjectId, 5
  FROM (
    SELECT t.Id AS TeacherId,
           1 + (ROW_NUMBER() OVER (ORDER BY t.Id) - 1)
               % (SELECT COUNT(*) FROM Subject WHERE IsDeleted = 0) AS SubjectRank
      FROM Teacher t WHERE t.IsDeleted = 0
  ) q
 CROSS APPLY (SELECT q.TeacherId, s.Id AS SubjectId
                FROM (SELECT Id, ROW_NUMBER() OVER (ORDER BY Id) rn FROM Subject WHERE IsDeleted = 0) s
               WHERE s.rn = q.SubjectRank) x
 WHERE NOT EXISTS (SELECT 1 FROM TeacherSubject ts
                    WHERE ts.TeacherId = x.TeacherId AND ts.SubjectId = x.SubjectId);
GO


/* =====================================================================
   PHẦN 3 — BỘ KHUNG PHÂN VIỆC (học kỳ, trường, làn dạy)

   Các bảng seed_* là bảng TRUNG GIAN, bị xóa ở PHẦN 13. Không dùng #temp vì
   file có nhiều batch GO và khi gỡ lỗi cần xem lại được từng bước.
   ===================================================================== */
PRINT N'PHẦN 3 — học kỳ, trường, làn dạy';
GO

DROP TABLE IF EXISTS seed_Slot, seed_Part, seed_Lane, seed_GV, seed_Truong,
                     seed_HocKy, seed_Ngay, seed_Phieu, seed_PhieuCho;
GO

CREATE TABLE seed_HocKy (HocKyId INT PRIMARY KEY, Ten NVARCHAR(60), TuNgay DATE, DenNgay DATE, NamHoc VARCHAR(9));
INSERT INTO seed_HocKy VALUES
 (1, N'Học kỳ 1 năm học 2025-2026', '2025-09-08', '2026-01-16', '2025-2026'),
 (2, N'Học kỳ 2 năm học 2025-2026', '2026-02-02', '2026-05-29', '2025-2026'),
 (3, N'Học kỳ 1 năm học 2026-2027', '2026-08-17', '2027-01-15', '2026-2027');

/* Trường nhận phân công: đang hợp tác, có lớp ACTIVE, có khung tiết. */
CREATE TABLE seed_Truong (SchoolId INT PRIMARY KEY, TruongIdx INT, TieuHoc BIT);
INSERT INTO seed_Truong (SchoolId, TruongIdx, TieuHoc)
SELECT s.Id, ROW_NUMBER() OVER (ORDER BY s.Id),
       CASE WHEN (SELECT COUNT(*) FROM Period p WHERE p.SchoolId = s.Id AND p.IsDeleted = 0) >= 10
            THEN 1 ELSE 0 END
  FROM School s
 WHERE s.IsDeleted = 0 AND s.Status = 'ACTIVE'
   AND EXISTS (SELECT 1 FROM SchoolClass c WHERE c.SchoolId = s.Id AND c.IsDeleted = 0 AND c.Status = 'ACTIVE')
   AND EXISTS (SELECT 1 FROM Period p WHERE p.SchoolId = s.Id AND p.IsDeleted = 0);

/* LÀN DẠY — xem giải thích mô hình ở đầu file. */
CREATE TABLE seed_Lane (
    LaneId   INT IDENTITY(1,1) PRIMARY KEY,
    SchoolId INT, TieuHoc BIT, DayIdx INT, Buoi VARCHAR(10), LaneIdx INT,
    CellIdx  INT,   -- 0..9 : (ngày, buổi) — đơn vị chống trùng giáo viên
    LaneRank INT    -- thứ tự làn trong cell
);
INSERT INTO seed_Lane (SchoolId, TieuHoc, DayIdx, Buoi, LaneIdx, CellIdx, LaneRank)
SELECT q.SchoolId, q.TieuHoc, q.DayIdx, q.Buoi, q.LaneIdx,
       (q.DayIdx - 1) * 2 + CASE WHEN q.Buoi = 'MORNING' THEN 0 ELSE 1 END,
       ROW_NUMBER() OVER (PARTITION BY q.DayIdx, q.Buoi ORDER BY q.SchoolId, q.LaneIdx) - 1
  FROM (
    SELECT t.SchoolId, t.TieuHoc, d.n AS DayIdx, b.Buoi, l.n AS LaneIdx
      FROM seed_Truong t
     CROSS APPLY (SELECT number AS n FROM master.dbo.spt_values WHERE type = 'P' AND number BETWEEN 1 AND 5) d
     CROSS APPLY (SELECT 'MORNING' AS Buoi UNION ALL SELECT 'AFTERNOON') b
     CROSS APPLY (SELECT number AS n FROM master.dbo.spt_values WHERE type = 'P' AND number BETWEEN 0 AND 1) l
     WHERE l.n = 0 OR (b.Buoi = 'AFTERNOON' AND t.TieuHoc = 1)
  ) q;
GO


/* =====================================================================
   PHẦN 4 — GÁN GIÁO VIÊN VÀO TỪNG PHẦN VIỆC
   ===================================================================== */
PRINT N'PHẦN 4 — gán giáo viên vào phần việc';
GO

/* Hai danh sách RIÊNG vì hai loại nhận hai kiểu việc khác nhau:
     cơ hữu      -> nhận TRỌN một buổi (4-5 tiết liền)
     thỉnh giảng -> nhận NỬA buổi (2-3 tiết), đúng kiểu người dạy theo tiết
   Giáo viên đã nghỉ việc (RETIRED) hoặc tạm đình chỉ (SUSPENDED) không nằm
   trong danh sách nào — họ vẫn còn hồ sơ và lịch sử, chỉ không nhận việc mới. */
CREATE TABLE seed_GV (Rn INT, TeacherId INT, Loai VARCHAR(12), PRIMARY KEY (Loai, Rn));
INSERT INTO seed_GV (Rn, TeacherId, Loai)
SELECT ROW_NUMBER() OVER (PARTITION BY EmploymentType ORDER BY Id) - 1, Id, EmploymentType
  FROM Teacher WHERE IsDeleted = 0 AND Status = 'ACTIVE';

CREATE TABLE seed_Part (PartId INT IDENTITY(1,1) PRIMARY KEY, LaneId INT, PartIdx INT, TeacherId INT, Loai VARCHAR(12));
GO

DECLARE @soCoHuu INT = (SELECT COUNT(*) FROM seed_GV WHERE Loai = 'CO_HUU');
DECLARE @soThinh INT = (SELECT COUNT(*) FROM seed_GV WHERE Loai = 'THINH_GIANG');

/* Mỗi cell dành 12 làn cho cơ hữu, phần còn lại cắt đôi cho thỉnh giảng.
   Con số 12 quay vòng theo cell (cộng CellIdx*12 rồi chia dư) để không phải
   lúc nào cũng đúng 12 trường đầu bảng được cơ hữu. */
;WITH CellSize AS (
    SELECT CellIdx, COUNT(*) AS n FROM seed_Lane GROUP BY CellIdx
),
Lane2 AS (
    SELECT l.*, cs.n AS CellSize,
           CASE WHEN ((l.LaneRank + l.CellIdx * 12) % cs.n) < 12 THEN 1 ELSE 0 END AS LaTronBuoi
      FROM seed_Lane l JOIN CellSize cs ON cs.CellIdx = l.CellIdx
),
CoHuu AS (
    SELECT LaneId, 0 AS PartIdx,
           ROW_NUMBER() OVER (ORDER BY CellIdx, LaneRank) - 1 AS Gidx,
           'CO_HUU' AS Loai
      FROM Lane2 WHERE LaTronBuoi = 1
),
Thinh AS (
    SELECT l.LaneId, p.PartIdx,
           ROW_NUMBER() OVER (ORDER BY l.CellIdx, l.LaneRank, p.PartIdx) - 1 AS Gidx,
           'THINH_GIANG' AS Loai
      FROM Lane2 l
     CROSS APPLY (SELECT 0 AS PartIdx UNION ALL SELECT 1) p
     WHERE l.LaTronBuoi = 0
)
INSERT INTO seed_Part (LaneId, PartIdx, TeacherId, Loai)
SELECT x.LaneId, x.PartIdx, g.TeacherId, x.Loai
  FROM (SELECT * FROM CoHuu UNION ALL SELECT * FROM Thinh) x
  JOIN seed_GV g
    ON g.Loai = x.Loai
   AND g.Rn = x.Gidx % CASE WHEN x.Loai = 'CO_HUU' THEN @soCoHuu ELSE @soThinh END;
GO


/* =====================================================================
   PHẦN 5 — TRẢI PHẦN VIỆC RA TỪNG TIẾT, GẮN LỚP
   ===================================================================== */
PRINT N'PHẦN 5 — trải phần việc ra từng tiết';
GO

CREATE TABLE seed_Slot (
    SlotId INT IDENTITY(1,1) PRIMARY KEY,
    HocKyId INT, TeacherId INT, SchoolId INT,
    DayOfWeek VARCHAR(3), PeriodId INT, ClassId INT
);

/* Phần việc "trọn buổi" phủ hết các tiết của buổi; phần việc "nửa buổi" lấy
   nửa đầu (PartIdx 0) hoặc nửa sau. Buổi sáng 5 tiết cắt thành 3+2, buổi
   chiều THCS 4 tiết cắt thành 2+2.

   LỚP xoay vòng theo công thức chứ không bốc ngẫu nhiên: hai làn cùng trường
   cùng tiết lệch nhau 5 bậc trong danh sách lớp nên chắc chắn ra hai lớp khác
   nhau (trường ít lớp nhất cũng có 12 lớp mỗi năm học). Đó là điều kiện đủ để
   không bao giờ có hai giáo viên cùng đứng một lớp trong một tiết. */
;WITH Tiet AS (
    SELECT p.Id AS PeriodId, p.SchoolId, p.SessionType,
           ROW_NUMBER() OVER (PARTITION BY p.SchoolId, p.SessionType ORDER BY p.PeriodNumber) - 1 AS TietIdx,
           COUNT(*)    OVER (PARTITION BY p.SchoolId, p.SessionType) AS SoTiet
      FROM Period p WHERE p.IsDeleted = 0
),
PhanViec AS (
    SELECT pa.PartIdx, pa.TeacherId, pa.Loai, l.SchoolId, l.DayIdx, l.Buoi, l.LaneIdx
      FROM seed_Part pa JOIN seed_Lane l ON l.LaneId = pa.LaneId
),
TietCuaPhanViec AS (
    SELECT pv.*, t.PeriodId, t.TietIdx
      FROM PhanViec pv
      JOIN Tiet t ON t.SchoolId = pv.SchoolId AND t.SessionType = pv.Buoi
     WHERE pv.Loai = 'CO_HUU'
        OR (pv.PartIdx = 0 AND t.TietIdx <  (t.SoTiet + 1) / 2)
        OR (pv.PartIdx = 1 AND t.TietIdx >= (t.SoTiet + 1) / 2)
),
Lop AS (
    SELECT c.Id AS ClassId, c.SchoolId, c.SchoolYear,
           ROW_NUMBER() OVER (PARTITION BY c.SchoolId, c.SchoolYear ORDER BY c.GradeLevel, c.Name) - 1 AS LopIdx,
           COUNT(*)    OVER (PARTITION BY c.SchoolId, c.SchoolYear) AS SoLop
      FROM SchoolClass c WHERE c.IsDeleted = 0 AND c.Status = 'ACTIVE'
)
INSERT INTO seed_Slot (HocKyId, TeacherId, SchoolId, DayOfWeek, PeriodId, ClassId)
SELECT hk.HocKyId, tp.TeacherId, tp.SchoolId,
       CASE tp.DayIdx WHEN 1 THEN 'MON' WHEN 2 THEN 'TUE' WHEN 3 THEN 'WED'
                      WHEN 4 THEN 'THU' ELSE 'FRI' END,
       tp.PeriodId, lo.ClassId
  FROM TietCuaPhanViec tp
 CROSS JOIN seed_HocKy hk
  JOIN Lop lo ON lo.SchoolId = tp.SchoolId
             AND lo.SchoolYear = hk.NamHoc
             AND lo.LopIdx = (tp.DayIdx * 3 + tp.LaneIdx * 5 + tp.TietIdx) % lo.SoLop;
GO


/* =====================================================================
   PHẦN 6 — PHIẾU PHÂN CÔNG VÀ Ô THỜI KHÓA BIỂU

   Từ đây trở đi là XÓA SẠCH RỒI SINH LẠI. Thứ tự DELETE bám theo khóa ngoại:
   con trước, cha sau.
   ===================================================================== */
PRINT N'PHẦN 6 — phiếu phân công và ô thời khóa biểu';
GO

DELETE FROM PayrollChangeLog;
DELETE FROM Payroll;
DELETE FROM AttendanceChangeLog;
DELETE FROM AttendanceAmendRequest;
DELETE FROM Attendance;
DELETE FROM ScheduleStatusLog;
DELETE FROM Schedule;
DELETE FROM AssignmentSlot;
DELETE FROM Assignment;

DBCC CHECKIDENT ('Assignment',     RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('AssignmentSlot', RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('Schedule',       RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('Attendance',     RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('Payroll',        RESEED, 0) WITH NO_INFOMSGS;

CREATE TABLE seed_Phieu (HocKyId INT, TeacherId INT, AssignmentId INT);
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');
DECLARE @hk INT = 1;

/* Một phiếu cho mỗi (giáo viên, học kỳ). Từ V27 một phiếu trải được NHIỀU
   trường/lớp nên không phải tách phiếu theo trường nữa. Môn lấy từ bảng
   TeacherSubject — phân công người không dạy được môn đó là dữ liệu sai ngay
   từ gốc, và đó chính là thứ AssignmentService kiểm khi tạo phiếu qua UI.

   Trạng thái bám theo THỜI GIAN THẬT: học kỳ đã khép -> COMPLETED, học kỳ
   đang chạy -> ACTIVE. Ngày xác nhận đặt trước ngày bắt đầu vài ngày, đúng
   như quy trình mời - nhận. */
WHILE @hk <= 3
BEGIN
    DECLARE @tu DATE, @den DATE, @trangThai VARCHAR(20);
    SELECT @tu = TuNgay, @den = DenNgay FROM seed_HocKy WHERE HocKyId = @hk;
    SET @trangThai = CASE WHEN @den < CAST(SYSDATETIME() AS DATE) THEN 'COMPLETED' ELSE 'ACTIVE' END;

    INSERT INTO Assignment (TeacherId, SchoolId, SubjectId, ClassId, StartDate, EndDate, Status,
                            IsDeleted, CreatedAt, CreatedBy,
                            ConfirmDeadline, ConfirmedAt, ConfirmedByUserId, ConfirmSource)
    OUTPUT inserted.TeacherId, inserted.Id INTO seed_Phieu (TeacherId, AssignmentId)
    SELECT q.TeacherId, q.SchoolId, q.SubjectId, q.ClassId, @tu, @den, @trangThai,
           0, @now, @admin,
           CAST(DATEADD(DAY, -5, @tu) AS DATETIME2(3)),
           CAST(DATEADD(DAY, -6, @tu) AS DATETIME2(3)),
           t.AppUserId, 'TEACHER'
      FROM (
        SELECT s.TeacherId, MIN(s.SchoolId) AS SchoolId, MIN(s.ClassId) AS ClassId,
               (SELECT MIN(ts.SubjectId) FROM TeacherSubject ts WHERE ts.TeacherId = s.TeacherId) AS SubjectId
          FROM seed_Slot s WHERE s.HocKyId = @hk GROUP BY s.TeacherId
      ) q
      JOIN Teacher t ON t.Id = q.TeacherId;

    UPDATE seed_Phieu SET HocKyId = @hk WHERE HocKyId IS NULL;
    SET @hk = @hk + 1;
END

INSERT INTO AssignmentSlot (AssignmentId, TeacherId, DayOfWeek, PeriodId, RoomId,
                            IsDeleted, CreatedAt, CreatedBy, ClassId, SchoolId)
SELECT p.AssignmentId, s.TeacherId, s.DayOfWeek, s.PeriodId, NULL,
       0, @now, @admin, s.ClassId, s.SchoolId
  FROM seed_Slot s
  JOIN seed_Phieu p ON p.HocKyId = s.HocKyId AND p.TeacherId = s.TeacherId;
GO


/* =====================================================================
   PHẦN 7 — BẢNG NGÀY
   ===================================================================== */
PRINT N'PHẦN 7 — bảng ngày';
GO

CREATE TABLE seed_Ngay (Ngay DATE PRIMARY KEY, Thu VARCHAR(3));
;WITH N AS (
    SELECT CAST('2025-09-01' AS DATE) AS d
    UNION ALL SELECT DATEADD(DAY, 1, d) FROM N WHERE d < '2027-06-30'
)
INSERT INTO seed_Ngay (Ngay, Thu)
SELECT d, CASE DATEPART(WEEKDAY, d)
            WHEN 2 THEN 'MON' WHEN 3 THEN 'TUE' WHEN 4 THEN 'WED'
            WHEN 5 THEN 'THU' WHEN 6 THEN 'FRI' WHEN 7 THEN 'SAT' ELSE 'SUN' END
  FROM N OPTION (MAXRECURSION 1000);
GO


/* =====================================================================
   PHẦN 8 — SINH BUỔI DẠY
   ===================================================================== */
PRINT N'PHẦN 8 — sinh buổi dạy (~86.700 dòng, mất khoảng 10 giây)';
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');

/* Trải mỗi ô thời khóa biểu ra từng tuần trong giai đoạn của phiếu, BỎ QUA
   ngày nghỉ. Bỏ bước lọc ngày nghỉ là sinh ra buổi dạy vào 30/4, 2/9, Tết —
   job khép sổ chấm công sẽ ghi VẮNG cho giáo viên và trừ thẳng vào lương vì
   một buổi chưa từng tồn tại. Đúng cái lỗi mà Flyway V29 sinh ra để vá.

   Kỳ nghỉ có SchoolId = NULL là nghỉ toàn hệ thống; có SchoolId là nghỉ riêng
   của một trường và KHÔNG được đụng tới lịch của trường khác. */
INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, Status, Source,
                      CreatedByUserId, ApprovedByUserId, ApprovedAt, IsDeleted, CreatedAt,
                      PeriodId, SourceSlotId)
SELECT a.Id, sl.TeacherId, NULL,
       CAST(CAST(n.Ngay AS DATETIME) + CAST(p.StartTime AS DATETIME) AS DATETIME2(3)),
       CAST(CAST(n.Ngay AS DATETIME) + CAST(p.EndTime   AS DATETIME) AS DATETIME2(3)),
       'APPROVED', 'MANUAL', @admin, @admin, @now, 0, @now,
       sl.PeriodId, sl.Id
  FROM AssignmentSlot sl
  JOIN Assignment a ON a.Id = sl.AssignmentId
  JOIN Period p     ON p.Id = sl.PeriodId
  JOIN seed_Ngay n  ON n.Thu = sl.DayOfWeek AND n.Ngay BETWEEN a.StartDate AND a.EndDate
 WHERE NOT EXISTS (SELECT 1 FROM Holiday h
                    WHERE h.IsDeleted = 0
                      AND n.Ngay BETWEEN h.FromDate AND h.ToDate
                      AND (h.SchoolId IS NULL OR h.SchoolId = sl.SchoolId));
GO


/* =====================================================================
   PHẦN 9 — CHẤM CÔNG CHO CÁC BUỔI ĐÃ QUA
   ===================================================================== */
PRINT N'PHẦN 9 — chấm công (~55.100 dòng)';
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');
DECLARE @homNay DATE = CAST(SYSDATETIME() AS DATE);

/* CHỈ buổi đã diễn ra mới có bản ghi chấm công. Buổi tương lai KHÔNG được
   sinh sẵn dòng nào: có dòng nghĩa là hệ thống khẳng định điều chưa xảy ra,
   và màn Chấm công sẽ đếm chúng vào thống kê tháng.

   Tỉ lệ trạng thái mô phỏng một trung tâm thật: phần lớn có mặt, một ít đi
   muộn, vài phần trăm nghỉ phép và vắng. Số "ngẫu nhiên" suy từ CHECKSUM(Id)
   chứ không dùng RAND() — chạy lại file cho ra ĐÚNG cùng bộ dữ liệu, nên
   demo hôm nay và demo tuần sau giống hệt nhau. */
;WITH BuoiDaQua AS (
    SELECT s.Id AS ScheduleId, s.TeacherId, CAST(s.StartTime AS DATE) AS WorkDate,
           CAST(s.StartTime AS TIME) AS GioBatDau, CAST(s.EndTime AS TIME) AS GioKetThuc,
           ABS(CHECKSUM(s.Id * 2654435761)) % 100 AS r
      FROM Schedule s
     WHERE s.IsDeleted = 0 AND s.Status = 'APPROVED'
       AND CAST(s.StartTime AS DATE) < @homNay
),
Phan AS (
    SELECT b.*,
           CASE WHEN b.r < 3  THEN 'ABSENT'
                WHEN b.r < 7  THEN 'LEAVE'
                WHEN b.r < 14 THEN 'LATE'
                ELSE 'PRESENT' END AS TrangThai
      FROM BuoiDaQua b
)
INSERT INTO Attendance (TeacherId, ScheduleId, WorkDate, CheckIn, CheckOut, Status,
                        CheckInMethod, ConfirmedByUserId, ConfirmedAt, Note,
                        CreatedAt, CreatedBy, AutoCheckOut)
SELECT p.TeacherId, p.ScheduleId, p.WorkDate,
       CASE p.TrangThai
            WHEN 'PRESENT' THEN DATEADD(MINUTE, -(p.r % 6) - 1, p.GioBatDau)
            WHEN 'LATE'    THEN DATEADD(MINUTE,  (p.r % 11) + 5, p.GioBatDau)
            ELSE NULL END,
       CASE WHEN p.TrangThai IN ('PRESENT', 'LATE')
            THEN DATEADD(MINUTE, (p.r % 4), p.GioKetThuc) ELSE NULL END,
       p.TrangThai,
       CASE p.TrangThai
            WHEN 'ABSENT' THEN 'SYSTEM'
            WHEN 'LEAVE'  THEN 'EMPLOYEE'
            WHEN 'LATE'   THEN 'SELF'
            ELSE CASE WHEN p.r % 5 = 0 THEN 'SCHOOL' ELSE 'SELF' END END,
       CASE WHEN p.TrangThai = 'LEAVE' THEN @admin ELSE NULL END,
       CASE WHEN p.TrangThai = 'LEAVE' THEN CAST(p.WorkDate AS DATETIME2(3)) ELSE NULL END,
       CASE p.TrangThai
            WHEN 'LEAVE'  THEN N'Nghỉ phép có đơn'
            WHEN 'ABSENT' THEN N'Hết buổi không có điểm danh — hệ thống tự ghi vắng'
            ELSE NULL END,
       @now, @admin,
       CASE WHEN p.TrangThai IN ('PRESENT', 'LATE') AND p.r % 9 = 0 THEN 1 ELSE 0 END
  FROM Phan p;
GO


/* =====================================================================
   PHẦN 10 — BẢNG LƯƠNG
   ===================================================================== */
PRINT N'PHẦN 10 — bảng lương';
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');

/* Tính ĐÚNG công thức của PayrollService.generate():
     - TaughtHours = SỐ TIẾT có công (PRESENT + LATE). Tên cột là di sản, xem
       ghi chú trong service; nghỉ phép và vắng KHÔNG được trả tiền.
     - Đơn giá tra theo KHỐI của lớp và theo NGÀY DẠY (bảng PayRate), không
       theo hôm nay — nên tính lại một kỳ cũ vẫn ra đúng số đã trả.
     - RatePerHour = tổng tiền / số tiết (đơn giá hiệu dụng; giáo viên dạy cả
       tiểu học lẫn THCS thì đây là trung bình có trọng số).
     - Lương cứng CHỈ áp cho giáo viên cơ hữu.
     - NetAmount là CỘT TÍNH SẴN trong DB, không ghi tay được. */
;WITH TietCoCong AS (
    SELECT a.TeacherId, YEAR(a.WorkDate) AS Nam, MONTH(a.WorkDate) AS Thang,
           TRY_CAST(c.GradeLevel AS INT) AS Khoi, a.WorkDate
      FROM Attendance a
      JOIN Schedule s          ON s.Id  = a.ScheduleId
      LEFT JOIN Assignment asg ON asg.Id = s.AssignmentId
      LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
      LEFT JOIN SchoolClass c  ON c.Id  = COALESCE(sl.ClassId, asg.ClassId)
     WHERE a.Status IN ('PRESENT', 'LATE')
),
CongTien AS (
    SELECT t.TeacherId, t.Nam, t.Thang, COUNT(*) AS SoTiet, SUM(pr.Amount) AS TongTien
      FROM TietCoCong t
      JOIN PayRate pr ON t.Khoi BETWEEN pr.GradeFrom AND pr.GradeTo
                     AND t.WorkDate >= pr.EffectiveFrom
                     AND (pr.EffectiveTo IS NULL OR t.WorkDate <= pr.EffectiveTo)
     GROUP BY t.TeacherId, t.Nam, t.Thang
)
INSERT INTO Payroll (TeacherId, PeriodMonth, PeriodYear, BaseSalary, TaughtHours,
                     RatePerHour, Allowance, Bonus, Deduction, Status, CreatedAt, CreatedBy)
SELECT ct.TeacherId, ct.Thang, ct.Nam,
       CASE WHEN t.EmploymentType = 'CO_HUU' THEN ISNULL(hd.BaseSalary, 0) ELSE 0 END,
       ct.SoTiet,
       CAST(ct.TongTien / NULLIF(ct.SoTiet, 0) AS DECIMAL(18,2)),
       CASE WHEN t.EmploymentType = 'CO_HUU' THEN ISNULL(hd.Allowance, 0) ELSE 0 END,
       /* Thưởng 300k: tháng đó không đi muộn buổi nào. */
       CASE WHEN NOT EXISTS (SELECT 1 FROM Attendance a2
                              WHERE a2.TeacherId = ct.TeacherId AND a2.Status = 'LATE'
                                AND YEAR(a2.WorkDate) = ct.Nam AND MONTH(a2.WorkDate) = ct.Thang)
            THEN 300000 ELSE 0 END,
       /* Trừ 50k mỗi buổi vắng không phép. */
       50000 * (SELECT COUNT(*) FROM Attendance a3
                 WHERE a3.TeacherId = ct.TeacherId AND a3.Status = 'ABSENT'
                   AND YEAR(a3.WorkDate) = ct.Nam AND MONTH(a3.WorkDate) = ct.Thang),
       /* Vòng đời phiếu: kỳ cũ đã chi, kỳ vừa khép đã chốt chờ chi, kỳ đang
          chạy còn nháp. Đủ ba trạng thái để demo được cả ba nút. */
       CASE WHEN DATEFROMPARTS(ct.Nam, ct.Thang, 1) < '2026-05-01' THEN 'PAID'
            WHEN DATEFROMPARTS(ct.Nam, ct.Thang, 1) = '2026-05-01' THEN 'FINALIZED'
            ELSE 'DRAFT' END,
       @now, @admin
  FROM CongTien ct
  JOIN Teacher t ON t.Id = ct.TeacherId
  LEFT JOIN Contract hd ON hd.TeacherId = ct.TeacherId AND hd.IsDeleted = 0;
GO


/* =====================================================================
   PHẦN 11 — PHIẾU ĐANG CHỜ XỬ LÝ

   Bốn trạng thái mà admin phải động tay tới: Chờ xác nhận, Hết hạn, Bị từ
   chối, Đã hủy. Không có chúng thì màn Phân công chỉ toàn phiếu đã xong và
   các nút Nhắc / Ép duyệt / Hủy không có gì để bấm.

   Xếp vào THỨ BẢY — ngày duy nhất chưa dùng trong toàn bộ lịch chính khóa
   nên chắc chắn không đụng giờ ai, không phải dò trùng.
   ===================================================================== */
PRINT N'PHẦN 11 — phiếu chờ xác nhận / hết hạn / bị từ chối / đã hủy';
GO

CREATE TABLE seed_PhieuCho (AssignmentId INT, TrangThai VARCHAR(20), TeacherId INT,
                            SchoolId INT, ClassId INT, PeriodId INT);
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');

;WITH GV AS (
    SELECT t.Id AS TeacherId, ROW_NUMBER() OVER (ORDER BY t.Id DESC) AS rn
      FROM Teacher t WHERE t.IsDeleted = 0 AND t.Status = 'ACTIVE'
),
TruongLop AS (
    SELECT c.SchoolId, c.Id AS ClassId,
           (SELECT MIN(p.Id) FROM Period p
             WHERE p.SchoolId = c.SchoolId AND p.IsDeleted = 0 AND p.SessionType = 'AFTERNOON') AS PeriodId,
           ROW_NUMBER() OVER (ORDER BY c.SchoolId, c.Id) AS rn
      FROM SchoolClass c
      JOIN seed_Truong t ON t.SchoolId = c.SchoolId
     WHERE c.IsDeleted = 0 AND c.Status = 'ACTIVE' AND c.SchoolYear = '2026-2027'
),
TrangThai AS (
              SELECT 'PENDING'   AS st, n.number AS i FROM master.dbo.spt_values n WHERE n.type = 'P' AND n.number BETWEEN  1 AND  8
    UNION ALL SELECT 'EXPIRED',      n.number       FROM master.dbo.spt_values n WHERE n.type = 'P' AND n.number BETWEEN  9 AND 13
    UNION ALL SELECT 'REJECTED',     n.number       FROM master.dbo.spt_values n WHERE n.type = 'P' AND n.number BETWEEN 14 AND 17
    UNION ALL SELECT 'CANCELLED',    n.number       FROM master.dbo.spt_values n WHERE n.type = 'P' AND n.number BETWEEN 18 AND 24
)
INSERT INTO seed_PhieuCho (TrangThai, TeacherId, SchoolId, ClassId, PeriodId)
SELECT tt.st, g.TeacherId, tl.SchoolId, tl.ClassId, tl.PeriodId
  FROM TrangThai tt
  JOIN GV g         ON g.rn  = tt.i
  JOIN TruongLop tl ON tl.rn = tt.i * 17;

INSERT INTO Assignment (TeacherId, SchoolId, SubjectId, ClassId, StartDate, EndDate, Status,
                        IsDeleted, DeletedAt, DeletedBy, CreatedAt, CreatedBy,
                        ConfirmDeadline, RejectionReason)
SELECT pc.TeacherId, pc.SchoolId,
       (SELECT MIN(ts.SubjectId) FROM TeacherSubject ts WHERE ts.TeacherId = pc.TeacherId),
       pc.ClassId, '2027-01-19', '2027-05-28', pc.TrangThai,
       CASE WHEN pc.TrangThai = 'CANCELLED' THEN 1 ELSE 0 END,
       CASE WHEN pc.TrangThai = 'CANCELLED' THEN @now ELSE NULL END,
       CASE WHEN pc.TrangThai = 'CANCELLED' THEN @admin ELSE NULL END,
       @now, @admin,
       CASE pc.TrangThai
            WHEN 'PENDING' THEN CAST(DATEADD(HOUR,  30, SYSDATETIME()) AS DATETIME2(3))
            WHEN 'EXPIRED' THEN CAST(DATEADD(HOUR, -20, SYSDATETIME()) AS DATETIME2(3))
            ELSE NULL END,
       CASE pc.TrangThai WHEN 'REJECTED' THEN N'Trùng lịch dạy ở trung tâm khác vào thứ Bảy.' ELSE NULL END
  FROM seed_PhieuCho pc;

/* Ghép Id vừa sinh về đúng dòng: các phiếu này là phiếu DUY NHẤT bắt đầu
   19/01/2027 nên nhận diện được bằng ngày bắt đầu. */
UPDATE pc SET pc.AssignmentId = a.Id
  FROM seed_PhieuCho pc
  JOIN Assignment a ON a.TeacherId = pc.TeacherId AND a.StartDate = '2027-01-19';

INSERT INTO AssignmentSlot (AssignmentId, TeacherId, DayOfWeek, PeriodId,
                            IsDeleted, CreatedAt, CreatedBy, ClassId, SchoolId)
SELECT pc.AssignmentId, pc.TeacherId, 'SAT', pc.PeriodId,
       CASE WHEN pc.TrangThai = 'CANCELLED' THEN 1 ELSE 0 END, @now, @admin, pc.ClassId, pc.SchoolId
  FROM seed_PhieuCho pc WHERE pc.AssignmentId IS NOT NULL;
GO


/* =====================================================================
   PHẦN 12 — BUỔI DẠY CHỜ XÁC NHẬN, NHẬT KÝ LƯƠNG, DỌN TOKEN
   ===================================================================== */
PRINT N'PHẦN 12 — buổi chờ xác nhận, nhật ký lương, dọn token';
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');

/* Buổi mang trạng thái PENDING chứ không phải APPROVED: lịch chỉ có hiệu lực
   khi giáo viên đã đồng ý. Phiếu bị từ chối / hết hạn / đã hủy không sinh
   buổi nào — chúng chưa bao giờ thành lịch thật. */
INSERT INTO Schedule (AssignmentId, TeacherId, StartTime, EndTime, Status, Source,
                      CreatedByUserId, IsDeleted, CreatedAt, PeriodId, SourceSlotId)
SELECT a.Id, sl.TeacherId,
       CAST(CAST(n.Ngay AS DATETIME) + CAST(p.StartTime AS DATETIME) AS DATETIME2(3)),
       CAST(CAST(n.Ngay AS DATETIME) + CAST(p.EndTime   AS DATETIME) AS DATETIME2(3)),
       'PENDING', 'MANUAL', @admin, 0, @now, sl.PeriodId, sl.Id
  FROM AssignmentSlot sl
  JOIN Assignment a ON a.Id = sl.AssignmentId
  JOIN Period p     ON p.Id = sl.PeriodId
  JOIN seed_Ngay n  ON n.Thu = sl.DayOfWeek AND n.Ngay BETWEEN a.StartDate AND a.EndDate
 WHERE a.Status = 'PENDING'
   AND NOT EXISTS (SELECT 1 FROM Holiday h
                    WHERE h.IsDeleted = 0
                      AND n.Ngay BETWEEN h.FromDate AND h.ToDate
                      AND (h.SchoolId IS NULL OR h.SchoolId = sl.SchoolId));

/* Nhật ký vòng đời phiếu lương: phiếu PAID phải đi qua FINALIZE rồi mới PAY.
   Không có nhật ký thì màn Bảng lương hiện "Đã trả" mà không ai biết ai chốt,
   ai chi, lúc nào. */
INSERT INTO PayrollChangeLog (PayrollId, Action, Reason, StatusBefore, StatusAfter,
                              NetAmountBefore, NetAmountAfter, ChangedBy, ChangedAt)
SELECT p.Id, 'FINALIZE', N'Chốt lương cuối kỳ', 'DRAFT', 'FINALIZED',
       p.NetAmount, p.NetAmount, @admin,
       CAST(DATEADD(DAY, 3, EOMONTH(DATEFROMPARTS(p.PeriodYear, p.PeriodMonth, 1))) AS DATETIME2(3))
  FROM Payroll p WHERE p.Status IN ('FINALIZED', 'PAID');

INSERT INTO PayrollChangeLog (PayrollId, Action, Reason, StatusBefore, StatusAfter,
                              NetAmountBefore, NetAmountAfter, ChangedBy, ChangedAt)
SELECT p.Id, 'PAY', N'Đã chuyển khoản', 'FINALIZED', 'PAID',
       p.NetAmount, p.NetAmount, @admin,
       CAST(DATEADD(DAY, 6, EOMONTH(DATEFROMPARTS(p.PeriodYear, p.PeriodMonth, 1))) AS DATETIME2(3))
  FROM Payroll p WHERE p.Status = 'PAID';

/* Dọn refresh token đã hết hạn / đã thu hồi. Bảng này chỉ lớn lên chứ không
   bao giờ nhỏ đi: mỗi lần đăng nhập thêm một dòng, không có gì dọn dòng cũ. */
DELETE FROM RefreshToken WHERE ExpiresAt < SYSUTCDATETIME() OR RevokedAt IS NOT NULL;
GO


/* =====================================================================
   PHẦN 13 — KIỂM CHỨNG VÀ DỌN BẢNG TRUNG GIAN

   Sáu con số dưới đây PHẢI bằng 0. Chúng không để trang trí: mỗi dòng là một
   luật mà nếu vỡ thì màn hình vẫn chạy bình thường nhưng dữ liệu đã sai —
   buổi dạy "ma" ngày lễ trừ oan tiền giáo viên, chấm công cho buổi chưa diễn
   ra, lớp có hai thầy cùng một tiết. Kiểu lỗi chỉ lộ ra khi có người đi đối
   chiếu, tức là quá muộn.
   ===================================================================== */
PRINT N'PHẦN 13 — kiểm chứng';
GO

SELECT N'Buổi dạy rơi vào ngày nghỉ' AS Luat, COUNT(*) AS PhaiBangKhong
  FROM Schedule s
  JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
  JOIN Holiday h ON h.IsDeleted = 0
                AND CAST(s.StartTime AS DATE) BETWEEN h.FromDate AND h.ToDate
                AND (h.SchoolId IS NULL OR h.SchoolId = sl.SchoolId)
 WHERE s.IsDeleted = 0
UNION ALL
SELECT N'Chấm công cho buổi chưa diễn ra', COUNT(*)
  FROM Attendance a JOIN Schedule s ON s.Id = a.ScheduleId
 WHERE s.StartTime > SYSDATETIME()
UNION ALL
SELECT N'Giáo viên ở hai nơi cùng một giờ', COUNT(*) FROM (
    SELECT s.TeacherId, s.StartTime FROM Schedule s
     WHERE s.IsDeleted = 0 AND s.Status IN ('APPROVED', 'PENDING')
     GROUP BY s.TeacherId, s.StartTime HAVING COUNT(*) > 1) x
UNION ALL
SELECT N'Lớp có hai giáo viên cùng một tiết', COUNT(*) FROM (
    SELECT sl.ClassId, s.StartTime FROM Schedule s
      JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
     WHERE s.IsDeleted = 0 AND s.Status IN ('APPROVED', 'PENDING')
     GROUP BY sl.ClassId, s.StartTime HAVING COUNT(*) > 1) x
UNION ALL
SELECT N'Ô lịch trỏ vào lớp của năm học khác', COUNT(*)
  FROM AssignmentSlot sl
  JOIN Assignment a  ON a.Id = sl.AssignmentId
  JOIN SchoolClass c ON c.Id = sl.ClassId
 WHERE sl.IsDeleted = 0
   AND LEFT(c.SchoolYear, 4) <> CAST(CASE WHEN MONTH(a.StartDate) >= 8
                                          THEN YEAR(a.StartDate)
                                          ELSE YEAR(a.StartDate) - 1 END AS VARCHAR(4))
UNION ALL
SELECT N'Phiếu lương lệch số tiết so với chấm công', COUNT(*)
  FROM Payroll p
 WHERE p.TaughtHours <> (SELECT COUNT(*) FROM Attendance a
                          WHERE a.TeacherId = p.TeacherId
                            AND a.Status IN ('PRESENT', 'LATE')
                            AND YEAR(a.WorkDate) = p.PeriodYear
                            AND MONTH(a.WorkDate) = p.PeriodMonth);
GO

/* Quy mô thực tế sau khi chạy. */
SELECT 'Giáo viên'          AS Bang, COUNT(*) AS SoDong FROM Teacher        WHERE IsDeleted = 0
UNION ALL SELECT 'Trường đang hợp tác', COUNT(*) FROM School         WHERE IsDeleted = 0 AND Status = 'ACTIVE'
UNION ALL SELECT 'Lớp học',             COUNT(*) FROM SchoolClass    WHERE IsDeleted = 0
UNION ALL SELECT 'Phiếu phân công',     COUNT(*) FROM Assignment
UNION ALL SELECT 'Ô thời khóa biểu',    COUNT(*) FROM AssignmentSlot
UNION ALL SELECT 'Buổi dạy',            COUNT(*) FROM Schedule
UNION ALL SELECT 'Bản ghi chấm công',   COUNT(*) FROM Attendance
UNION ALL SELECT 'Phiếu lương',         COUNT(*) FROM Payroll;
GO

DROP TABLE IF EXISTS seed_Slot, seed_Part, seed_Lane, seed_GV, seed_Truong,
                     seed_HocKy, seed_Ngay, seed_Phieu, seed_PhieuCho;
GO

PRINT N'HOÀN TẤT. Gỡ ra bằng TSDMS_Rollback_2026_DuLieuLon.sql';
GO
