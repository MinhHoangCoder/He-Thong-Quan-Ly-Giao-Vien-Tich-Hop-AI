package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body mở lại phiếu lương đã chốt (POST /api/v1/payroll/{id}/reopen và /reopen-period).
 *
 * <p>Lý do BẮT BUỘC, cùng nguyên tắc với {@link AttendanceRequest#adjustReason()}: mở khóa một
 * kỳ lương đã chốt là thao tác người ngoài nhìn vào phải hiểu được vì sao, không phải một cú
 * bấm nút im lặng.
 */
public record PayrollReopenRequest(
        @NotBlank(message = "Vui lòng nhập lý do mở lại bảng lương") @Size(max = 255, message = "Lý do tối đa 255 ký tự") String reason) {}
