package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.AuthResponse;
import com.kdc.tsdms.dto.ForgotPasswordRequest;
import com.kdc.tsdms.dto.LoginRequest;
import com.kdc.tsdms.dto.MessageResponse;
import com.kdc.tsdms.dto.RegisterRequest;
import com.kdc.tsdms.dto.ResetPasswordRequest;
import com.kdc.tsdms.dto.TokenRequest;
import com.kdc.tsdms.dto.UserInfo;
import com.kdc.tsdms.service.AuthService;
import com.kdc.tsdms.service.PasswordResetService;
import com.kdc.tsdms.service.RegistrationService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Toàn bộ API xác thực, tiền tố /api/v1/auth. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            RegistrationService registrationService,
            PasswordResetService passwordResetService) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
    }

    /** Đăng nhập (4 actor dùng chung). */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Cấp lại access token từ refresh token. */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody TokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Đăng xuất = thu hồi refresh token. */
    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody TokenRequest request) {
        authService.logout(request.refreshToken());
        return new MessageResponse("Đã đăng xuất");
    }

    /**
     * Tạo tài khoản GV/trường. Chặn theo QUYỀN (không theo role):
     * tạo GV cần {@code TEACHER_MANAGE} (HR), tạo trường cần {@code SCHOOL_MANAGE} (SALES),
     * ADMIN đi tắt. @PreAuthorize lọc thô (có ÍT NHẤT một trong hai quyền) — phân biệt
     * chính xác theo loại tài khoản do RegistrationService kiểm tra tiếp.
     */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE') or hasAuthority('SCHOOL_MANAGE')")
    @PostMapping("/register")
    public ResponseEntity<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        UserInfo created = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Quên mật khẩu: luôn trả về thông báo chung dù email có tồn tại hay không. */
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgot(request.email());
        return new MessageResponse("Nếu email tồn tại, chúng tôi đã gửi liên kết đặt lại mật khẩu.");
    }

    /** Đặt lại mật khẩu bằng token nhận qua email. */
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return new MessageResponse("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.");
    }

    /** Thông tin tài khoản đang đăng nhập (FE gọi để dựng menu theo vai trò). */
    @GetMapping("/me")
    public UserInfo me(Principal principal) {
        return authService.me(principal.getName());
    }
}
