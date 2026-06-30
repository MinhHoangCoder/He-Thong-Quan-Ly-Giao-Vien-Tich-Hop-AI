package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body tạo mới / sửa môn học (Subject), dùng trong trang "Nhóm môn học"
 * (quản lý môn lồng trong từng nhóm). categoryId BẮT BUỘC — đây là điểm mấu
 * chốt để tránh lặp lại lỗi "môn học không có nhóm" (CategoryId null) như cũ.
 */
public record SubjectRequest(
        @NotBlank(message = "Mã môn không được để trống") @Pattern(regexp = "^[A-Z0-9_]{2,20}$", message = "Mã môn chỉ gồm chữ hoa, số, dấu gạch dưới (2-20 ký tự)")
        String code,

        @NotBlank(message = "Tên môn học không được để trống") @Size(max = 150, message = "Tên môn tối đa 150 ký tự") String name,

        @NotNull(message = "Vui lòng chọn nhóm môn") Integer categoryId,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự") String description,

        @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "Status phải là ACTIVE hoặc DISABLED")
        String status) {}
