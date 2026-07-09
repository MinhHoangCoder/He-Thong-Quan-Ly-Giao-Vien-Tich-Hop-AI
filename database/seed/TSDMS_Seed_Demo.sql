/* =====================================================================
   TSDMS - SEED THEO LỚP: 4 DANH MỤC × LỚP 1..9 × 10 BÀI = 360 BÀI GIẢNG
   ---------------------------------------------------------------------
   DANH MỤC: Kĩ năng số · STEM - AI · Tiếng Anh · Tin học
   MỖI DANH MỤC: mỗi lớp (1..9) có 10 bài giảng.
   CHẠY SAU: "TSDMS_Schema.sql" (cần Branch). Guard NOT EXISTS: chạy lại
             nhiều lần không bị trùng.
   GHI CHÚ: "Kĩ năng số" là danh mục MỚI (KY_NANG_SO), khác với danh mục
            "Kĩ năng sống" (KY_NANG_SONG) ở các file seed trước.
   ===================================================================== */
USE TSDMS;
GO

/* ---------------------------------------------------------------------
   BƯỚC 1 — Bảo đảm 4 danh mục tồn tại (thêm mới "Kĩ năng số")
   --------------------------------------------------------------------- */
INSERT INTO SubjectCategory (Code, Name, Description)
SELECT v.Code, v.Name, v.Description
FROM (VALUES
    ('TIN_HOC',    N'Tin học',      N'Lập trình, tin học văn phòng, tư duy máy tính'),
    ('TIENG_ANH',  N'Tiếng Anh',    N'Giao tiếp, học thuật, tiếng Anh chuyên ngành'),
    ('STEM_AI',    N'STEM - AI',    N'Robotics, khoa học, trí tuệ nhân tạo'),
    ('KY_NANG_SO', N'Kĩ năng số',   N'Công dân số: an toàn mạng, thiết bị và công cụ số')
) AS v(Code, Name, Description)
WHERE NOT EXISTS (SELECT 1 FROM SubjectCategory sc WHERE sc.Code = v.Code);
GO

/* ---------------------------------------------------------------------
   BƯỚC 2 — Bảo đảm mỗi danh mục có ÍT NHẤT 1 môn (để Lesson.SubjectId trỏ vào)
   --------------------------------------------------------------------- */
INSERT INTO Subject (Code, Name, CategoryId, Description)
SELECT v.Code, v.Name, sc.Id, v.Description
FROM (VALUES
    ('TIN00',  N'Tin học tổng hợp',     'TIN_HOC',    N'Môn chung nhóm Tin học'),
    ('ANH00',  N'Tiếng Anh tổng hợp',   'TIENG_ANH',  N'Môn chung nhóm Tiếng Anh'),
    ('STEM00', N'STEM - AI tổng hợp',    'STEM_AI',    N'Môn chung nhóm STEM - AI'),
    ('KNSO00', N'Kĩ năng số',           'KY_NANG_SO', N'Môn chung nhóm Kĩ năng số')
) AS v(Code, Name, CatCode, Description)
JOIN SubjectCategory sc ON sc.Code = v.CatCode
WHERE NOT EXISTS (SELECT 1 FROM Subject s WHERE s.Code = v.Code);
GO

/* ---------------------------------------------------------------------
   BƯỚC 3 — Sinh 360 bài giảng bằng CROSS JOIN:
            (4 danh mục) × (lớp 1..9) × (10 chủ đề)
   Tiêu đề: "<Danh mục> - Lớp <n>: <Chủ đề>"  → duy nhất trong từng lớp.
   Độ khó : Lớp 1-3 = BASIC, 4-6 = INTERMEDIATE, 7-9 = ADVANCED.
   --------------------------------------------------------------------- */
;WITH
Grades AS (
    SELECT n FROM (VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9)) g(n)
),
Cats AS (
SELECT sc.Code AS CatCode, sc.Name AS CatName,
           (SELECT MIN(s.Id) FROM Subject s WHERE s.CategoryId = sc.Id) AS SubjectId
    FROM SubjectCategory sc
    WHERE sc.Code IN ('TIN_HOC','TIENG_ANH','STEM_AI','KY_NANG_SO')
),
Topics AS (
    SELECT * FROM (VALUES
        -- ===== Tin học =====
        ('TIN_HOC', 1,  N'Làm quen máy tính'),
        ('TIN_HOC', 2,  N'Sử dụng chuột và bàn phím'),
        ('TIN_HOC', 3,  N'Soạn thảo văn bản'),
        ('TIN_HOC', 4,  N'Vẽ trên máy tính'),
        ('TIN_HOC', 5,  N'Trình chiếu cơ bản'),
        ('TIN_HOC', 6,  N'Bảng tính cơ bản'),
        ('TIN_HOC', 7,  N'Internet an toàn'),
        ('TIN_HOC', 8,  N'Tư duy thuật toán'),
        ('TIN_HOC', 9,  N'Lập trình kéo thả'),
        ('TIN_HOC', 10, N'Dự án tin học'),
        -- ===== STEM - AI =====
        ('STEM_AI', 1,  N'Khám phá khoa học'),
        ('STEM_AI', 2,  N'Robot quanh ta'),
        ('STEM_AI', 3,  N'Lắp ráp mô hình'),
        ('STEM_AI', 4,  N'Cảm biến cơ bản'),
        ('STEM_AI', 5,  N'Trí tuệ nhân tạo là gì'),
        ('STEM_AI', 6,  N'Dữ liệu và thông tin'),
        ('STEM_AI', 7,  N'Lập trình điều khiển'),
        ('STEM_AI', 8,  N'Thí nghiệm STEM'),
        ('STEM_AI', 9,  N'Sáng tạo với AI'),
        ('STEM_AI', 10, N'Dự án STEM'),
        -- ===== Tiếng Anh =====
        ('TIENG_ANH', 1,  N'Bảng chữ cái và phát âm'),
        ('TIENG_ANH', 2,  N'Chào hỏi'),
        ('TIENG_ANH', 3,  N'Gia đình và bạn bè'),
        ('TIENG_ANH', 4,  N'Màu sắc và số đếm'),
        ('TIENG_ANH', 5,  N'Trường học'),
        ('TIENG_ANH', 6,  N'Động vật'),
        ('TIENG_ANH', 7,  N'Thức ăn và đồ uống'),
        ('TIENG_ANH', 8,  N'Thời tiết'),
        ('TIENG_ANH', 9,  N'Sở thích'),
        ('TIENG_ANH', 10, N'Kể chuyện đơn giản'),
        -- ===== Kĩ năng số =====
        ('KY_NANG_SO', 1,  N'An toàn trên mạng'),
        ('KY_NANG_SO', 2,  N'Thiết bị số'),
        ('KY_NANG_SO', 3,  N'Tìm kiếm thông tin'),
        ('KY_NANG_SO', 4,  N'Giao tiếp trực tuyến'),
        ('KY_NANG_SO', 5,  N'Bảo vệ thông tin cá nhân'),
        ('KY_NANG_SO', 6,  N'Ứng xử văn minh trên mạng'),
        ('KY_NANG_SO', 7,  N'Công cụ học tập số'),
        ('KY_NANG_SO', 8,  N'Sáng tạo nội dung số'),
        ('KY_NANG_SO', 9,  N'Nhận biết tin giả'),
        ('KY_NANG_SO', 10, N'Công dân số')
    ) AS t(CatCode, TopicNo, Topic)
)
INSERT INTO Lesson (SubjectId, BranchId, Title, Description, GradeLevel,
                    Duration, DifficultyLevel, Status)
SELECT
    c.SubjectId,
    b.Id,
    c.CatName + N' - Lớp ' + CAST(g.n AS nvarchar(2)) + N': ' + tp.Topic,
    N'Bài giảng ' + c.CatName + N' cho học sinh lớp ' + CAST(g.n AS nvarchar(2)),
    N'Lớp ' + CAST(g.n AS nvarchar(2)),
    45,
    CASE WHEN g.n <= 3 THEN 'BASIC'
         WHEN g.n <= 6 THEN 'INTERMEDIATE'
ELSE 'ADVANCED' END,
    'PUBLISHED'
FROM Cats c
JOIN Topics tp ON tp.CatCode = c.CatCode
CROSS JOIN Grades g
CROSS JOIN (SELECT Id FROM Branch WHERE Name = N'Chi nhánh trung tâm') b
WHERE c.SubjectId IS NOT NULL
  AND NOT EXISTS (
        SELECT 1 FROM Lesson l
        WHERE l.SubjectId = c.SubjectId
          AND l.Title = c.CatName + N' - Lớp ' + CAST(g.n AS nvarchar(2)) + N': ' + tp.Topic
  );
GO

/* ---------------------------------------------------------------------
   KIỂM TRA — số bài theo (Danh mục × Lớp), kỳ vọng: mỗi ô = 10
   --------------------------------------------------------------------- */
SELECT sc.Name AS DanhMuc, l.GradeLevel AS Lop, COUNT(*) AS SoBai
FROM SubjectCategory sc
JOIN Subject s ON s.CategoryId = sc.Id
JOIN Lesson  l ON l.SubjectId  = s.Id AND l.IsDeleted = 0
WHERE sc.Code IN ('TIN_HOC','TIENG_ANH','STEM_AI','KY_NANG_SO')
GROUP BY sc.Name, l.GradeLevel
ORDER BY sc.Name, l.GradeLevel;
GO
