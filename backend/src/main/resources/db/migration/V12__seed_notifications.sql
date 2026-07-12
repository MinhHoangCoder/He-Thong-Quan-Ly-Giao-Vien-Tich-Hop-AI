/* =====================================================================
   V12 — SEED THÔNG BÁO DEMO cho chuông thông báo (topbar).
   Gán theo Username ⇄ AppUser.Id (KHÔNG hard-code Id tự tăng), có guard
   NOT EXISTS để chạy lại nhiều lần vẫn an toàn (idempotent).
   RefEntity quyết định FE bấm vào điều hướng tới trang nào (Assignment →
   /assignments, Schedule → /schedule ...). RefId để NULL vì chỉ điều hướng
   tới danh sách, không tới 1 bản ghi cố định.
   ===================================================================== */
INSERT INTO Notification (RecipientUserId, Title, Content, Type, RefEntity, RefId, IsRead, ReadAt, CreatedAt)
SELECT u.Id,
       v.Title,
       v.Content,
       v.Type,
       v.RefEntity,
       NULL,
       v.IsRead,
       CASE WHEN v.IsRead = 1 THEN SYSUTCDATETIME() ELSE NULL END,
       DATEADD(MINUTE, v.MinsAgo, SYSUTCDATETIME())
FROM AppUser u
CROSS APPLY (VALUES
    (N'Phân công mới được tạo',
     N'Phân công Robotics cho THCS Lê Quý Đôn đã được tạo và bắt đầu tuần này.',
     'ASSIGNMENT', 'Assignment', 0, -12),
    (N'Buổi dạy chờ duyệt',
     N'Có một số buổi dạy đang chờ bạn xem xét và phê duyệt.',
     'SCHEDULE', 'Schedule', 0, -95),
    (N'Nhắc chấm công hôm nay',
     N'Vui lòng kiểm tra và xác nhận chấm công cho các buổi dạy trong ngày.',
     'ATTENDANCE', 'Attendance', 0, -240),
    (N'Bảng lương kỳ này đã sẵn sàng',
     N'Bảng lương đã được tính toán xong, mời bạn kiểm tra và duyệt.',
     'PAYROLL', 'Payroll', 1, -1440),
    (N'Chào mừng tới KDC EduOps AI',
     N'Bảng điều khiển đã được nâng cấp giao diện mới, dữ liệu cập nhật theo thời gian thực.',
     'SYSTEM', NULL, 1, -2880)
) AS v(Title, Content, Type, RefEntity, IsRead, MinsAgo)
WHERE u.Username IN ('admin', 'employee')
  AND NOT EXISTS (
      SELECT 1 FROM Notification n
      WHERE n.RecipientUserId = u.Id AND n.Title = v.Title
  );
