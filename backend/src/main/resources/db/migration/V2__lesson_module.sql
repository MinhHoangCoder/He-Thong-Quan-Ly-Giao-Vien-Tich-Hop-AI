/* =====================================================================
   TSDMS - V2: MODULE BÀI GIẢNG (LESSON MANAGEMENT)
   ---------------------------------------------------------------------
   Thêm 2 bảng:
     Lesson     (Bảng 28) : Bài giảng do nhân viên trung tâm quản lý
     LessonFile (Bảng 29) : File đính kèm của bài giảng (nhiều-1)

   LƯU Ý FLYWAY (quy tắc làm việc nhóm):
     - KHÔNG BAO GIỜ sửa file migration đã chạy (V1...) — Flyway lưu checksum
       từng file trong bảng flyway_schema_history; sửa file cũ sẽ làm app
       của mọi thành viên lỗi "Migration checksum mismatch" khi khởi động.
     - Muốn đổi schema → luôn tạo file V<số tiếp theo>__<mô tả>.sql mới.
     - File này tự áp dụng khi chạy backend (spring.flyway.enabled=true).

   VÌ SAO CÓ "IF OBJECT_ID(...) IS NULL"?
     Nhóm có 2 cách dựng DB: (a) chạy tay database/schema/TSDMS_Schema.sql
     trong SSMS (file đó ĐÃ chứa 2 bảng này), (b) để Flyway tự chạy V1+V2.
     DB dựng theo cách (a) khi khởi động backend sẽ được Flyway baseline ở
     version 1 rồi chạy V2 → nếu không có chốt chặn sẽ lỗi "bảng đã tồn tại".
   ===================================================================== */

/* ========== Bảng 28: Lesson — BÀI GIẢNG ==========
   Ý nghĩa : Nhân viên trung tâm tạo/sửa/xóa bài giảng, gắn với môn học,
             chi nhánh và (tùy chọn) giáo viên phụ trách soạn bài.
             Trường và giáo viên chỉ được XEM bài giảng đã PUBLISHED.
   QUAN HỆ : SubjectId → Subject (bài giảng thuộc môn nào),
             TeacherId → Teacher (GV phụ trách soạn, có thể NULL),
             BranchId  → Branch  (chi nhánh quản lý).
             Một bài giảng có nhiều LessonFile (file đính kèm).             */
IF OBJECT_ID(N'dbo.Lesson', N'U') IS NULL
BEGIN
CREATE TABLE Lesson (
    LessonId        INT IDENTITY PRIMARY KEY,
    SubjectId       INT            NOT NULL,          -- → Subject
    TeacherId       INT            NULL,              -- → Teacher (GV phụ trách, nếu có)
    BranchId        INT            NOT NULL,          -- → Branch (chi nhánh quản lý)
    Title           NVARCHAR(300)  NOT NULL,          -- tiêu đề bài giảng
    Description     NVARCHAR(2000) NULL,              -- mô tả / tóm tắt
    Content         NVARCHAR(MAX)  NULL,              -- nội dung chi tiết (rich text / markdown)
    GradeLevel      NVARCHAR(50)   NULL,              -- khối lớp áp dụng (vd: Lớp 6, Lớp 10)
    Duration        INT            NULL               -- thời lượng dạy dự kiến (PHÚT)
                    CONSTRAINT CK_Lesson_Duration CHECK (Duration IS NULL OR Duration > 0),
    DifficultyLevel VARCHAR(20)    NULL               -- BASIC / INTERMEDIATE / ADVANCED
                    CONSTRAINT CK_Lesson_Difficulty CHECK (DifficultyLevel IN ('BASIC','INTERMEDIATE','ADVANCED')),
    Status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT'  -- DRAFT(nháp) / PUBLISHED(đã đăng) / ARCHIVED(lưu kho)
                    CONSTRAINT CK_Lesson_Status CHECK (Status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    IsDeleted       BIT            NOT NULL DEFAULT 0,
    DeletedAt       DATETIME2(3)   NULL,
    DeletedBy       INT            NULL,
    CreatedAt       DATETIME2(3)   NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy       INT            NULL,
    UpdatedAt       DATETIME2(3)   NULL,
    UpdatedBy       INT            NULL,
    FOREIGN KEY (SubjectId) REFERENCES Subject(SubjectId),
    FOREIGN KEY (TeacherId) REFERENCES Teacher(TeacherId),
    FOREIGN KEY (BranchId)  REFERENCES Branch(BranchId)
);
CREATE INDEX IX_Lesson_Subject ON Lesson(SubjectId);   -- lọc bài giảng theo môn
CREATE INDEX IX_Lesson_Teacher ON Lesson(TeacherId);   -- lọc theo GV phụ trách
-- Gộp (BranchId, Status) vào MỘT index: phục vụ cả "lọc theo chi nhánh"
-- lẫn "lọc theo chi nhánh + trạng thái" (màn danh sách bài giảng hay dùng nhất).
CREATE INDEX IX_Lesson_Branch_Status ON Lesson(BranchId, Status) WHERE IsDeleted = 0;
CREATE INDEX IX_Lesson_Title ON Lesson(Title) WHERE IsDeleted = 0;  -- tìm theo tiêu đề
END

/* ========== Bảng 29: LessonFile — FILE ĐÍNH KÈM của bài giảng ==========
   Ý nghĩa : Một bài giảng có thể có nhiều file (PDF, PPTX, link video...).
   QUAN HỆ : LessonId → Lesson (nhiều-1).                                   */
IF OBJECT_ID(N'dbo.LessonFile', N'U') IS NULL
BEGIN
CREATE TABLE LessonFile (
    LessonFileId INT IDENTITY PRIMARY KEY,
    LessonId     INT           NOT NULL,              -- → Lesson
    FileName     NVARCHAR(255) NOT NULL,              -- tên file hiển thị
    FileUrl      VARCHAR(500)  NOT NULL,              -- đường dẫn / URL lưu trữ
    FileType     VARCHAR(50)   NULL,                  -- pdf / pptx / mp4 / link ...
    FileSizeKb   INT           NULL                   -- kích thước (KB)
                 CONSTRAINT CK_LessonFile_Size CHECK (FileSizeKb IS NULL OR FileSizeKb > 0),
    IsDeleted    BIT           NOT NULL DEFAULT 0,
    DeletedAt    DATETIME2(3)  NULL,
    DeletedBy    INT           NULL,
    CreatedAt    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy    INT           NULL,
    UpdatedAt    DATETIME2(3)  NULL,
    UpdatedBy    INT           NULL,
    FOREIGN KEY (LessonId) REFERENCES Lesson(LessonId)
);
CREATE INDEX IX_LessonFile_Lesson ON LessonFile(LessonId) WHERE IsDeleted = 0;
END
