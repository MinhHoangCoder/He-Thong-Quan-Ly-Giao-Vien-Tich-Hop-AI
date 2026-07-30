package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body tạo / sửa đánh giá giáo viên (INPUT).
 *
 * <p>SchoolId do server gán theo vai trò (chống spoof).
 * {@code forceDuplicate=true} khi client đã xác nhận lưu dù trùng kỳ.
 *
 * <p>Module đánh giá chỉ có 2 file DTO:
 * <ul>
 *   <li>{@link EvaluationRequest} — client gửi lên</li>
 *   <li>{@link EvaluationResponse} — server trả về (+ các kiểu lồng bên trong)</li>
 * </ul>
 */
public record EvaluationRequest(
        @NotNull(message = "Thiếu giáo viên (teacherId)") Integer teacherId,

        @NotNull(message = "Thiếu điểm đánh giá") @Min(value = 1, message = "Điểm tối thiểu là 1") @Max(value = 5, message = "Điểm tối đa là 5") Short score,

        @Size(max = 300, message = "Nhận xét tối đa 300 ký tự") String comment,

        @NotBlank(message = "Kỳ đánh giá không được để trống") @Size(max = 50, message = "Kỳ đánh giá tối đa 50 ký tự") String periodNote,
        /** Chỉ dùng khi CREATE — bỏ qua cảnh báo trùng kỳ. */
        Boolean forceDuplicate) {}
