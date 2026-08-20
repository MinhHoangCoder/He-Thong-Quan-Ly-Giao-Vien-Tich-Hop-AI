/* =====================================================================
   V31 — BỎ TÁC NHÂN "NHÀ TRƯỜNG": trường không còn là người dùng của hệ thống.

   QUYẾT ĐỊNH NGHIỆP VỤ (2026-08-19): hệ thống chỉ phục vụ HAI tác nhân —
   nhân sự trung tâm (ADMIN + các phòng ban) và GIÁO VIÊN. Trường khách hàng
   vẫn là DỮ LIỆU trung tâm của mọi phân công, nhưng không đăng nhập, không có
   màn hình, không có quyền.

   CÁI GÌ Ở LẠI (đừng nhầm — đây là xương sống của toàn bộ điều phối):
     · Bảng School và mọi cột SchoolId ở Assignment / AssignmentSlot / Period /
       SchoolClass / Room / ServiceContract / Holiday / TeacherEvaluation.
     · Quyền SCHOOL_VIEW / SCHOOL_MANAGE — đó là quyền của NHÂN VIÊN TRUNG TÂM
       quản lý hồ sơ trường, không phải quyền của trường.

   CÁI GÌ ĐI:
     · Role SCHOOL + 4 dòng RolePermission của nó.
     · Cột School.AppUserId — cầu nối duy nhất giữa hồ sơ trường và tài khoản
       đăng nhập. Bỏ cột này là chốt hạ ở tầng dữ liệu: không còn đường nào
       gắn một tài khoản vào một trường nữa, kể cả bằng SQL tay.

   AN TOÀN: chưa bao giờ có tài khoản trường nào được tạo (Role SCHOOL có 0
   người dùng, School.AppUserId toàn NULL), nên migration này không xóa dữ liệu
   thật của ai. Vẫn viết theo hướng phòng thủ để môi trường khác chạy được.
   ===================================================================== */

/* ---------- 1) Gỡ tài khoản trường (nếu môi trường nào lỡ có) ---------- */
DELETE ur
  FROM UserRole ur
  JOIN Role r ON r.Id = ur.RoleId
 WHERE r.Name = 'SCHOOL';
GO

DELETE rp
  FROM RolePermission rp
  JOIN Role r ON r.Id = rp.RoleId
 WHERE r.Name = 'SCHOOL';
GO

DELETE FROM Role WHERE Name = 'SCHOOL';
GO

/* ---------- 2) Bỏ cột School.AppUserId ----------
   Phải gỡ mọi ràng buộc/chỉ mục bám vào cột trước, nếu không ALTER sẽ nổ. */
DECLARE @sql NVARCHAR(MAX) = N'';

-- khóa ngoại
SELECT @sql = @sql + N'ALTER TABLE School DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';'
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
WHERE fk.parent_object_id = OBJECT_ID('dbo.School') AND c.name = 'AppUserId';

-- ràng buộc mặc định
SELECT @sql = @sql + N'ALTER TABLE School DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
WHERE dc.parent_object_id = OBJECT_ID('dbo.School') AND c.name = 'AppUserId';

-- ràng buộc UNIQUE: V1 khai `AppUserId INT NULL UNIQUE` ngay trong CREATE TABLE, và
-- SQL Server hiện thực nó bằng một CHỈ MỤC do RÀNG BUỘC sở hữu (tên tự sinh kiểu
-- UQ__School__FC65C731...). Chỉ mục loại này KHÔNG xóa bằng DROP INDEX được — server
-- từ chối thẳng: "An explicit DROP INDEX is not allowed on index ... It is being used
-- for UNIQUE KEY constraint enforcement" (lỗi 3723). Phải gỡ bằng DROP CONSTRAINT.
SELECT @sql = @sql + N'ALTER TABLE School DROP CONSTRAINT ' + QUOTENAME(i.name) + N';'
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID('dbo.School') AND c.name = 'AppUserId'
  AND i.is_primary_key = 0 AND i.is_unique_constraint = 1;

-- chỉ mục THƯỜNG (do CREATE INDEX tạo, kể cả unique filtered) — loại này mới DROP INDEX được
SELECT @sql = @sql + N'DROP INDEX ' + QUOTENAME(i.name) + N' ON School;'
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID('dbo.School') AND c.name = 'AppUserId'
  AND i.is_primary_key = 0 AND i.is_unique_constraint = 0;

IF @sql <> N'' EXEC sp_executesql @sql;
GO

IF COL_LENGTH('dbo.School', 'AppUserId') IS NOT NULL
    ALTER TABLE School DROP COLUMN AppUserId;
GO
