package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit test cho AuthService — KHÔNG cần DB hay Spring context, tất cả phụ thuộc đều mock.
 * Mỗi test kiểm tra một "luật" của nghiệp vụ đăng nhập/đăng xuất/refresh.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepo;

    @Mock
    private UserRoleRepository userRoleRepo;

    @Mock
    private RefreshTokenRepository refreshTokenRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private DisplayNameResolver displayNameResolver;

    @InjectMocks
    private AuthService authService;

    private AppUser activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new AppUser();
        activeUser.setId(1);
        activeUser.setUsername("admin");
        activeUser.setEmail("admin@tsdms.local");
        activeUser.setPasswordHash("$2b$hash");
        activeUser.setStatus("ACTIVE");
    }

    /** Gom các stub cần cho việc cấp cặp token (dùng ở login & refresh thành công). */
    private void stubTokenIssuing() {
        when(userRoleRepo.findRoleNamesByAppUserId(1)).thenReturn(List.of("ADMIN"));
        when(userRoleRepo.findPermissionCodesByAppUserId(1)).thenReturn(List.of("USER_VIEW"));
        when(displayNameResolver.resolve(any(AppUser.class))).thenReturn("Quản trị");
        when(jwtService.generateAccessToken(any(AppUser.class), anyList(), anyList(), anyString()))
                .thenReturn("access.jwt");
        when(jwtService.generateOpaqueToken()).thenReturn("raw-refresh");
        when(jwtService.sha256("raw-refresh")).thenReturn("hash-refresh");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
    }

    @Test
    void login_success_returnsTokensAndSavesRefresh() {
        when(appUserRepo.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "$2b$hash")).thenReturn(true);
        stubTokenIssuing();

        AuthResponse res = authService.login(new LoginRequest("admin", "secret"));

        assertThat(res.accessToken()).isEqualTo("access.jwt");
        assertThat(res.refreshToken()).isEqualTo("raw-refresh");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresIn()).isEqualTo(900L);
        assertThat(res.user().username()).isEqualTo("admin");
        assertThat(res.user().roles()).containsExactly("ADMIN");
        assertThat(res.user().perms()).containsExactly("USER_VIEW"); // token/UserInfo mang quyền chi tiết
        assertThat(activeUser.getLastLoginAt()).isNotNull(); // có cập nhật lần đăng nhập
        verify(refreshTokenRepo).save(any(RefreshToken.class)); // có lưu refresh token
    }

    @Test
    void login_wrongPassword_throws401() {
        when(appUserRepo.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "$2b$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(refreshTokenRepo, never()).save(any());
    }

    @Test
    void login_userNotFound_throws401() {
        when(appUserRepo.findByUsernameAndDeletedFalse("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "x")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_inactiveAccount_throws403() {
        activeUser.setStatus("LOCKED");
        when(appUserRepo.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(activeUser));
        // Mật khẩu ĐÚNG mới được biết tài khoản bị khóa (status kiểm tra SAU mật khẩu).
        when(passwordEncoder.matches("secret", "$2b$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "secret")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    /** Chống dò username: tài khoản bị khóa + mật khẩu SAI phải trả 401 (như user không tồn tại), không lộ 403. */
    @Test
    void login_lockedAccountWrongPassword_throws401NotForbidden() {
        activeUser.setStatus("LOCKED");
        when(appUserRepo.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "$2b$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void logout_revokesStoredToken() {
        RefreshToken rt = new RefreshToken();
        rt.setAppUserId(1);
        rt.setExpiresAt(Instant.now().plusSeconds(3600));
        when(jwtService.sha256("raw")).thenReturn("h");
        when(refreshTokenRepo.findByTokenHash("h")).thenReturn(Optional.of(rt));

        authService.logout("raw");

        assertThat(rt.getRevokedAt()).isNotNull(); // token bị thu hồi
    }

    @Test
    void logout_unknownToken_isSilent() {
        when(jwtService.sha256("raw")).thenReturn("h");
        when(refreshTokenRepo.findByTokenHash("h")).thenReturn(Optional.empty());

        // Không được ném lỗi dù token không tồn tại.
        authService.logout("raw");
    }

    @Test
    void refresh_rotatesToken_revokesOldAndIssuesNew() {
        RefreshToken old = new RefreshToken();
        old.setAppUserId(1);
        old.setExpiresAt(Instant.now().plusSeconds(3600)); // còn hạn -> isActive() = true
        when(jwtService.sha256("old-raw")).thenReturn("old-hash");
        when(refreshTokenRepo.findByTokenHash("old-hash")).thenReturn(Optional.of(old));
        when(appUserRepo.findById(1)).thenReturn(Optional.of(activeUser));
        stubTokenIssuing();

        AuthResponse res = authService.refresh("old-raw");

        assertThat(old.getRevokedAt()).isNotNull(); // token cũ bị thu hồi (rotation)
        assertThat(res.refreshToken()).isEqualTo("raw-refresh"); // cấp token mới
        verify(refreshTokenRepo).save(any(RefreshToken.class));
    }

    /** REUSE DETECTION: token đã rotate mà bị dùng lại = nghi bị đánh cắp → thu hồi MỌI phiên của user. */
    @Test
    void refresh_revokedTokenReplay_revokesAllSessions() {
        RefreshToken replayed = new RefreshToken();
        replayed.setAppUserId(1);
        replayed.setExpiresAt(Instant.now().plusSeconds(3600));
        replayed.setRevokedAt(Instant.now().minusSeconds(60)); // đã bị thu hồi từ trước
        when(jwtService.sha256("stolen")).thenReturn("stolen-hash");
        when(refreshTokenRepo.findByTokenHash("stolen-hash")).thenReturn(Optional.of(replayed));

        assertThatThrownBy(() -> authService.refresh("stolen"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(refreshTokenRepo).revokeAllActiveByAppUserId(any(Integer.class), any(Instant.class));
        verify(refreshTokenRepo, never()).save(any()); // không cấp token mới
    }

    @Test
    void refresh_expiredToken_throws401() {
        RefreshToken expired = new RefreshToken();
        expired.setAppUserId(1);
        expired.setExpiresAt(Instant.now().minusSeconds(10)); // hết hạn -> isActive() = false
        when(jwtService.sha256("x")).thenReturn("xh");
        when(refreshTokenRepo.findByTokenHash("xh")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("x"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void me_returnsUserInfoWithRolesAndPerms() {
        when(appUserRepo.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(activeUser));
        when(userRoleRepo.findRoleNamesByAppUserId(1)).thenReturn(List.of("ADMIN"));
        when(userRoleRepo.findPermissionCodesByAppUserId(1)).thenReturn(List.of("USER_VIEW"));

        UserInfo info = authService.me("admin");

        assertThat(info.username()).isEqualTo("admin");
        assertThat(info.email()).isEqualTo("admin@tsdms.local");
        assertThat(info.roles()).containsExactly("ADMIN");
        assertThat(info.perms()).containsExactly("USER_VIEW");
    }
}
