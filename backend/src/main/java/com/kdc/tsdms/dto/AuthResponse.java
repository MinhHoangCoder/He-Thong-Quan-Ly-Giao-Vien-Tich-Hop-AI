package com.kdc.tsdms.dto;

/** Kết quả đăng nhập / làm mới token. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType, // luôn là "Bearer"
        long expiresIn, // số giây access token còn sống
        UserInfo user) {}
