package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body cho POST /api/auth/register. Admin/Nhân viên tạo tài khoản cho GV/trường.
 * role chỉ chấp nhận "TEACHER" hoặc "SCHOOL".
 */
public record RegisterRequest(
        @NotBlank(message = "Thiếu vai trò (TEACHER|SCHOOL)")
        String role,

        @NotBlank(message = "Thiếu username") String username,
        @NotBlank @Email(message = "Email không hợp lệ") String email,
        @NotBlank(message = "Thiếu họ tên / tên trường") String fullName,

        @NotBlank @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự") String password,

        String phone,
        @NotNull(message = "Thiếu chi nhánh") Integer branchId) {}
