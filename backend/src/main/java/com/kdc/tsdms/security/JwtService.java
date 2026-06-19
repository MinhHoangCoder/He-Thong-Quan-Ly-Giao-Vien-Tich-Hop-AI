package com.kdc.tsdms.security;

import com.kdc.tsdms.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Sinh & kiểm tra token.
 *
 * <p>Access token = JWT có chữ ký HS256, mang sẵn roles -> mỗi request không cần
 * hỏi DB. Refresh token = chuỗi ngẫu nhiên "opaque" (không phải JWT); ta chỉ lưu
 * HASH của nó ở DB để có thể THU HỒI (đăng xuất) — đây là phần JWT thuần không làm được.
 */
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        // Khóa HS256 dựng từ secret (yêu cầu >= 32 byte, nếu ngắn jjwt sẽ ném lỗi).
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Tạo access token (JWT): subject = username + claim uid/roles/perms/fullName (tên hiển thị ghép sẵn). */
    public String generateAccessToken(AppUser user, List<String> roles, List<String> perms, String displayName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("roles", roles)
                // perms = danh sách MÃ QUYỀN chi tiết -> @PreAuthorize("hasAuthority('...')").
                .claim("perms", perms)
                .claim("fullName", displayName)
                .issuer(props.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getAccessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /** Kiểm tra chữ ký + hạn dùng, trả về claims. Token sai/hết hạn -> ném JwtException. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    /** Số giây sống của access token — dùng cho trường "expiresIn" trả về client. */
    public long accessTokenTtlSeconds() {
        return props.getAccessTokenTtl().toSeconds();
    }

    /** Thời điểm hết hạn của refresh token (now + refreshTokenTtl). */
    public Instant refreshTokenExpiry() {
        return Instant.now().plus(props.getRefreshTokenTtl());
    }

    /** Sinh chuỗi ngẫu nhiên 256-bit, an toàn mật mã, dùng làm refresh/reset token thô. */
    public String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Băm SHA-256 token thô -> chuỗi hex để lưu/so khớp ở DB (không lưu token thô). */
    public String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }
}
