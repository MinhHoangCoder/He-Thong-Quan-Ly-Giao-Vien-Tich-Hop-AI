/* =====================================================================
   TSDMS - V26: School.AppUserId — UNIQUE bỏ qua NULL (filtered index)
   ---------------------------------------------------------------------
   VẤN ĐỀ:
     V1 khai "AppUserId INT NULL UNIQUE" -> SQL Server tạo ràng buộc UNIQUE
     tự đặt tên (UQ__School__...). Khác hầu hết hệ CSDL khác, SQL Server coi
     NULL là MỘT GIÁ TRỊ trong ràng buộc UNIQUE, nên toàn bảng chỉ được
     phép có ĐÚNG MỘT dòng AppUserId = NULL.

     Trong khi đó màn hình "Quản lý trường" (SchoolService.create) tạo
     trường KHÔNG kèm tài khoản -> AppUserId luôn NULL. Trường đầu tiên
     không có tài khoản thì lưu được; từ trường thứ hai trở đi INSERT bị
     chính ràng buộc này chặn (Msg 2627) -> DataIntegrityViolationException
     -> 500 "Lỗi hệ thống, vui lòng thử lại sau", người dùng không hiểu vì
     sao form hợp lệ mà không lưu được.

     Ý đồ thiết kế của cột là quan hệ 1-1 TÙY CHỌN: "mỗi tài khoản chỉ gắn
     một trường, nhưng trường được phép chưa có tài khoản" (comment ngay
     tại V1). Ràng buộc hiện tại diễn đạt sai ý đồ đó.

   GIẢI PHÁP (giống V21 đã làm cho Subject.Code):
     Bỏ ràng buộc UNIQUE cũ, thay bằng UNIQUE INDEX có lọc
     WHERE AppUserId IS NOT NULL -> vẫn ép 1-1 trên các trường ĐÃ có tài
     khoản, nhưng bao nhiêu trường chưa có tài khoản cũng được.

   VÌ SAO KHÔNG LỌC THÊM IsDeleted = 0:
     Trường xóa mềm vẫn giữ nguyên AppUserId, và có luồng khôi phục từ
     Thùng rác. Nếu nới cho phép tài khoản của trường đã xóa được gán lại,
     lúc khôi phục sẽ có HAI trường sống cùng trỏ về một tài khoản ->
     findByAppUserIdAndDeletedFalse (dùng khi đăng nhập) trả về 2 bản ghi
     và nổ NonUniqueResultException. Giữ chặt ở đây là có chủ đích.

   AN TOÀN:
     - Ràng buộc cũ ép duy nhất trên MỌI hàng nên tập con AppUserId IS NOT
       NULL chắc chắn đã duy nhất -> tạo index lọc không thể vướng dữ liệu
       trùng sẵn có.
     - Tên ràng buộc cũ do SQL Server tự sinh (khác nhau ở mỗi DB) nên phải
       dò tên động rồi mới DROP; có guard nên chạy lại nhiều lần vẫn an toàn.
   ===================================================================== */

/* ---------- 1) Bỏ ràng buộc UNIQUE tự sinh trên cột AppUserId ---------- */
DECLARE @cname SYSNAME;
SELECT @cname = kc.name
FROM sys.key_constraints kc
WHERE kc.parent_object_id = OBJECT_ID(N'dbo.School')
  AND kc.[type] = 'UQ'
  AND EXISTS (
        SELECT 1
        FROM sys.index_columns ic
        JOIN sys.columns col
          ON col.object_id = ic.object_id AND col.column_id = ic.column_id
        WHERE ic.object_id = kc.parent_object_id
          AND ic.index_id  = kc.unique_index_id
          AND col.name     = 'AppUserId'
  );

/* QUOTENAME là lời gọi HÀM nên KHÔNG được ghép thẳng trong EXEC('...' + f(x));
   phải gom vào biến @sql rồi sp_executesql, nếu không SQL Server báo lỗi cú pháp. */
IF @cname IS NOT NULL
BEGIN
    DECLARE @sql NVARCHAR(MAX) = N'ALTER TABLE dbo.School DROP CONSTRAINT ' + QUOTENAME(@cname);
    EXEC sys.sp_executesql @sql;
END
GO

/* ---------- 2) UNIQUE có lọc: chỉ áp trên trường ĐÃ có tài khoản ---------- */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'UX_School_AppUser' AND object_id = OBJECT_ID(N'dbo.School'))
    CREATE UNIQUE INDEX UX_School_AppUser ON dbo.School(AppUserId) WHERE AppUserId IS NOT NULL;
GO
