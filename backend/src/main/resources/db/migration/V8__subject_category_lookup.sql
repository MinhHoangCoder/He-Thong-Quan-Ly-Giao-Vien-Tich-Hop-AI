/* =====================================================================
   V8 — Chuẩn hóa NHÓM MÔN: tách cột text Subject.Category -> bảng lookup
   ---------------------------------------------------------------------
   VẤN ĐỀ (trước V8):
     Subject.Category là NVARCHAR(50) text TỰ DO, KHÔNG có CHECK lẫn FK —
     khác mọi cột phân loại khác trong DB (Status/Type/Source... đều có CHECK).
     Giá trị chỉ nằm trong comment, DB không ép → dễ sai chính tả, không gom
     nhóm chuẩn, không quản lý nhóm qua UI.

   GIẢI PHÁP (cách "lookup + FK", giống tiền lệ Lesson đã bỏ Category text):
     1) Tạo bảng danh mục SubjectCategory (Code chuẩn + Name hiển thị).
     2) Seed 4 nhóm CHÍNH THỨC dùng chung với module Bài giảng (Lesson):
        Tin học · Tiếng Anh · STEM - AI · Kĩ năng sống.
     3) Thêm Subject.CategoryId (FK -> SubjectCategory) thay cho cột text.
     4) Backfill: map giá trị Category cũ sang nhóm chính thức rồi BỎ cột Category
        (STEM -> STEM_AI, CONG_DAN_SO -> KY_NANG_SONG; giá trị khác thử khớp
         trực tiếp theo Code, không khớp thì để NULL).

   GHI CHÚ:
     - Tách các bước bằng GO: SQL Server PHÂN TÍCH cả batch trước khi chạy, nên
       cột CategoryId phải tồn tại ở batch TRƯỚC mới UPDATE/DROP được ở batch sau.
     - PK đặt tên "Id" theo quy ước V7. Cột FK giữ prefix (CategoryId) để tự mô tả.
     - CategoryId để NULL-able như cột Category cũ (môn chưa gán nhóm vẫn hợp lệ).
   ===================================================================== */

/* ---------- 1) Bảng lookup SubjectCategory ---------- */
CREATE TABLE SubjectCategory (
    Id          INT IDENTITY PRIMARY KEY,
    Code        VARCHAR(50)   NOT NULL UNIQUE,      -- mã nhóm (khớp Enum Java): TIN_HOC | TIENG_ANH | STEM_AI | KY_NANG_SONG
    Name        NVARCHAR(100) NOT NULL,            -- tên hiển thị: Tin học | Tiếng Anh | STEM - AI | Kĩ năng sống
    Description NVARCHAR(500) NULL,
    Status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE / DISABLED(ngừng dùng)
                CONSTRAINT CK_SubjectCategory_Status CHECK (Status IN ('ACTIVE','DISABLED')),
    IsDeleted   BIT           NOT NULL DEFAULT 0,
    DeletedAt   DATETIME2(3)  NULL,
    DeletedBy   INT           NULL,
    CreatedAt   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CreatedBy   INT           NULL,
    UpdatedAt   DATETIME2(3)  NULL,
    UpdatedBy   INT           NULL
);
GO

/* ---------- 2) Seed 4 nhóm CHÍNH THỨC (dùng chung với module Bài giảng) ---------- */
INSERT INTO SubjectCategory (Code, Name, Description) VALUES
 ('TIN_HOC',      N'Tin học',      N'Tin học ứng dụng & lập trình'),
 ('TIENG_ANH',    N'Tiếng Anh',    N'Ngoại ngữ — Tiếng Anh'),
 ('STEM_AI',      N'STEM - AI',    N'STEM kết hợp Trí tuệ nhân tạo'),
 ('KY_NANG_SONG', N'Kĩ năng sống', N'Kĩ năng mềm & kĩ năng sống');
GO

/* ---------- 3) Thêm cột FK CategoryId vào Subject ---------- */
ALTER TABLE Subject ADD CategoryId INT NULL
    CONSTRAINT FK_Subject_Category REFERENCES SubjectCategory(Id);
GO

/* ---------- 4) Backfill: Category (text cũ) -> CategoryId (nhóm chính thức) ----------
   Map dữ liệu cũ về 4 nhóm chính thức. Giá trị không nằm trong bảng ánh xạ sẽ thử khớp
   trực tiếp theo Code; nếu vẫn không khớp thì CategoryId để NULL.                       */
UPDATE s
SET s.CategoryId = c.Id
FROM Subject s
JOIN SubjectCategory c
  ON c.Code = CASE s.Category
                  WHEN 'STEM'        THEN 'STEM_AI'        -- môn STEM cũ -> STEM - AI
                  WHEN 'CONG_DAN_SO' THEN 'KY_NANG_SONG'   -- Công dân số -> Kĩ năng sống
                  ELSE s.Category
              END;
GO

/* ---------- 5) Bỏ cột Category text cũ (không còn nguồn nào tham chiếu) ---------- */
ALTER TABLE Subject DROP COLUMN Category;
GO

/* ---------- 6) Index tra môn theo nhóm ---------- */
CREATE INDEX IX_Subject_Category ON Subject(CategoryId);
GO
