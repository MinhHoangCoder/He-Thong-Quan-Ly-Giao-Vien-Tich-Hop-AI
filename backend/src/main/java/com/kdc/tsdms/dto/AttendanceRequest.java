package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO ghi/sửa 1 dòng chấm công của ADMIN/kế toán (POST/PUT /api/v1/attendance).
 *
 * <p>Giờ vào/ra định dạng ISO "HH:mm" (vd "07:00"). Số giờ dạy do Service tự tính
 * từ checkIn/checkOut, frontend không gửi.
 *
 * <p>Từ V25, mọi thao tác tay đều phải kèm {@code adjustReason}: nguồn công chính thức là
 * giáo viên tự chấm, nên người ngoài động vào dòng nào là phải giải trình dòng đó.
 */
public record AttendanceRequest(
        @NotNull(message = "Vui lòng chọn giáo viên") Integer teacherId,

        /**
         * Buổi dạy tương ứng — BẮT BUỘC từ V25. Chấm công không gắn buổi thì không có mốc
         * giờ nào để kiểm tra "đã tới giờ dạy chưa", tức là đường vòng qua luật cấm chấm
         * công cho buổi chưa diễn ra.
         */
        @NotNull(message = "Vui lòng chọn buổi dạy") Long scheduleId,
        @NotNull(message = "Vui lòng chọn ngày làm việc") LocalDate workDate,
        LocalTime checkIn,
        LocalTime checkOut,

        /** PRESENT | ABSENT | LATE | LEAVE */
        @Pattern(regexp = "PRESENT|ABSENT|LATE|LEAVE", message = "Trạng thái không hợp lệ") String status,

        String note,

        /** Lý do can thiệp tay — bắt buộc, lưu lại trên chính dòng chấm công. */
        @NotBlank(message = "Vui lòng nhập lý do điều chỉnh") @Size(max = 255, message = "Lý do tối đa 255 ký tự") String adjustReason) {

    /**
     * Body GV tự check-in/out (POST /checkin, /checkout).
     * Giờ vào/ra KHÔNG nhận từ client — server tự lấy giờ hiện tại (chống sửa giờ).
     */
    public record Checkin(
            @NotNull(message = "Thiếu buổi dạy (scheduleId)")
            Long scheduleId,

            String note) {}
}
