/* =====================================================================
   PATCH 2026-08-21 — Kéo hạn hợp đồng của vài trường về gần hôm nay

   VÌ SAO CẦN: seed Hải Phòng đặt hạn hợp đồng sớm nhất là 25/05/2027,
   nên trên DB demo KHÔNG có trường nào "sắp hết hạn", và cũng không có
   trường nào đang ACTIVE mà hợp đồng đã quá hạn. Hai luật mới của màn
   Quản lý trường (cảnh báo sắp hết hạn, và trạng thái Hết hạn suy theo
   NGÀY thay vì theo cột Status) đúng nhưng không có dòng nào để nhìn.

   File này chỉ sửa NGÀY hợp đồng của 3 trường có sẵn. Không thêm/xóa
   trường, không đụng lớp - phân công - lịch dạy.

   Ngày tính TƯƠNG ĐỐI theo hôm nay, nên chạy lúc nào cũng ra kết quả
   như mô tả. Chạy lại nhiều lần không sinh dữ liệu thừa.

   CÁCH CHẠY (bắt buộc cờ -I vì School có filtered index UX_School_BranchName
   từ V36 — thiếu -I là SQL Server từ chối mọi lệnh UPDATE lên bảng này):
   sqlcmd -S localhost -E -C -I -d TSDMS -f 65001 -i 2026-08-21-han-hop-dong-truong.sql
   ===================================================================== */
SET NOCOUNT ON;

DECLARE @HomNay DATE = CAST(GETDATE() AS DATE);

/* Ba trường + số ngày còn lại muốn có. Số ÂM = hợp đồng đã quá hạn.       */
DECLARE @Muon TABLE (Ten NVARCHAR(200), SoNgayConLai INT);
INSERT INTO @Muon (Ten, SoNgayConLai) VALUES
  /* Sắp hết hạn — hiện badge vàng "Còn N ngày" và lọt bộ lọc "Sắp hết hạn". */
  (N'THCS Đằng Hải', 12),
  (N'TH Đông Khê', 26),
  /* Cột Status vẫn để nguyên 'ACTIVE': đây chính là trường hợp cần chứng minh —
     bảng hiển thị "Hết hạn" vì NGÀY đã qua, không phải vì ai đó sửa trạng thái.
     Trường này đang có phân công nên còn cho thấy: lịch dạy cũ giữ nguyên,
     nhưng không xếp được phân công MỚI vào nữa.                            */
  (N'THCS Chu Văn An', -30);

UPDATE s
SET s.ContractEndDate = DATEADD(DAY, m.SoNgayConLai, @HomNay),
    /* Ngày bắt đầu phải luôn trước ngày kết thúc — SchoolRequest chặn ngược lại,
       dữ liệu seed cũng không nên vi phạm chính luật đó.                    */
    s.ContractStartDate = CASE
        WHEN s.ContractStartDate IS NULL OR s.ContractStartDate > DATEADD(DAY, m.SoNgayConLai, @HomNay)
            THEN DATEADD(YEAR, -2, @HomNay)
        ELSE s.ContractStartDate END,
    s.UpdatedAt = SYSUTCDATETIME()
FROM School s
JOIN @Muon m ON m.Ten = s.Name
WHERE s.IsDeleted = 0;
PRINT CONCAT(N'Đã chỉnh hạn hợp đồng cho ', @@ROWCOUNT, N' trường.');

/* Hợp đồng dịch vụ được seed suy ra từ chính hai cột ngày ở trên, nên phải
   kéo theo — để nguyên là khối "Hợp đồng dịch vụ" trong chi tiết trường nói
   một đằng, hai ô ngày trên form nói một nẻo ngay từ lúc dựng dữ liệu.      */
UPDATE sv
SET sv.EndDate = s.ContractEndDate,
    sv.StartDate = s.ContractStartDate,
    sv.Status = CASE WHEN s.ContractEndDate < @HomNay THEN 'EXPIRED' ELSE 'ACTIVE' END
FROM ServiceContract sv
JOIN School s ON s.Id = sv.SchoolId AND s.IsDeleted = 0
JOIN @Muon m ON m.Ten = s.Name
WHERE sv.IsDeleted = 0;
PRINT CONCAT(N'Đã đồng bộ ', @@ROWCOUNT, N' hợp đồng dịch vụ theo hạn mới.');

SELECT s.Name AS N'Trường',
       s.Status AS N'Cột Status',
       s.ContractEndDate AS N'Hết hạn',
       DATEDIFF(DAY, @HomNay, s.ContractEndDate) AS N'Còn (ngày)'
FROM School s
JOIN @Muon m ON m.Ten = s.Name
WHERE s.IsDeleted = 0
ORDER BY s.ContractEndDate;
