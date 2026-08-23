package com.kdc.tsdms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Khai một mức đơn giá tiết dạy.
 *
 * <p>Không có {@code effectiveTo}: đóng một mức là việc của {@code PayRateService.close()},
 * không phải của form khai giá. Cho sửa hai đầu khoảng cùng lúc là mở đường tạo ra hai mức
 * chồng nhau cho cùng một khối.
 */
public record PayRateRequest(
        @NotNull(message = "Thiếu khối bắt đầu") @Min(value = 1, message = "Khối từ 1 đến 12") @Max(value = 12, message = "Khối từ 1 đến 12") Short gradeFrom,

        @NotNull(message = "Thiếu khối kết thúc") @Min(value = 1, message = "Khối từ 1 đến 12") @Max(value = 12, message = "Khối từ 1 đến 12") Short gradeTo,

        @NotNull(message = "Vui lòng nhập đơn giá") @DecimalMin(value = "1", message = "Đơn giá phải lớn hơn 0") BigDecimal amount,

        @NotNull(message = "Vui lòng chọn ngày bắt đầu áp dụng") LocalDate effectiveFrom,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự") String note) {}
