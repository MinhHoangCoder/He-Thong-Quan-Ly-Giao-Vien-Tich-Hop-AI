/* =====================================================================
   V37 — HỢP ĐỒNG GIÁO VIÊN GIỮ LỊCH SỬ, KHÔNG GHI ĐÈ.

   Bối cảnh: PUT /teacher/{id}/contract (upsertContract) đang tìm hợp đồng
   hiện tại rồi GHI ĐÈ ngay lên chính dòng đó. Sửa lương xong là số cũ
   biến mất — không thùng rác, không nhật ký, không cách nào biết trước
   đó ghi bao nhiêu hay ai sửa.

   Với một tờ hợp đồng lao động thì đây còn nặng hơn xóa cứng: xóa thì ít
   ra còn biết là đã mất, ghi đè thì dòng vẫn nằm đó trông như thật, chỉ
   là nội dung đã khác. Khi có tranh chấp lương, thứ cần trả lời là "hợp
   đồng ký ngày đó ghi bao nhiêu" — và hệ thống hiện không trả lời được.

   Mâu thuẫn với luật đã chốt ở Đợt 2: hợp đồng là hồ sơ pháp lý, chỉ
   được xóa MỀM. Cấm xóa cứng mà vẫn cho ghi đè thì mới bịt được một nửa.

   Cách sửa ở tầng service (TeacherService.saveContract): thay vì ghi đè,
   ĐÓNG bản cũ lại (Status = TERMINATED, IsDeleted = 1) rồi TẠO dòng mới.
   Mỗi lần sửa là một phiên bản, tra lại được đủ.

   Nhưng làm vậy thì vướng schema: V1 khai

       ContractNo VARCHAR(50) NOT NULL UNIQUE

   Ràng buộc UNIQUE này tính trên TOÀN BẢNG, kể cả dòng đã xóa mềm. Nên
   khi người dùng chỉ sửa mức lương mà GIỮ NGUYÊN số hợp đồng — trường
   hợp phổ biến nhất, vì phụ lục sửa lương không đổi số HĐ — bản mới sẽ
   đụng ngay vào bản cũ vừa đóng và toàn bộ thao tác nổ.

   V37 đổi sang CHỈ MỤC UNIQUE CÓ LỌC, chỉ chặn trùng giữa các hợp đồng
   CÒN HIỆU LỰC. Đây đúng ý nghĩa nghiệp vụ ngay từ đầu: hai hợp đồng
   đang sống không được trùng số; còn bản đã đóng thì giữ nguyên số của
   nó là chuyện bình thường. Dự án đã dùng đúng khuôn này ở Subject
   (V21), Room, SchoolClass và Period.

   BẪY khi gỡ ràng buộc cũ: `UNIQUE` khai thẳng trong CREATE TABLE sinh
   ra một RÀNG BUỘC (tên tự sinh UQ__Contract__...), và SQL Server từ
   chối DROP INDEX trên chỉ mục do ràng buộc sở hữu (lỗi 3723). Phải
   DROP CONSTRAINT. Đây đúng chỗ V31 đã vấp và làm chết Flyway.
   ===================================================================== */

/* ---------- 1. Gỡ ràng buộc UNIQUE toàn bảng ---------- */
DECLARE @sql NVARCHAR(MAX) = N'';

SELECT @sql = @sql + N'ALTER TABLE Contract DROP CONSTRAINT ' + QUOTENAME(i.name) + N';'
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID('dbo.Contract')
  AND c.name = 'ContractNo'
  AND i.is_primary_key = 0
  AND i.is_unique_constraint = 1;

IF @sql <> N'' EXEC sp_executesql @sql;
GO

/* ---------- 2. Chỉ mục unique CÓ LỌC ---------- */
/* Chỉ soi các dòng còn hiệu lực. Bản đã bị thay thế (IsDeleted = 1) nằm ngoài
   tầm ngắm nên giữ nguyên số hợp đồng cũ của nó được. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_Contract_No_Active'
                                           AND object_id = OBJECT_ID('dbo.Contract'))
    CREATE UNIQUE INDEX UX_Contract_No_Active ON Contract(ContractNo) WHERE IsDeleted = 0;
GO

/* ---------- 3. Chỉ mục tra lịch sử ---------- */
/* Màn hồ sơ giáo viên đọc "các phiên bản đã bị thay thế" theo TeacherId + IsDeleted,
   xếp mới nhất trước. IX_Contract_Teacher (V1) chỉ có TeacherId nên vẫn phải lọc
   thêm; thêm IsDeleted vào khóa để đọc thẳng. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Contract_Teacher_Deleted'
                                           AND object_id = OBJECT_ID('dbo.Contract'))
    CREATE INDEX IX_Contract_Teacher_Deleted ON Contract(TeacherId, IsDeleted) INCLUDE (StartDate);
GO
