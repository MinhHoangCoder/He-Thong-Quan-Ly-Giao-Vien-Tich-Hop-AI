package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body tạo/sửa lớp học (SchoolClass).
 *
 * <p>Tên lớp chuẩn: {khối}{1 chữ A–Z}{số 1–20 bắt buộc}, vd 7A1, 6B20 (không 7A).
 * GradeLevel gửi "Khối 7" (service chuẩn hóa nếu client gửi "7" / "Lớp 7").
 */
public record SchoolClassRequest(
        @NotNull(message = "Trường không được để trống") Integer schoolId,

        @NotBlank(message = "Tên lớp không được để trống") @Size(max = 20, message = "Tên lớp tối đa 20 ký tự") @Pattern(
                regexp = "^([1-9]|1[0-2])[A-Za-z](20|[1-9]|1[0-9])$",
                message = "Tên lớp dạng 7A1 / 6B20 (1 chữ + số 1–20, bắt buộc có số)")
        String name,

        @NotBlank(message = "Khối không được để trống") @Size(max = 50, message = "Khối tối đa 50 ký tự") String gradeLevel,

        @NotBlank(message = "Năm học không được để trống") @Size(max = 20, message = "Năm học tối đa 20 ký tự") @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "Năm học dạng YYYY-YYYY (vd: 2025-2026)")
        String schoolYear,

        @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "Status phải là ACTIVE hoặc INACTIVE")
        String status) {}
