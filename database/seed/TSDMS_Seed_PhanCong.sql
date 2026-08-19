/* =====================================================================
   TSDMS — SEED LUỒNG ĐIỀU PHỐI: PHÂN CÔNG → LỊCH DẠY → CHẤM CÔNG → LƯƠNG
   ---------------------------------------------------------------------
   CHẠY THẾ NÀO
     Mở trong SSMS (đã kết nối DB TSDMS) rồi Execute, hoặc:
       sqlcmd -S localhost -d TSDMS -U tsdms_app -P *** -i TSDMS_Seed_PhanCong.sql
     KHÔNG đưa vào Flyway — đây là dữ liệu demo. Chạy lại lần hai tự bỏ qua.

   VÌ SAO CÓ FILE NÀY
     Dự án có đủ hồ sơ nền (101 GV, 30 trường, 198 lớp, 285 khung tiết) nhưng
     TOÀN BỘ bảng vận hành đang rỗng: Assignment, AssignmentSlot, Schedule,
     Attendance, Payroll đều 0 dòng. Đó là một DÂY CHUYỀN — mắt đầu trống thì
     Bảng điều khiển, Lịch dạy, Chấm công, Bảng lương đều không có gì để hiện,
     dù code của cả bốn màn hình đã chạy được.

         Assignment → AssignmentSlot → Schedule → Attendance → Payroll

   FILE NÀY TẠO RA GÌ (3 đợt, phủ trọn vòng đời phiếu phân công)

     Đợt A — HỌC KỲ II NĂM HỌC 2025-2026 (05/01/2026 → 22/05/2026), ĐÃ XONG.
       Phiếu COMPLETED, buổi APPROVED, chấm công đầy đủ, lương đã chốt/đã trả.
       Đây là phần làm cho biểu đồ 7 tháng và tỷ lệ chuyên cần có số thật.
       Kèm theo: bộ LỚP năm học 2025-2026 (nhân bản từ lớp 2026-2027) — không
       có lớp của đúng năm học đó thì phân công tháng 1/2026 lại trỏ vào lớp
       năm 2026-2027, dữ liệu tự mâu thuẫn.

     Đợt B — HỌC KỲ I NĂM HỌC 2026-2027 (10/08/2026 → 15/01/2027), ĐANG CHẠY.
       Phiếu ACTIVE (giáo viên đã xác nhận), buổi APPROVED. Buổi đã trôi qua
       thì có chấm công; buổi phía trước để nguyên chờ tới ngày.

     Đợt C — PHIẾU VỪA GỬI, CHƯA XÁC NHẬN (từ thứ Hai tới → 15/01/2027).
       Phiếu PENDING kèm lời mời trong chuông (Notification RequiresAction),
       hạn trả lời còn hiệu lực; một phần nhỏ REJECTED kèm lý do. Có đợt này
       thì màn Phân công mới có việc để làm, không chỉ toàn phiếu đã xong.

   BA LUẬT NGHIỆP VỤ ĐƯỢC TÔN TRỌNG KHI XẾP (không phải random cho vui)
     1) Giáo viên chỉ dạy môn mình có trong TeacherSubject.
     2) Không ai — giáo viên hay lớp — bị xếp hai chỗ trùng giờ trong cùng
        một năm học (so theo KHOẢNG GIỜ THẬT của tiết, không so số tiết: tiết
        1 của hai trường là hai PeriodId khác nhau mà giờ vẫn đè nhau).
     3) Một giáo viên không chạy hai trường trong cùng một ngày.
     Xếp bằng vòng lặp tham lam, ưu tiên giáo viên đã quen trường và người
     đang rảnh nhất, trần 20 tiết/tuần mỗi người.

   NGÀY NGHỈ
     Buổi dạy KHÔNG sinh vào ngày có trong bảng Holiday (Flyway V29) — cùng
     đúng một luật với AssignmentService.generateSchedules. Vì thế file này
     ĐÒI bảng Holiday phải tồn tại; chưa có thì dừng, không seed nửa vời.

   ĐƠN GIÁ LƯƠNG
     115.000đ/tiết (khối 1-5) và 125.000đ/tiết (khối 6-9) — chép đúng hằng số
     TH_RATE/THCS_RATE trong PayrollService để bấm "Tạo bảng lương" trên giao
     diện ra ĐÚNG con số này, không nhảy số. Dòng lương của tháng hiện tại để
     DRAFT cho đúng: PayrollService.generate() chỉ ghi đè dòng nháp.

   GỠ RA: chạy database/seed/TSDMS_Rollback_PhanCong.sql
   ===================================================================== */

USE TSDMS;
GO

/* Filtered index trên AssignmentSlot/SchoolClass đòi QUOTED_IDENTIFIER ON,
   SSMS bật sẵn còn sqlcmd thì không (lỗi 1934). */
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---- CHỐT CHẶN: đã nạp rồi thì thoát ---- */
IF EXISTS (SELECT 1 FROM Assignment)
BEGIN
    PRINT N'>>> Đã có dữ liệu phân công — bỏ qua, không chạy lại.';
    PRINT N'    Muốn nạp lại: chạy TSDMS_Rollback_PhanCong.sql trước.';
    RETURN;
END

BEGIN TRY
BEGIN TRANSACTION;

/* ═══════════════ 0) TIỀN ĐỀ ═══════════════ */

DECLARE @Admin INT = (SELECT Id FROM AppUser WHERE Username = 'admin' AND IsDeleted = 0);
IF @Admin IS NULL
    THROW 50001, N'Không tìm thấy tài khoản admin — kiểm tra lại DB trước khi seed.', 1;

IF OBJECT_ID('dbo.Holiday', 'U') IS NULL
    THROW 50002, N'Chưa có bảng Holiday (Flyway V29). Khởi động backend một lần cho Flyway chạy migration rồi seed lại — thiếu bảng này lịch sẽ sinh cả buổi vào ngày lễ.', 1;

/* Người ký phân công: một nhân viên điều phối bất kỳ của trung tâm. */
DECLARE @DieuPhoi INT = (SELECT TOP 1 Id FROM Employee WHERE IsDeleted = 0 ORDER BY Id);

DECLARE @Today DATE = CAST(GETDATE() AS DATE);

/* Biến đếm cho các dòng PRINT tiến độ — PRINT không nhận truy vấn con. */
DECLARE @n1 INT, @n2 INT, @n3 INT, @n4 INT;

/* Mốc học kỳ. GETDATE() ở máy này trả thẳng giờ tường Việt Nam (xem ghi chú
   múi giờ ở TsdmsApplication) nên không phải bù offset. */
DECLARE @A_Tu DATE = '2026-01-05', @A_Den DATE = '2026-05-22';   -- HK2 2025-2026, đã xong
DECLARE @B_Tu DATE = '2026-08-10', @B_Den DATE = '2027-01-15';   -- HK1 2026-2027, đang chạy

/* Đợt C bắt đầu từ thứ Hai TỚI: phiếu tạo mới không được bắt đầu trong quá
   khứ (luật ở AssignmentService.create), seed cũng phải theo cho khớp. */
DECLARE @C_Tu DATE = DATEADD(DAY, ((0 - (DATEDIFF(DAY, '19000101', @Today) % 7)) + 7) % 7, @Today);
IF @C_Tu <= @Today SET @C_Tu = DATEADD(WEEK, 1, @C_Tu);
DECLARE @C_Den DATE = @B_Den;

IF @C_Tu >= @C_Den
    THROW 50003, N'Hôm nay đã quá ngày kết thúc học kỳ I 2026-2027 — sửa lại mốc @B_Den trong file seed trước khi chạy.', 1;

PRINT N'0) Mốc thời gian: HK2 2025-2026 ' + CONVERT(NVARCHAR(10), @A_Tu, 103) + N'→' + CONVERT(NVARCHAR(10), @A_Den, 103)
    + N' · HK1 2026-2027 ' + CONVERT(NVARCHAR(10), @B_Tu, 103) + N'→' + CONVERT(NVARCHAR(10), @B_Den, 103)
    + N' · phiếu mới từ ' + CONVERT(NVARCHAR(10), @C_Tu, 103);

/* ═══════════════ 1) LỚP CỦA NĂM HỌC 2025-2026 ═══════════════
   Nhân bản danh sách lớp của các trường đang hoạt động. Trường có lớp 6A ở
   năm nào cũng vậy — đây là dữ liệu đúng, không phải bản sao chữa cháy. */

INSERT INTO SchoolClass (SchoolId, Name, GradeLevel, SchoolYear, Status, CreatedBy)
SELECT c.SchoolId, c.Name, c.GradeLevel, '2025-2026', 'ACTIVE', @Admin
FROM SchoolClass c
JOIN School s ON s.Id = c.SchoolId AND s.Status = 'ACTIVE' AND s.IsDeleted = 0
WHERE c.SchoolYear = '2026-2027'
  AND c.IsDeleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM SchoolClass x
      WHERE x.SchoolId = c.SchoolId AND x.Name = c.Name AND x.SchoolYear = '2025-2026' AND x.IsDeleted = 0);

PRINT N'1) Đã tạo ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' lớp cho năm học 2025-2026.';

/* ═══════════════ 2) MÔN NÀO DẠY ĐƯỢC KHỐI NÀO ═══════════════
   Không có bảng này thì lớp 2 sẽ được xếp môn "Bảng tính Excel" và đơn giá
   lương tính theo khối cũng thành vô nghĩa. */

CREATE TABLE #MonKhoi (Code VARCHAR(20) COLLATE DATABASE_DEFAULT PRIMARY KEY, KhoiMin INT, KhoiMax INT);
INSERT INTO #MonKhoi (Code, KhoiMin, KhoiMax) VALUES
 ('TH01', 3, 9),   -- Tin học cơ bản (GDPT 2018)
 ('TH02', 6, 9),   -- Soạn thảo văn bản & Trình chiếu
 ('TH03', 7, 9),   -- Bảng tính Excel cho học sinh
 ('TA01', 1, 2),   -- Tiếng Anh làm quen (lớp 1-2)
 ('TA02', 3, 5),   -- Tiếng Anh Tiểu học 3-5 (Global Success)
 ('TA03', 3, 5),   -- Tiếng Anh Tiểu học 3-5 (i-Learn Smart Start)
 ('TA04', 6, 9),   -- Tiếng Anh THCS 6-9 (Global Success)
 ('TA05', 6, 9),   -- Tiếng Anh THCS 6-9 (i-Learn Smart World)
 ('TA06', 1, 5),   -- Ngữ âm & Phonics Tiểu học
 ('SA01', 3, 7),   -- Lập trình Scratch
 ('SA02', 4, 9),   -- Lập trình robot Leanbot
 ('SA03', 3, 8),   -- Lập trình robot Vincibot
 ('SA04', 1, 5),   -- Lập trình robot Spike Essential
 ('SA05', 6, 9),   -- Lập trình robot RoboSim
 ('SA06', 1, 6),   -- Tinkering
 ('SA07', 6, 9),   -- Nhập môn Trí tuệ nhân tạo
 ('KNS01', 1, 9),  -- Kỹ năng giao tiếp & thuyết trình
 ('KNS02', 3, 9),  -- Kỹ năng làm việc nhóm & lãnh đạo
 ('KNS03', 1, 9),  -- Kỹ năng tự bảo vệ & phòng chống xâm hại
 ('KNS04', 6, 9),  -- Kỹ năng quản lý thời gian & tài chính cá nhân
 ('KNS05', 3, 9),  -- Công dân số cơ bản
 ('KNS06', 4, 9),  -- An toàn trên không gian mạng
 ('KNS07', 6, 9);  -- Khai thác AI an toàn cho học sinh

/* ═══════════════ 3) DANH SÁCH KHÓA CẦN MỞ ═══════════════ */

CREATE TABLE #Lop (
    ClassId  INT PRIMARY KEY,
    SchoolId INT,
    Khoi     INT,
    NamHoc   VARCHAR(9) COLLATE DATABASE_DEFAULT,
    rn       INT
);
INSERT INTO #Lop (ClassId, SchoolId, Khoi, NamHoc, rn)
SELECT c.Id, c.SchoolId, TRY_CAST(c.GradeLevel AS INT), c.SchoolYear,
       ROW_NUMBER() OVER (PARTITION BY c.SchoolYear ORDER BY c.SchoolId, c.Id)
FROM SchoolClass c
JOIN School s ON s.Id = c.SchoolId AND s.Status = 'ACTIVE' AND s.IsDeleted = 0
WHERE c.IsDeleted = 0
  AND c.Status = 'ACTIVE'
  AND c.SchoolYear IN ('2025-2026', '2026-2027')
  AND TRY_CAST(c.GradeLevel AS INT) BETWEEN 1 AND 9
  AND EXISTS (SELECT 1 FROM Period p WHERE p.SchoolId = c.SchoolId AND p.IsDeleted = 0);

CREATE TABLE #NhuCau (
    Seq       INT IDENTITY(1,1) PRIMARY KEY,
    Dot       CHAR(1) COLLATE DATABASE_DEFAULT,
    NamHoc    VARCHAR(9) COLLATE DATABASE_DEFAULT,
    SchoolId  INT,
    ClassId   INT,
    Khoi      INT,
    SubjectId INT NULL,
    SoTiet    INT,
    TeacherId INT NULL,
    TuNgay    DATE,
    DenNgay   DATE,
    TrangThai VARCHAR(10) COLLATE DATABASE_DEFAULT
);

/* Không phải lớp nào cũng mua chương trình của trung tâm — lấy một phần, và
   lấy theo công thức cố định để chạy lại ở máy khác vẫn ra đúng bộ dữ liệu. */
INSERT INTO #NhuCau (Dot, NamHoc, SchoolId, ClassId, Khoi, SoTiet, TuNgay, DenNgay, TrangThai)
SELECT 'A', NamHoc, SchoolId, ClassId, Khoi,
       CASE WHEN rn % 5 = 0 THEN 1 ELSE 2 END, @A_Tu, @A_Den, 'COMPLETED'
FROM #Lop WHERE NamHoc = '2025-2026' AND rn % 3 <> 0;

INSERT INTO #NhuCau (Dot, NamHoc, SchoolId, ClassId, Khoi, SoTiet, TuNgay, DenNgay, TrangThai)
SELECT 'B', NamHoc, SchoolId, ClassId, Khoi,
       CASE WHEN rn % 5 = 0 THEN 1 ELSE 2 END, @B_Tu, @B_Den, 'ACTIVE'
FROM #Lop WHERE NamHoc = '2026-2027' AND rn % 3 <> 0;

INSERT INTO #NhuCau (Dot, NamHoc, SchoolId, ClassId, Khoi, SoTiet, TuNgay, DenNgay, TrangThai)
SELECT 'C', NamHoc, SchoolId, ClassId, Khoi,
       1, @C_Tu, @C_Den,
       CASE WHEN rn % 30 = 0 THEN 'REJECTED' ELSE 'PENDING' END
FROM #Lop WHERE NamHoc = '2026-2027' AND rn % 6 = 0;

/* Mỗi lớp một môn, xoay vòng trong các môn hợp khối để bộ dữ liệu đủ đa dạng
   (nếu luôn lấy môn đầu bảng thì 132 lớp cùng học một môn). */
;WITH HopKhoi AS (
    SELECT n.Seq, s.Id AS SubjectId,
           ROW_NUMBER() OVER (PARTITION BY n.Seq ORDER BY s.Code) AS sn,
           COUNT(*)     OVER (PARTITION BY n.Seq)                 AS cnt
    FROM #NhuCau n
    JOIN #MonKhoi m ON n.Khoi BETWEEN m.KhoiMin AND m.KhoiMax
    JOIN Subject  s ON s.Code = m.Code AND s.IsDeleted = 0
)
UPDATE n SET SubjectId = h.SubjectId
FROM #NhuCau n
JOIN HopKhoi h ON h.Seq = n.Seq AND h.sn = (n.Seq % h.cnt) + 1;

DELETE FROM #NhuCau WHERE SubjectId IS NULL;

SELECT @n1 = COUNT(*),
       @n2 = SUM(CASE WHEN Dot = 'A' THEN 1 ELSE 0 END),
       @n3 = SUM(CASE WHEN Dot = 'B' THEN 1 ELSE 0 END),
       @n4 = SUM(CASE WHEN Dot = 'C' THEN 1 ELSE 0 END)
FROM #NhuCau;
PRINT N'3) Cần mở ' + CAST(@n1 AS NVARCHAR(10)) + N' khóa ('
    + CAST(@n2 AS NVARCHAR(10)) + N' đợt A · '
    + CAST(@n3 AS NVARCHAR(10)) + N' đợt B · '
    + CAST(@n4 AS NVARCHAR(10)) + N' đợt C).';

/* ═══════════════ 4) XẾP GIÁO VIÊN + THỨ + TIẾT ═══════════════
   Vòng lặp tham lam: duyệt từng khóa, tìm ô trống đầu tiên còn hợp mọi luật.
   Chậm hơn một câu INSERT nhưng đây là chỗ DUY NHẤT bảo đảm dữ liệu sinh ra
   không tự mâu thuẫn — mà lịch trùng giờ thì mọi báo cáo phía sau đều sai. */

CREATE TABLE #Thu (Code CHAR(3) COLLATE DATABASE_DEFAULT PRIMARY KEY, Idx INT, Ord INT);
INSERT INTO #Thu (Code, Idx, Ord) VALUES ('MON',0,1), ('TUE',1,2), ('WED',2,3), ('THU',3,4), ('FRI',4,5);

CREATE TABLE #Ban (          -- ô đã bị chiếm, theo từng năm học
    NamHoc    VARCHAR(9) COLLATE DATABASE_DEFAULT,
    DayCode   CHAR(3) COLLATE DATABASE_DEFAULT,
    TeacherId INT,
    ClassId   INT,
    SchoolId  INT,
    StartTime TIME(0),
    EndTime   TIME(0)
);
CREATE INDEX IX_Ban ON #Ban (NamHoc, DayCode, TeacherId);

CREATE TABLE #Slot (Seq INT, DayCode CHAR(3) COLLATE DATABASE_DEFAULT, PeriodId INT);

DECLARE @seq INT, @namhoc VARCHAR(9), @school INT, @class INT, @subject INT, @sotiet INT;
DECLARE @gv INT, @i INT, @day CHAR(3), @pid INT, @gvChon INT, @st TIME(0), @et TIME(0);

DECLARE curKhoa CURSOR LOCAL FAST_FORWARD FOR
    SELECT Seq, NamHoc, SchoolId, ClassId, SubjectId, SoTiet FROM #NhuCau ORDER BY Seq;
OPEN curKhoa;
FETCH NEXT FROM curKhoa INTO @seq, @namhoc, @school, @class, @subject, @sotiet;

WHILE @@FETCH_STATUS = 0
BEGIN
    SET @gv = NULL;
    SET @i  = 0;

    WHILE @i < @sotiet
    BEGIN
        SET @day = NULL; SET @pid = NULL; SET @gvChon = NULL; SET @st = NULL; SET @et = NULL;

        SELECT TOP 1
               @gvChon = ts.TeacherId, @day = d.Code, @pid = p.Id,
               @st = p.StartTime, @et = p.EndTime
        FROM #Thu d
        CROSS JOIN Period p
        JOIN TeacherSubject ts ON ts.SubjectId = @subject
        JOIN Teacher t ON t.Id = ts.TeacherId AND t.IsDeleted = 0 AND t.Status = 'ACTIVE'
        WHERE p.SchoolId = @school
          AND p.IsDeleted = 0
          -- Tiết thứ hai của cùng một phiếu phải do CHÍNH giáo viên đó dạy.
          AND (@gv IS NULL OR ts.TeacherId = @gv)
          -- Mỗi phiếu tối đa 1 tiết/ngày (2 tiết/tuần thì rơi vào 2 ngày khác nhau).
          AND NOT EXISTS (SELECT 1 FROM #Slot s2 WHERE s2.Seq = @seq AND s2.DayCode = d.Code)
          -- Trần tải: 8 tiết/tuần một người (~34 tiết/tháng ≈ 4 triệu đồng lương tiết).
          AND (SELECT COUNT(*) FROM #Ban b WHERE b.NamHoc = @namhoc AND b.TeacherId = ts.TeacherId) < 8
          -- Lớp đang bận giờ đó?
          AND NOT EXISTS (
                SELECT 1 FROM #Ban b
                WHERE b.NamHoc = @namhoc AND b.DayCode = d.Code AND b.ClassId = @class
                  AND b.StartTime < p.EndTime AND p.StartTime < b.EndTime)
          -- Giáo viên đang bận giờ đó (ở BẤT KỲ trường nào)?
          AND NOT EXISTS (
                SELECT 1 FROM #Ban b
                WHERE b.NamHoc = @namhoc AND b.DayCode = d.Code AND b.TeacherId = ts.TeacherId
                  AND b.StartTime < p.EndTime AND p.StartTime < b.EndTime)
          -- Không chạy hai trường trong cùng một ngày (không có thời gian di chuyển).
          AND NOT EXISTS (
                SELECT 1 FROM #Ban b
                WHERE b.NamHoc = @namhoc AND b.DayCode = d.Code AND b.TeacherId = ts.TeacherId
                  AND b.SchoolId <> @school)
        ORDER BY
              -- ưu tiên giáo viên đã dạy sẵn ở trường này
              CASE WHEN EXISTS (SELECT 1 FROM #Ban b
                                WHERE b.NamHoc = @namhoc AND b.TeacherId = ts.TeacherId AND b.SchoolId = @school)
                   THEN 0 ELSE 1 END,
              -- rồi DỒN việc cho người đang dạy nhiều nhất (còn dưới trần), thay vì rải
              -- mỏng: rải đều 300 tiết/tuần cho 91 giáo viên thì ai cũng 3 tiết/tuần và
              -- bảng lương ra 500 nghìn đồng/tháng — con số đúng công thức nhưng vô lý.
              (SELECT COUNT(*) FROM #Ban b WHERE b.NamHoc = @namhoc AND b.TeacherId = ts.TeacherId) DESC,
              -- chương trình của trung tâm ưu tiên xếp buổi chiều
              CASE WHEN p.PeriodNumber >= 6 THEN 0 ELSE 1 END,
              d.Ord, p.PeriodNumber, ts.TeacherId;

        IF @pid IS NULL BREAK;   -- hết chỗ trống hợp lệ: khóa này chỉ mở được ít tiết hơn dự tính

        INSERT INTO #Slot (Seq, DayCode, PeriodId) VALUES (@seq, @day, @pid);
        INSERT INTO #Ban (NamHoc, DayCode, TeacherId, ClassId, SchoolId, StartTime, EndTime)
        VALUES (@namhoc, @day, @gvChon, @class, @school, @st, @et);

        SET @gv = @gvChon;
        SET @i  = @i + 1;
    END

    IF @gv IS NOT NULL
        UPDATE #NhuCau SET TeacherId = @gv WHERE Seq = @seq;

    FETCH NEXT FROM curKhoa INTO @seq, @namhoc, @school, @class, @subject, @sotiet;
END

CLOSE curKhoa;
DEALLOCATE curKhoa;

/* Khóa không xếp nổi tiết nào thì bỏ hẳn — thà ít phiếu còn hơn phiếu rỗng. */
DELETE FROM #Slot   WHERE Seq IN (SELECT Seq FROM #NhuCau WHERE TeacherId IS NULL);
DELETE FROM #NhuCau WHERE TeacherId IS NULL;

SELECT @n1 = COUNT(*) FROM #Slot;
SELECT @n2 = COUNT(*), @n3 = COUNT(DISTINCT TeacherId) FROM #NhuCau;
PRINT N'4) Đã xếp ' + CAST(@n1 AS NVARCHAR(10)) + N' tiết/tuần cho '
    + CAST(@n2 AS NVARCHAR(10)) + N' phiếu, '
    + CAST(@n3 AS NVARCHAR(10)) + N' giáo viên tham gia.';

/* ═══════════════ 5) GHI PHIẾU PHÂN CÔNG ═══════════════ */

CREATE TABLE #MapA (Seq INT PRIMARY KEY, AssignmentId INT);

/* MERGE ... ON 1=0 chỉ để mượn mệnh đề OUTPUT: nó là cách duy nhất lấy được
   Id vừa sinh KÈM khóa nguồn (Seq) trong một lần INSERT hàng loạt. */
MERGE INTO Assignment AS tgt
USING (
    SELECT n.Seq, n.TeacherId, n.SchoolId, n.SubjectId, n.ClassId,
           n.TuNgay, n.DenNgay, n.TrangThai, n.Dot,
           t.AppUserId AS GvUserId
    FROM #NhuCau n
    JOIN Teacher t ON t.Id = n.TeacherId
) AS src
ON 1 = 0
WHEN NOT MATCHED THEN
    INSERT (TeacherId, SchoolId, SubjectId, ClassId, AssignedByEmployeeId,
            StartDate, EndDate, Status, CreatedAt, CreatedBy,
            ConfirmDeadline, ConfirmedAt, ConfirmedByUserId, ConfirmSource, RejectionReason)
    VALUES (src.TeacherId, src.SchoolId, src.SubjectId, src.ClassId, @DieuPhoi,
            src.TuNgay, src.DenNgay, src.TrangThai,
            DATEADD(DAY, -10, CAST(src.TuNgay AS DATETIME2(3))), @Admin,
            /* Các cột xác nhận để trống ở đây, ghi ngay sau bằng một UPDATE. */
            NULL, NULL, NULL, NULL, NULL)
OUTPUT src.Seq, inserted.Id INTO #MapA (Seq, AssignmentId);

/* Các cột phụ thuộc trạng thái ghi ở bước riêng cho dễ đọc (và vì CASE lồng
   trong MERGE rất khó soát lại sau này).

   HẠN XÁC NHẬN của phiếu CHỜ phải nằm ở TƯƠNG LAI: job quét hằng giờ
   (AssignmentApprovalService.sweepExpired) chuyển mọi phiếu PENDING quá hạn
   thành EXPIRED và hủy sạch buổi dạy chưa diễn ra — seed hạn quá khứ thì chỉ
   sau một tiếng chạy backend là toàn bộ đợt C tự bốc hơi. */
UPDATE a
   SET a.ConfirmDeadline = CASE
           WHEN n.TrangThai = 'PENDING'  THEN DATEADD(DAY, 2, GETDATE())
           ELSE DATEADD(HOUR, 48, DATEADD(DAY, -10, CAST(n.TuNgay AS DATETIME2(3))))
       END,
       a.ConfirmedAt = CASE
           WHEN n.TrangThai IN ('ACTIVE', 'COMPLETED') THEN DATEADD(DAY, -7, CAST(n.TuNgay AS DATETIME2(3)))
           ELSE NULL
       END,
       a.ConfirmedByUserId = CASE
           WHEN n.TrangThai IN ('ACTIVE', 'COMPLETED') THEN t.AppUserId
           ELSE NULL
       END,
       a.ConfirmSource = CASE
           WHEN n.TrangThai IN ('ACTIVE', 'COMPLETED') THEN 'TEACHER'
           ELSE NULL
       END,
       a.RejectionReason = CASE
           WHEN n.TrangThai = 'REJECTED' THEN N'Trùng lịch dạy cố định tại trường khác, xin nhận phiếu khác.'
           ELSE NULL
       END
  FROM Assignment a
  JOIN #MapA m ON m.AssignmentId = a.Id
  JOIN #NhuCau n ON n.Seq = m.Seq
  JOIN Teacher t ON t.Id = n.TeacherId;

SELECT @n1 = COUNT(*) FROM #MapA;
PRINT N'5) Đã ghi ' + CAST(@n1 AS NVARCHAR(10)) + N' phiếu phân công.';

/* ═══════════════ 6) GHI Ô THỜI KHÓA BIỂU ═══════════════ */

CREATE TABLE #MapS (Seq INT, DayCode CHAR(3) COLLATE DATABASE_DEFAULT, SlotId INT);

MERGE INTO AssignmentSlot AS tgt
USING (
    SELECT s.Seq, s.DayCode, s.PeriodId, m.AssignmentId, n.TeacherId, n.ClassId, n.SchoolId
    FROM #Slot s
    JOIN #NhuCau n ON n.Seq = s.Seq
    JOIN #MapA   m ON m.Seq = s.Seq
) AS src
ON 1 = 0
WHEN NOT MATCHED THEN
    INSERT (AssignmentId, TeacherId, DayOfWeek, PeriodId, ClassId, SchoolId, CreatedAt, CreatedBy)
    VALUES (src.AssignmentId, src.TeacherId, src.DayCode, src.PeriodId, src.ClassId, src.SchoolId,
            SYSUTCDATETIME(), @Admin)
OUTPUT src.Seq, src.DayCode, inserted.Id INTO #MapS (Seq, DayCode, SlotId);

SELECT @n1 = COUNT(*) FROM #MapS;
PRINT N'6) Đã ghi ' + CAST(@n1 AS NVARCHAR(10)) + N' ô thời khóa biểu.';

/* ═══════════════ 7) NỞ Ô THỜI KHÓA BIỂU THÀNH BUỔI DẠY ═══════════════
   Đúng thuật toán của AssignmentService.generateSchedules: tìm ngày đầu tiên
   trùng thứ, rồi cộng 7 ngày một cho tới hết giai đoạn — TRỪ ngày nghỉ. */

CREATE TABLE #SlotRun (
    SlotId INT PRIMARY KEY, AssignmentId INT, TeacherId INT, SchoolId INT, PeriodId INT,
    NgayDau DATE, DenNgay DATE
);
INSERT INTO #SlotRun (SlotId, AssignmentId, TeacherId, SchoolId, PeriodId, NgayDau, DenNgay)
SELECT ms.SlotId, ma.AssignmentId, n.TeacherId, n.SchoolId, sl.PeriodId,
       /* '19000101' là thứ Hai → phép chia dư cho 7 ra 0=T2 … 6=CN, không phụ
          thuộc SET DATEFIRST của phiên đang chạy. */
       DATEADD(DAY, ((t.Idx - (DATEDIFF(DAY, '19000101', n.TuNgay) % 7)) + 7) % 7, n.TuNgay),
       n.DenNgay
FROM #MapS ms
JOIN #Slot   sl ON sl.Seq = ms.Seq AND sl.DayCode = ms.DayCode
JOIN #NhuCau n  ON n.Seq  = ms.Seq
JOIN #MapA   ma ON ma.Seq = ms.Seq
JOIN #Thu    t  ON t.Code = ms.DayCode;

CREATE TABLE #Buoi (SlotId INT, AssignmentId INT, TeacherId INT, PeriodId INT, Ngay DATE);

;WITH Tuan AS (
    SELECT TOP (60) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 AS n FROM sys.all_objects
)
INSERT INTO #Buoi (SlotId, AssignmentId, TeacherId, PeriodId, Ngay)
SELECT r.SlotId, r.AssignmentId, r.TeacherId, r.PeriodId, DATEADD(WEEK, w.n, r.NgayDau)
FROM #SlotRun r
CROSS JOIN Tuan w
WHERE DATEADD(WEEK, w.n, r.NgayDau) <= r.DenNgay
  AND NOT EXISTS (
        SELECT 1 FROM Holiday h
        WHERE h.IsDeleted = 0
          AND (h.SchoolId IS NULL OR h.SchoolId = r.SchoolId)
          AND DATEADD(WEEK, w.n, r.NgayDau) BETWEEN h.FromDate AND h.ToDate);

/* Buổi sinh ra ở PENDING rồi mới được duyệt ở bước 8 — đi đúng đường thật để
   trigger TR_Schedule_StatusLog ghi lại được lịch sử đổi trạng thái. */
INSERT INTO Schedule (AssignmentId, TeacherId, StartTime, EndTime, Status, Source, CreatedByUserId, PeriodId, SourceSlotId, CreatedAt)
SELECT b.AssignmentId, b.TeacherId,
       DATEADD(SECOND, DATEDIFF(SECOND, '00:00:00', p.StartTime), CAST(b.Ngay AS DATETIME2(3))),
       DATEADD(SECOND, DATEDIFF(SECOND, '00:00:00', p.EndTime),   CAST(b.Ngay AS DATETIME2(3))),
       'PENDING', 'MANUAL', @Admin, b.PeriodId, b.SlotId, SYSUTCDATETIME()
FROM #Buoi b
JOIN Period p ON p.Id = b.PeriodId;

DECLARE @SoBuoi INT = @@ROWCOUNT;
DECLARE @SoNgayNghi INT = (
    SELECT COUNT(*) FROM #SlotRun r
    CROSS JOIN (SELECT TOP (60) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 AS n FROM sys.all_objects) w
    WHERE DATEADD(WEEK, w.n, r.NgayDau) <= r.DenNgay
      AND EXISTS (SELECT 1 FROM Holiday h
                  WHERE h.IsDeleted = 0
                    AND (h.SchoolId IS NULL OR h.SchoolId = r.SchoolId)
                    AND DATEADD(WEEK, w.n, r.NgayDau) BETWEEN h.FromDate AND h.ToDate));

PRINT N'7) Đã sinh ' + CAST(@SoBuoi AS NVARCHAR(10)) + N' buổi dạy; bỏ qua '
    + CAST(@SoNgayNghi AS NVARCHAR(10)) + N' buổi rơi vào ngày nghỉ (bảng Holiday).';

/* ═══════════════ 8) DUYỆT BUỔI THEO TRẠNG THÁI PHIẾU ═══════════════ */

UPDATE sc
   SET sc.Status = 'APPROVED',
       sc.ApprovedByUserId = a.ConfirmedByUserId,
       sc.ApprovedAt = a.ConfirmedAt,
       sc.UpdatedAt = SYSUTCDATETIME(),
       sc.UpdatedBy = a.ConfirmedByUserId
  FROM Schedule sc
  JOIN Assignment a ON a.Id = sc.AssignmentId
 WHERE a.Status IN ('ACTIVE', 'COMPLETED');

PRINT N'8a) Đã duyệt ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' buổi (phiếu đã được giáo viên xác nhận).';

/* Phiếu bị từ chối: buổi chưa diễn ra bị hủy, đúng như cancelPendingSchedules. */
UPDATE sc
   SET sc.Status = 'CANCELLED',
       sc.UpdatedAt = SYSUTCDATETIME(),
       sc.UpdatedBy = @Admin
  FROM Schedule sc
  JOIN Assignment a ON a.Id = sc.AssignmentId
 WHERE a.Status = 'REJECTED';

PRINT N'8b) Đã hủy ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' buổi của phiếu bị từ chối.';

/* ═══════════════ 9) CHẤM CÔNG CHO BUỔI ĐÃ DẠY ═══════════════
   Chỉ buổi ĐÃ DUYỆT và ĐÃ KẾT THÚC. Tỷ lệ trạng thái rải theo CHECKSUM(Id)
   nên cố định, chạy lại ở máy khác vẫn ra đúng con số ấy. */

DECLARE @Now DATETIME2(3) = CAST(GETDATE() AS DATETIME2(3));

CREATE TABLE #Cham (
    ScheduleId BIGINT PRIMARY KEY, TeacherId INT, Ngay DATE,
    TietBatDau TIME(0), TietKetThuc TIME(0), R INT
);
INSERT INTO #Cham (ScheduleId, TeacherId, Ngay, TietBatDau, TietKetThuc, R)
SELECT sc.Id, sc.TeacherId, CAST(sc.StartTime AS DATE), p.StartTime, p.EndTime,
       ABS(CHECKSUM(sc.Id)) % 100
FROM Schedule sc
JOIN Period p ON p.Id = sc.PeriodId
WHERE sc.Status = 'APPROVED' AND sc.EndTime < @Now;

INSERT INTO Attendance
    (TeacherId, ScheduleId, WorkDate, CheckIn, CheckOut, Status, CheckInMethod,
     ConfirmedByUserId, ConfirmedAt, Note, AutoCheckOut, CreatedAt, CreatedBy)
SELECT c.TeacherId, c.ScheduleId, c.Ngay,
       /* Có mặt: tới sớm vài phút. Đi muộn: vào sau giờ 5-14 phút. Vắng/nghỉ
          phép: không có giờ vào ra. */
       CASE WHEN c.R <= 85 THEN DATEADD(MINUTE, -(c.R % 7) - 1, c.TietBatDau)
            WHEN c.R <= 92 THEN DATEADD(MINUTE,  (c.R % 10) + 5, c.TietBatDau)
            ELSE NULL END,
       CASE WHEN c.R <= 92 THEN DATEADD(MINUTE, (c.R % 4), c.TietKetThuc)
            ELSE NULL END,
       CASE WHEN c.R <= 85 THEN 'PRESENT'
            WHEN c.R <= 92 THEN 'LATE'
            WHEN c.R <= 96 THEN 'ABSENT'
            ELSE 'LEAVE' END,
       CASE WHEN c.R <= 92 AND c.R % 10 = 0 THEN 'EMPLOYEE'
            WHEN c.R <= 92 THEN 'SELF'
            WHEN c.R <= 96 THEN 'SYSTEM'
            ELSE 'EMPLOYEE' END,
       CASE WHEN (c.R <= 92 AND c.R % 10 = 0) OR c.R > 96 THEN @Admin ELSE NULL END,
       CASE WHEN (c.R <= 92 AND c.R % 10 = 0) OR c.R > 96
            THEN DATEADD(HOUR, 20, CAST(c.Ngay AS DATETIME2(3))) ELSE NULL END,
       CASE WHEN c.R BETWEEN 93 AND 96 THEN N'Hệ thống ghi vắng: hết buổi 30 phút vẫn không có ai chấm công.'
            WHEN c.R > 96 THEN N'Nghỉ phép có báo trước.'
            WHEN c.R BETWEEN 86 AND 92 THEN N'Vào lớp muộn.'
            ELSE NULL END,
       0,
       DATEADD(HOUR, 12, CAST(c.Ngay AS DATETIME2(3))), @Admin
FROM #Cham c;

PRINT N'9) Đã ghi ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' dòng chấm công.';

/* ═══════════════ 10) BẢNG LƯƠNG ═══════════════
   Công thức chép nguyên PayrollService: TaughtHours = SỐ TIẾT có mặt (tính cả
   đi muộn), RatePerHour = ĐƠN GIÁ HIỆU DỤNG = tổng tiền / số tiết. NetAmount
   là cột computed của DB nên không ghi tay. BaseSalary để 0 cho khớp
   generate() — nó cũng không đọc Contract.BaseSalary. */

DECLARE @LastY INT, @LastM INT;
SELECT TOP 1 @LastY = YEAR(WorkDate), @LastM = MONTH(WorkDate)
FROM Attendance
WHERE NOT (YEAR(WorkDate) = YEAR(@Today) AND MONTH(WorkDate) = MONTH(@Today))
ORDER BY YEAR(WorkDate) DESC, MONTH(WorkDate) DESC;

;WITH Tien AS (
    SELECT at.TeacherId,
           YEAR(at.WorkDate)  AS Y,
           MONTH(at.WorkDate) AS M,
           CASE WHEN TRY_CAST(k.GradeLevel AS INT) BETWEEN 6 AND 9 THEN 125000.00 ELSE 115000.00 END AS DonGia
    FROM Attendance at
    JOIN Schedule sc ON sc.Id = at.ScheduleId
    LEFT JOIN AssignmentSlot sl ON sl.Id = sc.SourceSlotId
    LEFT JOIN SchoolClass    k  ON k.Id  = sl.ClassId
    WHERE at.Status IN ('PRESENT', 'LATE')
)
INSERT INTO Payroll (TeacherId, PeriodMonth, PeriodYear, BaseSalary, TaughtHours, RatePerHour,
                     Allowance, Bonus, Deduction, Status, CreatedAt, CreatedBy)
SELECT TeacherId, M, Y, 0, COUNT(*), ROUND(SUM(DonGia) / COUNT(*), 2),
       0, 0, 0,
       CASE WHEN Y = YEAR(@Today) AND M = MONTH(@Today) THEN 'DRAFT'
            WHEN Y = @LastY AND M = @LastM              THEN 'FINALIZED'
            ELSE 'PAID' END,
       SYSUTCDATETIME(), @Admin
FROM Tien
GROUP BY TeacherId, Y, M;

PRINT N'10) Đã lập ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' dòng lương.';

/* ═══════════════ 11) LỜI MỜI DẠY TRONG CHUÔNG ═══════════════
   Phiếu còn chờ xác nhận phải có thông báo kèm nút Xác nhận/Từ chối, đúng
   như AssignmentApprovalService.publishInvite phát ra — không có nó thì giáo
   viên không có đường nào để trả lời phiếu. */

INSERT INTO Notification (RecipientUserId, Title, Content, Type, RefEntity, RefId, IsRead, CreatedAt, RequiresAction, ActionStatus)
SELECT t.AppUserId,
       N'Bạn được phân công lịch dạy mới',
       s.Name + N' · ' + sch.Name + N' · lớp ' + k.Name
           + N' · ' + CAST((SELECT COUNT(*) FROM AssignmentSlot x WHERE x.AssignmentId = a.Id AND x.IsDeleted = 0) AS NVARCHAR(5))
           + N' tiết/tuần · Từ ' + CONVERT(NVARCHAR(10), a.StartDate, 103)
           + N' đến ' + CONVERT(NVARCHAR(10), a.EndDate, 103)
           + N' · HẠN XÁC NHẬN: ' + CONVERT(NVARCHAR(16), a.ConfirmDeadline, 120)
           + N'. Quá hạn phiếu tự hết hiệu lực — bấm để xem lịch chi tiết.',
       'ASSIGNMENT', 'Assignment', CAST(a.Id AS BIGINT), 0, SYSUTCDATETIME(), 1, 'PENDING'
FROM Assignment a
JOIN Teacher     t   ON t.Id   = a.TeacherId
JOIN Subject     s   ON s.Id   = a.SubjectId
JOIN School      sch ON sch.Id = a.SchoolId
JOIN SchoolClass k   ON k.Id   = a.ClassId
WHERE a.Status = 'PENDING' AND t.AppUserId IS NOT NULL;

PRINT N'11) Đã gửi ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' lời mời dạy chờ giáo viên trả lời.';

DROP TABLE #MonKhoi; DROP TABLE #Lop; DROP TABLE #NhuCau; DROP TABLE #Thu;
DROP TABLE #Ban; DROP TABLE #Slot; DROP TABLE #MapA; DROP TABLE #MapS;
DROP TABLE #SlotRun; DROP TABLE #Buoi; DROP TABLE #Cham;

COMMIT TRANSACTION;

PRINT N'';
PRINT N'>>> XONG. Kiểm tra nhanh: Bảng điều khiển (số buổi/chuyên cần/lương),';
PRINT N'    Phân công (tab Chờ xác nhận), Chấm công, Bảng lương từng tháng.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    IF CURSOR_STATUS('local', 'curKhoa') >= 0 BEGIN CLOSE curKhoa; DEALLOCATE curKhoa; END
    DROP TABLE IF EXISTS #MonKhoi; DROP TABLE IF EXISTS #Lop; DROP TABLE IF EXISTS #NhuCau;
    DROP TABLE IF EXISTS #Thu; DROP TABLE IF EXISTS #Ban; DROP TABLE IF EXISTS #Slot;
    DROP TABLE IF EXISTS #MapA; DROP TABLE IF EXISTS #MapS; DROP TABLE IF EXISTS #SlotRun;
    DROP TABLE IF EXISTS #Buoi; DROP TABLE IF EXISTS #Cham;
    PRINT N'!!! LỖI — đã rollback toàn bộ, DB giữ nguyên như trước khi chạy.';
    THROW;
END CATCH
GO
