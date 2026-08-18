/* =====================================================================
   TSDMS — SEED 30 TRƯỜNG TH/THCS CÔNG LẬP (HẢI PHÒNG)
   ---------------------------------------------------------------------
   CHẠY THẾ NÀO
     Mở trong SSMS (đã kết nối DB TSDMS) rồi Execute. KHÔNG đưa vào Flyway —
     đây là dữ liệu demo, đúng quy ước ở đầu database/seed/TSDMS_Seed_Demo.sql.
     File tự đặt SET QUOTED_IDENTIFIER ON nên chạy bằng sqlcmd cũng được,
     không cần cờ -I. Chạy lại lần hai sẽ tự bỏ qua.

   FILE NÀY LÀM GÌ
     1) Đưa bộ đếm Id của School/Period/SchoolClass về đầu (chỉ khi bảng rỗng).
     2) Thêm 30 trường: 15 tiểu học + 15 THCS,
        thuộc 5 phường Hồng Bàng · Lê Chân · Ngô Quyền · Hải An · Kiến An.
     3) Tạo KHUNG TIẾT cho TỪNG trường theo đúng mẫu đã chuẩn hóa ở V22
        (tiểu học 10 tiết/ngày, tiết 35 phút; THCS 9 tiết/ngày, tiết 45 phút).
        Không có khung tiết thì trường đó KHÔNG phân công được — lỗi này dự án
        đã từng dính với trường TH Nguyễn Công Hòa.
     4) Tạo LỚP cho các trường đang hoạt động (18 trường): TH khối 1-5,
        THCS khối 6-9, mỗi khối 2-3 lớp, năm học 2026-2027. Tên lớp theo quy
        ước V15 (TH: 1A1, 2A1...; THCS: 6D1, 7C1, 8B1, 9A1).

   ⚠ TÊN TRƯỜNG LÀ DỮ LIỆU CẦN RÀ SOÁT
     Đây là các trường học CÓ THẬT, không phải dữ liệu bịa như hồ sơ giáo viên.
     Danh sách dựng theo quy ước đặt tên phổ biến ("Trường Tiểu học <tên phường>")
     và theo hiểu biết chung, KHÔNG phải trích từ danh bạ chính thức của Sở
     GD&ĐT Hải Phòng. Vì vậy:
       - Danh sách KHÔNG chắc đã đầy đủ ("toàn bộ" trường trong 5 phường).
       - Một vài trường có thể sai phường, hoặc không còn tồn tại sau sáp nhập.
     Bảng đối chiếu từng dòng: database/seed/TSDMS_TruongHaiPhong_CanRaSoat.md
     Đối chiếu với danh bạ của Phòng GD&ĐT rồi sửa lại trước khi dùng thật.

   CÁC Ô CỐ TÌNH ĐỂ TRỐNG
     - Phone / Email: KHÔNG bịa số điện thoại cho trường học có thật — ai đó
       bấm gọi là làm phiền người ngoài.
     - AppUserId: không trường nào có tài khoản đăng nhập (khách hàng chốt).
     - Room: không tạo phòng học; xếp lịch không bắt buộc có phòng.
     - ContactPerson: tên người Việt GIẢ, KHÔNG phải hiệu trưởng thật.

   GỠ RA: chạy database/seed/TSDMS_Rollback_TruongHaiPhong.sql
   ===================================================================== */

USE TSDMS;
GO

/* Bật QUOTED_IDENTIFIER: School/Room/Period/SchoolClass đều có filtered index
   (UX_Class_School_Name_Year, UX_Period_School_Number...), SQL Server từ chối
   INSERT lên bảng có filtered index khi tùy chọn này tắt (lỗi 1934). SSMS bật
   sẵn, sqlcmd thì không. */
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---- CHỐT CHẶN: đã nạp rồi thì thoát ---- */
IF EXISTS (SELECT 1 FROM School WHERE Name = N'TH Quán Toan' AND IsDeleted = 0)
BEGIN
    PRINT N'>>> Bộ trường Hải Phòng đã được nạp trước đó — bỏ qua, không chạy lại.';
    RETURN;
END

BEGIN TRY
BEGIN TRANSACTION;

DECLARE @Admin  INT = (SELECT Id FROM AppUser WHERE Username = 'admin' AND IsDeleted = 0);
DECLARE @Branch INT = (SELECT Id FROM Branch  WHERE Name = N'Chi nhánh trung tâm' AND IsDeleted = 0);

IF @Branch IS NULL
    THROW 50001, N'Không tìm thấy chi nhánh "Chi nhánh trung tâm" — kiểm tra lại DB trước khi seed.', 1;


/* =====================================================================
   1) ĐƯA BỘ ĐẾM ID VỀ ĐẦU (chỉ khi bảng đang rỗng)
   ---------------------------------------------------------------------
   Bộ đếm IDENTITY chỉ tiến, không lùi khi xóa dòng. Nạp rồi gỡ vài lần là
   trường đầu tiên mang Id kiểu 149 thay vì 1. Bảng còn dòng thì bỏ qua —
   reseed khi đang có dữ liệu sẽ đâm trùng khóa chính.
   ===================================================================== */
IF NOT EXISTS (SELECT 1 FROM School)      DBCC CHECKIDENT('School',      RESEED, 0) WITH NO_INFOMSGS;
IF NOT EXISTS (SELECT 1 FROM Period)      DBCC CHECKIDENT('Period',      RESEED, 0) WITH NO_INFOMSGS;
IF NOT EXISTS (SELECT 1 FROM SchoolClass) DBCC CHECKIDENT('SchoolClass', RESEED, 0) WITH NO_INFOMSGS;


/* =====================================================================
   2) TRƯỜNG
   ---------------------------------------------------------------------
   Nạp vào bảng tạm rồi INSERT sang bảng thật bằng JOIN theo TÊN — giữ quy
   tắc "không hard-code Id tự tăng".

   COLLATE DATABASE_DEFAULT: bảng tạm nằm ở tempdb (collation của server),
   DB TSDMS dùng Vietnamese_CI_AS — thiếu là JOIN theo tên nổ lỗi 468.

   Cột Lvl ('TH'/'THCS') KHÔNG có trong bảng School; nó chỉ dùng trong file
   này để chọn khung tiết và khối lớp cho đúng cấp.
   ===================================================================== */
CREATE TABLE #Truong (
    Name    NVARCHAR(200) COLLATE DATABASE_DEFAULT PRIMARY KEY,
    Lvl     VARCHAR(10)   COLLATE DATABASE_DEFAULT NOT NULL,  -- TH / THCS
    Addr    NVARCHAR(255) COLLATE DATABASE_DEFAULT,
    Contact NVARCHAR(150) COLLATE DATABASE_DEFAULT,
    Status  VARCHAR(20)   COLLATE DATABASE_DEFAULT,           -- ACTIVE / INACTIVE / EXPIRED
    CStart  DATE, CEnd DATE
);

INSERT INTO #Truong (Name, Lvl, Addr, Contact, Status, CStart, CEnd) VALUES
 (N'TH Quán Toan', 'TH', N'phường Hồng Bàng, Hải Phòng', N'Lê Thị Nga', 'EXPIRED', '2023-06-28', '2026-04-11'),
 (N'TH Hùng Vương', 'TH', N'phường Hồng Bàng, Hải Phòng', N'Hoàng Thị Hạnh', 'INACTIVE', NULL, NULL),
 (N'TH Thượng Lý', 'TH', N'phường Hồng Bàng, Hải Phòng', N'Ngô Thị Thủy', 'INACTIVE', NULL, NULL),
 (N'THCS Quán Toan', 'THCS', N'phường Hồng Bàng, Hải Phòng', N'Phạm Thị Nga', 'ACTIVE', '2023-09-14', '2027-11-21'),
 (N'THCS Hùng Vương', 'THCS', N'phường Hồng Bàng, Hải Phòng', N'Nguyễn Văn Long', 'ACTIVE', '2023-08-27', '2029-12-06'),
 (N'THCS Thượng Lý', 'THCS', N'phường Hồng Bàng, Hải Phòng', N'Lê Thị Nga', 'ACTIVE', '2026-05-28', '2028-08-25'),
 (N'TH Dư Hàng', 'TH', N'phường Lê Chân, Hải Phòng', N'Lê Minh Trung', 'ACTIVE', '2023-10-19', '2028-07-24'),
 (N'TH Lê Văn Tám', 'TH', N'phường Lê Chân, Hải Phòng', N'Đỗ Thị Vân', 'EXPIRED', '2021-12-03', '2025-08-01'),
 (N'TH Vĩnh Niệm', 'TH', N'phường Lê Chân, Hải Phòng', N'Phạm Thị Hà', 'INACTIVE', NULL, NULL),
 (N'THCS Dư Hàng Kênh', 'THCS', N'phường Lê Chân, Hải Phòng', N'Phạm Thị Lan', 'INACTIVE', NULL, NULL),
 (N'THCS Vĩnh Niệm', 'THCS', N'phường Lê Chân, Hải Phòng', N'Phạm Thị Hà', 'ACTIVE', '2025-01-06', '2029-08-25'),
 (N'THCS Nghĩa Xá', 'THCS', N'phường Lê Chân, Hải Phòng', N'Trần Đức Sơn', 'INACTIVE', NULL, NULL),
 (N'TH Lạc Viên', 'TH', N'phường Ngô Quyền, Hải Phòng', N'Trần Thị Thủy', 'EXPIRED', '2021-06-22', '2024-02-08'),
 (N'TH Đông Khê', 'TH', N'phường Ngô Quyền, Hải Phòng', N'Nguyễn Thị Nhung', 'ACTIVE', '2024-02-09', '2027-06-17'),
 (N'TH Đằng Giang', 'TH', N'phường Ngô Quyền, Hải Phòng', N'Đỗ Thị Nga', 'ACTIVE', '2023-07-20', '2028-12-22'),
 (N'THCS Chu Văn An', 'THCS', N'phường Ngô Quyền, Hải Phòng', N'Phạm Thị Nga', 'ACTIVE', '2024-03-08', '2029-11-08'),
 (N'THCS Lạc Viên', 'THCS', N'phường Ngô Quyền, Hải Phòng', N'Hoàng Thị Trang', 'ACTIVE', '2025-07-07', '2029-10-23'),
 (N'THCS Đông Khê', 'THCS', N'phường Ngô Quyền, Hải Phòng', N'Ngô Quốc Kiên', 'EXPIRED', '2021-02-15', '2026-05-05'),
 (N'TH Đằng Hải', 'TH', N'phường Hải An, Hải Phòng', N'Đỗ Thị Hạnh', 'INACTIVE', NULL, NULL),
 (N'TH Cát Bi', 'TH', N'phường Hải An, Hải Phòng', N'Nguyễn Văn Long', 'ACTIVE', '2025-02-03', '2028-07-20'),
 (N'TH Tràng Cát', 'TH', N'phường Hải An, Hải Phòng', N'Đỗ Thanh Tuấn', 'ACTIVE', '2026-03-25', '2028-11-27'),
 (N'THCS Đằng Hải', 'THCS', N'phường Hải An, Hải Phòng', N'Hoàng Hữu Thắng', 'ACTIVE', '2023-08-09', '2027-05-25'),
 (N'THCS Cát Bi', 'THCS', N'phường Hải An, Hải Phòng', N'Đặng Tiến Nam', 'INACTIVE', NULL, NULL),
 (N'THCS Nam Hải', 'THCS', N'phường Hải An, Hải Phòng', N'Trần Thị Hương', 'ACTIVE', '2023-12-19', '2027-11-03'),
 (N'TH Quán Trữ', 'TH', N'phường Kiến An, Hải Phòng', N'Đặng Tiến Hải', 'ACTIVE', '2026-06-21', '2029-01-26'),
 (N'TH Đồng Hòa', 'TH', N'phường Kiến An, Hải Phòng', N'Bùi Thị Oanh', 'ACTIVE', '2026-02-18', '2027-08-04'),
 (N'TH Nguyễn Công Hòa', 'TH', N'phường Kiến An, Hải Phòng', N'Ngô Thị Yến', 'ACTIVE', '2025-09-21', '2028-09-01'),
 (N'THCS Quán Trữ', 'THCS', N'phường Kiến An, Hải Phòng', N'Vũ Thị Hạnh', 'INACTIVE', NULL, NULL),
 (N'THCS Đồng Hòa', 'THCS', N'phường Kiến An, Hải Phòng', N'Nguyễn Thị Nga', 'ACTIVE', '2025-11-19', '2029-02-10'),
 (N'THCS Trần Thành Ngọ', 'THCS', N'phường Kiến An, Hải Phòng', N'Trần Thị Hương', 'ACTIVE', '2026-03-01', '2028-10-12');

INSERT INTO School (BranchId, Name, Address, ContactPerson, Status, ContractStartDate, ContractEndDate, CreatedBy)
SELECT @Branch, t.Name, t.Addr, t.Contact, t.Status, t.CStart, t.CEnd, @Admin
FROM #Truong t
WHERE NOT EXISTS (SELECT 1 FROM School s WHERE s.Name = t.Name AND s.IsDeleted = 0);

PRINT N'2) Đã thêm ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' trường.';


/* =====================================================================
   3) KHUNG TIẾT — sao y mẫu đã chuẩn hóa ở V22
   ---------------------------------------------------------------------
   Tiểu học : tiết 35 phút, ra chơi 15 phút sau tiết 2 mỗi buổi, 10 tiết/ngày.
   THCS     : tiết 45 phút, ra chơi 10 phút sau tiết 2 mỗi buổi, 9 tiết/ngày.
   Giờ ra chơi thể hiện bằng KHOẢNG TRỐNG giữa EndTime tiết trước và StartTime
   tiết sau, không phải một dòng riêng.
   ===================================================================== */
CREATE TABLE #KhungTiet (
    Lvl VARCHAR(10) COLLATE DATABASE_DEFAULT,
    PeriodNumber TINYINT,
    SessionType  VARCHAR(10) COLLATE DATABASE_DEFAULT,
    StartTime TIME(0), EndTime TIME(0)
);

INSERT INTO #KhungTiet (Lvl, PeriodNumber, SessionType, StartTime, EndTime) VALUES
 ('TH', 1, 'MORNING', '07:00', '07:35'),
 ('TH', 2, 'MORNING', '07:35', '08:10'),
 ('TH', 3, 'MORNING', '08:25', '09:00'),
 ('TH', 4, 'MORNING', '09:00', '09:35'),
 ('TH', 5, 'MORNING', '09:35', '10:10'),
 ('TH', 6, 'AFTERNOON', '14:00', '14:35'),
 ('TH', 7, 'AFTERNOON', '14:35', '15:10'),
 ('TH', 8, 'AFTERNOON', '15:25', '16:00'),
 ('TH', 9, 'AFTERNOON', '16:00', '16:35'),
 ('TH', 10, 'AFTERNOON', '16:35', '17:10'),
 ('THCS', 1, 'MORNING', '07:00', '07:45'),
 ('THCS', 2, 'MORNING', '07:45', '08:30'),
 ('THCS', 3, 'MORNING', '08:40', '09:25'),
 ('THCS', 4, 'MORNING', '09:25', '10:10'),
 ('THCS', 5, 'MORNING', '10:10', '10:55'),
 ('THCS', 6, 'AFTERNOON', '14:00', '14:45'),
 ('THCS', 7, 'AFTERNOON', '14:45', '15:30'),
 ('THCS', 8, 'AFTERNOON', '15:40', '16:25'),
 ('THCS', 9, 'AFTERNOON', '16:25', '17:10');

INSERT INTO Period (SchoolId, PeriodNumber, SessionType, StartTime, EndTime, CreatedBy)
SELECT s.Id, k.PeriodNumber, k.SessionType, k.StartTime, k.EndTime, @Admin
FROM #Truong t
JOIN School     s ON s.Name = t.Name AND s.IsDeleted = 0
JOIN #KhungTiet k ON k.Lvl  = t.Lvl
WHERE NOT EXISTS (
    SELECT 1 FROM Period p
    WHERE p.SchoolId = s.Id AND p.PeriodNumber = k.PeriodNumber AND p.IsDeleted = 0
);

PRINT N'3) Đã tạo ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' tiết trong khung tiết.';


/* =====================================================================
   4) LỚP HỌC — chỉ cho trường đang hoạt động
   ---------------------------------------------------------------------
   GradeLevel lưu ĐÚNG MỘT chữ số '1'..'9' (ràng buộc V26). Bảng lương suy
   đơn giá từ đây: khối 1-5 = tiểu học 115.000đ/tiết, khối 6-9 = THCS
   125.000đ/tiết — ghi sai khối là sai tiền lương mà không có lỗi nào bắn ra.
   ===================================================================== */
CREATE TABLE #Lop (
    TruongName NVARCHAR(200) COLLATE DATABASE_DEFAULT,
    TenLop     NVARCHAR(100) COLLATE DATABASE_DEFAULT,
    Khoi       NVARCHAR(50)  COLLATE DATABASE_DEFAULT
);

INSERT INTO #Lop (TruongName, TenLop, Khoi) VALUES
 (N'THCS Quán Toan', N'6D1', N'6'),
 (N'THCS Quán Toan', N'6D2', N'6'),
 (N'THCS Quán Toan', N'7C1', N'7'),
 (N'THCS Quán Toan', N'7C2', N'7'),
 (N'THCS Quán Toan', N'8B1', N'8'),
 (N'THCS Quán Toan', N'8B2', N'8'),
 (N'THCS Quán Toan', N'9A1', N'9'),
 (N'THCS Quán Toan', N'9A2', N'9'),
 (N'THCS Hùng Vương', N'6D1', N'6'),
 (N'THCS Hùng Vương', N'6D2', N'6'),
 (N'THCS Hùng Vương', N'6D3', N'6'),
 (N'THCS Hùng Vương', N'7C1', N'7'),
 (N'THCS Hùng Vương', N'7C2', N'7'),
 (N'THCS Hùng Vương', N'7C3', N'7'),
 (N'THCS Hùng Vương', N'8B1', N'8'),
 (N'THCS Hùng Vương', N'8B2', N'8'),
 (N'THCS Hùng Vương', N'8B3', N'8'),
 (N'THCS Hùng Vương', N'9A1', N'9'),
 (N'THCS Hùng Vương', N'9A2', N'9'),
 (N'THCS Thượng Lý', N'6D1', N'6'),
 (N'THCS Thượng Lý', N'6D2', N'6'),
 (N'THCS Thượng Lý', N'7C1', N'7'),
 (N'THCS Thượng Lý', N'7C2', N'7'),
 (N'THCS Thượng Lý', N'8B1', N'8'),
 (N'THCS Thượng Lý', N'8B2', N'8'),
 (N'THCS Thượng Lý', N'9A1', N'9'),
 (N'THCS Thượng Lý', N'9A2', N'9'),
 (N'THCS Thượng Lý', N'9A3', N'9'),
 (N'TH Dư Hàng', N'1A1', N'1'),
 (N'TH Dư Hàng', N'1A2', N'1'),
 (N'TH Dư Hàng', N'1A3', N'1'),
 (N'TH Dư Hàng', N'2A1', N'2'),
 (N'TH Dư Hàng', N'2A2', N'2'),
 (N'TH Dư Hàng', N'2A3', N'2'),
 (N'TH Dư Hàng', N'3A1', N'3'),
 (N'TH Dư Hàng', N'3A2', N'3'),
 (N'TH Dư Hàng', N'4A1', N'4'),
 (N'TH Dư Hàng', N'4A2', N'4'),
 (N'TH Dư Hàng', N'5A1', N'5'),
 (N'TH Dư Hàng', N'5A2', N'5'),
 (N'TH Dư Hàng', N'5A3', N'5'),
 (N'THCS Vĩnh Niệm', N'6D1', N'6'),
 (N'THCS Vĩnh Niệm', N'6D2', N'6'),
 (N'THCS Vĩnh Niệm', N'6D3', N'6'),
 (N'THCS Vĩnh Niệm', N'7C1', N'7'),
 (N'THCS Vĩnh Niệm', N'7C2', N'7'),
 (N'THCS Vĩnh Niệm', N'8B1', N'8'),
 (N'THCS Vĩnh Niệm', N'8B2', N'8'),
 (N'THCS Vĩnh Niệm', N'8B3', N'8'),
 (N'THCS Vĩnh Niệm', N'9A1', N'9'),
 (N'THCS Vĩnh Niệm', N'9A2', N'9'),
 (N'THCS Vĩnh Niệm', N'9A3', N'9'),
 (N'TH Đông Khê', N'1A1', N'1'),
 (N'TH Đông Khê', N'1A2', N'1'),
 (N'TH Đông Khê', N'2A1', N'2'),
 (N'TH Đông Khê', N'2A2', N'2'),
 (N'TH Đông Khê', N'2A3', N'2'),
 (N'TH Đông Khê', N'3A1', N'3'),
 (N'TH Đông Khê', N'3A2', N'3'),
 (N'TH Đông Khê', N'4A1', N'4'),
 (N'TH Đông Khê', N'4A2', N'4'),
 (N'TH Đông Khê', N'4A3', N'4'),
 (N'TH Đông Khê', N'5A1', N'5'),
 (N'TH Đông Khê', N'5A2', N'5'),
 (N'TH Đông Khê', N'5A3', N'5'),
 (N'TH Đằng Giang', N'1A1', N'1'),
 (N'TH Đằng Giang', N'1A2', N'1'),
 (N'TH Đằng Giang', N'2A1', N'2'),
 (N'TH Đằng Giang', N'2A2', N'2'),
 (N'TH Đằng Giang', N'3A1', N'3'),
 (N'TH Đằng Giang', N'3A2', N'3'),
 (N'TH Đằng Giang', N'4A1', N'4'),
 (N'TH Đằng Giang', N'4A2', N'4'),
 (N'TH Đằng Giang', N'4A3', N'4'),
 (N'TH Đằng Giang', N'5A1', N'5'),
 (N'TH Đằng Giang', N'5A2', N'5'),
 (N'THCS Chu Văn An', N'6D1', N'6'),
 (N'THCS Chu Văn An', N'6D2', N'6'),
 (N'THCS Chu Văn An', N'6D3', N'6'),
 (N'THCS Chu Văn An', N'7C1', N'7'),
 (N'THCS Chu Văn An', N'7C2', N'7'),
 (N'THCS Chu Văn An', N'8B1', N'8'),
 (N'THCS Chu Văn An', N'8B2', N'8'),
 (N'THCS Chu Văn An', N'8B3', N'8'),
 (N'THCS Chu Văn An', N'9A1', N'9'),
 (N'THCS Chu Văn An', N'9A2', N'9'),
 (N'THCS Chu Văn An', N'9A3', N'9'),
 (N'THCS Lạc Viên', N'6D1', N'6'),
 (N'THCS Lạc Viên', N'6D2', N'6'),
 (N'THCS Lạc Viên', N'7C1', N'7'),
 (N'THCS Lạc Viên', N'7C2', N'7'),
 (N'THCS Lạc Viên', N'8B1', N'8'),
 (N'THCS Lạc Viên', N'8B2', N'8'),
 (N'THCS Lạc Viên', N'9A1', N'9'),
 (N'THCS Lạc Viên', N'9A2', N'9'),
 (N'THCS Lạc Viên', N'9A3', N'9'),
 (N'TH Cát Bi', N'1A1', N'1'),
 (N'TH Cát Bi', N'1A2', N'1'),
 (N'TH Cát Bi', N'2A1', N'2'),
 (N'TH Cát Bi', N'2A2', N'2'),
 (N'TH Cát Bi', N'3A1', N'3'),
 (N'TH Cát Bi', N'3A2', N'3'),
 (N'TH Cát Bi', N'3A3', N'3'),
 (N'TH Cát Bi', N'4A1', N'4'),
 (N'TH Cát Bi', N'4A2', N'4'),
 (N'TH Cát Bi', N'5A1', N'5'),
 (N'TH Cát Bi', N'5A2', N'5'),
 (N'TH Cát Bi', N'5A3', N'5'),
 (N'TH Tràng Cát', N'1A1', N'1'),
 (N'TH Tràng Cát', N'1A2', N'1'),
 (N'TH Tràng Cát', N'2A1', N'2'),
 (N'TH Tràng Cát', N'2A2', N'2'),
 (N'TH Tràng Cát', N'2A3', N'2'),
 (N'TH Tràng Cát', N'3A1', N'3'),
 (N'TH Tràng Cát', N'3A2', N'3'),
 (N'TH Tràng Cát', N'3A3', N'3'),
 (N'TH Tràng Cát', N'4A1', N'4'),
 (N'TH Tràng Cát', N'4A2', N'4'),
 (N'TH Tràng Cát', N'5A1', N'5'),
 (N'TH Tràng Cát', N'5A2', N'5'),
 (N'THCS Đằng Hải', N'6D1', N'6'),
 (N'THCS Đằng Hải', N'6D2', N'6'),
 (N'THCS Đằng Hải', N'7C1', N'7'),
 (N'THCS Đằng Hải', N'7C2', N'7'),
 (N'THCS Đằng Hải', N'7C3', N'7'),
 (N'THCS Đằng Hải', N'8B1', N'8'),
 (N'THCS Đằng Hải', N'8B2', N'8'),
 (N'THCS Đằng Hải', N'9A1', N'9'),
 (N'THCS Đằng Hải', N'9A2', N'9'),
 (N'THCS Nam Hải', N'6D1', N'6'),
 (N'THCS Nam Hải', N'6D2', N'6'),
 (N'THCS Nam Hải', N'6D3', N'6'),
 (N'THCS Nam Hải', N'7C1', N'7'),
 (N'THCS Nam Hải', N'7C2', N'7'),
 (N'THCS Nam Hải', N'8B1', N'8'),
 (N'THCS Nam Hải', N'8B2', N'8'),
 (N'THCS Nam Hải', N'8B3', N'8'),
 (N'THCS Nam Hải', N'9A1', N'9'),
 (N'THCS Nam Hải', N'9A2', N'9'),
 (N'THCS Nam Hải', N'9A3', N'9'),
 (N'TH Quán Trữ', N'1A1', N'1'),
 (N'TH Quán Trữ', N'1A2', N'1'),
 (N'TH Quán Trữ', N'2A1', N'2'),
 (N'TH Quán Trữ', N'2A2', N'2'),
 (N'TH Quán Trữ', N'2A3', N'2'),
 (N'TH Quán Trữ', N'3A1', N'3'),
 (N'TH Quán Trữ', N'3A2', N'3'),
 (N'TH Quán Trữ', N'3A3', N'3'),
 (N'TH Quán Trữ', N'4A1', N'4'),
 (N'TH Quán Trữ', N'4A2', N'4'),
 (N'TH Quán Trữ', N'5A1', N'5'),
 (N'TH Quán Trữ', N'5A2', N'5'),
 (N'TH Đồng Hòa', N'1A1', N'1'),
 (N'TH Đồng Hòa', N'1A2', N'1'),
 (N'TH Đồng Hòa', N'1A3', N'1'),
 (N'TH Đồng Hòa', N'2A1', N'2'),
 (N'TH Đồng Hòa', N'2A2', N'2'),
 (N'TH Đồng Hòa', N'2A3', N'2'),
 (N'TH Đồng Hòa', N'3A1', N'3'),
 (N'TH Đồng Hòa', N'3A2', N'3'),
 (N'TH Đồng Hòa', N'3A3', N'3'),
 (N'TH Đồng Hòa', N'4A1', N'4'),
 (N'TH Đồng Hòa', N'4A2', N'4'),
 (N'TH Đồng Hòa', N'5A1', N'5'),
 (N'TH Đồng Hòa', N'5A2', N'5'),
 (N'TH Nguyễn Công Hòa', N'1A1', N'1'),
 (N'TH Nguyễn Công Hòa', N'1A2', N'1'),
 (N'TH Nguyễn Công Hòa', N'2A1', N'2'),
 (N'TH Nguyễn Công Hòa', N'2A2', N'2'),
 (N'TH Nguyễn Công Hòa', N'2A3', N'2'),
 (N'TH Nguyễn Công Hòa', N'3A1', N'3'),
 (N'TH Nguyễn Công Hòa', N'3A2', N'3'),
 (N'TH Nguyễn Công Hòa', N'3A3', N'3'),
 (N'TH Nguyễn Công Hòa', N'4A1', N'4'),
 (N'TH Nguyễn Công Hòa', N'4A2', N'4'),
 (N'TH Nguyễn Công Hòa', N'4A3', N'4'),
 (N'TH Nguyễn Công Hòa', N'5A1', N'5'),
 (N'TH Nguyễn Công Hòa', N'5A2', N'5'),
 (N'THCS Đồng Hòa', N'6D1', N'6'),
 (N'THCS Đồng Hòa', N'6D2', N'6'),
 (N'THCS Đồng Hòa', N'7C1', N'7'),
 (N'THCS Đồng Hòa', N'7C2', N'7'),
 (N'THCS Đồng Hòa', N'8B1', N'8'),
 (N'THCS Đồng Hòa', N'8B2', N'8'),
 (N'THCS Đồng Hòa', N'9A1', N'9'),
 (N'THCS Đồng Hòa', N'9A2', N'9'),
 (N'THCS Đồng Hòa', N'9A3', N'9'),
 (N'THCS Trần Thành Ngọ', N'6D1', N'6'),
 (N'THCS Trần Thành Ngọ', N'6D2', N'6'),
 (N'THCS Trần Thành Ngọ', N'6D3', N'6'),
 (N'THCS Trần Thành Ngọ', N'7C1', N'7'),
 (N'THCS Trần Thành Ngọ', N'7C2', N'7'),
 (N'THCS Trần Thành Ngọ', N'7C3', N'7'),
 (N'THCS Trần Thành Ngọ', N'8B1', N'8'),
 (N'THCS Trần Thành Ngọ', N'8B2', N'8'),
 (N'THCS Trần Thành Ngọ', N'9A1', N'9'),
 (N'THCS Trần Thành Ngọ', N'9A2', N'9'),
 (N'THCS Trần Thành Ngọ', N'9A3', N'9');

INSERT INTO SchoolClass (SchoolId, Name, GradeLevel, SchoolYear, Status, CreatedBy)
SELECT s.Id, l.TenLop, l.Khoi, '2026-2027', 'ACTIVE', @Admin
FROM #Lop l
JOIN School s ON s.Name = l.TruongName AND s.IsDeleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM SchoolClass c
    WHERE c.SchoolId = s.Id AND c.Name = l.TenLop AND c.SchoolYear = '2026-2027' AND c.IsDeleted = 0
);

PRINT N'4) Đã tạo ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' lớp học.';

DROP TABLE #Truong; DROP TABLE #KhungTiet; DROP TABLE #Lop;

COMMIT TRANSACTION;

PRINT N'';
PRINT N'>>> XONG. NHỚ rà soát lại tên trường theo danh bạ Phòng GD&ĐT —';
PRINT N'    xem database/seed/TSDMS_TruongHaiPhong_CanRaSoat.md';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DROP TABLE IF EXISTS #Truong;
    DROP TABLE IF EXISTS #KhungTiet;
    DROP TABLE IF EXISTS #Lop;
    PRINT N'!!! LỖI — đã rollback toàn bộ, DB giữ nguyên như trước khi chạy.';
    THROW;
END CATCH
GO
