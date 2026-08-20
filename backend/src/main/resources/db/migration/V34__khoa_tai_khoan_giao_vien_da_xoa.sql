/* =====================================================================
   V34 — VÁ TÀI KHOẢN GIÁO VIÊN ĐÃ XÓA MÀ VẪN ĐĂNG NHẬP ĐƯỢC.

   Lỗi: xóa giáo viên chỉ đụng bảng Teacher, không đụng AppUser.
     · Xóa mềm  (vào thùng rác) → Teacher.IsDeleted = 1, tài khoản vẫn ACTIVE.
     · Xóa vĩnh viễn            → mất hẳn dòng Teacher, tài khoản vẫn ACTIVE
                                  và thành TÀI KHOẢN MỒ CÔI (còn role TEACHER
                                  nhưng không còn hồ sơ nào phía sau).

   Mà AuthService.login chỉ hỏi AppUser: còn sống không, đúng mật khẩu không,
   Status có ACTIVE không. Nó KHÔNG hỏi "tài khoản này còn hồ sơ không". Nên
   giáo viên đã bị xóa vẫn đăng nhập bình thường — chỉ khác là mọi màn hình
   của họ (Lịch dạy, Chấm công, Bảng lương, Sửa chấm công) ném 403 "Tài khoản
   không có hồ sơ giáo viên". Người bấm xóa không hề biết.

   TeacherService đã được sửa để khóa tài khoản ngay lúc xóa (và mở lại lúc
   khôi phục). Migration này dọn phần ĐÃ HỎNG TỪ TRƯỚC — code sửa rồi thì
   không tự lành dữ liệu cũ.

   VÌ SAO KHÔNG XÓA CỨNG AppUser: bảng này bị 13 bảng khác trỏ vào. Xóa mềm
   đã đủ chặn đăng nhập (login lọc theo IsDeleted = 0) và vẫn nhả username ra
   cho người sau dùng lại.
   ===================================================================== */

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ---------------------------------------------------------------------
   1. TÀI KHOẢN MỒ CÔI — mang role TEACHER nhưng không còn dòng Teacher nào.
      Hồ sơ đã bị xóa vĩnh viễn nên tài khoản cũng không còn lý do tồn tại:
      xóa mềm + khóa.
   --------------------------------------------------------------------- */
UPDATE u
SET u.IsDeleted = 1,
    u.DeletedAt = SYSUTCDATETIME(),
    u.Status    = 'INACTIVE',
    u.UpdatedAt = SYSUTCDATETIME()
FROM AppUser u
WHERE u.IsDeleted = 0
  AND EXISTS (SELECT 1 FROM UserRole ur JOIN Role r ON r.Id = ur.RoleId
              WHERE ur.AppUserId = u.Id AND r.Name = 'TEACHER')
  /* Rào an toàn: tài khoản kiêm ADMIN thì THA — admin có thể được gán nhầm
     role TEACHER mà không bao giờ có hồ sơ giáo viên, khóa nhầm là mất
     đường vào hệ thống. */
  AND NOT EXISTS (SELECT 1 FROM UserRole ur JOIN Role r ON r.Id = ur.RoleId
                  WHERE ur.AppUserId = u.Id AND r.Name = 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM Teacher t WHERE t.AppUserId = u.Id);
GO

/* ---------------------------------------------------------------------
   2. HỒ SƠ NẰM TRONG THÙNG RÁC — chỉ KHÓA, không xóa mềm tài khoản.
      Hồ sơ còn khôi phục được, nên tài khoản phải còn nguyên để mở lại
      (restoreTeacher gọi moLaiTaiKhoanDangNhap, và hàm đó cố ý không hồi
      sinh tài khoản đã xóa mềm).
   --------------------------------------------------------------------- */
UPDATE u
SET u.Status    = 'INACTIVE',
    u.UpdatedAt = SYSUTCDATETIME()
FROM AppUser u
JOIN Teacher t ON t.AppUserId = u.Id
WHERE t.IsDeleted = 1
  AND u.IsDeleted = 0
  AND u.Status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM UserRole ur JOIN Role r ON r.Id = ur.RoleId
                  WHERE ur.AppUserId = u.Id AND r.Name = 'ADMIN');
GO

/* ---------------------------------------------------------------------
   3. Thu hồi refresh token của các tài khoản vừa bị khóa — nếu không, phiên
      đang mở vẫn tự gia hạn cho tới khi token hết hạn.
   --------------------------------------------------------------------- */
UPDATE rt
SET rt.RevokedAt = SYSUTCDATETIME()
FROM RefreshToken rt
JOIN AppUser u ON u.Id = rt.AppUserId
WHERE rt.RevokedAt IS NULL
  AND (u.IsDeleted = 1 OR u.Status <> 'ACTIVE');
GO
