/* =====================================================================
   TSDMS — SEED DỮ LIỆU NĂM HỌC 2025–2026 (ĐÃ KẾT THÚC)
   ---------------------------------------------------------------------
   VÌ SAO CẦN FILE NÀY
   Trước file này, toàn bộ dữ liệu giao dịch trong DB nằm ở năm học
   2026–2027 (07/09/2026 → 28/05/2027), tức là HOÀN TOÀN Ở TƯƠNG LAI so
   với ngày hiện tại. Kèm theo đó Attendance / Payroll / TeacherEvaluation
   đều rỗng. Hệ quả: mọi biểu đồ "xu hướng N tháng gần nhất" của Bảng điều
   khiển đều trống, mọi phép so sánh cùng kỳ đều không tính được.

   File này bù đúng phần còn thiếu: MỘT NĂM HỌC ĐÃ HOÀN THÀNH ở phía sau
   (08/09/2025 → 29/05/2026), đầy đủ vòng đời nghiệp vụ:
       Phân công → Lịch dạy → Chấm công → Bảng lương → Đánh giá giáo viên.

   NGUYÊN TẮC THIẾT KẾ
   1. TÁI LẬP ĐƯỢC — không dùng NEWID()/RAND(). Mọi lựa chọn "ngẫu nhiên"
      đều là hàm băm CHECKSUM() trên khóa chính, nên chạy lại luôn ra
      cùng một bộ số. Bảo vệ đồ án mà số liệu nhảy mỗi lần chạy là hỏng.
   2. DỊCH ĐÚNG 364 NGÀY (= 52 tuần) so với năm học 2026–2027, nên THỨ
      TRONG TUẦN được giữ nguyên tuyệt đối: tiết "Thứ Hai tiết 1" của năm
      cũ vẫn rơi vào Thứ Hai. Dịch 365 ngày là lệch một thứ, hỏng cả năm.
   3. TÔN TRỌNG NGÀY NGHỈ — buổi nào rơi vào Holiday sẽ bị gỡ, không phải
      để cho đẹp mà vì "số buổi dạy tháng 2" phải sụt xuống do nghỉ Tết,
      đó mới là dữ liệu biết nói.
   4. KHÔNG ĐỘNG VÀO DỮ LIỆU CŨ — chỉ INSERT. Năm học 2026–2027 giữ
      nguyên từng dòng.

   YÊU CẦU TRƯỚC KHI CHẠY
   - Đã chạy TSDMS_Seed_100GiaoVien.sql và TSDMS_Seed_TruongHaiPhong.sql
   - Đã có sẵn phân công năm học 2026–2027 (bảng Assignment có dòng với
     StartDate >= 2026-09-01) — file này soi chiếu từ đó.

   GỠ BỎ: chạy TSDMS_Rollback_NamHoc2025.sql
   ===================================================================== */

SET QUOTED_IDENTIFIER ON;   -- BẮT BUỘC: Payroll có index trên cột tính sẵn NetAmount
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;          -- lỗi giữa chừng là quay lui sạch, không để lại nửa vời
GO

PRINT N'===== SEED NĂM HỌC 2025–2026 =====';
GO

/* ---------------------------------------------------------------------
   THAM SỐ CHUNG
   --------------------------------------------------------------------- */
DECLARE @Shift INT = -364;                       -- 52 tuần chẵn → giữ nguyên thứ
DECLARE @NamHocTu  DATE = '2025-09-08';          -- Thứ Hai
DECLARE @NamHocDen DATE = '2026-05-29';          -- Thứ Sáu
DECLARE @Admin INT = (SELECT TOP 1 Id FROM AppUser WHERE Username = 'admin');

IF @Admin IS NULL
BEGIN
    RAISERROR (N'Không tìm thấy tài khoản admin — chạy seed nền trước đã.', 16, 1);
    RETURN;
END;

IF EXISTS (SELECT 1 FROM Assignment WHERE IsDeleted = 0 AND StartDate = @NamHocTu)
BEGIN
    PRINT N'>> Dữ liệu năm học 2025–2026 ĐÃ CÓ — bỏ qua. Muốn nạp lại thì chạy rollback trước.';
    RETURN;
END;

IF NOT EXISTS (SELECT 1 FROM Assignment WHERE IsDeleted = 0 AND StartDate >= '2026-09-01')
BEGIN
    RAISERROR (N'Chưa có phân công năm 2026–2027 để soi chiếu — nạp TSDMS_Seed_PhanCong.sql trước.', 16, 1);
    RETURN;
END;

BEGIN TRANSACTION;

/* =====================================================================
   BƯỚC 1 — PHÂN CÔNG (Assignment)
   Nhân bản bộ phân công năm 2026–2027, lùi ngày 364 hôm.
   Trạng thái: đa số COMPLETED (năm học đã khép lại); ~1/12 số phiếu để
   CANCELLED cho biểu đồ cơ cấu có đủ ba màu — trung tâm nào cũng có phiếu
   huỷ giữa chừng, dữ liệu 100% COMPLETED mới là dữ liệu không thật.
   ===================================================================== */
PRINT N'[1/6] Phân công…';

CREATE TABLE #Nguon (
    OldId    INT PRIMARY KEY,
    TeacherId INT NOT NULL,
    SchoolId  INT NOT NULL,
    SubjectId INT NOT NULL,
    ClassId   INT NULL,
    StartDate DATE NOT NULL,
    EndDate   DATE NOT NULL,
    Status    VARCHAR(20) COLLATE DATABASE_DEFAULT NOT NULL
);

INSERT INTO #Nguon (OldId, TeacherId, SchoolId, SubjectId, ClassId, StartDate, EndDate, Status)
SELECT a.Id,
       a.TeacherId,
       a.SchoolId,
       a.SubjectId,
       a.ClassId,
       DATEADD(DAY, @Shift, a.StartDate),
       DATEADD(DAY, @Shift, a.EndDate),
       CASE WHEN ABS(CHECKSUM(a.Id * 7919)) % 12 = 0 THEN 'CANCELLED' ELSE 'COMPLETED' END
FROM Assignment a
WHERE a.IsDeleted = 0
  AND a.StartDate >= '2026-09-01';

-- MERGE thay cho INSERT vì chỉ MERGE mới OUTPUT được cột của NGUỒN, nhờ đó
-- lấy được cặp (Id cũ → Id mới) trong đúng một lượt ghi.
CREATE TABLE #MapAsg (OldId INT PRIMARY KEY, NewId INT NOT NULL);

MERGE INTO Assignment AS tgt
USING #Nguon AS src
ON 1 = 0
WHEN NOT MATCHED THEN
    INSERT (TeacherId, SchoolId, SubjectId, ClassId, StartDate, EndDate, Status, IsDeleted, CreatedAt, CreatedBy)
    VALUES (src.TeacherId, src.SchoolId, src.SubjectId, src.ClassId,
            src.StartDate, src.EndDate, src.Status, 0,
            DATEADD(DAY, -14, CAST(src.StartDate AS DATETIME2(3))), @Admin)
OUTPUT inserted.Id, src.OldId INTO #MapAsg (NewId, OldId);

PRINT N'      → ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' phân công';

/* =====================================================================
   BƯỚC 2 — Ô LỊCH TUẦN (AssignmentSlot)
   Mẫu lặp (thứ, tiết, phòng) chép nguyên từ phân công gốc.
   ===================================================================== */
PRINT N'[2/6] Ô lịch tuần…';

-- ClassId PHẢI được chép theo: từ V16 đơn giá một tiết tra theo KHỐI của lớp dạy ở chính
-- tiết đó (Tiểu học và THCS khác giá). Bỏ sót cột này thì PayrollService rơi về đơn giá
-- mặc định và toàn bộ bảng lương THCS bị tính thiếu tiền.
CREATE TABLE #NguonSlot (
    OldId        INT PRIMARY KEY,
    NewAsgId     INT NOT NULL,
    TeacherId    INT NOT NULL,
    DayOfWeek    VARCHAR(10) COLLATE DATABASE_DEFAULT NOT NULL,
    PeriodId     INT NOT NULL,
    RoomId       INT NULL,
    SchoolId     INT NULL,
    ClassId      INT NULL
);

INSERT INTO #NguonSlot (OldId, NewAsgId, TeacherId, DayOfWeek, PeriodId, RoomId, SchoolId, ClassId)
SELECT s.Id, m.NewId, s.TeacherId, s.DayOfWeek, s.PeriodId, s.RoomId, s.SchoolId, s.ClassId
FROM AssignmentSlot s
JOIN #MapAsg m ON m.OldId = s.AssignmentId
WHERE s.IsDeleted = 0;

CREATE TABLE #MapSlot (OldId INT PRIMARY KEY, NewId INT NOT NULL);

MERGE INTO AssignmentSlot AS tgt
USING #NguonSlot AS src
ON 1 = 0
WHEN NOT MATCHED THEN
    INSERT (AssignmentId, TeacherId, DayOfWeek, PeriodId, RoomId, SchoolId, ClassId,
            IsDeleted, CreatedAt, CreatedBy)
    VALUES (src.NewAsgId, src.TeacherId, src.DayOfWeek, src.PeriodId, src.RoomId, src.SchoolId,
            src.ClassId, 0, SYSUTCDATETIME(), @Admin)
OUTPUT inserted.Id, src.OldId INTO #MapSlot (NewId, OldId);

PRINT N'      → ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' ô lịch';

/* =====================================================================
   BƯỚC 3 — LỊCH DẠY (Schedule)
   Mỗi buổi của năm 2026–2027 sinh ra một buổi tương ứng lùi 364 ngày.
   Phân bố trạng thái (băm trên Id nguồn nên cố định):
       ~94% APPROVED — buổi dạy thật, là mẫu số của mọi thống kê
       ~4%  CANCELLED — nghỉ đột xuất, trường báo huỷ
       ~2%  REJECTED  — đề xuất xếp lịch bị từ chối
   Phân công đã CANCELLED thì toàn bộ buổi của nó cũng CANCELLED — dữ liệu
   phải nhất quán, không thể có buổi "đã duyệt" nằm dưới phiếu đã huỷ.
   ===================================================================== */
PRINT N'[3/6] Lịch dạy…';

INSERT INTO Schedule (AssignmentId, TeacherId, RoomId, StartTime, EndTime, Status, Source,
                      PeriodId, SourceSlotId, CreatedByUserId, ApprovedByUserId, ApprovedAt,
                      RejectionReason, IsDeleted, CreatedAt)
SELECT ma.NewId,
       sch.TeacherId,
       sch.RoomId,
       DATEADD(DAY, @Shift, sch.StartTime),
       DATEADD(DAY, @Shift, sch.EndTime),
       tt.Status,
       'MANUAL',
       sch.PeriodId,
       ms.NewId,
       @Admin,
       CASE WHEN tt.Status = 'APPROVED' THEN @Admin ELSE NULL END,
       CASE WHEN tt.Status = 'APPROVED'
            THEN DATEADD(DAY, @Shift - 7, CAST(sch.StartTime AS DATETIME2(3))) END,
       CASE WHEN tt.Status = 'REJECTED' THEN N'Trùng lịch giáo viên tại cơ sở khác' END,
       0,
       DATEADD(DAY, @Shift - 14, CAST(sch.StartTime AS DATETIME2(3)))
FROM Schedule sch
JOIN #MapAsg  ma ON ma.OldId = sch.AssignmentId
LEFT JOIN #MapSlot ms ON ms.OldId = sch.SourceSlotId
JOIN #Nguon   ng ON ng.OldId = sch.AssignmentId
CROSS APPLY (
    SELECT Status = CASE
        WHEN ng.Status = 'CANCELLED' THEN 'CANCELLED'
        WHEN ABS(CHECKSUM(sch.Id * 104729)) % 100 < 94 THEN 'APPROVED'
        WHEN ABS(CHECKSUM(sch.Id * 104729)) % 100 < 98 THEN 'CANCELLED'
        ELSE 'REJECTED' END
) tt
WHERE sch.IsDeleted = 0
  AND sch.StartTime >= '2026-09-01';

PRINT N'      → ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' buổi (trước khi trừ ngày nghỉ)';

-- Gỡ những buổi rơi vào ngày nghỉ của NĂM HỌC NÀY (Tết 2026, 30/4, 1/5…).
-- Lịch nghỉ hai năm học không trùng nhau nên phép dịch 364 ngày để lọt vài buổi
-- vào ngày nghỉ; xoá đi thì đường biểu đồ tháng 2 mới có cú sụt đúng như thật.
DELETE sch
FROM Schedule sch
WHERE sch.StartTime >= @NamHocTu
  AND sch.StartTime <  DATEADD(DAY, 1, @NamHocDen)
  AND EXISTS (
        SELECT 1 FROM Holiday h
        WHERE h.IsDeleted = 0
          AND (h.SchoolId IS NULL OR h.SchoolId = (SELECT a.SchoolId FROM Assignment a WHERE a.Id = sch.AssignmentId))
          AND CAST(sch.StartTime AS DATE) BETWEEN h.FromDate AND h.ToDate);

PRINT N'      → gỡ ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' buổi trùng ngày nghỉ';

/* =====================================================================
   BƯỚC 4 — CHẤM CÔNG (Attendance)
   Một dòng cho mỗi buổi ĐÃ DUYỆT. Phân bố sát thực tế một trung tâm:
       89% PRESENT · 6% LATE · 3% LEAVE (nghỉ phép) · 2% ABSENT
   Giờ vào của dòng LATE luôn > giờ vào tiết + 15 phút, đúng bằng ngưỡng
   AttendanceService.LATE_THRESHOLD_MIN — nếu seed đặt lệch ngưỡng thì màn
   hình Chấm công sẽ hiện "đi muộn" cho một giờ vào đúng hạn, và người
   chấm sẽ bắt được ngay.
   ===================================================================== */
PRINT N'[4/6] Chấm công…';

INSERT INTO Attendance (TeacherId, ScheduleId, WorkDate, CheckIn, CheckOut, Status,
                        CheckInMethod, ConfirmedByUserId, ConfirmedAt, AutoCheckOut, CreatedAt, CreatedBy)
SELECT sch.TeacherId,
       sch.Id,
       CAST(sch.StartTime AS DATE),
       -- Vắng/nghỉ phép thì không có giờ vào ra
       CASE tt.Status
            WHEN 'PRESENT' THEN DATEADD(MINUTE, -(ABS(CHECKSUM(sch.Id * 31)) % 8), CAST(sch.StartTime AS TIME(0)))
            -- 16..25 phút: đủ để vượt ngưỡng 15' nhưng vẫn trong tiết ngắn nhất (35') nên
            -- ràng buộc CK_Attendance_Time (CheckIn < CheckOut) không bao giờ vỡ
            WHEN 'LATE'    THEN DATEADD(MINUTE,  16 + ABS(CHECKSUM(sch.Id * 37)) % 10, CAST(sch.StartTime AS TIME(0)))
       END,
       CASE WHEN tt.Status IN ('PRESENT', 'LATE')
            THEN DATEADD(MINUTE, ABS(CHECKSUM(sch.Id * 41)) % 6, CAST(sch.EndTime AS TIME(0))) END,
       tt.Status,
       CASE ABS(CHECKSUM(sch.Id * 53)) % 10
            WHEN 0 THEN 'EMPLOYEE' WHEN 1 THEN 'SCHOOL' WHEN 2 THEN 'DEVICE' ELSE 'SELF' END,
       CASE WHEN ABS(CHECKSUM(sch.Id * 59)) % 3 = 0 THEN @Admin END,
       CASE WHEN ABS(CHECKSUM(sch.Id * 59)) % 3 = 0
            THEN DATEADD(HOUR, 6, CAST(sch.EndTime AS DATETIME2(3))) END,
       -- ~7% buổi giáo viên quên bấm giờ ra, hệ thống tự chốt
       CASE WHEN tt.Status IN ('PRESENT', 'LATE') AND ABS(CHECKSUM(sch.Id * 61)) % 15 = 0 THEN 1 ELSE 0 END,
       CAST(sch.EndTime AS DATETIME2(3)),
       @Admin
FROM Schedule sch
CROSS APPLY (
    SELECT Status = CASE
        WHEN ABS(CHECKSUM(sch.Id * 15485863)) % 100 < 89 THEN 'PRESENT'
        WHEN ABS(CHECKSUM(sch.Id * 15485863)) % 100 < 95 THEN 'LATE'
        WHEN ABS(CHECKSUM(sch.Id * 15485863)) % 100 < 98 THEN 'LEAVE'
        ELSE 'ABSENT' END
) tt
WHERE sch.IsDeleted = 0
  AND sch.Status = 'APPROVED'
  AND sch.StartTime >= @NamHocTu
  AND sch.StartTime <  DATEADD(DAY, 1, @NamHocDen);

PRINT N'      → ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' dòng chấm công';

/* =====================================================================
   BƯỚC 5 — BẢNG LƯƠNG (Payroll)
   Chín kỳ 09/2025 → 05/2026.

   PHẢI TÍNH ĐÚNG BẰNG CÔNG THỨC CỦA PayrollService.generate(), không được
   tự nghĩ ra cách khác. Lý do rất cụ thể: màn hình Bảng lương có nút "Tạo
   bảng lương" chạy lại đúng hàm đó. Nếu seed tính kiểu khác thì chỉ cần
   người chấm bấm nút ấy một lần là mọi con số đổi hết, và cả Bảng điều
   khiển lẫn Bảng lương cùng lệch — không có cách nào giải thích.

   Mô hình lương của trung tâm (đọc từ PayrollService):
     · Trả theo TIẾT, mỗi buổi có mặt (PRESENT hoặc LATE) = 1 tiết.
     · Đơn giá theo CẤP của lớp dạy ở chính tiết đó:
           khối 1–5 (Tiểu học) 115.000đ · khối 6–9 (THCS) 125.000đ
     · Cột TaughtHours được dùng lại để lưu SỐ TIẾT, RatePerHour lưu
       ĐƠN GIÁ MỖI TIẾT (tên cột là di sản, ý nghĩa đã đổi).
     · BaseSalary và Allowance để 0 — bộ sinh không đụng vào hai cột này.

   Bonus/Deduction thì bộ sinh cũng không đụng tới; đây là hai ô admin sửa
   tay trên màn hình Bảng lương, nên seed điền sẵn để màn hình có dữ liệu
   thật mà chạy lại bộ sinh vẫn không ghi đè.

   Trạng thái kỳ lương dựng theo vòng đời thật: kỳ cũ đã trả, kỳ áp chót
   mới chốt, kỳ cuối còn nháp — để khối "Cần xử lý ngay" của Bảng điều
   khiển có việc thật mà cảnh báo.
   ===================================================================== */
PRINT N'[5/6] Bảng lương…';

;WITH TietDay AS (
    SELECT att.TeacherId,
           PeriodYear  = CAST(YEAR(sch.StartTime)  AS SMALLINT),
           PeriodMonth = CAST(MONTH(sch.StartTime) AS TINYINT),
           SoTiet      = COUNT(*),
           -- Tổng tiền = cộng đơn giá của từng tiết. Giáo viên dạy hai cấp (hiếm) thì
           -- đơn giá hiệu dụng ở dưới thành trung bình có trọng số — đúng như Java làm.
           TongTien    = SUM(CASE WHEN TRY_CAST(cls.GradeLevel AS INT) BETWEEN 6 AND 9
                                  THEN 125000 ELSE 115000 END)
    FROM Attendance att
    JOIN Schedule sch ON sch.Id = att.ScheduleId
    JOIN Assignment asg ON asg.Id = sch.AssignmentId
    LEFT JOIN AssignmentSlot slot ON slot.Id = sch.SourceSlotId
    -- Lớp lấy từ Ô LỊCH trước, phân công sau: một phân công trải nhiều lớp, mà lớp 5
    -- và lớp 6 khác đơn giá nên đọc ở cấp phân công là tính sai tiền.
    LEFT JOIN SchoolClass cls ON cls.Id = COALESCE(slot.ClassId, asg.ClassId)
    WHERE sch.IsDeleted = 0
      AND sch.StartTime >= @NamHocTu
      AND sch.StartTime <  DATEADD(DAY, 1, @NamHocDen)
      AND att.Status IN ('PRESENT', 'LATE')
    GROUP BY att.TeacherId, YEAR(sch.StartTime), MONTH(sch.StartTime)
)
INSERT INTO Payroll (TeacherId, PeriodMonth, PeriodYear, BaseSalary, TaughtHours, RatePerHour,
                     Allowance, Bonus, Deduction, Status, CreatedAt, CreatedBy)
SELECT g.TeacherId,
       g.PeriodMonth,
       g.PeriodYear,
       0,                                                    -- BaseSalary: bộ sinh không đặt
       CAST(g.SoTiet AS DECIMAL(9, 2)),                       -- TaughtHours = SỐ TIẾT
       CAST(g.TongTien * 1.0 / g.SoTiet AS DECIMAL(18, 2)),   -- RatePerHour = ĐƠN GIÁ / TIẾT
       0,                                                    -- Allowance: bộ sinh không đặt
       -- Thưởng chuyên cần (admin điều chỉnh tay): kỳ nào không vắng buổi nào thì có
       CASE WHEN NOT EXISTS (
                SELECT 1 FROM Attendance a2
                JOIN Schedule s2 ON s2.Id = a2.ScheduleId
                WHERE a2.TeacherId = g.TeacherId AND a2.Status = 'ABSENT'
                  AND YEAR(s2.StartTime) = g.PeriodYear AND MONTH(s2.StartTime) = g.PeriodMonth)
            THEN 300000 ELSE 0 END,
       -- Khấu trừ 50.000đ mỗi buổi vào lớp muộn
       50000 * (SELECT COUNT(*) FROM Attendance a3
                JOIN Schedule s3 ON s3.Id = a3.ScheduleId
                WHERE a3.TeacherId = g.TeacherId AND a3.Status = 'LATE'
                  AND YEAR(s3.StartTime) = g.PeriodYear AND MONTH(s3.StartTime) = g.PeriodMonth),
       CASE
            WHEN g.PeriodYear * 100 + g.PeriodMonth <= 202603 THEN 'PAID'
            WHEN g.PeriodYear * 100 + g.PeriodMonth  = 202604 THEN 'FINALIZED'
            ELSE 'DRAFT' END,
       DATETIMEFROMPARTS(g.PeriodYear, g.PeriodMonth, 28, 9, 0, 0, 0),
       @Admin
FROM TietDay g
WHERE g.SoTiet > 0
  AND NOT EXISTS (
    SELECT 1 FROM Payroll p
    WHERE p.TeacherId = g.TeacherId AND p.PeriodYear = g.PeriodYear AND p.PeriodMonth = g.PeriodMonth);

PRINT N'      → ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' dòng lương';

/* =====================================================================
   BƯỚC 6 — ĐÁNH GIÁ GIÁO VIÊN (TeacherEvaluation)
   Hai đợt mỗi năm học (cuối HK1 và cuối HK2), do trường mà giáo viên dạy
   nhiều tiết nhất đứng ra chấm. Điểm KHÔNG rải đều 1–5: đánh giá thật
   luôn lệch về phía cao (30% điểm 5, 42% điểm 4…). Rải đều là dấu hiệu
   nhận biết dữ liệu bịa, và điểm trung bình sẽ ra đúng 3.0 — vô nghĩa.
   ===================================================================== */
PRINT N'[6/6] Đánh giá giáo viên…';

;WITH TruongChinh AS (
    -- Trường mà mỗi GV dạy nhiều tiết nhất trong năm học → người đánh giá hợp lý nhất
    SELECT sch.TeacherId, a.SchoolId,
           Hang = ROW_NUMBER() OVER (PARTITION BY sch.TeacherId ORDER BY COUNT(*) DESC, a.SchoolId)
    FROM Schedule sch
    JOIN Assignment a ON a.Id = sch.AssignmentId
    WHERE sch.IsDeleted = 0 AND sch.Status = 'APPROVED'
      AND sch.StartTime >= @NamHocTu AND sch.StartTime < DATEADD(DAY, 1, @NamHocDen)
    GROUP BY sch.TeacherId, a.SchoolId
),
Dot AS (
    SELECT 1 AS Ky, N'Học kỳ I năm học 2025–2026'  AS Nhan, CAST('2026-01-16' AS DATE) AS Ngay
    UNION ALL
    SELECT 2, N'Học kỳ II năm học 2025–2026', CAST('2026-05-30' AS DATE)
)
INSERT INTO TeacherEvaluation (TeacherId, EvaluatorUserId, SchoolId, Score, Comment, PeriodNote,
                               IsDeleted, CreatedAt, CreatedBy)
SELECT tc.TeacherId,
       @Admin,
       tc.SchoolId,
       diem.Score,
       CASE diem.Score
            WHEN 5 THEN N'Chuyên môn vững, chủ động phối hợp với nhà trường. Đề nghị tiếp tục hợp tác.'
            WHEN 4 THEN N'Hoàn thành tốt nhiệm vụ giảng dạy, học sinh phản hồi tích cực.'
            WHEN 3 THEN N'Hoàn thành nhiệm vụ. Cần chuẩn bị giáo án kỹ hơn cho các tiết thực hành.'
            WHEN 2 THEN N'Còn để lớp trầm, đề nghị dự giờ đồng nghiệp và điều chỉnh phương pháp.'
            ELSE        N'Chưa đáp ứng yêu cầu của nhà trường trong kỳ này.' END,
       d.Nhan,
       0,
       CAST(d.Ngay AS DATETIME2(3)),
       @Admin
FROM TruongChinh tc
CROSS JOIN Dot d
CROSS APPLY (
    SELECT Score = CAST(CASE
        WHEN ABS(CHECKSUM(tc.TeacherId * 7 + d.Ky * 1299709)) % 100 < 30 THEN 5
        WHEN ABS(CHECKSUM(tc.TeacherId * 7 + d.Ky * 1299709)) % 100 < 72 THEN 4
        WHEN ABS(CHECKSUM(tc.TeacherId * 7 + d.Ky * 1299709)) % 100 < 93 THEN 3
        WHEN ABS(CHECKSUM(tc.TeacherId * 7 + d.Ky * 1299709)) % 100 < 99 THEN 2
        ELSE 1 END AS TINYINT)
) diem
WHERE tc.Hang = 1;

PRINT N'      → ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' lượt đánh giá';

DROP TABLE #Nguon, #MapAsg, #NguonSlot, #MapSlot;

COMMIT TRANSACTION;
GO

/* ---------------------------------------------------------------------
   ĐỐI CHIẾU SAU KHI NẠP
   --------------------------------------------------------------------- */
PRINT N'';
PRINT N'===== KẾT QUẢ =====';
SELECT N'Phân công 2025–2026' AS Bang, COUNT(*) AS SoDong
FROM Assignment WHERE IsDeleted = 0 AND StartDate = '2025-09-08'
UNION ALL SELECT N'Buổi dạy', COUNT(*) FROM Schedule
    WHERE IsDeleted = 0 AND StartTime >= '2025-09-08' AND StartTime < '2026-05-30'
UNION ALL SELECT N'  ├ đã duyệt', COUNT(*) FROM Schedule
    WHERE IsDeleted = 0 AND Status = 'APPROVED' AND StartTime >= '2025-09-08' AND StartTime < '2026-05-30'
UNION ALL SELECT N'Chấm công', COUNT(*) FROM Attendance WHERE WorkDate BETWEEN '2025-09-08' AND '2026-05-29'
UNION ALL SELECT N'Bảng lương', COUNT(*) FROM Payroll
    WHERE PeriodYear * 100 + PeriodMonth BETWEEN 202509 AND 202605
UNION ALL SELECT N'Đánh giá', COUNT(*) FROM TeacherEvaluation
    WHERE IsDeleted = 0 AND PeriodNote LIKE N'%2025–2026%';
GO
