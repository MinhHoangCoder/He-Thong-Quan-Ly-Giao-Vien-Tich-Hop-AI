/* =====================================================================
   TSDMS — GỠ SEED DỮ LIỆU LỚN (đợt 2026-08)

   Đảo ngược database/seed/TSDMS_Seed_2026_DuLieuLon.sql.

   GỠ ĐƯỢC GÌ
     - Toàn bộ phân công / lịch dạy / chấm công / bảng lương do đợt seed sinh
       ra (xóa CỨNG, không phải xóa mềm — đây là dữ liệu demo, giữ lại trong
       thùng rác chỉ làm bẩn màn hình).
     - 50 giáo viên bổ sung cùng tài khoản, hợp đồng, môn dạy được của họ.
     - Các lớp và phòng học sinh thêm ở PHẦN 1.

   NHẬN DIỆN DÒNG CẦN XÓA BẰNG CỘT MỐC, KHÔNG ĐOÁN THEO NGÀY
     File seed ghi Id lớn nhất của SchoolClass / Room / AppUser / Teacher /
     Contract vào bảng seed_2026_Moc TRƯỚC khi chèn dòng nào. File này chỉ
     xóa dòng có Id LỚN HƠN mốc, nên không thể đụng nhầm dữ liệu có sẵn. Bản
     đầu tiên đoán theo CreatedAt và đã xóa lố hai lớp vốn được tạo cùng ngày.

     Không có bảng mốc (seed chưa từng chạy, hoặc đã bị xóa tay) thì file này
     dừng lại và báo, thay vì xóa mò.

   KHÔNG GỠ (cố ý)
     - 100 giáo viên gốc: thuộc đợt seed TSDMS_Seed_100GiaoVien.sql, gỡ bằng
       file rollback riêng của nó.
     - Trạng thái và hạn hợp đồng của trường: file seed chỉ MỞ LẠI trường và
       gia hạn hợp đồng, không tạo trường mới. Đưa chúng về INACTIVE máy móc
       sẽ đụng vào cả những trường vốn đã hoạt động từ trước.
     - Bảng zz_bak* đã xóa ở PHẦN 0: chúng là rác của các đợt seed cũ, không
       có gì để khôi phục. Muốn lấy lại thì restore từ file .bak.
     - EmploymentType của 100 giáo viên gốc: file seed đã ghi đè tỉ lệ
       30/120. Muốn về đúng bộ cũ thì restore từ file .bak.

   Nói cách khác: file này đủ để dọn sạch phần ĐIỀU PHỐI và trả số giáo viên
   về 100. Muốn về đúng trạng thái trước khi chạy seed thì cách chắc chắn duy
   nhất là restore bản sao lưu đã tạo trước đó.
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* CHỐT CHẶN: không có bảng mốc thì DỪNG, không xóa mò.
   SET NOEXEC ON là cách duy nhất chặn được các batch phía sau: RETURN chỉ
   thoát batch hiện tại, còn RAISERROR thì SSMS vẫn chạy tiếp phần sau dấu
   GO. NOEXEC bỏ qua mọi batch còn lại cho tới khi gặp SET NOEXEC OFF ở cuối
   file. */
IF OBJECT_ID('dbo.seed_2026_Moc', 'U') IS NULL
BEGIN
    RAISERROR (N'Không tìm thấy bảng seed_2026_Moc — đợt seed 2026-08 chưa từng chạy trên DB này, hoặc bảng mốc đã bị xóa tay. Dừng lại để không xóa nhầm dữ liệu có sẵn.', 16, 1);
    SET NOEXEC ON;
END
GO

DECLARE @mocLop   INT = (SELECT MocId FROM seed_2026_Moc WHERE Bang = 'SchoolClass');
DECLARE @mocPhong INT = (SELECT MocId FROM seed_2026_Moc WHERE Bang = 'Room');

BEGIN TRANSACTION;

/* ---- 1. Dữ liệu điều phối — xóa theo đúng thứ tự khóa ngoại ---- */
DELETE FROM PayrollChangeLog;
DELETE FROM Payroll;
DELETE FROM AttendanceChangeLog;
DELETE FROM AttendanceAmendRequest;
DELETE FROM Attendance;
DELETE FROM ScheduleStatusLog;
DELETE FROM Schedule;
DELETE FROM AssignmentSlot;
DELETE FROM Assignment;
PRINT N'  · Đã xóa phân công / lịch dạy / chấm công / bảng lương';

/* ---- 2. Bảng trung gian (phòng khi file seed dừng giữa chừng) ----
   KHÔNG xóa seed_2026_Moc ở đây: nó còn phải dùng ở bước 5. */
DROP TABLE IF EXISTS seed_Slot, seed_Part, seed_Lane, seed_GV, seed_Truong,
                     seed_HocKy, seed_Ngay, seed_Phieu, seed_PhieuCho;

/* ---- 3. 50 giáo viên bổ sung ----
   Nhận diện qua username: 50 tài khoản này do đợt seed 2026-08 tạo ra, danh
   sách khớp đúng phần 2.1 của file seed. Xóa hồ sơ trước, tài khoản sau. */
DECLARE @gv TABLE (UserName VARCHAR(100) PRIMARY KEY);
INSERT INTO @gv (UserName) VALUES
('gv.dangminhkhoa'),  ('gv.dangthutrang'), ('gv.hoangbaolong'),   ('gv.hoangdieulinh'),
('gv.lamquocdat'),    ('gv.lamthanhvan'),  ('gv.luongducduy'),    ('gv.luonghamy'),
('gv.maianhtuan'),    ('gv.maiphuongthao'),('gv.ngogiabao'),      ('gv.ngokhanhchi'),
('gv.nguyendangkhoi'),('gv.nguyenhaiyen'), ('gv.nguyentrongnghia'),('gv.nguyentueminh'),
('gv.phamdinhphuc'),  ('gv.phamlanhuong'), ('gv.phamquangvinh'),  ('gv.phamtuyetnhung'),
('gv.phanbathang'),   ('gv.phanngocanh'),  ('gv.quachhuunam'),    ('gv.quachthuyduong'),
('gv.taconghoang'),   ('gv.tamyhanh'),     ('gv.thaivancuong'),   ('gv.thaixuanmai'),
('gv.trandackien'),   ('gv.tranhongnhung'),('gv.tranmanhhung'),   ('gv.trantouyen'),
('gv.trinhbaloc'),    ('gv.trinhdieuthuy'),('gv.truonghoaiphong'),('gv.truongkieuoanh'),
('gv.vanductoan'),    ('gv.vanthiloan'),   ('gv.vudinhson'),      ('gv.vuhavy'),
('gv.vuongchithanh'), ('gv.vuongtamnhu'),  ('gv.bachdongquan'),   ('gv.bachhanhnguyen'),
('gv.chukienan'),     ('gv.chunganha'),    ('gv.duongbaotrung'),  ('gv.duongcamtu'),
('gv.hasytruong'),    ('gv.hayenngoc');

DECLARE @teacherIds TABLE (Id INT PRIMARY KEY);
INSERT INTO @teacherIds (Id)
SELECT t.Id FROM Teacher t
  JOIN AppUser u ON u.Id = t.AppUserId
  JOIN @gv g     ON g.UserName = u.Username;

DELETE FROM TeacherSubject   WHERE TeacherId IN (SELECT Id FROM @teacherIds);
DELETE FROM Certificate      WHERE TeacherId IN (SELECT Id FROM @teacherIds);
DELETE FROM TeacherEvaluation WHERE TeacherId IN (SELECT Id FROM @teacherIds);
DELETE FROM Contract         WHERE TeacherId IN (SELECT Id FROM @teacherIds);
DELETE FROM Teacher          WHERE Id        IN (SELECT Id FROM @teacherIds);

DECLARE @userIds TABLE (Id INT PRIMARY KEY);
INSERT INTO @userIds (Id) SELECT u.Id FROM AppUser u JOIN @gv g ON g.UserName = u.Username;

DELETE FROM RefreshToken       WHERE AppUserId IN (SELECT Id FROM @userIds);
DELETE FROM PasswordResetToken WHERE AppUserId IN (SELECT Id FROM @userIds);
DELETE FROM Notification       WHERE RecipientUserId IN (SELECT Id FROM @userIds);
DELETE FROM UserRole           WHERE AppUserId IN (SELECT Id FROM @userIds);
DELETE FROM AppUser            WHERE Id        IN (SELECT Id FROM @userIds);
PRINT N'  · Đã xóa 50 giáo viên bổ sung và tài khoản của họ';

/* ---- 4. Hợp đồng do đợt seed tạo cho giáo viên gốc ----
   Nhận diện qua số hợp đồng: đợt này dùng tiền tố HD-2025-GV, các đợt trước
   dùng HD-2026-GV. Chỉ xóa hợp đồng chưa từng được dùng ở đâu khác. */
DELETE FROM Contract WHERE ContractNo LIKE 'HD-2025-GV%';

/* ---- 5. Lớp và phòng học sinh thêm ----
   Chỉ dòng có Id LỚN HƠN cột mốc. Điều kiện "không còn ai trỏ vào" vẫn giữ
   để phòng trường hợp ai đó chạy file này khi chưa xóa phân công. */
DELETE c FROM SchoolClass c
 WHERE c.Id > @mocLop
   AND NOT EXISTS (SELECT 1 FROM AssignmentSlot sl WHERE sl.ClassId = c.Id)
   AND NOT EXISTS (SELECT 1 FROM Assignment a      WHERE a.ClassId = c.Id)
   AND NOT EXISTS (SELECT 1 FROM ClassEnrollment e WHERE e.ClassId = c.Id);
PRINT N'  · Đã xóa lớp học sinh thêm';

DELETE r FROM Room r
 WHERE r.Id > @mocPhong
   AND NOT EXISTS (SELECT 1 FROM AssignmentSlot sl WHERE sl.RoomId = r.Id)
   AND NOT EXISTS (SELECT 1 FROM Schedule s        WHERE s.RoomId = r.Id);
PRINT N'  · Đã xóa phòng học sinh thêm';

DROP TABLE seed_2026_Moc;

COMMIT TRANSACTION;
GO

SELECT 'Giáo viên'        AS Bang, COUNT(*) AS SoDong FROM Teacher WHERE IsDeleted = 0
UNION ALL SELECT 'Lớp học',           COUNT(*) FROM SchoolClass WHERE IsDeleted = 0
UNION ALL SELECT 'Phiếu phân công',   COUNT(*) FROM Assignment
UNION ALL SELECT 'Buổi dạy',          COUNT(*) FROM Schedule
UNION ALL SELECT 'Bản ghi chấm công', COUNT(*) FROM Attendance
UNION ALL SELECT 'Phiếu lương',       COUNT(*) FROM Payroll;
GO

PRINT N'ĐÃ GỠ XONG.';
GO

/* Mở lại việc thực thi cho các câu lệnh chạy sau file này trong cùng phiên. */
SET NOEXEC OFF;
GO
