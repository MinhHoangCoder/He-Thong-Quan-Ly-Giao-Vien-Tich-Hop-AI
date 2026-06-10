package com.kdc.tsdms.service;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.PasswordResetToken;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.PasswordResetTokenRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.security.JwtService;
import com.kdc.tsdms.security.ResetProperties;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Luồng QUÊN MẬT KHẨU: forgot (sinh token + gửi email) và reset (đổi mật khẩu bằng token). */
@Service
public class PasswordResetService {

    private final AppUserRepository appUserRepo;
    private final PasswordResetTokenRepository resetRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ResetProperties resetProps;

    public PasswordResetService(
            AppUserRepository appUserRepo,
            PasswordResetTokenRepository resetRepo,
            RefreshTokenRepository refreshTokenRepo,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ResetProperties resetProps) {
        this.appUserRepo = appUserRepo;
        this.resetRepo = resetRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.resetProps = resetProps;
    }

    @Transactional
    public void forgot(String email) {
        // Nếu email tồn tại -> sinh token + gửi mail. Nếu KHÔNG -> vẫn im lặng,
        // tránh để kẻ xấu dò email nào có trong hệ thống (user enumeration).
        appUserRepo.findByEmailAndDeletedFalse(email).ifPresent(user -> {
            String rawToken = jwtService.generateOpaqueToken();

            PasswordResetToken prt = new PasswordResetToken();
            prt.setAppUserId(user.getId());
            prt.setTokenHash(jwtService.sha256(rawToken));
            prt.setExpiresAt(Instant.now().plus(resetProps.getTokenTtl()));
            resetRepo.save(prt);

            String link = resetProps.getBaseUrl() + "/reset-password?token=" + rawToken;
            emailService.sendPasswordReset(user.getEmail(), user.getFullName(), link);
        });
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken prt = resetRepo
                .findByTokenHash(jwtService.sha256(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Token không hợp lệ"));

        if (!prt.isUsable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Token đã được dùng hoặc đã hết hạn");
        }
        AppUser user = appUserRepo
                .findById(prt.getAppUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Tài khoản không tồn tại"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        prt.setUsedAt(Instant.now()); // đánh dấu token đã dùng -> không tái sử dụng

        // Đổi mật khẩu thường vì NGHI BỊ LỘ tài khoản -> thu hồi mọi refresh token đang
        // sống, đăng xuất mọi thiết bị. Kẻ đã chiếm phiên không thể tiếp tục dùng.
        refreshTokenRepo.revokeAllActiveByAppUserId(user.getId(), Instant.now());
    }
}
