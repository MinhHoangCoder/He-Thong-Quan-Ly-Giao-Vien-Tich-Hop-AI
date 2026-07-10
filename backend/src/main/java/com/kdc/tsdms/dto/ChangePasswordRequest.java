package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body cho POST /api/v1/me/change-password (ĐANG đăng nhập, khác luồng quên mật khẩu).
 * Bắt buộc nhập mật khẩu hiện tại để xác nhận chính chủ — access token bị trộm
 * không đủ để chiếm hẳn tài khoản.
 * {@code refreshToken} (tùy chọn): phiên hiện tại — được GIỮ LẠI khi thu hồi các phiên khác;
 * không gửi thì thu hồi tất cả (đăng xuất mọi thiết bị, kể cả thiết bị này).
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Thiếu mật khẩu hiện tại") String currentPassword,

        // Cùng chính sách với RegisterRequest/ResetPasswordRequest: 8–72 ký tự, hoa + thường + số.
        @NotBlank @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$",
                message = "Mật khẩu 8–72 ký tự, phải có chữ hoa, chữ thường và chữ số")
        String newPassword,

        String refreshToken) {}
