package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body cho POST /api/auth/reset-password. */
public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu token") String token,

        @NotBlank @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự") String newPassword) {}
