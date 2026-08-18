package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * Giáo viên xin bổ sung chấm công cho một buổi đã lỡ (POST /attendance/amend-requests).
 *
 * <p>Giáo viên TỰ KHAI giờ vào/ra — khác hẳn check-in thường (giờ do server ghi, client
 * không gửi được). Đây là chỗ duy nhất giáo viên nói được giờ của mình, nên bắt buộc kèm
 * lý do và phải qua admin duyệt mới thành công.
 */
public record AttendanceAmendCreateRequest(
        @NotNull(message = "Thiếu buổi dạy (scheduleId)") Long scheduleId,

        @NotBlank(message = "Vui lòng nhập lý do xin bổ sung") @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason,

        @NotNull(message = "Vui lòng nhập giờ vào") LocalTime proposedCheckIn,
        @NotNull(message = "Vui lòng nhập giờ ra") LocalTime proposedCheckOut) {}
