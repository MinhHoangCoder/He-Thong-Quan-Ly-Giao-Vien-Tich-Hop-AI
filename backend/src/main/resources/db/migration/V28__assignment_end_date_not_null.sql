/* =====================================================================
   V28 — PHÂN CÔNG PHẢI CÓ NGÀY KẾT THÚC THẬT (hết "không giới hạn").

   Bối cảnh: bỏ trống ngày kết thúc thì Assignment.EndDate lưu NULL, nhưng
   Service CHỈ sinh Schedule trong 8 tuần rồi dừng. Dữ liệu vì thế nói dối
   ở hai chỗ:

     1) Màn hình ghi "17/08/2026 → không giới hạn" trong khi buổi dạy cuối
        cùng nằm ở tuần thứ 8 — người xếp lịch tưởng phiếu còn chạy.
     2) Nặng hơn: luật chống trùng (datesOverlap) coi EndDate NULL là VÔ HẠN
        nên khung giờ đó bị giữ chỗ VĨNH VIỄN. Giáo viên không bao giờ xếp
        được lịch mới vào khung giờ ấy, dù thực tế đã trống từ lâu.

   Cách chốt: lấy ngày của BUỔI DẠY CUỐI CÙNG thật sự đã sinh ra. Đó là sự
   thật đo được, không phải con số suy đoán. Phiếu không có buổi nào (bị hủy
   sạch, hoặc dữ liệu rác) thì lùi về StartDate + 8 tuần cho khớp quy ước
   DEFAULT_WEEKS ở AssignmentService.

   KHÔNG đặt NOT NULL cho cột: ràng buộc đó thuộc về tầng nghiệp vụ (Service
   đã luôn ghi giá trị thật từ V28 trở đi). Ép ở DB thì mọi INSERT cũ trong
   test/seed đều gãy mà chẳng thêm được bảo đảm nào.
   ===================================================================== */

/* Buổi dạy cuối cùng của từng phiếu — kể cả buổi đã xóa mềm, vì chúng vẫn
   là phần lịch đã từng tồn tại và sẽ sống lại nếu phiếu được khôi phục. */
UPDATE a
   SET a.EndDate = CAST(x.LastStart AS DATE)
  FROM Assignment a
  JOIN (SELECT AssignmentId, MAX(StartTime) AS LastStart
          FROM Schedule
         GROUP BY AssignmentId) x ON x.AssignmentId = a.Id
 WHERE a.EndDate IS NULL;
GO

/* Phiếu không còn buổi nào để suy ra: dùng đúng quy ước mặc định 8 tuần. */
UPDATE Assignment
   SET EndDate = DATEADD(WEEK, 8, StartDate)
 WHERE EndDate IS NULL;
GO

/* Lưới an toàn: sau hai bước trên không được còn dòng nào EndDate < StartDate
   (dữ liệu rác cũ có thể có buổi lệch ngày). Kéo về đúng ngày bắt đầu. */
UPDATE Assignment
   SET EndDate = StartDate
 WHERE EndDate < StartDate;
GO
