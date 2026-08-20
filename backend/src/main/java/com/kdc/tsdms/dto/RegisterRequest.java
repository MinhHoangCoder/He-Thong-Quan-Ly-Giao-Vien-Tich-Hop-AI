package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body cho POST /api/auth/register. Admin/Nhân viên tạo tài khoản GIÁO VIÊN.
 *
 * <p>Trước 2026-08-19 endpoint này còn tạo được tài khoản cho TRƯỜNG. Trường nay không còn
 * là người dùng của hệ thống (Flyway V31), nên {@code role} chỉ nhận "TEACHER" — giữ lại
 * trường này thay vì bỏ hẳn để client cũ gửi role sai vẫn nhận được thông báo rõ ràng.
 */
public record RegisterRequest(
        @NotBlank(message = "Thiếu vai trò (TEACHER)") String role,

        @NotBlank(message = "Thiếu username") String username,
        @NotBlank @Email(message = "Email không hợp lệ") String email,

        // Họ tên giáo viên: firstName (tên gọi) + lastName (họ và tên đệm).
        String firstName,
        String lastName,

        // Lookahead (?=...) = "phải chứa ít nhất 1 ký tự loại này". Max 72: BCrypt chỉ băm 72 byte đầu.
        @NotBlank @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$",
                message = "Mật khẩu 8–72 ký tự, phải có chữ hoa, chữ thường và chữ số")
        String password,

        String phone,
        @NotNull(message = "Thiếu chi nhánh") Integer branchId) {}
