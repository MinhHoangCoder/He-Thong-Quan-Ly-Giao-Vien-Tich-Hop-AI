/* =====================================================================
   TSDMS - V23: Viết lại NỘI DUNG các lời mời phân công theo mẫu RÚT GỌN
   ---------------------------------------------------------------------
   BỐI CẢNH:
     Lời mời dạy trước đây liệt kê TỪNG tiết trong nội dung thông báo
     ("Thứ 3 Tiết 6 lớp 1A1, Thứ 3 Tiết 7 lớp 1A2, …"). Phiếu vài chục tiết
     là một khối chữ giáo viên không đọc nổi trên chuông, mà cột Content chỉ
     giữ 1000 ký tự (NotificationService) nên phiếu lớn còn hiện ra CỤT.

     Từ nay nội dung chỉ nói QUY MÔ (bao nhiêu tiết/tuần, mấy lớp); chi tiết
     thứ/tiết/lớp nằm ở bảng thời khóa biểu bật lên khi bấm vào thông báo.

     Code đã đổi rồi nhưng thông báo CŨ vẫn nằm trong DB với chuỗi dài, nên
     phải viết lại ở đây — nếu không giáo viên vẫn thấy mẫu cũ cho tới khi
     có phiếu mới.

   PHẠM VI:
     CHỈ lời mời dạy, nhận diện bằng RequiresAction = 1 — đó là cờ riêng của
     publishInvite(). Các thông báo Assignment khác ("Giáo viên đã xác nhận",
     "Trung tâm đã duyệt thay bạn", "không phản hồi trước hạn") đều có
     RequiresAction = 0 và PHẢI giữ nguyên câu chữ.

   AN TOÀN:
     Idempotent — nội dung sinh lại từ dữ liệu phân công nên chạy bao nhiêu
     lần cũng ra cùng một chuỗi. Thông báo trỏ tới phân công đã bị xóa cứng
     thì JOIN không khớp và được bỏ qua.
   ===================================================================== */

UPDATE n
SET n.Content =
        N'phân công #' + CAST(a.Id AS nvarchar(20))
        + N' · ' + s.Name
        + N' · ' + sub.Name
        + N' · '
        + CASE
              WHEN x.SlotCount = 0 THEN N'(chưa có tiết)'
              ELSE CAST(x.SlotCount AS nvarchar(10)) + N' tiết/tuần'
                   + CASE
                         WHEN x.ClassCount > 0 THEN N' · ' + CAST(x.ClassCount AS nvarchar(10)) + N' lớp'
                         ELSE N''
                     END
          END
        + N' · Từ ' + CONVERT(nvarchar(10), a.StartDate, 103)
        + N' đến ' + CONVERT(nvarchar(10), COALESCE(a.EndDate, x.LastLesson, a.StartDate), 103)
        + N' · HẠN XÁC NHẬN: '
        + ISNULL(
              CONVERT(nvarchar(5), a.ConfirmDeadline, 108) + N' ' + CONVERT(nvarchar(10), a.ConfirmDeadline, 103),
              N'—')
        + N'. Quá hạn phiếu tự hết hiệu lực — bấm để xem lịch chi tiết.'
FROM Notification n
JOIN Assignment a   ON a.Id   = n.RefId
JOIN School     s   ON s.Id   = a.SchoolId
JOIN Subject    sub ON sub.Id = a.SubjectId
CROSS APPLY (
    SELECT
        /* Số tiết/tuần và số LỚP KHÁC NHAU — khớp đúng slotSummary() bên Java.
           COUNT(DISTINCT ...) tự bỏ qua NULL, giống bước lọc bỏ lớp trống ở đó.
           COALESCE phải nằm ở bảng dẫn xuất v: gộp cột trong (sl) với cột ngoài (a) NGAY
           TRONG hàm tổng hợp thì SQL Server báo lỗi 8124. */
        (SELECT COUNT(*)
           FROM AssignmentSlot sl
          WHERE sl.AssignmentId = a.Id AND sl.IsDeleted = 0)                     AS SlotCount,
        (SELECT COUNT(DISTINCT v.ClassId)
           FROM (SELECT COALESCE(sl.ClassId, a.ClassId) AS ClassId
                   FROM AssignmentSlot sl
                  WHERE sl.AssignmentId = a.Id AND sl.IsDeleted = 0) v)          AS ClassCount,
        /* Phiếu không có ngày kết thúc thì lấy ngày của buổi CUỐI đã sinh. */
        (SELECT MAX(CONVERT(date, sc.StartTime))
           FROM Schedule sc
          WHERE sc.AssignmentId = a.Id AND sc.IsDeleted = 0)                     AS LastLesson
) x
WHERE n.RefEntity = 'Assignment'
  AND n.RefId IS NOT NULL
  AND n.RequiresAction = 1;
GO
