package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body cho POST /api/auth/reset-password. */
public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu token") String token,

        // Cùng chính sách với RegisterRequest: 8–72 ký tự, có hoa + thường + số.
        @NotBlank @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$",
                message = "Mật khẩu 8–72 ký tự, phải có chữ hoa, chữ thường và chữ số")
        String newPassword) {}
