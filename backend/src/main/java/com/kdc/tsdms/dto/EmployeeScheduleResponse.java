package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.kdc.tsdms.entity.EmployeeSchedule;

/**
 * DTO response cho 1 buổi làm việc thực tế của nhân viên.
 * Dùng cho:
 * <ul>
 *   <li>Nhân viên xem lịch của mình theo tuần.</li>
 *   <li>HR/Admin xem tổng hợp lịch tất cả nhân viên (màn hình timetable nhân viên).</li>
 * </ul>
 */
public class EmployeeScheduleResponse {

    public Long id;

    // ── Nhân viên ─────────────────────────────────────────────────────────
    public Integer employeeId;
    public String employeeName;
    /** Loại hình: FULL_TIME | PART_TIME — giúp frontend phân biệt màu hiển thị. */
    public String employmentType;

    // ── Ca làm việc ───────────────────────────────────────────────────────
    public LocalDate workDate;
    /**
     * Thứ trong tuần tính từ workDate — tự map trong fromEntity() để frontend
     * group timetable 7 cột mà không cần tự tính.
     * MON | TUE | WED | THU | FRI
     */
    public String dayOfWeek;

    public String dayOfWeekLabel;

    /** MORNING | AFTERNOON */
    public String shiftType;

    public String shiftLabel;

    /** Giờ bắt đầu (08:00 hoặc 14:00). */
    public LocalTime startTime;
    /** Giờ kết thúc (11:00 hoặc 17:00). */
    public LocalTime endTime;

    // ── Trạng thái ────────────────────────────────────────────────────────
    /** SCHEDULED | ON_LEAVE | CANCELLED */
    public String status;

    public String statusLabel;

    /** FIXED | FROM_REQUEST | MANUAL — giúp HR biết nguồn gốc tạo lịch. */
    public String source;

    /** Ghi chú (từ đơn đăng ký hoặc HR tự thêm). */
    public String note;

    /**
     * Factory method build từ entity EmployeeSchedule + tên NV + loại hình làm việc.
     *
     * @param s              entity EmployeeSchedule
     * @param employeeName   họ tên nhân viên
     * @param employmentType loại hình làm việc của NV (FULL_TIME / PART_TIME)
     */
    public static EmployeeScheduleResponse fromEntity(EmployeeSchedule s, String employeeName, String employmentType) {

        EmployeeScheduleResponse r = new EmployeeScheduleResponse();
        r.employeeId = s.getEmployeeId();
        r.employeeName = employeeName;
        r.employmentType = employmentType;
        r.workDate = s.getWorkDate();
        r.shiftType = s.getShiftType();
        r.shiftLabel = mapShiftLabel(s.getShiftType());
        r.startTime = s.getStartTime();
        r.endTime = s.getEndTime();
        r.status = s.getStatus();
        r.statusLabel = mapStatusLabel(s.getStatus());
        r.source = s.getSource();
        r.note = s.getNote();

        // Tự tính thứ từ workDate để frontend group timetable
        if (s.getWorkDate() != null) {
            r.dayOfWeek = mapDayOfWeek(s.getWorkDate());
            r.dayOfWeekLabel = mapDayLabel(r.dayOfWeek);
        }
        return r;
    }

    private static String mapDayOfWeek(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

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
            default -> dayOfWeek;
        };
    }

    private static String mapShiftLabel(String shiftType) {
        if (shiftType == null) return "";
        return switch (shiftType) {
            case "MORNING" -> "Sáng 08:00–11:00";
            case "AFTERNOON" -> "Chiều 14:00–17:00";
            default -> shiftType;
        };
    }

    private static String mapStatusLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "SCHEDULED" -> "Đã xếp lịch";
            case "ON_LEAVE" -> "Nghỉ phép / nghỉ lễ";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
}
