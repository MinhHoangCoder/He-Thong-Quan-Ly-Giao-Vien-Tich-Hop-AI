package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.Attendance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * DTO chi tiết 1 dòng chấm công — kèm tên GV + số giờ làm (tự tính từ giờ vào/ra)
 * để frontend hiển thị trực tiếp.
 */
public class AttendanceResponse {

    public Long id;
    public Integer teacherId;
    public String teacherName;
    public Long scheduleId;
    public LocalDate workDate;
    public LocalTime checkIn;
    public LocalTime checkOut;

    /** Số giờ làm = checkOut − checkIn (0 nếu thiếu / vắng). */
    public BigDecimal hours;

    /** PRESENT | ABSENT | LATE | LEAVE */
    public String status;

    public String checkInMethod;
    public String note;

    /* ── Thông tin buổi dạy (ghép từ Schedule → Assignment/Period), null nếu chấm công không gắn buổi. ── */
    public Integer schoolId;
    public String schoolName;
    public Integer classId;
    public String className;
    public Integer subjectId;
    public String subjectName;
    public Integer periodId;
    public Short periodNumber;

    /** Số tiết trong BUỔI (chiều đánh lại từ 1) — nhãn "Chiều · Tiết 5" lấy từ đây. */
    public Short indexInSession;

    /** MORNING | AFTERNOON */
    public String sessionType;

    public static AttendanceResponse fromEntity(Attendance a, String teacherName) {
        AttendanceResponse r = new AttendanceResponse();
        r.id = a.getId();
        r.teacherId = a.getTeacherId();
        r.teacherName = teacherName;
        r.scheduleId = a.getScheduleId();
        r.workDate = a.getWorkDate();
        r.checkIn = a.getCheckIn();
        r.checkOut = a.getCheckOut();
        r.hours = computeHours(a.getCheckIn(), a.getCheckOut());
        r.status = a.getStatus();
        r.checkInMethod = a.getCheckInMethod();
        r.note = a.getNote();
        return r;
    }

    /** Số giờ giữa 2 mốc, làm tròn 2 chữ số; null/âm → 0. */
    public static BigDecimal computeHours(LocalTime in, LocalTime out) {
        if (in == null || out == null || !out.isAfter(in)) {
            return BigDecimal.ZERO;
        }
        long minutes = Duration.between(in, out).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /**
     * Trạng thái check-in của MỘT buổi dạy hôm nay (GET /checkin/today).
     *
     * @param state OPEN (chưa check-in) | CHECKED_IN (đã vào, chưa ra) | DONE (đã vào + ra)
     */
    public record CheckinSession(
            Long scheduleId,
            Integer schoolId,
            String schoolName,
            String subjectName,
            String className,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime,
            String state,
            LocalTime checkIn,
            LocalTime checkOut) {}

    /** Toàn cảnh check-in hôm nay của GV đang đăng nhập. */
    public record CheckinToday(LocalDate date, List<CheckinSession> sessions) {}
}
