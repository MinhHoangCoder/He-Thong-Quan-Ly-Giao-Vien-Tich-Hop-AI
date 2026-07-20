package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body tạo/sửa lớp học (SchoolClass). */
public record SchoolClassRequest(
        @NotNull(message = "Trường không được để trống") Integer schoolId,

        @NotBlank(message = "Tên lớp không được để trống") @Size(max = 100, message = "Tên lớp tối đa 100 ký tự") String name,

        @Size(max = 50, message = "Khối tối đa 50 ký tự") String gradeLevel,

        @NotBlank(message = "Năm học không được để trống") @Size(max = 20, message = "Năm học tối đa 20 ký tự") @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "Năm học dạng YYYY-YYYY (vd: 2025-2026)")
        String schoolYear,

        @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "Status phải là ACTIVE hoặc INACTIVE")
        String status) {}
