/* =====================================================================
   TSDMS — GỠ BỘ 100 GIÁO VIÊN + 23 MÔN HỌC
   ---------------------------------------------------------------------
   Đảo ngược database/seed/TSDMS_Seed_100GiaoVien.sql.

   CHỈ XÓA ĐÚNG DỮ LIỆU CỦA BỘ SEED. Diện xóa gồm: 100 username của lần sinh
   hiện tại (liệt kê thẳng trong file này) CỘNG mọi tài khoản mang dấu vân
   tay của bộ seed — username 'gv.*' VÀ email '@tsdms.local'. Vế thứ hai là
   bắt buộc: sinh lại file seed là 100 username đổi hết, chỉ dựa vào danh
   sách thì không dọn nổi dữ liệu do bản seed trước để lại. Giáo viên bạn tự
   thêm qua giao diện mang email thật nên không bao giờ dính.

   BA THỨ KHÔNG GỠ ĐƯỢC (cố ý):
     - 3 tài khoản TEACHER mồ côi đã bị bước 0 của file seed xóa: chúng
       không còn dữ liệu để khôi phục.
     - Tên nhóm môn 'STEM - AI': đây là sửa lỗi chính tả, giữ lại là đúng.
       Muốn trả về tên cũ thì bỏ chú thích ở khối cuối file.
     - Địa chỉ chi nhánh đã đổi về 'Hải Phòng': đây là sự thật về cơ sở của
       trung tâm, không phải dữ liệu demo, nên gỡ giáo viên không đụng tới.

   CHỐT CHẶN AN TOÀN: nếu bất kỳ GV nào trong danh sách đã được dùng thật
   (có phân công, lịch dạy, chấm công, bảng lương, đánh giá, bài giảng),
   script DỪNG và báo lỗi thay vì xóa mù làm gãy dữ liệu vận hành.
   ===================================================================== */

USE TSDMS;
GO

/* Bật QUOTED_IDENTIFIER vì các bảng đụng tới có filtered index — xem chú
   thích cùng chỗ trong TSDMS_Seed_100GiaoVien.sql. */
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
BEGIN TRANSACTION;

/* COLLATE DATABASE_DEFAULT: bảng tạm ở tempdb lấy collation của server, còn
   DB TSDMS dùng Vietnamese_CI_AS — thiếu là JOIN theo Username/Code nổ lỗi 468. */
CREATE TABLE #U (Username VARCHAR(50) COLLATE DATABASE_DEFAULT PRIMARY KEY);
INSERT INTO #U (Username) VALUES
 ('gv.hothanhhuong'),
 ('gv.hoangphuongnhi'),
 ('gv.taphuhuy'),
 ('gv.quachnhatha'),
 ('gv.homanhvinh'),
 ('gv.trankimthao'),
 ('gv.luuthedat'),
 ('gv.nguyenvantuyet'),
 ('gv.buihaihang'),
 ('gv.lammaily'),
 ('gv.vuhuuphong'),
 ('gv.dangthanhtien'),
 ('gv.tobaomai'),
 ('gv.ngotantrung'),
 ('gv.phankimdiep'),
 ('gv.tobichdung'),
 ('gv.vohaihuyen'),
 ('gv.caokhanhoanh'),
 ('gv.hakimha'),
 ('gv.chuvietnghia'),
 ('gv.doanthuylinh'),
 ('gv.vuthanhtrang'),
 ('gv.vuongmaitrinh'),
 ('gv.dangmanhhai'),
 ('gv.lethutrang'),
 ('gv.tangochong'),
 ('gv.buiyentuyet'),
 ('gv.doanthuynhung'),
 ('gv.tranviettu'),
 ('gv.hangochien'),
 ('gv.vuongtanminh'),
 ('gv.buithiha'),
 ('gv.quachtuyethien'),
 ('gv.tranhoaimy'),
 ('gv.phamdieugiang'),
 ('gv.nghiemquocnam'),
 ('gv.phamthuynga'),
 ('gv.dinhngocnhung'),
 ('gv.doyenan'),
 ('gv.doanphuonggiang'),
 ('gv.phamdinhthang'),
 ('gv.tatienvu'),
 ('gv.luuvanmai'),
 ('gv.nguyenducgiang'),
 ('gv.buingochai'),
 ('gv.vuthanhha'),
 ('gv.lelandung'),
 ('gv.doanduckhai'),
 ('gv.hothinhi'),
 ('gv.vothuythuy'),
 ('gv.luuthunga'),
 ('gv.buikimthu'),
 ('gv.lethuytuyet'),
 ('gv.nghiemkhanhdung'),
 ('gv.ngoquocviet'),
 ('gv.tranxuanvinh'),
 ('gv.phamthanhdung'),
 ('gv.duongphumanh'),
 ('gv.duongthihang'),
 ('gv.lyhonganh'),
 ('gv.duongdieutuyet'),
 ('gv.tasynghia'),
 ('gv.hokhackien'),
 ('gv.dokimyen'),
 ('gv.duonghoaidiep'),
 ('gv.doanbaoly'),
 ('gv.vuanhmai'),
 ('gv.lykhacphong'),
 ('gv.tranhaitram'),
 ('gv.duongkimthu'),
 ('gv.tranvanha'),
 ('gv.hathuylan'),
 ('gv.phanphuongtrang'),
 ('gv.vuongthevinh'),
 ('gv.taxuangiang'),
 ('gv.nghiemhongmai'),
 ('gv.nguyenthuyuyen'),
 ('gv.voquangson'),
 ('gv.phanquynhlinh'),
 ('gv.trinhngocan'),
 ('gv.vobichan'),
 ('gv.vuhoaitrang'),
 ('gv.hoangdieuthuy'),
 ('gv.dangtheviet'),
 ('gv.haquynhhue'),
 ('gv.thaigiaanh'),
 ('gv.hathihang'),
 ('gv.quachdinhkhanh'),
 ('gv.vodinhkhanh'),
 ('gv.voanhphuong'),
 ('gv.thaicongvu'),
 ('gv.hovanquang'),
 ('gv.lengocthinh'),
 ('gv.dinhminhquyen'),
 ('gv.caongochoang'),
 ('gv.trinhyenhong'),
 ('gv.vuonghuuhuy'),
 ('gv.phanhoaimai'),
 ('gv.hoanghailinh'),
 ('gv.maithuyhanh');

CREATE TABLE #S (Code VARCHAR(20) COLLATE DATABASE_DEFAULT PRIMARY KEY);
INSERT INTO #S (Code) VALUES
 ('TH01'),
 ('TH02'),
 ('TH03'),
 ('TA01'),
 ('TA02'),
 ('TA03'),
 ('TA04'),
 ('TA05'),
 ('TA06'),
 ('SA01'),
 ('SA02'),
 ('SA03'),
 ('SA04'),
 ('SA05'),
 ('SA06'),
 ('SA07'),
 ('KNS01'),
 ('KNS02'),
 ('KNS03'),
 ('KNS04'),
 ('KNS05'),
 ('KNS06'),
 ('KNS07');

/* ---- Gom Id của nhóm cần xóa ----
   Nhận diện theo HAI đường, hợp lại:
     (a) đúng 100 username của LẦN SINH HIỆN TẠI (bảng #U ở trên);
     (b) DẤU VÂN TAY của bộ seed: username bắt đầu 'gv.' VÀ email thuộc miền
         '@tsdms.local'.

   Vì sao cần (b): file seed được sinh ra từ một bộ sinh ngẫu nhiên, sinh lại
   là 100 username đổi hết. Nếu chỉ dựa vào (a) thì file rollback MỚI không
   dọn nổi dữ liệu do file seed CŨ nạp vào — sót lại vài chục giáo viên, và
   lần seed kế tiếp sẽ đâm trùng số hợp đồng. Đây là lỗi đã xảy ra thật.

   Vì sao (b) vẫn an toàn: '@tsdms.local' là tên miền nội bộ chỉ bộ seed này
   dùng. Giáo viên bạn tự thêm qua giao diện luôn mang email thật (gmail,
   tên miền trường...) nên không bao giờ lọt vào diện xóa. Thêm nữa, các chốt
   chặn ngay bên dưới sẽ DỪNG toàn bộ script nếu có bất kỳ ai trong diện này
   đã phát sinh dữ liệu vận hành. */
DECLARE @T TABLE (TeacherId INT PRIMARY KEY, AppUserId INT);
INSERT INTO @T (TeacherId, AppUserId)
SELECT t.Id, u.Id
FROM AppUser u
JOIN Teacher t ON t.AppUserId = u.Id
WHERE u.Username IN (SELECT Username FROM #U)
   OR (u.Username LIKE 'gv.%' AND u.Email LIKE '%@tsdms.local');

/* ---- CHỐT CHẶN: có dữ liệu vận hành thì DỪNG ---- */
IF EXISTS (SELECT 1 FROM Assignment        a  JOIN @T z ON z.TeacherId = a.TeacherId)
    THROW 50010, N'Có giáo viên đã được phân công (Assignment) — hủy phân công trước khi gỡ.', 1;
IF EXISTS (SELECT 1 FROM AssignmentSlot    s  JOIN @T z ON z.TeacherId = s.TeacherId)
    THROW 50011, N'Có giáo viên đang nằm trong khung lịch phân công (AssignmentSlot).', 1;
IF EXISTS (SELECT 1 FROM Schedule          sc JOIN @T z ON z.TeacherId = sc.TeacherId)
    THROW 50012, N'Có giáo viên đã có buổi dạy (Schedule).', 1;
IF EXISTS (SELECT 1 FROM Attendance        at JOIN @T z ON z.TeacherId = at.TeacherId)
    THROW 50013, N'Có giáo viên đã có dữ liệu chấm công (Attendance).', 1;
IF EXISTS (SELECT 1 FROM Payroll           p  JOIN @T z ON z.TeacherId = p.TeacherId)
    THROW 50014, N'Có giáo viên đã có bảng lương (Payroll).', 1;
IF EXISTS (SELECT 1 FROM TeacherEvaluation te JOIN @T z ON z.TeacherId = te.TeacherId)
    THROW 50015, N'Có giáo viên đã được đánh giá (TeacherEvaluation).', 1;
IF EXISTS (SELECT 1 FROM Lesson            l  JOIN @T z ON z.TeacherId = l.TeacherId)
    THROW 50016, N'Có giáo viên đang gắn với bài giảng (Lesson).', 1;

/* ---- Xóa theo đúng thứ tự khóa ngoại ----
   Đếm TRƯỚC khi xóa: sau lệnh DELETE thì @T vẫn còn nhưng PRINT không nhận
   subquery (lỗi 1046), nên phải gán sẵn ra biến. */
DECLARE @GoBo INT = (SELECT COUNT(*) FROM @T);

DELETE FROM TeacherSubject WHERE TeacherId IN (SELECT TeacherId FROM @T);
DELETE FROM Certificate    WHERE TeacherId IN (SELECT TeacherId FROM @T);
DELETE FROM Contract       WHERE TeacherId IN (SELECT TeacherId FROM @T);
DELETE FROM Teacher        WHERE Id        IN (SELECT TeacherId FROM @T);
DELETE FROM UserRole       WHERE AppUserId IN (SELECT AppUserId FROM @T);
DELETE FROM RefreshToken       WHERE AppUserId IN (SELECT AppUserId FROM @T);
DELETE FROM PasswordResetToken WHERE AppUserId IN (SELECT AppUserId FROM @T);
DELETE FROM AppUser        WHERE Id        IN (SELECT AppUserId FROM @T);

/* Quét nốt tài khoản của bộ seed bị bỏ lại KHÔNG kèm hồ sơ Teacher — sinh ra
   khi một lần chạy trước đó hỏng giữa chừng. Không dọn thì username/email bị
   chiếm chỗ và lần seed sau báo trùng khóa. */
DELETE ur FROM UserRole ur
JOIN AppUser u ON u.Id = ur.AppUserId
WHERE u.Username LIKE 'gv.%' AND u.Email LIKE '%@tsdms.local'
  AND NOT EXISTS (SELECT 1 FROM Teacher t WHERE t.AppUserId = u.Id);

DELETE u FROM AppUser u
WHERE u.Username LIKE 'gv.%' AND u.Email LIKE '%@tsdms.local'
  AND NOT EXISTS (SELECT 1 FROM Teacher t WHERE t.AppUserId = u.Id);

PRINT N'Đã gỡ ' + CAST(@GoBo AS NVARCHAR(10)) + N' giáo viên.';

/* ---- Môn học: chỉ xóa môn KHÔNG còn ai tham chiếu ---- */
DELETE FROM Subject
WHERE Code IN (SELECT Code FROM #S)
  AND NOT EXISTS (SELECT 1 FROM TeacherSubject ts WHERE ts.SubjectId = Subject.Id)
  AND NOT EXISTS (SELECT 1 FROM Assignment     a  WHERE a.SubjectId  = Subject.Id)
  AND NOT EXISTS (SELECT 1 FROM Lesson         l  WHERE l.SubjectId  = Subject.Id);

PRINT N'Đã gỡ ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' môn học (môn còn bị tham chiếu sẽ được giữ lại).';

/* ---- Trả tên nhóm môn về 'NLS-STEM - AI'? Bỏ chú thích dòng dưới nếu thật sự muốn. ----
UPDATE SubjectCategory SET Name = N'NLS-STEM - AI' WHERE Code = 'STEM_AI';
*/

DROP TABLE #U; DROP TABLE #S;

COMMIT TRANSACTION;
PRINT N'>>> XONG.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DROP TABLE IF EXISTS #U;
    DROP TABLE IF EXISTS #S;
    PRINT N'!!! LỖI — đã rollback, không xóa gì cả.';
    THROW;
END CATCH
GO
