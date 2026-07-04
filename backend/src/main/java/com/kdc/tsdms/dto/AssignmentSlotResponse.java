package com.kdc.tsdms.dto;

import java.time.LocalTime;

import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Period;

/**
 * DTO response cho 1 slot (Thứ + Tiết) — trả về kèm thông tin tiết học
 * (PeriodNumber, StartTime, EndTime) để frontend hiển thị trực tiếp,
 * không cần gọi thêm API tra Period.
 */
public class AssignmentSlotResponse {

    public Integer id;
    public Integer assignmentId;

    /** MON | TUE | WED | THU | FRI | SAT | SUN */
    public String dayOfWeek;

    /** Tên hiển thị: "Thứ 2", "Thứ 3" ... "Chủ nhật" — Service/Response tự map từ dayOfWeek. */
    public String dayOfWeekLabel;

    // ── Thông tin tiết học (join từ Period) ─────────────────────────────
    public Integer periodId;

    /** Số thứ tự tiết trong ngày của trường đó, vd: 1, 2, 3... */
    public Short periodNumber;

    /** MORNING | AFTERNOON */
    public String sessionType;

    /** Giờ bắt đầu tiết, vd: 07:00 */
    public LocalTime startTime;

    /** Giờ kết thúc tiết, vd: 07:35 */
    public LocalTime endTime;

    /** ACTIVE | CANCELLED */
    public String status;

    /**
     * Factory method tổng hợp từ entity AssignmentSlot + entity Period (join thủ công).
     * Service gọi sau khi đã load cả 2 entity riêng.
     *
     * @param slot   entity AssignmentSlot
     * @param period entity Period tương ứng (có thể null nếu Period bị xóa mềm)
     */
    public static AssignmentSlotResponse fromEntity(AssignmentSlot slot, Period period) {
        AssignmentSlotResponse r = new AssignmentSlotResponse();
        r.id           = slot.getId();
        r.assignmentId = slot.getAssignmentId();
        r.dayOfWeek    = slot.getDayOfWeek();
        r.dayOfWeekLabel = mapDayLabel(slot.getDayOfWeek()); // tự map sang tiếng Việt
        r.periodId     = slot.getPeriodId();
        

        // Gắn thêm thông tin tiết học nếu Period còn tồn tại
        if (period != null) {
            r.periodNumber = period.getPeriodNumber();
            r.sessionType  = period.getSessionType();
            r.startTime    = period.getStartTime();
            r.endTime      = period.getEndTime();
        }
        return r;
    }

    /** Map mã thứ sang tên tiếng Việt để frontend hiển thị trực tiếp. */
    private static String mapDayLabel(String dayOfWeek) {
        if (dayOfWeek == null) return "";
        return switch (dayOfWeek) {
            case "MON" -> "Thứ 2";
            case "TUE" -> "Thứ 3";
            case "WED" -> "Thứ 4";
            case "THU" -> "Thứ 5";
            case "FRI" -> "Thứ 6";
            case "SAT" -> "Thứ 7";
            case "SUN" -> "Chủ nhật";
            default    -> dayOfWeek;
        };
    }
}