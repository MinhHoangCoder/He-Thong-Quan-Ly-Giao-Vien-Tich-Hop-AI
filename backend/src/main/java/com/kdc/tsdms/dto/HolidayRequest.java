package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO tạo/sửa một kỳ nghỉ (POST/PUT /api/v1/holidays).
 *
 * <p>Nghỉ một ngày thì {@code fromDate} = {@code toDate} — form chỉ hiện một ô ngày và tự
 * điền cả hai, người dùng không phải hiểu chuyện khoảng ngày.
 */
public record HolidayRequest(
        @NotNull(message = "Vui lòng chọn ngày bắt đầu") LocalDate fromDate,
        @NotNull(message = "Vui lòng chọn ngày kết thúc") LocalDate toDate,

        @NotBlank(message = "Tên kỳ nghỉ không được để trống") @Size(max = 150, message = "Tên kỳ nghỉ tối đa 150 ký tự") String name,

        /** NATIONAL (lễ theo luật) | BREAK (kỳ nghỉ của học sinh) | CENTER (trung tâm nghỉ riêng). */
        @Pattern(regexp = "^(NATIONAL|BREAK|CENTER)$", message = "Loại kỳ nghỉ phải là NATIONAL, BREAK hoặc CENTER")
        String kind,

        /** Bỏ trống = áp dụng toàn hệ thống; có giá trị = chỉ riêng trường đó nghỉ. */
        Integer schoolId,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự") String note) {}
