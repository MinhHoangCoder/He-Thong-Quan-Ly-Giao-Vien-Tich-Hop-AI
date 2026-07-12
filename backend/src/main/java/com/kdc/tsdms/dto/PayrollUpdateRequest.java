package com.kdc.tsdms.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * DTO điều chỉnh 1 dòng lương (PUT /api/v1/payroll/{id}) — kế toán chỉnh lương cứng,
 * đơn giá giờ, phụ cấp, thưởng, khấu trừ. NetAmount DB tự tính lại.
 */
public record PayrollUpdateRequest(
        @PositiveOrZero(message = "Lương cứng không âm") BigDecimal baseSalary,
        @PositiveOrZero(message = "Đơn giá giờ không âm") BigDecimal ratePerHour,
        @PositiveOrZero(message = "Phụ cấp không âm") BigDecimal allowance,
        @PositiveOrZero(message = "Thưởng không âm") BigDecimal bonus,
        @PositiveOrZero(message = "Khấu trừ không âm") BigDecimal deduction) {}
