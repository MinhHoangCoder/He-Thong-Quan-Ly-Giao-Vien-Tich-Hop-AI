package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body cho POST /api/auth/register. Admin/Nhân viên tạo tài khoản cho GV/trường.
 * role chỉ chấp nhận "TEACHER" hoặc "SCHOOL".
 */
public record RegisterRequest(
        @NotBlank(message = "Thiếu vai trò (TEACHER|SCHOOL)")
        String role,

        @NotBlank(message = "Thiếu username") String username,
        @NotBlank @Email(message = "Email không hợp lệ") String email,

        // Họ tên: GIÁO VIÊN dùng firstName (tên gọi) + lastName (họ và tên đệm); TRƯỜNG dùng
        // fullName (tên trường). Bắt buộc theo role -> kiểm tra trong RegistrationService.
        String firstName,
        String lastName,
        String fullName,

        // Lookahead (?=...) = "phải chứa ít nhất 1 ký tự loại này". Max 72: BCrypt chỉ băm 72 byte đầu.
        @NotBlank @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$",
                message = "Mật khẩu 8–72 ký tự, phải có chữ hoa, chữ thường và chữ số")
        String password,

        String phone,
        @NotNull(message = "Thiếu chi nhánh") Integer branchId) {}
