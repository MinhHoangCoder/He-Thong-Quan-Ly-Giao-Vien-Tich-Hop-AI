package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;

/** Body cho POST /api/auth/login. */
public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống") String username,

        @NotBlank(message = "Mật khẩu không được để trống") String password) {}
