package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body cho PUT /api/v1/me/profile — user tự sửa thông tin liên hệ của CHÍNH MÌNH.
 * Chỉ cho sửa email + SĐT; họ tên/username/chức danh do phòng Nhân sự / admin quản.
 */
public record UpdateProfileRequest(
        @NotBlank @Email(message = "Email không hợp lệ") String email,

        // SĐT không bắt buộc; nếu có thì theo dạng Việt Nam: 0/+84 + 9-10 chữ số
        // (cùng quy ước với TeacherResponse — dự án chỉ phục vụ người dùng Việt).
        @Pattern(regexp = "^$|^(\\+84|0)\\d{9,10}$", message = "SĐT phải bắt đầu bằng 0 hoặc +84, gồm 10–11 chữ số")
        String phone) {}
