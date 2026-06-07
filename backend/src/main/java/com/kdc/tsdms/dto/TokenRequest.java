package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;

/** Dùng chung cho /refresh và /logout: chỉ cần refresh token. */
public record TokenRequest(
        @NotBlank(message = "Thiếu refresh token") String refreshToken) {}
