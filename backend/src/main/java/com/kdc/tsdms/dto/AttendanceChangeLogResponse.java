package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.AttendanceChangeLog;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Một dòng nhật ký thay đổi chấm công.
 *
 * @param actionLabel nhãn tiếng Việt của {@code action} — dựng ở backend để mọi màn hình
 *     gọi tên giống nhau
 * @param changedByName tên người sửa; "Hệ thống" khi job tự khép sổ
 */
public record AttendanceChangeLogResponse(
        Long id,
        String action,
        String actionLabel,
        String oldStatus,
        String newStatus,
        LocalTime oldCheckIn,
        LocalTime newCheckIn,
        LocalTime oldCheckOut,
        LocalTime newCheckOut,
        String changedByName,
        Instant changedAt) {

    public static AttendanceChangeLogResponse fromEntity(AttendanceChangeLog l, String changedByName) {
        return new AttendanceChangeLogResponse(
                l.getId(),
                l.getAction(),
                label(l.getAction()),
                l.getOldStatus(),
                l.getNewStatus(),
                l.getOldCheckIn(),
                l.getNewCheckIn(),
                l.getOldCheckOut(),
                l.getNewCheckOut(),
                l.getChangedByUserId() == null ? "Hệ thống" : changedByName,
                l.getChangedAt());
    }

    private static String label(String action) {
        return switch (action == null ? "" : action) {
            case "CREATE" -> "Tạo dòng chấm công";
            case "UPDATE" -> "Sửa chấm công";
            case "AUTO_CHECKOUT" -> "Hệ thống chốt giờ ra";
            case "AUTO_ABSENT" -> "Hệ thống ghi Vắng";
            default -> action;
        };
    }
}
