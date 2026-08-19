/* =====================================================================
   V29 — LỊCH NGHỈ (Holiday): ngày lễ & kỳ nghỉ mà KHÔNG sinh buổi dạy.

   Bối cảnh: generator lịch dạy (AssignmentService.generateSchedules) trải
   mỗi ô thời khóa biểu ra thành buổi dạy bằng vòng lặp "cộng thêm 1 tuần",
   KHÔNG hỏi ngày đó có phải ngày nghỉ không. Hệ quả dây chuyền:

     1) Phiếu phân công kéo dài một học kỳ luôn đẻ ra buổi rơi vào 30/4,
        2/9, Tết... — thời khóa biểu của giáo viên có buổi dạy vào ngày
        trường đóng cửa.
     2) Nặng hơn: AttendanceSweepService quét buổi đã qua mà không ai chấm
        rồi tự ghi VẮNG. Buổi "ma" ngày lễ vì thế thành một dòng vắng thật,
        kéo tụt tỷ lệ chuyên cần và TRỪ THẲNG VÀO LƯƠNG (PayrollService chỉ
        đếm buổi PRESENT/LATE). Không ai bấm nút nào sai cả.

   Vì sao là BẢNG chứ không phải hằng số trong code: ngày nghỉ đổi từng năm
   (Tết theo âm lịch, ngày liền kề 2/9 do Chính phủ chốt hằng năm) và mỗi
   trường còn có kỳ nghỉ riêng. Nhét vào Java thì mỗi năm phải build lại.

   PHẠM VI (cột SchoolId):
     - NULL  = áp dụng TOÀN HỆ THỐNG (lễ quốc gia, nghỉ hè chung).
     - có Id = chỉ trường đó nghỉ (sửa chữa trường, lịch riêng của trường).

   Lưu KHOẢNG (FromDate..ToDate) chứ không lưu từng ngày: một kỳ nghỉ hè là
   MỘT dòng đọc được bằng mắt, thay vì 60 dòng rời rạc. Ngày đơn thì
   FromDate = ToDate. Các khoảng ĐƯỢC PHÉP chồng nhau — một ngày là ngày
   nghỉ khi có BẤT KỲ dòng nào phủ nó, nên không cần ràng buộc chống trùng.
   ===================================================================== */

IF OBJECT_ID('dbo.Holiday', 'U') IS NULL
CREATE TABLE Holiday (
    Id        INT IDENTITY PRIMARY KEY,
    FromDate  DATE          NOT NULL,
    ToDate    DATE          NOT NULL,
    Name      NVARCHAR(150) NOT NULL,
    Kind      VARCHAR(20)   NOT NULL CONSTRAINT DF_Holiday_Kind DEFAULT 'NATIONAL'
              CONSTRAINT CK_Holiday_Kind CHECK (Kind IN ('NATIONAL', 'BREAK', 'CENTER')),
    SchoolId  INT           NULL,          -- NULL = toàn hệ thống
    Note      NVARCHAR(255) NULL,
    IsDeleted BIT           NOT NULL CONSTRAINT DF_Holiday_IsDeleted DEFAULT 0,
    DeletedAt DATETIME2(3)  NULL,
    DeletedBy INT           NULL,
    CreatedAt DATETIME2(3)  NOT NULL CONSTRAINT DF_Holiday_CreatedAt DEFAULT SYSUTCDATETIME(),
    CreatedBy INT           NULL,
    UpdatedAt DATETIME2(3)  NULL,
    UpdatedBy INT           NULL,
    CONSTRAINT CK_Holiday_Range CHECK (ToDate >= FromDate),
    CONSTRAINT FK_Holiday_School FOREIGN KEY (SchoolId) REFERENCES School(Id)
);
GO

/* Truy vấn duy nhất bảng này phục vụ: "trong [from..to] có ngày nghỉ nào?" */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Holiday_Range')
    CREATE INDEX IX_Holiday_Range ON Holiday(FromDate, ToDate) INCLUDE (SchoolId) WHERE IsDeleted = 0;
GO

/* ---------------------------------------------------------------------
   DỮ LIỆU NỀN — năm học 2025-2026 và 2026-2027.

   ĐÂY LÀ DANH MỤC NGHIỆP VỤ (như Role/Permission), KHÔNG phải data demo,
   nên nằm trong migration: thiếu nó thì generator sinh sai lịch ở MỌI môi
   trường, kể cả production.

   ⚠ HAI LOẠI DÒNG, ĐỘ TIN CẬY KHÁC HẲN NHAU:

   (a) NGÀY DƯƠNG LỊCH CỐ ĐỊNH — chắc chắn, ghi thẳng trong Bộ luật Lao
       động 2019 Điều 112: 1/1, 30/4, 1/5, 2/9.

   (b) NGÀY SUY TỪ ÂM LỊCH + NGÀY NGHỈ BÙ — CẦN RÀ SOÁT trước khi dùng
       thật. Tết Nguyên đán và Giỗ Tổ Hùng Vương quy đổi theo âm lịch; ngày
       nghỉ liền kề 2/9 và số ngày nghỉ Tết do Chính phủ công bố RIÊNG từng
       năm. Các dòng này đánh dấu '[CẦN RÀ SOÁT]' ở cột Note — đối chiếu
       thông báo chính thức rồi UPDATE lại, đừng tin số ở đây.

   Kỳ nghỉ hè / nghỉ Tết của HỌC SINH dài hơn ngày lễ của người lao động
   (Kind = 'BREAK'): trung tâm không vào trường dạy trong những ngày đó.
   --------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM Holiday)
INSERT INTO Holiday (FromDate, ToDate, Name, Kind, Note) VALUES
 /* --- (a) chắc chắn --- */
 ('2026-01-01', '2026-01-01', N'Tết Dương lịch',                    'NATIONAL', NULL),
 ('2026-04-30', '2026-04-30', N'Ngày Giải phóng miền Nam',          'NATIONAL', NULL),
 ('2026-05-01', '2026-05-01', N'Ngày Quốc tế Lao động',             'NATIONAL', NULL),
 ('2026-09-02', '2026-09-02', N'Quốc khánh',                        'NATIONAL', NULL),
 ('2027-01-01', '2027-01-01', N'Tết Dương lịch',                    'NATIONAL', NULL),
 ('2027-04-30', '2027-04-30', N'Ngày Giải phóng miền Nam',          'NATIONAL', NULL),
 ('2027-05-01', '2027-05-01', N'Ngày Quốc tế Lao động',             'NATIONAL', NULL),

 /* --- (b) suy từ âm lịch / do Chính phủ chốt hằng năm --- */
 ('2026-02-17', '2026-02-17', N'Tết Nguyên đán Bính Ngọ (mùng 1)',  'NATIONAL',
  N'[CẦN RÀ SOÁT] Mùng 1 quy đổi từ âm lịch.'),
 ('2026-04-26', '2026-04-26', N'Giỗ Tổ Hùng Vương (10/3 âm lịch)',  'NATIONAL',
  N'[CẦN RÀ SOÁT] Quy đổi từ âm lịch, có thể lệch 1 ngày.'),
 ('2026-09-03', '2026-09-03', N'Nghỉ liền kề Quốc khánh',           'NATIONAL',
  N'[CẦN RÀ SOÁT] Luật cho nghỉ 2/9 + 1 ngày liền trước HOẶC liền sau; Chính phủ chốt từng năm.'),
 ('2027-02-06', '2027-02-06', N'Tết Nguyên đán Đinh Mùi (mùng 1)',  'NATIONAL',
  N'[CẦN RÀ SOÁT] Mùng 1 quy đổi từ âm lịch.'),

 /* --- kỳ nghỉ của học sinh (trung tâm không vào trường) --- */
 ('2026-02-12', '2026-02-22', N'Nghỉ Tết Nguyên đán Bính Ngọ',      'BREAK',
  N'[CẦN RÀ SOÁT] Độ dài nghỉ Tết của học sinh do Sở GD&ĐT quyết định từng năm.'),
 ('2026-06-01', '2026-07-31', N'Nghỉ hè 2026',                      'BREAK',
  N'[CẦN RÀ SOÁT] Mốc kết thúc năm học và tựu trường do Sở GD&ĐT công bố.'),
 ('2027-01-30', '2027-02-09', N'Nghỉ Tết Nguyên đán Đinh Mùi',      'BREAK',
  N'[CẦN RÀ SOÁT] Độ dài nghỉ Tết của học sinh do Sở GD&ĐT quyết định từng năm.'),
 ('2027-06-01', '2027-07-31', N'Nghỉ hè 2027',                      'BREAK',
  N'[CẦN RÀ SOÁT] Mốc kết thúc năm học và tựu trường do Sở GD&ĐT công bố.');
GO
