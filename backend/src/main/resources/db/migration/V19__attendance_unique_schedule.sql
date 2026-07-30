-- V16: moi buoi day (ScheduleId) chi co TOI DA 1 dong cham cong.
-- Ly do: 2 request check-in dong thoi (double-click) cung vuot qua buoc kiem tra
-- "da co ban ghi chua" o tang service roi cung INSERT -> 2 dong cham cong cho
-- cung 1 buoi -> luong tinh doi. Unique index chan tan goc o DB.

-- 1) Don du lieu trung neu co (giu dong Id nho nhat cho moi ScheduleId).
DELETE a
FROM dbo.Attendance a
JOIN dbo.Attendance b
    ON b.ScheduleId = a.ScheduleId AND b.Id < a.Id
WHERE a.ScheduleId IS NOT NULL;

-- 2) Unique index co loc: ScheduleId NULL = cham cong tay khong gan buoi day
--    (van cho phep nhieu dong), con moi ScheduleId cu the chi 1 dong.
CREATE UNIQUE NONCLUSTERED INDEX UX_Attendance_ScheduleId
    ON dbo.Attendance (ScheduleId)
    WHERE ScheduleId IS NOT NULL;
