package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body tạo / sửa đánh giá giáo viên. SchoolId do server gán theo vai trò (chống spoof). */
public record EvaluationRequest(
        @NotNull(message = "Thiếu giáo viên (teacherId)") Integer teacherId,

        @NotNull(message = "Thiếu điểm đánh giá") @Min(value = 1, message = "Điểm tối thiểu là 1") @Max(value = 5, message = "Điểm tối đa là 5") Short score,

        @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự") String comment,

        @Size(max = 50, message = "Kỳ đánh giá tối đa 50 ký tự") String periodNote) {}
