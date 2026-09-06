/* =====================================================================
   TSDMS — SEED PHÂN CÔNG HỌC KỲ I NĂM HỌC 2026-2027 (03/09/2026 → 31/12/2026)
   ---------------------------------------------------------------------
   CHẠY THẾ NÀO
     sqlcmd -S localhost -d TSDMS -E -i TSDMS_Seed_PhanCong_2026_09.sql
     KHÔNG đưa vào Flyway — đây là dữ liệu demo, không phải cấu trúc.
     Chạy TRƯỚC TSDMS_Seed_ChamCong_2026_09.sql (file kia bám vào buổi dạy
     do file này sinh ra).

   VÌ SAO CÓ FILE NÀY
     Đợt phân công "đang chạy" trong DB được sinh từ 17/08/2026 và kéo tới tận
     22/08/2027 — một khoảng dài hơn cả năm học, đủ để lịch dạy trông rối và
     khó nói thành câu khi trình bày. File này thay riêng phần ĐANG DẠY bằng
     một học kỳ gọn: khai giảng 03/09/2026, kết thúc 31/12/2026.

   XÓA GÌ, GIỮ GÌ — đây là chỗ dễ làm hỏng demo nhất nên nói rõ
     XÓA  · phiếu phân công KHÔNG phải COMPLETED (toàn bộ đợt đang chạy) và
            mọi thứ treo vào chúng: ô thời khóa biểu, buổi dạy, nhật ký đổi
            trạng thái buổi, chấm công, đơn xin nghỉ, lời mời trong chuông.
          · phiếu lương kỳ 8/2026 và 9/2026 — hai kỳ này tính từ chính đám
            chấm công vừa bị xóa, để lại là để lại một con số không còn gì
            đỡ phía sau.
     GIỮ · 280 phiếu COMPLETED của năm học 2025-2026 (08/09/2025 → 29/05/2026)
            cùng buổi dạy, chấm công và phiếu lương đã trả của chúng.

     Vì sao KHÔNG xóa sạch cho gọn: chính phần lịch sử ấy là thứ nuôi biểu đồ
     7 tháng ở Bảng điều khiển, tỷ lệ chuyên cần, và các kỳ lương trạng thái
     PAID. Dọn hết đi thì mở phần mềm lên chỉ còn bảng trắng, mà "hệ thống
     chạy được" với "hệ thống có dữ liệu để nói" là hai chuyện khác nhau.

   BỐN LUẬT ĐƯỢC TÔN TRỌNG KHI XẾP (không phải random)
     1) Giáo viên chỉ dạy môn mình có trong TeacherSubject.
     2) Một giáo viên KHÔNG BAO GIỜ bị xếp hai buổi cùng thứ. Đây là ràng
        buộc mạnh hơn "không trùng tiết" và được bảo đảm bằng cách xây chứ
        không bằng cách dò: ba ô của một phiếu lấy thứ theo bước nhảy 2, 4, 6
        trên vòng 6 ngày (Hai→Bảy), ba số dư đó luôn khác nhau.
     3) Mỗi giáo viên 2–3 buổi/tuần — đúng nhịp của giáo viên thỉnh giảng đi
        nhiều trường, không phải giáo viên biên chế.
     4) Buổi dạy không rơi vào ngày nghỉ đã khai trong bảng Holiday.

   BỐN DÒNG ĐỂ DEMO
     Sau khi sinh, 4 phiếu được đặt về REJECTED (2) và EXPIRED (2). Chúng
     được chọn theo THỨ HẠNG Id giảm dần chứ không theo Id cụ thể, vì màn
     Phân công sắp xếp Id giảm dần và mỗi trang 10 dòng — chọn hạng 12, 14,
     16, 18 thì cả bốn chắc chắn nằm ở TRANG 2, bất kể lần chạy này sinh ra
     dải Id nào.
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @tuNgay  DATE = '2026-09-03';
DECLARE @denNgay DATE = '2026-12-31';
DECLARE @admin   INT  = (SELECT MIN(Id) FROM AppUser WHERE Username = 'admin');

/* =====================================================================
   1) DỌN ĐỢT ĐANG DẠY
   ---------------------------------------------------------------------
   Xóa CỨNG chứ không xóa mềm: đây là dữ liệu demo bị thay, không phải hồ sơ
   nghiệp vụ bị hủy. Để lại dưới dạng IsDeleted=1 thì thùng rác của màn Phân
   công đầy 141 dòng rác ngay trước buổi trình bày.

   Thứ tự xóa đi ngược chiều khóa ngoại: nhật ký → bảng con → bảng cha.
   ===================================================================== */

/* Phiếu sắp bị xóa — gom một lần rồi dùng lại, thay vì lặp lại điều kiện
   "Status <> COMPLETED" ở bảy câu lệnh và để chúng lệch nhau. */
IF OBJECT_ID('tempdb..#PhieuXoa') IS NOT NULL DROP TABLE #PhieuXoa;
SELECT Id INTO #PhieuXoa FROM Assignment WHERE Status <> 'COMPLETED';

IF OBJECT_ID('tempdb..#BuoiXoa') IS NOT NULL DROP TABLE #BuoiXoa;
SELECT s.Id INTO #BuoiXoa FROM Schedule s JOIN #PhieuXoa p ON p.Id = s.AssignmentId;
CREATE UNIQUE CLUSTERED INDEX IX_BuoiXoa ON #BuoiXoa(Id);

/* Lời mời/thông báo trỏ tới phiếu sắp biến mất. Không có khóa ngoại nên DB
   không kêu, nhưng để lại thì chuông hiện lời mời bấm vào là lỗi 404. */
DELETE n FROM Notification n
WHERE (n.RefEntity = 'Assignment' AND n.RefId IN (SELECT Id FROM #PhieuXoa))
   OR n.RefEntity = 'AssignmentLeaveRequest';

DELETE FROM AttendanceChangeLog
WHERE AttendanceId IN (SELECT a.Id FROM Attendance a JOIN #BuoiXoa b ON b.Id = a.ScheduleId);

DELETE FROM AttendanceAmendRequest WHERE ScheduleId IN (SELECT Id FROM #BuoiXoa);

DELETE FROM Attendance WHERE ScheduleId IN (SELECT Id FROM #BuoiXoa);

DELETE FROM ScheduleStatusLog WHERE ScheduleId IN (SELECT Id FROM #BuoiXoa);

DELETE FROM Schedule WHERE Id IN (SELECT Id FROM #BuoiXoa);

DELETE FROM AssignmentLeaveRequest WHERE AssignmentId IN (SELECT Id FROM #PhieuXoa);

DELETE FROM AssignmentSlot WHERE AssignmentId IN (SELECT Id FROM #PhieuXoa);

DELETE FROM Assignment WHERE Id IN (SELECT Id FROM #PhieuXoa);

/* Hai kỳ lương tính từ đám chấm công vừa xóa. Kỳ 5/2026 trở về trước giữ
   nguyên — chúng vẫn có đủ chấm công phía sau đỡ. */
DELETE FROM PayrollChangeLog
WHERE PayrollId IN (SELECT Id FROM Payroll WHERE PeriodYear = 2026 AND PeriodMonth IN (8, 9));
DELETE FROM Payroll WHERE PeriodYear = 2026 AND PeriodMonth IN (8, 9);
GO

/* =====================================================================
   2) XẾP PHIẾU: mỗi giáo viên một phiếu, tại một trường, 2–3 buổi/tuần
   ===================================================================== */

DECLARE @tuNgay  DATE = '2026-09-03';
DECLARE @denNgay DATE = '2026-12-31';
DECLARE @admin   INT  = (SELECT MIN(Id) FROM AppUser WHERE Username = 'admin');

/* KHOẢNG KHỐI CỦA TỪNG MÔN.
   Bảng Subject không có cột khối — ràng buộc nằm trong chính tên môn ("Tiếng
   Anh Tiểu học 3-5", "… THCS 6-9", "Tiếng Anh làm quen (Lớp 1-2)"). Bóc nó ra
   thành hai số để còn ghép được với khối của lớp.

   Vì sao phải làm: không có bước này thì seed sẽ xếp "Tiếng Anh Tiểu học 3-5"
   vào lớp 6D3. Dữ liệu vẫn hợp lệ với mọi ràng buộc của DB, nhưng ai đọc bảng
   cũng thấy sai — và đó đúng là kiểu sai khiến người xem nghi ngờ toàn bộ phần
   còn lại. Môn không ghi khối trong tên (Scratch, Kỹ năng, Tin học cơ bản…)
   thì để mở 1-9, vì chúng dạy được cả hai cấp thật. */
IF OBJECT_ID('tempdb..#MonKhoi') IS NOT NULL DROP TABLE #MonKhoi;
SELECT s.Id AS SubjectId,
       CASE WHEN s.Name LIKE N'%(Lớp 1-2)%'   THEN 1
            WHEN s.Name LIKE N'%3-5%'         THEN 3
            WHEN s.Name LIKE N'%6-9%'         THEN 6
            WHEN s.Name LIKE N'%Tiểu học%'    THEN 1
            WHEN s.Name LIKE N'%THCS%'        THEN 6
            WHEN s.Name LIKE N'%lớp 1'        THEN 1
            ELSE 1 END AS khoiTu,
       CASE WHEN s.Name LIKE N'%(Lớp 1-2)%'   THEN 2
            WHEN s.Name LIKE N'%3-5%'         THEN 5
            WHEN s.Name LIKE N'%6-9%'         THEN 9
            WHEN s.Name LIKE N'%Tiểu học%'    THEN 5
            WHEN s.Name LIKE N'%THCS%'        THEN 9
            WHEN s.Name LIKE N'%lớp 1'        THEN 1
            ELSE 9 END AS khoiDen
INTO #MonKhoi
FROM Subject s
WHERE s.IsDeleted = 0;

/* Chỉ những trường thật sự xếp được: phải có khung tiết VÀ có lớp đang hoạt
   động. Trường thiếu một trong hai thì phiếu sinh ra sẽ trỏ vào khoảng không. */
IF OBJECT_ID('tempdb..#Truong') IS NOT NULL DROP TABLE #Truong;
SELECT s.Id AS SchoolId,
       ROW_NUMBER() OVER (ORDER BY s.Id) - 1 AS rn,
       (SELECT COUNT(*) FROM Period p WHERE p.SchoolId = s.Id AND p.IsDeleted = 0) AS soTiet,
       /* Cấp học của trường suy từ chính khối của các lớp đang có, không đọc tên
          trường: tên là chuỗi người nhập, khối là dữ liệu. */
       (SELECT MIN(TRY_CAST(c.GradeLevel AS INT)) FROM SchoolClass c
         WHERE c.SchoolId = s.Id AND c.IsDeleted = 0 AND c.Status = 'ACTIVE')      AS khoiMin,
       (SELECT MAX(TRY_CAST(c.GradeLevel AS INT)) FROM SchoolClass c
         WHERE c.SchoolId = s.Id AND c.IsDeleted = 0 AND c.Status = 'ACTIVE')      AS khoiMax
INTO #Truong
FROM School s
WHERE s.IsDeleted = 0
  AND s.Status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM Period p WHERE p.SchoolId = s.Id AND p.IsDeleted = 0)
  AND EXISTS (SELECT 1 FROM SchoolClass c
               WHERE c.SchoolId = s.Id AND c.IsDeleted = 0 AND c.Status = 'ACTIVE'
                 AND TRY_CAST(c.GradeLevel AS INT) IS NOT NULL);

DECLARE @soTruong INT = (SELECT COUNT(*) FROM #Truong);

IF OBJECT_ID('tempdb..#GV') IS NOT NULL DROP TABLE #GV;
SELECT t.Id AS TeacherId,
       ROW_NUMBER() OVER (ORDER BY t.Id) - 1 AS rn
INTO #GV
FROM Teacher t
WHERE t.IsDeleted = 0
  AND EXISTS (SELECT 1 FROM TeacherSubject ts WHERE ts.TeacherId = t.Id);

/* Chọn MÔN trước, rồi mới tìm TRƯỜNG hợp với môn đó.
   Bản đầu làm ngược lại — chia trường theo vòng tròn rồi mới lọc môn — và mất
   7 giáo viên: ai chỉ dạy môn THCS mà rơi vào trường tiểu học thì không còn
   môn nào hợp lệ, phiếu biến mất không kèn trống. Đảo thứ tự thì mọi giáo viên
   đều xếp được, vì hệ thống có đủ cả trường tiểu học lẫn trường THCS.

   Vẫn giữ tinh thần chia đều: trường được chọn bắt đầu từ đúng vị trí vòng
   tròn của giáo viên rồi mới đi tiếp cho tới trường đầu tiên hợp cấp. */
IF OBJECT_ID('tempdb..#Phieu') IS NOT NULL DROP TABLE #Phieu;
SELECT g.TeacherId,
       g.rn,
       mon.SubjectId,
       mon.khoiTu,
       mon.khoiDen,
       tr.SchoolId,
       tr.soTiet,
       /* 2 hoặc 3 buổi/tuần, xen kẽ theo thứ tự giáo viên. */
       2 + (g.rn % 2) AS soBuoi
INTO #Phieu
FROM #GV g
CROSS APPLY (
    /* Môn "chính" của giáo viên: ưu tiên môn khoanh vùng khối HẸP nhất — đó là
       môn chuyên sâu của họ, còn môn mở 1-9 là môn ai cũng dạy được. */
    SELECT TOP 1 ts.SubjectId, mk.khoiTu, mk.khoiDen
    FROM TeacherSubject ts
    JOIN #MonKhoi mk ON mk.SubjectId = ts.SubjectId
    WHERE ts.TeacherId = g.TeacherId
    ORDER BY (mk.khoiDen - mk.khoiTu), ts.SubjectId
) mon
CROSS APPLY (
    SELECT TOP 1 t.SchoolId, t.soTiet
    FROM #Truong t
    /* Khoảng khối của môn phải GIAO với khoảng khối của trường. */
    WHERE mon.khoiTu <= t.khoiMax
      AND mon.khoiDen >= t.khoiMin
    ORDER BY ((t.rn - (g.rn % @soTruong) + @soTruong) % @soTruong)
) tr;

INSERT INTO Assignment
    (TeacherId, SchoolId, SubjectId, ClassId, AssignedByEmployeeId,
     StartDate, EndDate, Status, IsDeleted, CreatedAt, CreatedBy,
     ConfirmDeadline, ConfirmedAt, ConfirmedByUserId, ConfirmSource)
SELECT p.TeacherId,
       p.SchoolId,
       p.SubjectId,
       NULL,                       -- lớp nằm ở từng ô thời khóa biểu (V16)
       NULL,                       -- không còn hồ sơ nhân viên từ V33
       @tuNgay,
       @denNgay,
       'ACTIVE',
       0,
       DATEADD(DAY, -7, CAST(@tuNgay AS DATETIME2(3))),
       @admin,
       DATEADD(DAY, -4, CAST(@tuNgay AS DATETIME2(3))),
       DATEADD(DAY, -5, CAST(@tuNgay AS DATETIME2(3))),
       @admin,
       'TEACHER'
FROM #Phieu p
ORDER BY p.rn;

/* Nối lại Id phiếu vừa sinh với giáo viên để xếp ô thời khóa biểu. */
IF OBJECT_ID('tempdb..#PhieuId') IS NOT NULL DROP TABLE #PhieuId;
SELECT a.Id AS AssignmentId, p.*
INTO #PhieuId
FROM Assignment a
JOIN #Phieu p ON p.TeacherId = a.TeacherId AND p.SchoolId = a.SchoolId
WHERE a.Status = 'ACTIVE' AND a.StartDate = @tuNgay;

/* Lớp DẠY ĐƯỢC của từng phiếu: lớp của đúng trường ấy và có khối nằm trong
   khoảng khối của môn. Đánh số theo từng phiếu để ô thời khóa biểu bốc ra
   bằng phép chia dư mà không bao giờ trượt ra ngoài danh sách. */
IF OBJECT_ID('tempdb..#LopChon') IS NOT NULL DROP TABLE #LopChon;
SELECT pi.AssignmentId,
       c.Id AS ClassId,
       ROW_NUMBER() OVER (PARTITION BY pi.AssignmentId ORDER BY c.Name) - 1 AS idx,
       COUNT(*)     OVER (PARTITION BY pi.AssignmentId)                     AS soLop
INTO #LopChon
FROM #PhieuId pi
JOIN SchoolClass c
  ON c.SchoolId = pi.SchoolId
 AND c.IsDeleted = 0
 AND c.Status = 'ACTIVE'
 AND TRY_CAST(c.GradeLevel AS INT) BETWEEN pi.khoiTu AND pi.khoiDen;

/* Ba ô ứng viên cho mỗi phiếu, cắt bớt theo soBuoi.
   Thứ lấy bước nhảy 2/4/6 trên vòng 6 ngày → ba số dư 2, 4, 0 luôn khác nhau,
   nên một giáo viên không thể có hai buổi cùng thứ. */
IF OBJECT_ID('tempdb..#O') IS NOT NULL DROP TABLE #O;
SELECT pi.AssignmentId,
       pi.TeacherId,
       pi.SchoolId,
       k.k,
       CASE ((pi.rn + k.k * 2) % 6)
            WHEN 0 THEN 'MON' WHEN 1 THEN 'TUE' WHEN 2 THEN 'WED'
            WHEN 3 THEN 'THU' WHEN 4 THEN 'FRI' ELSE 'SAT' END AS DayOfWeek,
       (pi.rn * 2 + k.k) % pi.soTiet AS tietIdx,
       (pi.rn * 3 + k.k) % lc.soLop  AS lopIdx
INTO #O
FROM #PhieuId pi
CROSS JOIN (SELECT 1 AS k UNION ALL SELECT 2 UNION ALL SELECT 3) k
CROSS APPLY (SELECT TOP 1 soLop FROM #LopChon WHERE AssignmentId = pi.AssignmentId) lc
WHERE k.k <= pi.soBuoi;

/* Khung tiết của từng trường, đánh số để tra theo tietIdx.
   Ưu tiên buổi SÁNG rồi mới tới buổi CHIỀU — lịch trung tâm thực tế dạy sáng
   là chính, xếp ngẫu nhiên sáng/chiều lẫn lộn nhìn là biết máy sinh. */
IF OBJECT_ID('tempdb..#Tiet') IS NOT NULL DROP TABLE #Tiet;
SELECT p.Id AS PeriodId, p.SchoolId, p.StartTime, p.EndTime,
       ROW_NUMBER() OVER (PARTITION BY p.SchoolId
                          ORDER BY CASE WHEN p.SessionType = 'MORNING' THEN 0 ELSE 1 END,
                                   p.PeriodNumber) - 1 AS idx
INTO #Tiet
FROM Period p
WHERE p.IsDeleted = 0;

INSERT INTO AssignmentSlot
    (AssignmentId, TeacherId, DayOfWeek, PeriodId, RoomId, IsDeleted, CreatedAt, CreatedBy, ClassId, SchoolId)
SELECT o.AssignmentId, o.TeacherId, o.DayOfWeek, t.PeriodId, NULL, 0,
       DATEADD(DAY, -7, CAST(@tuNgay AS DATETIME2(3))), @admin, l.ClassId, o.SchoolId
FROM #O o
JOIN #Tiet    t ON t.SchoolId = o.SchoolId AND t.idx = o.tietIdx
JOIN #LopChon l ON l.AssignmentId = o.AssignmentId AND l.idx = o.lopIdx;
GO

/* =====================================================================
   3) SINH BUỔI DẠY TỪNG NGÀY
   ---------------------------------------------------------------------
   Thứ trong tuần tính bằng DATEDIFF từ mốc 01/01/1900 (một ngày thứ Hai) chứ
   KHÔNG dùng DATENAME/DATEPART(weekday): hai hàm kia đổi kết quả theo
   @@DATEFIRST và theo ngôn ngữ của phiên đăng nhập, nên cùng một script chạy
   trên hai máy có thể ra hai lịch khác nhau — đúng loại lỗi không ai ngờ tới.
   ===================================================================== */

DECLARE @tuNgay  DATE = '2026-09-03';
DECLARE @denNgay DATE = '2026-12-31';
DECLARE @admin   INT  = (SELECT MIN(Id) FROM AppUser WHERE Username = 'admin');

IF OBJECT_ID('tempdb..#Ngay') IS NOT NULL DROP TABLE #Ngay;
WITH so AS (
    SELECT TOP (DATEDIFF(DAY, @tuNgay, @denNgay) + 1)
           ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 AS n
    FROM sys.all_objects a CROSS JOIN sys.all_objects b
)
SELECT DATEADD(DAY, n, @tuNgay) AS d,
       DATEDIFF(DAY, '19000101', DATEADD(DAY, n, @tuNgay)) % 7 AS thuIdx  -- 0 = thứ Hai
INTO #Ngay
FROM so;

CREATE UNIQUE CLUSTERED INDEX IX_Ngay ON #Ngay(d);

INSERT INTO Schedule
    (AssignmentId, TeacherId, RoomId, StartTime, EndTime, Status, Source,
     PeriodId, SourceSlotId, CreatedByUserId, ApprovedByUserId, ApprovedAt, IsDeleted, UpdatedBy)
SELECT sl.AssignmentId,
       sl.TeacherId,
       NULL,
       /* Ghép ngày với giờ của tiết. Phải đi vòng qua DATETIME: toán tử "+"
          không nhận DATETIME2, còn DATETIME thì cộng được rồi ép ngược về
          DATETIME2(0) đúng kiểu cột. */
       CAST(CAST(n.d AS DATETIME) + CAST(p.StartTime AS DATETIME) AS DATETIME2(0)),
       CAST(CAST(n.d AS DATETIME) + CAST(p.EndTime   AS DATETIME) AS DATETIME2(0)),
       'APPROVED',
       'MANUAL',
       sl.PeriodId,
       sl.Id,
       @admin,
       @admin,
       DATEADD(DAY, -5, CAST(@tuNgay AS DATETIME2(3))),
       0,
       @admin
FROM AssignmentSlot sl
JOIN Assignment a ON a.Id = sl.AssignmentId AND a.StartDate = @tuNgay AND a.IsDeleted = 0
JOIN Period p     ON p.Id = sl.PeriodId
JOIN #Ngay n ON n.thuIdx = CASE sl.DayOfWeek
                               WHEN 'MON' THEN 0 WHEN 'TUE' THEN 1 WHEN 'WED' THEN 2
                               WHEN 'THU' THEN 3 WHEN 'FRI' THEN 4 WHEN 'SAT' THEN 5 ELSE 6 END
WHERE sl.IsDeleted = 0
  /* Ngày nghỉ đã khai thì không sinh buổi ngay từ đầu — sạch hơn là sinh ra
     rồi hủy, vì hủy để lại một dòng CANCELLED không ai giải thích được. */
  AND NOT EXISTS (
        SELECT 1 FROM Holiday h
        WHERE h.IsDeleted = 0
          AND n.d BETWEEN h.FromDate AND h.ToDate
          AND (h.SchoolId IS NULL OR h.SchoolId = sl.SchoolId));
GO

/* =====================================================================
   4) BỐN PHIẾU ĐỂ DEMO NHÁNH TỪ CHỐI / HẾT HẠN — nằm ở TRANG 2
   ---------------------------------------------------------------------
   Màn Phân công xếp Id giảm dần, 10 dòng một trang, nên hạng 11–20 là trang 2.
   Lấy hạng 12, 14, 16, 18 để bốn dòng nằm rải chứ không dính chùm.
   ===================================================================== */

DECLARE @tuNgay DATE = '2026-09-03';

IF OBJECT_ID('tempdb..#Demo') IS NOT NULL DROP TABLE #Demo;
SELECT Id, ROW_NUMBER() OVER (ORDER BY Id DESC) AS hang
INTO #Demo
FROM Assignment
WHERE IsDeleted = 0 AND StartDate = @tuNgay;

/* Phiếu bị từ chối / hết hạn thì CHƯA từng có hiệu lực, nên buổi dạy của
   chúng không được tồn tại. Xóa trước khi đổi trạng thái. */
DELETE FROM ScheduleStatusLog
WHERE ScheduleId IN (SELECT s.Id FROM Schedule s JOIN #Demo d ON d.Id = s.AssignmentId
                      WHERE d.hang IN (12, 14, 16, 18));
DELETE FROM Schedule
WHERE AssignmentId IN (SELECT Id FROM #Demo WHERE hang IN (12, 14, 16, 18));

UPDATE a
SET a.Status            = 'REJECTED',
    a.ConfirmedAt       = NULL,
    a.ConfirmedByUserId = NULL,
    a.ConfirmSource     = NULL,
    a.RejectionReason   = CASE d.hang
                              WHEN 12 THEN N'Trùng lịch dạy tại trung tâm khác vào sáng thứ Năm.'
                              ELSE N'Nhà xa, buổi chiều muộn không kịp đón con.' END,
    a.UpdatedAt         = DATEADD(DAY, -3, CAST(a.StartDate AS DATETIME2(3)))
FROM Assignment a JOIN #Demo d ON d.Id = a.Id
WHERE d.hang IN (12, 14);

UPDATE a
SET a.Status            = 'EXPIRED',
    a.ConfirmedAt       = NULL,
    a.ConfirmedByUserId = NULL,
    a.ConfirmSource     = NULL,
    /* Hạn trả lời đã trôi qua — đó chính là định nghĩa của EXPIRED. */
    a.ConfirmDeadline   = DATEADD(DAY, -2, CAST(a.StartDate AS DATETIME2(3))),
    a.UpdatedAt         = DATEADD(DAY, -2, CAST(a.StartDate AS DATETIME2(3)))
FROM Assignment a JOIN #Demo d ON d.Id = a.Id
WHERE d.hang IN (16, 18);
GO

/* =====================================================================
   5) TỰ KIỂM CHỨNG — in ra để người chạy đối chiếu, không im lặng báo xong
   ===================================================================== */

DECLARE @tuNgay DATE = '2026-09-03';

SELECT N'Phiếu phân công mới' AS chiTieu, COUNT(*) AS soLuong
FROM Assignment WHERE IsDeleted = 0 AND StartDate = @tuNgay
UNION ALL
SELECT N'  · trong đó ACTIVE', COUNT(*) FROM Assignment
 WHERE IsDeleted = 0 AND StartDate = @tuNgay AND Status = 'ACTIVE'
UNION ALL
SELECT N'  · trong đó REJECTED', COUNT(*) FROM Assignment
 WHERE IsDeleted = 0 AND StartDate = @tuNgay AND Status = 'REJECTED'
UNION ALL
SELECT N'  · trong đó EXPIRED', COUNT(*) FROM Assignment
 WHERE IsDeleted = 0 AND StartDate = @tuNgay AND Status = 'EXPIRED'
UNION ALL
SELECT N'Ô thời khóa biểu', COUNT(*) FROM AssignmentSlot sl
 JOIN Assignment a ON a.Id = sl.AssignmentId WHERE a.StartDate = @tuNgay
UNION ALL
SELECT N'Buổi dạy sinh ra', COUNT(*) FROM Schedule s
 JOIN Assignment a ON a.Id = s.AssignmentId WHERE a.StartDate = @tuNgay
UNION ALL
SELECT N'Phiếu COMPLETED giữ lại', COUNT(*) FROM Assignment WHERE Status = 'COMPLETED';

/* Phải ra 0 dòng: một giáo viên hai buổi cùng thứ. */
SELECT N'TRÙNG LỊCH (phải rỗng)' AS canhBao, sl.TeacherId, sl.DayOfWeek, COUNT(*) AS soO
FROM AssignmentSlot sl
JOIN Assignment a ON a.Id = sl.AssignmentId AND a.StartDate = @tuNgay AND a.IsDeleted = 0
WHERE sl.IsDeleted = 0
GROUP BY sl.TeacherId, sl.DayOfWeek
HAVING COUNT(*) > 1;

/* Phải ra 0 dòng: môn dạy cho khối nằm ngoài phạm vi ghi trong tên môn
   (ví dụ "Tiếng Anh Tiểu học 3-5" xếp vào lớp 6). */
SELECT N'MÔN LỆCH KHỐI (phải rỗng)' AS canhBao, sub.Name AS mon, c.Name AS lop
FROM AssignmentSlot sl
JOIN Assignment a  ON a.Id = sl.AssignmentId AND a.StartDate = @tuNgay AND a.IsDeleted = 0
JOIN Subject sub   ON sub.Id = a.SubjectId
JOIN SchoolClass c ON c.Id = sl.ClassId
WHERE sl.IsDeleted = 0
  AND TRY_CAST(c.GradeLevel AS INT) NOT BETWEEN
        CASE WHEN sub.Name LIKE N'%(Lớp 1-2)%' THEN 1 WHEN sub.Name LIKE N'%3-5%' THEN 3
             WHEN sub.Name LIKE N'%6-9%' THEN 6 WHEN sub.Name LIKE N'%Tiểu học%' THEN 1
             WHEN sub.Name LIKE N'%THCS%' THEN 6 WHEN sub.Name LIKE N'%lớp 1' THEN 1 ELSE 1 END
    AND CASE WHEN sub.Name LIKE N'%(Lớp 1-2)%' THEN 2 WHEN sub.Name LIKE N'%3-5%' THEN 5
             WHEN sub.Name LIKE N'%6-9%' THEN 9 WHEN sub.Name LIKE N'%Tiểu học%' THEN 5
             WHEN sub.Name LIKE N'%THCS%' THEN 9 WHEN sub.Name LIKE N'%lớp 1' THEN 1 ELSE 9 END;

/* Phải ra 0 dòng: buổi dạy rơi vào ngày nghỉ đã khai. */
SELECT N'BUỔI RƠI VÀO NGÀY NGHỈ (phải rỗng)' AS canhBao, COUNT(*) AS soBuoi
FROM Schedule s
JOIN Assignment a ON a.Id = s.AssignmentId AND a.StartDate = @tuNgay
WHERE EXISTS (SELECT 1 FROM Holiday h
              WHERE h.IsDeleted = 0
                AND CAST(s.StartTime AS DATE) BETWEEN h.FromDate AND h.ToDate
                AND (h.SchoolId IS NULL OR h.SchoolId = a.SchoolId))
HAVING COUNT(*) > 0;
GO
