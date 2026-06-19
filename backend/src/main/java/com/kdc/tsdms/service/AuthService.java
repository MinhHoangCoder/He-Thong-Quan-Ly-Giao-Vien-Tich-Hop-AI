package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.AuthResponse;
import com.kdc.tsdms.dto.LoginRequest;
import com.kdc.tsdms.dto.UserInfo;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.RefreshToken;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.UserRoleRepository;
import com.kdc.tsdms.security.JwtService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đăng nhập / làm mới token / đăng xuất. Dùng chung cho cả 4 actor. */
@Service
public class AuthService {

    private final AppUserRepository appUserRepo;
    private final UserRoleRepository userRoleRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DisplayNameResolver displayNameResolver;

    public AuthService(
            AppUserRepository appUserRepo,
            UserRoleRepository userRoleRepo,
            RefreshTokenRepository refreshTokenRepo,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            DisplayNameResolver displayNameResolver) {
        this.appUserRepo = appUserRepo;
        this.userRoleRepo = userRoleRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.displayNameResolver = displayNameResolver;
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Cố tình dùng CÙNG một thông báo cho "sai user" và "sai mật khẩu" để không lộ
        // tài khoản nào tồn tại.
        AppUser user = appUserRepo
                .findByUsernameAndDeletedFalse(req.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu"));

        // Kiểm tra MẬT KHẨU trước, STATUS sau. Nếu báo "bị khóa" trước khi xác minh mật
        // khẩu, kẻ tấn công gửi mật khẩu rác cũng biết được username tồn tại (403 ≠ 401).
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa hoặc ngừng hoạt động");
        }

        user.setLastLoginAt(Instant.now()); // dirty checking sẽ tự UPDATE khi commit
        List<String> roles = userRoleRepo.findRoleNamesByAppUserId(user.getId());
        List<String> perms = userRoleRepo.findPermissionCodesByAppUserId(user.getId());
        return issueTokens(user, roles, perms);
    }

    // noRollbackFor: ở nhánh "replay token đã thu hồi" ta CHỦ ĐỘNG revoke toàn bộ phiên
    // rồi mới ném 401 — nếu để mặc định, RuntimeException sẽ rollback và xóa luôn lệnh revoke.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepo
                .findByTokenHash(jwtService.sha256(rawRefreshToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ"));

        // REUSE DETECTION: token ĐÃ BỊ THU HỒI (đã rotate) mà vẫn được gửi lên = nhiều
        // khả năng token bị đánh cắp (kẻ trộm hoặc chủ thật đang xài bản cũ). Không thể
        // biết ai là ai -> thu hồi TOÀN BỘ phiên của user, buộc đăng nhập lại bằng mật khẩu.
        if (stored.getRevokedAt() != null) {
            refreshTokenRepo.revokeAllActiveByAppUserId(stored.getAppUserId(), Instant.now());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token đã hết hạn hoặc bị thu hồi");
        }
        if (!stored.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token đã hết hạn hoặc bị thu hồi");
        }
        AppUser user = appUserRepo
                .findById(stored.getAppUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));

        // ROTATION: thu hồi token cũ rồi cấp cặp token mới -> nếu token cũ bị đánh cắp
        // và dùng lại sau khi đã refresh, nó sẽ vô hiệu.
        stored.setRevokedAt(Instant.now());
        List<String> roles = userRoleRepo.findRoleNamesByAppUserId(user.getId());
        List<String> perms = userRoleRepo.findPermissionCodesByAppUserId(user.getId());
        return issueTokens(user, roles, perms);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepo.findByTokenHash(jwtService.sha256(rawRefreshToken)).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now()); // thu hồi -> đăng xuất thiết bị đó
            }
        });
        // Không báo lỗi nếu token không tồn tại: logout nên "im lặng thành công".
    }

    /** Thông tin user đang đăng nhập (cho GET /api/auth/me). */
    @Transactional(readOnly = true)
    public UserInfo me(String username) {
        AppUser user = appUserRepo
                .findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Phiên không hợp lệ"));
        List<String> roles = userRoleRepo.findRoleNamesByAppUserId(user.getId());
        List<String> perms = userRoleRepo.findPermissionCodesByAppUserId(user.getId());
        String displayName = displayNameResolver.resolve(user);
        return new UserInfo(user.getId(), user.getUsername(), displayName, user.getEmail(), roles, perms);
    }

    /** Cấp access token (JWT) + refresh token (opaque, lưu hash). */
    private AuthResponse issueTokens(AppUser user, List<String> roles, List<String> perms) {
        String displayName = displayNameResolver.resolve(user);
        String accessToken = jwtService.generateAccessToken(user, roles, perms, displayName);

        String rawRefresh = jwtService.generateOpaqueToken();
        RefreshToken rt = new RefreshToken();
        rt.setAppUserId(user.getId());
        rt.setTokenHash(jwtService.sha256(rawRefresh));
        rt.setExpiresAt(jwtService.refreshTokenExpiry());
        refreshTokenRepo.save(rt);

        UserInfo info = new UserInfo(user.getId(), user.getUsername(), displayName, user.getEmail(), roles, perms);
        return new AuthResponse(accessToken, rawRefresh, "Bearer", jwtService.accessTokenTtlSeconds(), info);
    }
}
