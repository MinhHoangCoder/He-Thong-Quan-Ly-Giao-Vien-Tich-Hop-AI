/* =====================================================================
   TSDMS - V5: TÀI KHOẢN TEST "employee" GỘP CẢ 4 PHÒNG BAN
   ---------------------------------------------------------------------
   Mục tiêu: biến tài khoản demo 'employee' thành TÀI KHOẢN TEST tiện lợi —
   có ĐỦ chức năng của cả 4 phòng ban (HR + ACCOUNTANT + ACADEMIC + SALES)
   trong MỘT lần đăng nhập, nhưng VẪN bị giới hạn theo chi nhánh (vì KHÔNG
   phải ADMIN — quy ước: chỉ ADMIN mới bỏ qua lọc chi nhánh ở tầng service).

   So sánh 2 tài khoản test:
     - 'admin'    : full chức năng, KHÔNG giới hạn chi nhánh (god-mode toàn hệ thống).
     - 'employee' : full chức năng phòng ban, CÓ giới hạn chi nhánh (god-mode 1 chi nhánh).

   Vì sao gán 4 ROLE thay vì seed bộ permission riêng cho employee:
     - Token tự gộp (union) quyền của mọi role user có -> employee có đủ quyền 4 phòng.
     - Tự ĐỒNG BỘ nếu sau này ma trận RolePermission đổi (không phải bảo trì 2 nơi).

   LƯU Ý FLYWAY (quy tắc nhóm):
     - KHÔNG sửa V1..V4 đã chạy (sai checksum -> cả nhóm lỗi khởi động).
     - File này CHỈ THÊM dữ liệu (UserRole), idempotent bằng WHERE NOT EXISTS.
     - Gán theo Username ⇄ Role.Name, TUYỆT ĐỐI không hard-code Id tự tăng.
   ===================================================================== */

INSERT INTO UserRole (AppUserId, RoleId)
SELECT u.AppUserId, r.RoleId
FROM AppUser u
JOIN Role r ON r.Name IN ('HR', 'ACCOUNTANT', 'ACADEMIC', 'SALES')
WHERE u.Username = 'employee'
  AND NOT EXISTS (SELECT 1 FROM UserRole ur
                  WHERE ur.AppUserId = u.AppUserId AND ur.RoleId = r.RoleId);
GO
