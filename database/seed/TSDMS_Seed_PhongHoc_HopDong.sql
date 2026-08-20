/* =====================================================================
   TSDMS — SEED PHÒNG HỌC (Room) + HỢP ĐỒNG DỊCH VỤ (ServiceContract)
   ---------------------------------------------------------------------
   CHẠY THẾ NÀO
     Mở trong SSMS rồi Execute, hoặc:
       sqlcmd -S localhost -d TSDMS -U tsdms_app -P *** -i TSDMS_Seed_PhongHoc_HopDong.sql
     ĐÒI bộ 30 trường Hải Phòng đã nạp. Chạy lại lần hai tự bỏ qua.

   HAI BẢNG NÀY ĐANG RỖNG HOÀN TOÀN
     · Room — lịch dạy không có phòng, cột RoomId toàn null.
     · ServiceContract — module "Doanh thu" của phòng Kế toán không có gì để tính.

   PHÒNG HỌC
     4-6 phòng mỗi trường: phòng máy tính, phòng STEM/robot, phòng học chức năng,
     trường lớn thêm hội trường. Đây là các phòng trường DÀNH RIÊNG cho chương
     trình của trung tâm, không phải toàn bộ phòng của trường — nên con số nhỏ.

     ⚠ CỐ Ý KHÔNG gán phòng vào lịch dạy đã seed: `AssignmentSlot.RoomId` và
     `Schedule.RoomId` vẫn để null. Gán phòng cho 9.712 buổi mà không chạy luật
     chống trùng phòng thì sẽ đẻ ra cảnh hai lớp ngồi chung một phòng lúc 14:00 —
     tệ hơn là để trống. Muốn có phòng trên thời khóa biểu thì xếp lại qua giao
     diện Phân công, ở đó có kiểm tra đầy đủ.

   HỢP ĐỒNG DỊCH VỤ
     Chỉ ký cho trường CÓ NGÀY HỢP ĐỒNG trong hồ sơ (`School.ContractStartDate`
     khác null) — 22 trường: 18 đang hoạt động + 4 đã hết hạn. 8 trường INACTIVE
     không có ngày hợp đồng nghĩa là chưa từng ký, nên không dựng hợp đồng cho họ.

     Ngày và trạng thái LẤY THẲNG từ hồ sơ trường, không bịa ngày mới — hai chỗ
     nói hai kiểu về cùng một hợp đồng là lỗi dữ liệu.

     Giá trị hợp đồng = SỐ LỚP × đơn giá/lớp/năm:
       · Tiểu học 14.000.000đ  · THCS 16.000.000đ
     Căn cứ: một lớp học 2 tiết/tuần × 35 tuần = 70 tiết/năm, chi phí trả giáo
     viên 115-125k/tiết ≈ 8-8,8 triệu; phần còn lại là thiết bị, học liệu, điều
     phối và lợi nhuận. Con số giải thích được, không phải số tròn bịa ra.

     Trường ĐÃ HẾT HẠN nay không còn lớp nào trong hệ thống (lớp đã bị gỡ theo
     hợp đồng) → lấy quy mô 8 lớp làm căn cứ cho hợp đồng cũ, thay vì tính ra 0đ.

   GỠ RA: chạy database/seed/TSDMS_Rollback_PhongHoc_HopDong.sql
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---- CHỐT CHẶN ---- */
IF EXISTS (SELECT 1 FROM Room) OR EXISTS (SELECT 1 FROM ServiceContract)
BEGIN
    PRINT N'>>> Đã có phòng học hoặc hợp đồng dịch vụ — bỏ qua, không chạy lại.';
    PRINT N'    Muốn nạp lại: chạy TSDMS_Rollback_PhongHoc_HopDong.sql trước.';
    RETURN;
END

BEGIN TRY
BEGIN TRANSACTION;

DECLARE @Admin INT = (SELECT Id FROM AppUser WHERE Username = 'admin' AND IsDeleted = 0);
IF @Admin IS NULL
    THROW 50030, N'Không tìm thấy tài khoản admin.', 1;

IF NOT EXISTS (SELECT 1 FROM School WHERE IsDeleted = 0)
    THROW 50031, N'Chưa có trường nào — chạy TSDMS_Seed_TruongHaiPhong.sql trước.', 1;

DECLARE @n INT;

/* ═══════════════ 1) PHÒNG HỌC ═══════════════
   Mẫu phòng dùng chung, mỗi trường lấy 4-6 dòng đầu tùy quy mô. Trường THCS
   thường có phòng tin học riêng nên được nhiều phòng hơn. */

CREATE TABLE #MauPhong (
    Ord      INT PRIMARY KEY,
    Ten      NVARCHAR(100) COLLATE DATABASE_DEFAULT,
    Loai     VARCHAR(20) COLLATE DATABASE_DEFAULT,
    SucChua  INT,
    Nha      NVARCHAR(50) COLLATE DATABASE_DEFAULT,
    Tang     VARCHAR(10) COLLATE DATABASE_DEFAULT
);
INSERT INTO #MauPhong (Ord, Ten, Loai, SucChua, Nha, Tang) VALUES
 (1, N'Phòng máy tính 1',        'LAB',       40, N'Nhà A', '2'),
 (2, N'Phòng STEM - Robotics',   'LAB',       32, N'Nhà A', '3'),
 (3, N'Phòng học chức năng 1',   'CLASSROOM', 45, N'Nhà B', '1'),
 (4, N'Phòng học chức năng 2',   'CLASSROOM', 45, N'Nhà B', '2'),
 (5, N'Phòng máy tính 2',        'LAB',       40, N'Nhà A', '2'),
 (6, N'Hội trường',              'HALL',     200, N'Nhà B', '1');

/* Số phòng theo cấp học + quy mô: THCS 5-6 phòng, tiểu học 4-5. */
INSERT INTO Room (SchoolId, Name, Building, Floor, Type, Capacity, Status, CreatedAt, CreatedBy)
SELECT s.Id, m.Ten, m.Nha, m.Tang, m.Loai, m.SucChua,
       /* Một vài phòng đang sửa chữa — để trạng thái MAINTENANCE có dữ liệu thật. */
       CASE WHEN m.Ord = 4 AND s.Id % 7 = 0 THEN 'MAINTENANCE' ELSE 'AVAILABLE' END,
       SYSUTCDATETIME(), @Admin
FROM School s
CROSS JOIN #MauPhong m
WHERE s.IsDeleted = 0
  AND m.Ord <= CASE WHEN s.Name LIKE N'THCS%' THEN 5 + (s.Id % 2)   -- 5 hoặc 6
                    ELSE 4 + (s.Id % 2) END                          -- 4 hoặc 5
  AND NOT EXISTS (SELECT 1 FROM Room r WHERE r.SchoolId = s.Id AND r.Name = m.Ten AND r.IsDeleted = 0);

SET @n = @@ROWCOUNT;
DECLARE @soTruong INT = (SELECT COUNT(*) FROM School WHERE IsDeleted = 0);
PRINT N'1) Đã tạo ' + CAST(@n AS NVARCHAR(10)) + N' phòng học cho '
    + CAST(@soTruong AS NVARCHAR(10)) + N' trường.';

/* ═══════════════ 2) HỢP ĐỒNG DỊCH VỤ ═══════════════ */

CREATE TABLE #HD (
    SchoolId INT PRIMARY KEY,
    BranchId INT,
    TuNgay   DATE,
    DenNgay  DATE,
    SoLop    INT,
    DonGia   DECIMAL(18,2),
    TrangThai VARCHAR(20) COLLATE DATABASE_DEFAULT
);

INSERT INTO #HD (SchoolId, BranchId, TuNgay, DenNgay, SoLop, DonGia, TrangThai)
SELECT s.Id, s.BranchId, s.ContractStartDate, s.ContractEndDate,
       /* Trường đã hết hạn không còn lớp trong hệ thống → lấy quy mô 8 lớp làm
          căn cứ cho hợp đồng cũ, thay vì ra giá trị 0đ. */
       CASE WHEN x.SoLop > 0 THEN x.SoLop ELSE 8 END,
       CASE WHEN s.Name LIKE N'THCS%' THEN 16000000.00 ELSE 14000000.00 END,
       CASE s.Status WHEN 'ACTIVE' THEN 'ACTIVE' WHEN 'EXPIRED' THEN 'EXPIRED' ELSE 'DRAFT' END
FROM School s
CROSS APPLY (
    SELECT COUNT(*) AS SoLop FROM SchoolClass c
    WHERE c.SchoolId = s.Id AND c.SchoolYear = '2026-2027' AND c.IsDeleted = 0
) x
WHERE s.IsDeleted = 0
  AND s.ContractStartDate IS NOT NULL
  AND s.ContractEndDate IS NOT NULL;

/* Mã hợp đồng đánh số theo NĂM KÝ, thứ tự ổn định theo Id trường (chạy lại ở
   máy khác vẫn ra đúng mã ấy). Cột ContractCode có ràng buộc UNIQUE. */
INSERT INTO ServiceContract (SchoolId, BranchId, ContractCode, StartDate, EndDate, ContractValue, Status, CreatedAt, CreatedBy)
SELECT h.SchoolId, h.BranchId,
       N'HDDV/' + CAST(YEAR(h.TuNgay) AS NVARCHAR(4)) + N'/'
           + RIGHT(N'000' + CAST(ROW_NUMBER() OVER (PARTITION BY YEAR(h.TuNgay) ORDER BY h.SchoolId) AS NVARCHAR(4)), 3),
       h.TuNgay, h.DenNgay,
       h.SoLop * h.DonGia,
       h.TrangThai,
       /* Hợp đồng nhập vào hệ thống ngay khi ký. */
       CAST(h.TuNgay AS DATETIME2(3)), @Admin
FROM #HD h;

SET @n = @@ROWCOUNT;
PRINT N'2) Đã tạo ' + CAST(@n AS NVARCHAR(10)) + N' hợp đồng dịch vụ.';

DECLARE @tong DECIMAL(18,2) = (SELECT SUM(ContractValue) FROM ServiceContract WHERE Status = 'ACTIVE' AND IsDeleted = 0);
PRINT N'   · Tổng giá trị hợp đồng ĐANG hiệu lực: '
    + FORMAT(@tong, 'N0', 'vi-VN') + N' đ';

DROP TABLE #MauPhong; DROP TABLE #HD;

COMMIT TRANSACTION;
PRINT N'';
PRINT N'>>> XONG. Phòng học dùng được khi xếp phân công mới; hợp đồng dịch vụ là nguồn số cho module Doanh thu.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DROP TABLE IF EXISTS #MauPhong; DROP TABLE IF EXISTS #HD;
    PRINT N'!!! LỖI — đã rollback toàn bộ, DB giữ nguyên.';
    THROW;
END CATCH
GO
