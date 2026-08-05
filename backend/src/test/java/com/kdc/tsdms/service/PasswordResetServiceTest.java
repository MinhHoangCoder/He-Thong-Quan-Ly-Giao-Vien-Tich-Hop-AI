package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.PasswordResetToken;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.PasswordResetTokenRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.security.JwtService;
import com.kdc.tsdms.security.ResetProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit test cho PasswordResetService — KHÔNG cần DB, mọi collaborator mock bằng Mockito.
 *
 * <p>Trọng tâm luồng QUÊN MẬT KHẨU: forgot im lặng khi email lạ (chống dò email), reset
 * chỉ chấp nhận token còn hạn & chưa dùng (1 lần), đổi mật khẩu xong thì đánh dấu token đã
 * dùng + thu hồi mọi refresh token (đăng xuất mọi thiết bị).
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private AppUserRepository appUserRepo;

    @Mock
    private PasswordResetTokenRepository resetRepo;

    @Mock
    private RefreshTokenRepository refreshTokenRepo;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private ResetProperties resetProps;

    @Mock
    private DisplayNameResolver displayNameResolver;

    @InjectMocks
    private PasswordResetService service;

    private static AppUser user(int id, String username, String email) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("oldHash");
        u.setStatus("ACTIVE");
        return u;
    }

    /** Token đặt lại còn dùng được: chưa dùng (usedAt null) và chưa hết hạn. */
    private static PasswordResetToken usableToken(int appUserId) {
        PasswordResetToken prt = new PasswordResetToken();
        prt.setAppUserId(appUserId);
        prt.setTokenHash("hash");
        prt.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        return prt;
    }

    private static void assertStatus(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ApiException.class);
        assertThat(((ApiException) t).getStatus()).isEqualTo(expected);
    }

    // ---------- forgot() ----------

    @Test
    void forgot_existingEmail_savesHashedTokenAndSendsLink() {
        AppUser u = user(42, "gv01", "gv01@tsdms.local");
        when(appUserRepo.findByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(Optional.of(u));
        when(jwtService.generateOpaqueToken()).thenReturn("raw-token-123");
        when(jwtService.sha256("raw-token-123")).thenReturn("hashed-token");
        when(resetProps.getMaxPerDay()).thenReturn(5);
        when(resetProps.getTokenTtl()).thenReturn(Duration.ofMinutes(30));
        when(resetProps.getBaseUrl()).thenReturn("http://localhost:5173");
        when(displayNameResolver.resolve(u)).thenReturn("Nguyễn Văn An");

        Instant before = Instant.now();
        service.forgot("gv01@tsdms.local");

        // Lưu HASH chứ không lưu token thô; gắn đúng user; hạn ~ now + 30 phút.
        ArgumentCaptor<PasswordResetToken> prtCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetRepo).save(prtCaptor.capture());
        PasswordResetToken saved = prtCaptor.getValue();
        assertThat(saved.getAppUserId()).isEqualTo(42);
        assertThat(saved.getTokenHash()).isEqualTo("hashed-token");
        assertThat(saved.getExpiresAt())
                .isBetween(before.plus(Duration.ofMinutes(29)), Instant.now().plus(Duration.ofMinutes(31)));

        // Email gửi tới đúng địa chỉ, dùng tên hiển thị, link chứa token THÔ (không phải hash).
        verify(emailService)
                .sendPasswordReset(
                        "gv01@tsdms.local",
                        "Nguyễn Văn An",
                        "http://localhost:5173/reset-password?token=raw-token-123");
    }

    @Test
    void forgot_unknownEmail_silentlyDoesNothing() {
        // Email không tồn tại -> KHÔNG sinh token, KHÔNG gửi mail (chống user enumeration),
        // và cũng không ném lỗi để kẻ xấu không phân biệt được email nào có thật.
        when(appUserRepo.findByEmailAndDeletedFalse("ghost@tsdms.local")).thenReturn(Optional.empty());

        service.forgot("ghost@tsdms.local");

        verify(resetRepo, never()).save(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    // ---------- forgot(): chống dội mail (email bombing) ----------

    @Test
    void forgot_withinCooldown_silentlySkipsSecondMail() {
        // Vừa phát link 10 giây trước, cooldown 1 phút -> yêu cầu này bị bỏ qua.
        // Rate limit theo IP không đỡ được kịch bản này vì kẻ tấn công chỉ cần đổi IP.
        AppUser u = user(42, "gv01", "gv01@tsdms.local");
        PasswordResetToken justIssued = usableToken(42);
        justIssued.setCreatedAt(Instant.now().minus(Duration.ofSeconds(10)));

        when(appUserRepo.findByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(Optional.of(u));
        when(resetProps.getMaxPerDay()).thenReturn(5);
        when(resetProps.getResendCooldown()).thenReturn(Duration.ofMinutes(1));
        when(resetRepo.findTopByAppUserIdOrderByIdDesc(42)).thenReturn(Optional.of(justIssued));

        service.forgot("gv01@tsdms.local");

        // Im lặng HỆT nhánh email lạ -> kẻ tấn công không suy ra được email có thật hay không.
        verify(resetRepo, never()).save(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void forgot_dailyLimitReached_silentlySkips() {
        // Đã phát đủ 5 link trong 24h -> chặn, kể cả khi đã qua thời gian nghỉ.
        AppUser u = user(42, "gv01", "gv01@tsdms.local");
        when(appUserRepo.findByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(Optional.of(u));
        when(resetProps.getMaxPerDay()).thenReturn(5);
        when(resetRepo.countByAppUserIdAndCreatedAtAfter(eq(42), any(Instant.class)))
                .thenReturn(5L);

        service.forgot("gv01@tsdms.local");

        verify(resetRepo, never()).save(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void forgot_newLink_expiresPreviousActiveLinks() {
        // Phát link mới -> link cũ còn sống bị đẩy hết hạn, chỉ 1 link dùng được tại 1 thời điểm.
        AppUser u = user(42, "gv01", "gv01@tsdms.local");
        PasswordResetToken older = usableToken(42);

        when(appUserRepo.findByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(Optional.of(u));
        when(resetProps.getMaxPerDay()).thenReturn(5);
        when(resetProps.getTokenTtl()).thenReturn(Duration.ofMinutes(30));
        when(resetProps.getBaseUrl()).thenReturn("http://localhost:5173");
        when(jwtService.generateOpaqueToken()).thenReturn("raw-token-456");
        when(jwtService.sha256("raw-token-456")).thenReturn("hashed-456");
        when(displayNameResolver.resolve(u)).thenReturn("Nguyễn Văn An");
        when(resetRepo.findByAppUserIdAndUsedAtIsNullAndExpiresAtAfter(eq(42), any(Instant.class)))
                .thenReturn(List.of(older));

        service.forgot("gv01@tsdms.local");

        assertThat(older.isUsable()).isFalse();
        verify(resetRepo).saveAll(List.of(older));
        verify(emailService)
                .sendPasswordReset(
                        "gv01@tsdms.local",
                        "Nguyễn Văn An",
                        "http://localhost:5173/reset-password?token=raw-token-456");
    }

    @Test
    void forgot_lockedAccount_silentlySkips() {
        // Tài khoản bị khóa thì có đặt lại mật khẩu cũng không đăng nhập được (AuthService chặn
        // theo status) -> đừng gửi mail. Vẫn im lặng để không lộ trạng thái tài khoản ra ngoài.
        AppUser locked = user(42, "gv01", "gv01@tsdms.local");
        locked.setStatus("LOCKED");
        when(appUserRepo.findByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(Optional.of(locked));

        service.forgot("gv01@tsdms.local");

        verify(resetRepo, never()).save(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    // ---------- reset() ----------

    @Test
    void reset_validToken_changesPasswordMarksUsedAndRevokesSessions() {
        AppUser u = user(42, "gv01", "gv01@tsdms.local");
        PasswordResetToken prt = usableToken(42);
        when(jwtService.sha256("raw-token-123")).thenReturn("hashed-token");
        when(resetRepo.findByTokenHash("hashed-token")).thenReturn(Optional.of(prt));
        when(appUserRepo.findById(42)).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("NewPass1")).thenReturn("newHash");

        service.reset("raw-token-123", "NewPass1");

        // Mật khẩu mới đã băm và gán vào user.
        assertThat(u.getPasswordHash()).isEqualTo("newHash");
        // Token đánh dấu đã dùng -> không tái sử dụng.
        assertThat(prt.getUsedAt()).isNotNull();
        // Thu hồi mọi refresh token đang sống -> đăng xuất mọi thiết bị.
        verify(refreshTokenRepo).revokeAllActiveByAppUserId(eq(42), any(Instant.class));
    }

    @Test
    void reset_unknownToken_throws400AndChangesNothing() {
        when(jwtService.sha256("bad-token")).thenReturn("no-such-hash");
        when(resetRepo.findByTokenHash("no-such-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset("bad-token", "NewPass1"))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void reset_expiredToken_throws400() {
        PasswordResetToken expired = usableToken(42);
        expired.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1))); // đã hết hạn
        when(jwtService.sha256("raw-token-123")).thenReturn("hashed-token");
        when(resetRepo.findByTokenHash("hashed-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.reset("raw-token-123", "NewPass1"))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        // Hết hạn -> dừng trước khi đụng tới user / đổi mật khẩu / thu hồi phiên.
        verify(appUserRepo, never()).findById(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void reset_alreadyUsedToken_throws400() {
        PasswordResetToken used = usableToken(42);
        used.setUsedAt(Instant.now().minus(Duration.ofMinutes(5))); // đã dùng rồi -> 1 lần
        when(jwtService.sha256("raw-token-123")).thenReturn("hashed-token");
        when(resetRepo.findByTokenHash("hashed-token")).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.reset("raw-token-123", "NewPass1"))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void reset_lockedAccount_throws403AndKeepsOldPassword() {
        // Admin khóa tài khoản SAU khi link đã gửi đi -> link không được phép cứu tài khoản đó.
        AppUser locked = user(42, "gv01", "gv01@tsdms.local");
        locked.setStatus("LOCKED");
        PasswordResetToken prt = usableToken(42);
        when(jwtService.sha256("raw-token-123")).thenReturn("hashed-token");
        when(resetRepo.findByTokenHash("hashed-token")).thenReturn(Optional.of(prt));
        when(appUserRepo.findById(42)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.reset("raw-token-123", "NewPass1"))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));

        assertThat(locked.getPasswordHash()).isEqualTo("oldHash");
        assertThat(prt.getUsedAt()).isNull(); // token chưa bị tiêu, người dùng vẫn dùng lại được
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void reset_softDeletedAccount_throws400() {
        // Tài khoản bị xóa mềm sau khi phát link -> link phải chết theo, không hồi sinh được.
        AppUser deleted = user(42, "gv01", "gv01@tsdms.local");
        deleted.setDeleted(true);
        PasswordResetToken prt = usableToken(42);
        when(jwtService.sha256("raw-token-123")).thenReturn("hashed-token");
        when(resetRepo.findByTokenHash("hashed-token")).thenReturn(Optional.of(prt));
        when(appUserRepo.findById(42)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.reset("raw-token-123", "NewPass1"))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        assertThat(deleted.getPasswordHash()).isEqualTo("oldHash");
    }

    @Test
    void reset_samePasswordAsCurrent_throws400() {
        // Người dùng thường vào đây vì NGHI lộ mật khẩu -> đặt lại y hệt cái cũ là vô nghĩa.
        // Cùng luật với luồng đổi mật khẩu ở trang Cài đặt.
        AppUser u = user(42, "gv01", "gv01@tsdms.local");
        PasswordResetToken prt = usableToken(42);
        when(jwtService.sha256("raw-token-123")).thenReturn("hashed-token");
        when(resetRepo.findByTokenHash("hashed-token")).thenReturn(Optional.of(prt));
        when(appUserRepo.findById(42)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("NewPass1", "oldHash")).thenReturn(true);

        assertThatThrownBy(() -> service.reset("raw-token-123", "NewPass1"))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        verify(passwordEncoder, never()).encode(anyString());
        assertThat(prt.getUsedAt()).isNull();
    }

    @Test
    void reset_userMissing_throws400() {
        // Token hợp lệ nhưng tài khoản đã bị xóa -> 400, không đổi gì.
        PasswordResetToken prt = usableToken(99);
        when(jwtService.sha256("raw-token-123")).thenReturn("hashed-token");
        when(resetRepo.findByTokenHash("hashed-token")).thenReturn(Optional.of(prt));
        when(appUserRepo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset("raw-token-123", "NewPass1"))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        assertThat(prt.getUsedAt()).isNull();
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }
}
