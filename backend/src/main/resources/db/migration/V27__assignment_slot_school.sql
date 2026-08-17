/* =====================================================================
   V27 — PHÂN CÔNG THEO GIÁO VIÊN: mỗi ô Thứ+Tiết mang TRƯỜNG riêng.

   Trước V27, trường nằm ở bảng cha (Assignment.SchoolId) nên MỘT phiếu chỉ
   phủ được MỘT trường. Nghiệp vụ thật của trung tâm là điều phối theo NGƯỜI:
   sáng thứ 2 thầy A dạy tiết 1 ở TH Dư Hàng rồi tiết 2 chạy sang THCS Vinh
   Quang. Muốn mô tả ca đó thì phải tạo hai phiếu rời, và người xếp lịch mất
   khả năng nhìn một ngày dạy của giáo viên như một khối thống nhất.

   Hạ SchoolId xuống bảng con AssignmentSlot — lặp lại đúng khuôn V16 đã làm
   với ClassId. Grain thật giờ là: 1 tiết = 1 trường + 1 lớp.

   Assignment.SchoolId GIỮ NGUYÊN (không xóa, vẫn NOT NULL) làm TRƯỜNG CHÍNH
   của phiếu — dùng cho các màn tổng hợp và cho dữ liệu cũ. Tầng Service ưu
   tiên trường của slot, chỉ fallback về Assignment.SchoolId khi slot chưa gán
   (xem schoolIdOf() ở ScheduleService).

   VÌ SAO BACKFILL TỪ Assignment.SchoolId CHỨ KHÔNG TỪ SchoolClass.SchoolId:
   lớp CÓ THỂ được chuyển sang trường khác (SchoolClassService cho phép), nên
   trường hiện tại của lớp không nhất thiết là trường lúc phân công. SchoolId
   của phiếu mới là sự thật lịch sử, và nó cũng đúng bằng thứ mà mọi màn hình
   đang đọc hôm nay → backfill kiểu này bảo đảm KHÔNG đổi hành vi dữ liệu cũ.

   Guard COL_LENGTH/sys.* để chạy lại nhiều lần vẫn an toàn (idempotent).
   Các lệnh chạm tới cột VỪA thêm phải bọc EXEC(): SQL Server phân tích cả
   file như MỘT lô lệnh nên cột chưa tồn tại tại thời điểm parse.
   ===================================================================== */
IF COL_LENGTH('AssignmentSlot', 'SchoolId') IS NULL
    ALTER TABLE AssignmentSlot ADD SchoolId INT NULL;

/* Backfill: mọi slot cũ thừa hưởng trường của phân công cha → không đổi hành vi hiển thị. */
EXEC('
    UPDATE s
       SET s.SchoolId = a.SchoolId
      FROM AssignmentSlot s
      JOIN Assignment a ON a.Id = s.AssignmentId
     WHERE s.SchoolId IS NULL;
');

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_AssignmentSlot_School')
    EXEC('
        ALTER TABLE AssignmentSlot
            ADD CONSTRAINT FK_AssignmentSlot_School
                FOREIGN KEY (SchoolId) REFERENCES School(Id);
    ');

/* Màn xếp lịch hỏi "trường này đã có ô lịch nào bị chiếm" để khóa sẵn ô trên lưới
   (GET /assignments/class-busy) — không có index thì mỗi lần mở lưới là một lần quét bảng. */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
         WHERE name = 'IX_AssignmentSlot_School_Day'
           AND object_id = OBJECT_ID('AssignmentSlot'))
    EXEC('CREATE INDEX IX_AssignmentSlot_School_Day ON AssignmentSlot(SchoolId, DayOfWeek);');
