package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO ghi/sửa 1 dòng chấm công (POST/PUT /api/v1/attendance).
 *
 * <p>Giờ vào/ra định dạng ISO "HH:mm" (vd "07:00"). Số giờ dạy do Service tự tính
 * từ checkIn/checkOut, frontend không gửi.
 */
public record AttendanceRequest(
        @NotNull(message = "Vui lòng chọn giáo viên") Integer teacherId,

        /** Buổi dạy tương ứng (nullable — có thể chấm công lẻ). */
        Long scheduleId,
        @NotNull(message = "Vui lòng chọn ngày làm việc") LocalDate workDate,
        LocalTime checkIn,
        LocalTime checkOut,

        /** PRESENT | ABSENT | LATE | LEAVE */
        @Pattern(regexp = "PRESENT|ABSENT|LATE|LEAVE", message = "Trạng thái không hợp lệ") String status,

        String note) {}
