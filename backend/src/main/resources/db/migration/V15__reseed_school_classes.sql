/* =====================================================================
   V15 — RESET dữ liệu giao dịch + SEED LẠI danh sách lớp cho các trường.

   Bối cảnh: đổi danh sách lớp sang quy ước mới (nhiều lớp/khối, tên kiểu
   1A1/6D2/9A3). Vì lớp cũ đang bị Phân công tham chiếu (kéo theo Lịch dạy /
   Chấm công / Bảng lương), nên theo yêu cầu (reset sạch) migration này:
     1) Xóa TOÀN BỘ dữ liệu giao dịch theo đúng thứ tự khóa ngoại.
     2) Xóa lớp cũ (+ ClassEnrollment) của các trường ĐANG hoạt động
        (bỏ qua THPT đã xóa mềm).
     3) Chèn danh sách lớp mới cho 5 trường (3 TH khối 1–5, 2 THCS khối 6–9),
        số lớp/khối lệch nhau để không trường nào giống trường nào.

   Quy ước tên: chữ cái theo khối 1–5=A, 6=D, 7=C, 8=B, 9=A; tên = <khối><chữ><số>.
   GradeLevel lưu số "1".."9" (Bảng lương suy đơn giá TH/THCS từ đây).
   Có guard JOIN School (IsDeleted=0) nên chạy an toàn cả trên DB chưa có đủ trường.
   ===================================================================== */

/* 1) Reset dữ liệu giao dịch — đúng thứ tự FK:
   Payroll → Attendance → ScheduleStatusLog → Schedule → AssignmentSlot → Assignment. */
DELETE FROM Payroll;
DELETE FROM Attendance;
DELETE FROM ScheduleStatusLog;
DELETE FROM Schedule;
DELETE FROM AssignmentSlot;
DELETE FROM Assignment;

/* 2) Xóa lớp cũ (+ enrollment) của các trường sẽ seed lại — giữ nguyên lớp trường khác. */
DELETE FROM ClassEnrollment WHERE ClassId IN (SELECT Id FROM SchoolClass WHERE SchoolId IN (4, 5, 6, 7, 8));
DELETE FROM SchoolClass WHERE SchoolId IN (4, 5, 6, 7, 8);

/* 3) Chèn lớp mới. Bảng kế hoạch (Trường, Khối, Chữ, SốLớp) trải thành từng lớp
      bằng cách nối với dãy số 1..5. Chỉ chèn cho trường CÓ THẬT & chưa xóa. */
;WITH Nums(n) AS (
    SELECT n FROM (VALUES (1), (2), (3), (4), (5)) v(n)
),
ClassPlan(SchoolId, Grade, Letter, Sections) AS (
    SELECT * FROM (VALUES
        -- TH Dư Hàng (#4) — khối 1–5, chữ A
        (4, 1, 'A', 5), (4, 2, 'A', 5), (4, 3, 'A', 4), (4, 4, 'A', 4), (4, 5, 'A', 3),
        -- TH Lê Văn Tám (#5)
        (5, 1, 'A', 5), (5, 2, 'A', 4), (5, 3, 'A', 5), (5, 4, 'A', 3), (5, 5, 'A', 4),
        -- TH Nguyễn Công Hòa (#8)
        (8, 1, 'A', 4), (8, 2, 'A', 5), (8, 3, 'A', 3), (8, 4, 'A', 5), (8, 5, 'A', 5),
        -- THCS Chu Văn An (#6) — khối 6–9, chữ 6=D 7=C 8=B 9=A
        (6, 6, 'D', 4), (6, 7, 'C', 4), (6, 8, 'B', 3), (6, 9, 'A', 4),
        -- THCS Vinh Quang (#7)
        (7, 6, 'D', 3), (7, 7, 'C', 4), (7, 8, 'B', 4), (7, 9, 'A', 3)
    ) p(SchoolId, Grade, Letter, Sections)
)
INSERT INTO SchoolClass (SchoolId, Name, GradeLevel, SchoolYear, Status, IsDeleted, CreatedAt)
SELECT p.SchoolId,
       CONCAT(p.Grade, p.Letter, n.n),      -- vd 1A1, 6D2, 9A3
       CAST(p.Grade AS VARCHAR(2)),          -- GradeLevel = "1".."9"
       '2026-2027',
       'ACTIVE',
       0,
       SYSUTCDATETIME()
FROM ClassPlan p
JOIN Nums n ON n.n <= p.Sections
JOIN School s ON s.Id = p.SchoolId AND s.IsDeleted = 0
ORDER BY p.SchoolId, p.Grade, n.n;
