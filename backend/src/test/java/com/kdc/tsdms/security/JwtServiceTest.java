package com.kdc.tsdms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdc.tsdms.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho JwtService — KHÔNG cần DB hay Spring context.
 * JwtService chỉ phụ thuộc JwtProperties (POJO) nên dựng thẳng object thật, không mock.
 * Mỗi test kiểm tra một "luật" của token: nội dung claim, hạn dùng, chữ ký, token thô & băm.
 */
class JwtServiceTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private JwtService jwt;

    private static JwtProperties props(String secret, Duration accessTtl, Duration refreshTtl) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret); // >= 32 byte, nếu ngắn jjwt sẽ ném lỗi khi dựng key
        p.setIssuer("tsdms-test");
        p.setAccessTokenTtl(accessTtl);
        p.setRefreshTokenTtl(refreshTtl);
        return p;
    }

    private static AppUser user(int id, String username) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    @BeforeEach
    void setUp() {
        jwt = new JwtService(props("test-secret-key-which-is-at-least-32-bytes-long!!", ACCESS_TTL, REFRESH_TTL));
    }

    // claims JWT trả List thô -> ép kiểu phần tử để assertThat suy luận được (tránh lỗi ECJ trên máy khác)
    @Test
    @SuppressWarnings("unchecked")
    void generateThenParse_roundTrip_carriesAllClaims() {
        String token = jwt.generateAccessToken(
                user(7, "teacher1"), List.of("ADMIN", "TEACHER"), List.of("USER_VIEW", "LESSON_EDIT"), "Nguyễn Văn A");

        Claims c = jwt.parse(token).getPayload();

        assertThat(c.getSubject()).isEqualTo("teacher1");
        assertThat(c.get("uid", Integer.class)).isEqualTo(7);
        assertThat((List<String>) c.get("roles", List.class)).containsExactly("ADMIN", "TEACHER");
        assertThat((List<String>) c.get("perms", List.class)).containsExactly("USER_VIEW", "LESSON_EDIT");
        assertThat(c.get("fullName", String.class)).isEqualTo("Nguyễn Văn A");
        assertThat(c.getIssuer()).isEqualTo("tsdms-test");
    }

    @Test
    void accessToken_expirationEqualsConfiguredTtl() {
        String token = jwt.generateAccessToken(user(1, "admin"), List.of("ADMIN"), List.of(), "Quản trị");

        Claims c = jwt.parse(token).getPayload();
        long ttlSeconds = (c.getExpiration().getTime() - c.getIssuedAt().getTime()) / 1000;

        assertThat(ttlSeconds).isEqualTo(ACCESS_TTL.toSeconds());
    }

    @Test
    void parse_expiredToken_throwsExpiredJwtException() {
        // TTL âm -> token tạo ra đã hết hạn ngay, kiểm tra được mà không cần sleep (deterministic).
        JwtService expiredJwt = new JwtService(
                props("test-secret-key-which-is-at-least-32-bytes-long!!", Duration.ofSeconds(-60), REFRESH_TTL));
        String token = expiredJwt.generateAccessToken(user(1, "admin"), List.of("ADMIN"), List.of(), "Quản trị");

        assertThatThrownBy(() -> expiredJwt.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parse_tokenSignedWithOtherSecret_throwsJwtException() {
        JwtService other =
                new JwtService(props("a-completely-different-secret-key-32bytes-min!!", ACCESS_TTL, REFRESH_TTL));
        String foreignToken = other.generateAccessToken(user(1, "admin"), List.of("ADMIN"), List.of(), "Quản trị");

        // Chữ ký do "other" ký, "jwt" dùng khóa khác -> verify thất bại.
        assertThatThrownBy(() -> jwt.parse(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void parse_malformedToken_throwsJwtException() {
        assertThatThrownBy(() -> jwt.parse("not.a.valid.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void accessTokenTtlSeconds_returnsConfiguredTtl() {
        assertThat(jwt.accessTokenTtlSeconds()).isEqualTo(ACCESS_TTL.toSeconds());
    }

    @Test
    void refreshTokenExpiry_isNowPlusRefreshTtl() {
        Instant before = Instant.now();
        Instant expiry = jwt.refreshTokenExpiry();
        Instant after = Instant.now();

        assertThat(expiry)
                .isAfterOrEqualTo(before.plus(REFRESH_TTL).minusSeconds(2))
                .isBeforeOrEqualTo(after.plus(REFRESH_TTL).plusSeconds(2));
    }

    @Test
    void generateOpaqueToken_is256BitUrlSafeAndUnique() {
        String a = jwt.generateOpaqueToken();
        String b = jwt.generateOpaqueToken();

        assertThat(Base64.getUrlDecoder().decode(a)).hasSize(32); // 256-bit
        assertThat(a).isNotEqualTo(b); // mỗi lần sinh phải khác nhau
    }

    @Test
    void sha256_isDeterministicWithKnownVector() {
        // Vector chuẩn: SHA-256("abc") = ba7816bf...20015ad (64 ký tự hex thường).
        String hash = jwt.sha256("abc");

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(jwt.sha256("abc")).isEqualTo(hash); // gọi lại cùng input -> cùng kết quả
    }
}
