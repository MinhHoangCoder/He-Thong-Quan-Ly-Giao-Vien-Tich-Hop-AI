/* =====================================================================
   TSDMS - V22: CHUẨN HÓA KHUNG TIẾT + thêm tiết "Chiều 5" cho TIỂU HỌC
   ---------------------------------------------------------------------
   BỐI CẢNH:
     Khung tiết (bảng Period) mỗi trường một kiểu, dựng bằng script seed rời
     nên đã trôi khỏi quy ước: giờ ra chơi rải rác sau tiết 1 VÀ tiết 2, khối
     THCS tan chiều lúc 17:30. Nghiệp vụ chốt lại một quy ước DUY NHẤT:

       - Mỗi buổi RA CHƠI ĐÚNG 1 LẦN, ngay sau tiết 2; các tiết còn lại nối
         liền nhau, KHÔNG có thời gian chuyển tiết.
       - Tiểu học : tiết 35', ra chơi 15'. THCS trở lên: tiết 45', ra chơi 10'.
       - Vào học 07:00 (sáng) và 14:00 (chiều); TAN muộn nhất 17:15.
       - Tiểu học có THÊM tiết chiều thứ 5 (PeriodNumber 10) — đây là yêu cầu
         gốc; THCS giữ 4 tiết chiều vì 5 tiết 45' không lọt mốc 17:15.

     Cộng lại: cả hai cấp đều tan chiều 17:10.

   CÁCH LÀM:
     Bảng mẫu (#PeriodTarget) = KHUNG CHUẨN × từng trường, cấp trường suy từ
     KHỐI lớp cao nhất (1..5 = tiểu học) — không hardcode SchoolId nên máy nào
     chạy cũng ra đúng khung của trường mình. Sau đó UPDATE tiết đã có + INSERT
     tiết còn thiếu. Không xóa tiết nào (AssignmentSlot/Schedule đang trỏ vào).

     Idempotent: chạy lại nhiều lần đều ra cùng một trạng thái.

   KÈM THEO (siết ràng buộc còn thiếu của Period):
     - CK_Period_Time      : StartTime phải trước EndTime.
     - TR_Period_NoOverlap : hai tiết cùng một trường không được đè giờ lên nhau.
       Trước đây DB chỉ chặn trùng PeriodNumber, sửa nhầm giờ một dòng là sinh
       trùng lịch âm thầm mà không tầng nào bắt được.

   GHI CHÚ:
     Schedule chép cứng giờ từ Period lúc sinh buổi (AssignmentService), nên
     phần cuối đồng bộ lại giờ cho các buổi TƯƠNG LAI. Buổi đã qua giữ nguyên
     để không viết lại lịch sử chấm công.
   ===================================================================== */

/* ---------- 1) Dựng bảng mẫu: khung chuẩn × từng trường ---------- */
IF OBJECT_ID('tempdb..#PeriodTarget') IS NOT NULL DROP TABLE #PeriodTarget;

/* COLLATE DATABASE_DEFAULT: bảng tạm nằm ở tempdb (collation Latin1) còn DB là
   Vietnamese_CI_AS — không ép collation thì phép so SessionType nổ lỗi 468. */
CREATE TABLE #PeriodTarget (
    SchoolId     INT         NOT NULL,
    PeriodNumber TINYINT     NOT NULL,
    SessionType  VARCHAR(10) COLLATE DATABASE_DEFAULT NOT NULL,
    StartTime    TIME(0)     NOT NULL,
    EndTime      TIME(0)     NOT NULL,
    PRIMARY KEY (SchoolId, PeriodNumber)
);

;WITH Frame(Lvl, PeriodNumber, SessionType, StartTime, EndTime) AS (
    SELECT * FROM (VALUES
        /* TIỂU HỌC — tiết 35', ra chơi 15' sau tiết 2 mỗi buổi. 10 tiết/ngày. */
        ('TH',  1, 'MORNING',   '07:00', '07:35'),
        ('TH',  2, 'MORNING',   '07:35', '08:10'),   -- ra chơi 15'
        ('TH',  3, 'MORNING',   '08:25', '09:00'),
        ('TH',  4, 'MORNING',   '09:00', '09:35'),
        ('TH',  5, 'MORNING',   '09:35', '10:10'),
        ('TH',  6, 'AFTERNOON', '14:00', '14:35'),
        ('TH',  7, 'AFTERNOON', '14:35', '15:10'),   -- ra chơi 15'
        ('TH',  8, 'AFTERNOON', '15:25', '16:00'),
        ('TH',  9, 'AFTERNOON', '16:00', '16:35'),
        ('TH', 10, 'AFTERNOON', '16:35', '17:10'),   -- TIẾT MỚI: "Chiều · Tiết 5"
        /* THCS trở lên — tiết 45', ra chơi 10' sau tiết 2 mỗi buổi. 9 tiết/ngày. */
        ('THCS',  1, 'MORNING',   '07:00', '07:45'),
        ('THCS',  2, 'MORNING',   '07:45', '08:30'),  -- ra chơi 10'
        ('THCS',  3, 'MORNING',   '08:40', '09:25'),
        ('THCS',  4, 'MORNING',   '09:25', '10:10'),
        ('THCS',  5, 'MORNING',   '10:10', '10:55'),
        ('THCS',  6, 'AFTERNOON', '14:00', '14:45'),
        ('THCS',  7, 'AFTERNOON', '14:45', '15:30'),  -- ra chơi 10'
        ('THCS',  8, 'AFTERNOON', '15:40', '16:25'),
        ('THCS',  9, 'AFTERNOON', '16:25', '17:10')
    ) v(Lvl, PeriodNumber, SessionType, StartTime, EndTime)
),
/* Cấp của trường = khối lớp CAO NHẤT đang mở (1..5 tiểu học, từ 6 trở lên THCS/THPT).
   Trường chưa có lớp nào thì bỏ qua — không đoán khung tiết cho trường trống. */
SchoolLevel(SchoolId, Lvl) AS (
    SELECT c.SchoolId,
           CASE WHEN MAX(TRY_CAST(c.GradeLevel AS INT)) <= 5 THEN 'TH' ELSE 'THCS' END
    FROM SchoolClass c
    JOIN School s ON s.Id = c.SchoolId AND s.IsDeleted = 0
    WHERE c.IsDeleted = 0 AND TRY_CAST(c.GradeLevel AS INT) IS NOT NULL
    GROUP BY c.SchoolId
)
INSERT INTO #PeriodTarget (SchoolId, PeriodNumber, SessionType, StartTime, EndTime)
SELECT sl.SchoolId, f.PeriodNumber, f.SessionType,
       CONVERT(TIME(0), f.StartTime), CONVERT(TIME(0), f.EndTime)
FROM SchoolLevel sl
JOIN Frame f ON f.Lvl = sl.Lvl;

/* ---------- 2) Sửa giờ các tiết ĐÃ CÓ ---------- */
UPDATE p
SET    p.SessionType = t.SessionType,
       p.StartTime   = t.StartTime,
       p.EndTime     = t.EndTime,
       p.UpdatedAt   = SYSUTCDATETIME()
FROM   Period p
JOIN   #PeriodTarget t
       ON t.SchoolId = p.SchoolId AND t.PeriodNumber = p.PeriodNumber
WHERE  p.IsDeleted = 0
  AND (p.SessionType <> t.SessionType OR p.StartTime <> t.StartTime OR p.EndTime <> t.EndTime);

/* ---------- 3) Thêm tiết còn THIẾU (tiết chiều 5 của tiểu học; trường chưa có khung nào) ---------- */
INSERT INTO Period (SchoolId, PeriodNumber, SessionType, StartTime, EndTime)
SELECT t.SchoolId, t.PeriodNumber, t.SessionType, t.StartTime, t.EndTime
FROM   #PeriodTarget t
WHERE  NOT EXISTS (
           SELECT 1 FROM Period p
           WHERE p.SchoolId = t.SchoolId AND p.PeriodNumber = t.PeriodNumber AND p.IsDeleted = 0);

DROP TABLE #PeriodTarget;
GO

/* ---------- 4) Đồng bộ giờ cho các buổi dạy TƯƠNG LAI ----------
   Schedule.StartTime/EndTime là bản CHỤP giờ của Period lúc sinh buổi, đổi khung
   tiết thì buổi cũ vẫn giữ giờ cũ. Kéo lại theo PeriodId cho buổi từ hôm nay trở
   đi; buổi đã qua giữ nguyên (chấm công đã đối chiếu theo giờ đó). */
UPDATE s
SET    s.StartTime = DATEADD(second, DATEDIFF(second, '00:00:00', p.StartTime), CONVERT(datetime2(0), CONVERT(date, s.StartTime))),
       s.EndTime   = DATEADD(second, DATEDIFF(second, '00:00:00', p.EndTime),   CONVERT(datetime2(0), CONVERT(date, s.StartTime))),
       s.UpdatedAt = SYSUTCDATETIME()
FROM   Schedule s
JOIN   Period p ON p.Id = s.PeriodId AND p.IsDeleted = 0
WHERE  s.IsDeleted = 0
  AND  CONVERT(date, s.StartTime) >= CONVERT(date, SYSUTCDATETIME())
  AND (CONVERT(TIME(0), s.StartTime) <> p.StartTime OR CONVERT(TIME(0), s.EndTime) <> p.EndTime);
GO

/* ---------- 5) Ràng buộc còn thiếu: giờ bắt đầu phải trước giờ kết thúc ---------- */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'CK_Period_Time' AND parent_object_id = OBJECT_ID(N'dbo.Period'))
    ALTER TABLE Period ADD CONSTRAINT CK_Period_Time CHECK (StartTime < EndTime);
GO

/* ---------- 6) Ràng buộc còn thiếu: hai tiết CÙNG TRƯỜNG không được đè giờ ----------
   UX_Period_School_Number chỉ chặn trùng SỐ tiết, không chặn trùng GIỜ. Đặt ở tầng DB
   vì Period hiện chưa có màn hình quản lý — mọi thay đổi đều đi bằng SQL tay. */
CREATE OR ALTER TRIGGER TR_Period_NoOverlap
ON Period
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM   inserted i
        JOIN   Period p
               ON p.SchoolId = i.SchoolId AND p.Id <> i.Id AND p.IsDeleted = 0
        WHERE  i.IsDeleted = 0
          AND  i.StartTime < p.EndTime
          AND  p.StartTime < i.EndTime)   -- so CHẶT: hai tiết liền kề (16:35 & 16:35) KHÔNG tính là đè
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50022, N'Khung tiet bi de gio len nhau trong cung mot truong', 1;
    END
END;
GO
