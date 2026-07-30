/* =====================================================================
   V16 — PHÂN CÔNG THEO TỪNG TIẾT: mỗi ô Thứ+Tiết mang LỚP riêng.

   Trước V16, lớp nằm ở bảng cha (Assignment.ClassId) nên MỌI tiết của một
   phân công buộc phải cùng một lớp — không mô tả được nghiệp vụ thật
   "sáng tiết 1 dạy 1A1, tiết 2 dạy 1A2, tiết 3 dạy 1A3".
   Hạ ClassId xuống bảng con AssignmentSlot để đúng grain "1 tiết = 1 lớp".

   Assignment.ClassId GIỮ NGUYÊN (không xóa) làm lớp mặc định/đại diện —
   dữ liệu cũ và các API cũ vẫn đọc được; tầng Service ưu tiên lớp của slot
   và chỉ fallback về Assignment.ClassId khi slot chưa gán.

   Guard COL_LENGTH/sys.* để chạy lại nhiều lần vẫn an toàn (idempotent).
   Các lệnh chạm tới cột VỪA thêm phải bọc EXEC(): SQL Server phân tích cả
   file như MỘT lô lệnh nên cột chưa tồn tại tại thời điểm parse.
   ===================================================================== */
IF COL_LENGTH('AssignmentSlot', 'ClassId') IS NULL
    ALTER TABLE AssignmentSlot ADD ClassId INT NULL;

/* Backfill: mọi slot cũ thừa hưởng lớp của phân công cha → không đổi hành vi hiển thị. */
EXEC('
    UPDATE s
       SET s.ClassId = a.ClassId
      FROM AssignmentSlot s
      JOIN Assignment a ON a.Id = s.AssignmentId
     WHERE s.ClassId IS NULL
       AND a.ClassId IS NOT NULL;
');

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_AssignmentSlot_Class')
    EXEC('
        ALTER TABLE AssignmentSlot
            ADD CONSTRAINT FK_AssignmentSlot_Class
                FOREIGN KEY (ClassId) REFERENCES SchoolClass(Id);
    ');

/* Tra "lớp này đang được dạy ở những ô lịch nào" + join lấy tên lớp khi dựng response. */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
         WHERE name = 'IX_AssignmentSlot_ClassId'
           AND object_id = OBJECT_ID('AssignmentSlot'))
    EXEC('CREATE INDEX IX_AssignmentSlot_ClassId ON AssignmentSlot(ClassId);');

/* Dò trùng lịch nay quét theo GV + THỨ (so khoảng giờ thật của tiết, không so PeriodId
   nữa — hai trường khác nhau có PeriodId khác nhau nhưng giờ vẫn đè lên nhau). */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
         WHERE name = 'IX_AssignmentSlot_Teacher_Day'
           AND object_id = OBJECT_ID('AssignmentSlot'))
    CREATE INDEX IX_AssignmentSlot_Teacher_Day ON AssignmentSlot(TeacherId, DayOfWeek);
