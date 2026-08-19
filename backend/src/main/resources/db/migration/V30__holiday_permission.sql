/* =====================================================================
   V30 — QUYỀN QUẢN LÝ LỊCH NGHỈ (HOLIDAY_VIEW / HOLIDAY_MANAGE).

   V29 dựng bảng Holiday và nối vào generator, nhưng thêm/sửa ngày nghỉ vẫn
   phải gõ SQL. Trước mỗi năm học lịch nghỉ đều đổi (Tết theo âm lịch, ngày
   nghỉ bù do Chính phủ chốt từng năm, trường sửa chữa nghỉ riêng) — bắt phòng
   Đào tạo mở SSMS ra sửa là cách chắc chắn để dữ liệu này không bao giờ được
   cập nhật.

   Vì sao là PERMISSION RIÊNG chứ không neo vào role ADMIN: dự án đã bỏ neo tên
   role ở tầng service từ bản 2026-08-09. Có quyền riêng thì sau này mở cho ai
   chỉ là gán thêm một dòng RolePermission, không phải sửa code rồi build lại.

   ADMIN không cần cấp: đi tắt bằng hasRole('ADMIN') ở @PreAuthorize, đúng như
   mọi quyền khác từ V3.
   ===================================================================== */

INSERT INTO Permission (Code, Description)
SELECT v.Code, v.Description
FROM (VALUES
    ('HOLIDAY_VIEW',   N'Xem lịch nghỉ (ngày lễ, kỳ nghỉ)'),
    ('HOLIDAY_MANAGE', N'Thêm/sửa/xóa lịch nghỉ')
) AS v(Code, Description)
WHERE NOT EXISTS (SELECT 1 FROM Permission p WHERE p.Code = v.Code);
GO

/* Phòng Đào tạo là nơi nắm lịch năm học — cũng chính là nơi đang giữ quyền
   xếp/duyệt lịch dạy (SCHEDULE_MANAGE, SCHEDULE_APPROVE từ V3). */
INSERT INTO RolePermission (RoleId, PermissionId)
SELECT r.Id, p.Id
FROM Role r
JOIN Permission p ON p.Code IN ('HOLIDAY_VIEW', 'HOLIDAY_MANAGE')
WHERE r.Name = 'ACADEMIC'
  AND NOT EXISTS (SELECT 1 FROM RolePermission rp WHERE rp.RoleId = r.Id AND rp.PermissionId = p.Id);
GO
