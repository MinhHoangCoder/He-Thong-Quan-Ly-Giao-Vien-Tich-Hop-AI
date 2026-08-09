package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Tab "Hôm nay" của admin — MỌI buổi dạy đã duyệt trong ngày, kèm buổi nào đã được chấm
 * và buổi nào chưa.
 *
 * <p>Dựng từ LỊCH DẠY chứ không từ bảng chấm công: buổi giáo viên chưa bấm gì thì chưa có
 * dòng chấm công nào, nhìn bảng chấm công sẽ không thấy nó tồn tại.
 */
public record AttendanceTodayResponse(LocalDate date, Summary summary, List<Row> sessions) {

    /**
     * Một buổi dạy hôm nay.
     *
     * @param state NOT_YET (chưa tới cửa sổ) | OPEN (đang mở, chưa chấm) | CHECKED_IN (đã
     *     vào, chưa ra) | DONE (đủ vào/ra) | MISSED (hết buổi mà chưa chấm) | ABSENT (hệ
     *     thống/admin đã ghi Vắng hoặc Nghỉ phép)
     */
    public record Row(
            Long scheduleId,
            Integer teacherId,
            String teacherName,
            String schoolName,
            String className,
            String subjectName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String state,
            String attendanceStatus,
            String checkInMethod,
            LocalTime checkIn,
            LocalTime checkOut,
            boolean autoCheckOut) {}

    /** Số liệu cho dải thẻ trên đầu tab — cùng công thức với thông báo tổng kết cuối ngày. */
    public record Summary(int total, int marked, int late, int absent, int missing, int autoClosed) {}
}
