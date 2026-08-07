/* =====================================================================
   TSDMS - V21: Subject.Code — UNIQUE tính theo xóa mềm (filtered index)
   ---------------------------------------------------------------------
   VẤN ĐỀ:
     V1 khai Code là "VARCHAR(20) NOT NULL UNIQUE" -> SQL Server tạo ràng
     buộc UNIQUE tự đặt tên (UQ__Subject__...) áp lên MỌI bản ghi, kể cả
     bản ghi đã xóa mềm (IsDeleted = 1).

     Nhưng tầng nghiệp vụ (SubjectService.create/update) chỉ kiểm tra trùng
     mã trên các môn CHƯA xóa (existsByCodeAndDeletedFalse). Vì vậy khi tạo
     lại 1 môn dùng mã của môn đã bị XÓA MỀM (vd STEM03), check app cho qua
     nhưng INSERT bị chính ràng buộc UNIQUE của DB chặn -> 500
     DataIntegrityViolationException (log "Lỗi không xử lý được").

   GIẢI PHÁP (đồng bộ với AppUser.Username/Email, Room, SchoolClass...):
     Bỏ ràng buộc UNIQUE cũ (áp mọi hàng) và thay bằng UNIQUE INDEX có lọc
     WHERE IsDeleted = 0 -> chỉ ép duy nhất trên môn còn sống, cho phép
     dùng lại mã của môn đã xóa. Đúng như hành vi tầng app mong đợi.

   AN TOÀN:
     - Ràng buộc cũ ép duy nhất trên TẤT CẢ hàng nên tập con IsDeleted = 0
       chắc chắn đã duy nhất -> tạo index lọc không thể vướng dữ liệu trùng.
     - Tên ràng buộc cũ do SQL Server tự sinh (khác nhau mỗi DB) nên phải
       dò tên động rồi mới DROP; có guard nên chạy lại nhiều lần vẫn an toàn.
   ===================================================================== */

/* ---------- 1) Bỏ ràng buộc UNIQUE tự sinh trên cột Code ---------- */
DECLARE @cname SYSNAME;
SELECT @cname = kc.name
FROM sys.key_constraints kc
WHERE kc.parent_object_id = OBJECT_ID(N'dbo.Subject')
  AND kc.[type] = 'UQ'
  AND EXISTS (
        SELECT 1
        FROM sys.index_columns ic
        JOIN sys.columns col
          ON col.object_id = ic.object_id AND col.column_id = ic.column_id
        WHERE ic.object_id = kc.parent_object_id
          AND ic.index_id  = kc.unique_index_id
          AND col.name     = 'Code'
  );

/* QUOTENAME là lời gọi HÀM nên KHÔNG được ghép thẳng trong EXEC('...' + f(x));
   phải gom vào biến @sql rồi sp_executesql, nếu không SQL Server báo lỗi cú pháp. */
IF @cname IS NOT NULL
BEGIN
    DECLARE @sql NVARCHAR(MAX) = N'ALTER TABLE dbo.Subject DROP CONSTRAINT ' + QUOTENAME(@cname);
    EXEC sys.sp_executesql @sql;
END
GO

/* ---------- 2) UNIQUE có lọc: chỉ áp trên môn CHƯA xóa mềm ---------- */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'UX_Subject_Code' AND object_id = OBJECT_ID(N'dbo.Subject'))
    CREATE UNIQUE INDEX UX_Subject_Code ON dbo.Subject(Code) WHERE IsDeleted = 0;
GO
