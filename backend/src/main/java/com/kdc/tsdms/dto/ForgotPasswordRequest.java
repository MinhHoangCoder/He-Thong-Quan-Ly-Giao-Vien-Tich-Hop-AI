package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body cho POST /api/auth/forgot-password. */
public record ForgotPasswordRequest(
        @NotBlank @Email(message = "Email không hợp lệ") String email) {}
