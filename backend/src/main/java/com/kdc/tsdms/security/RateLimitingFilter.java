package com.kdc.tsdms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Giới hạn tần suất (rate limit) cho các endpoint auth NHẠY CẢM — chủ yếu để:
 * <ul>
 *   <li>chống dò mật khẩu (brute-force) ở {@code /login};</li>
 *   <li>chống spam gửi email / dò email ở {@code /forgot-password}.</li>
 * </ul>
 *
 * <p>Dùng {@link TokenBucket} in-memory theo khoá (IP + path). Vượt ngưỡng → trả 429
 * kèm header {@code Retry-After}. Chạy 1 lần/request nhờ {@link OncePerRequestFilter}.
 *
 * <p>Hạn chế (ghi rõ để nâng cấp sau): trạng thái nằm trong RAM của 1 node; IP lấy từ
 * {@code X-Forwarded-For} có thể bị giả mạo nếu KHÔNG đứng sau reverse-proxy tin cậy.
 * Production nên dùng Redis/Bucket4j và chỉ tin X-Forwarded-For sau proxy của mình.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    /** Chỉ chặn đúng các endpoint tốn kém/nhạy cảm; phần còn lại không đụng tới. */
    private static final Set<String> LIMITED_PATHS =
            Set.of("/api/v1/auth/login", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password");

    /** Chặn rò rỉ bộ nhớ: khi vượt số khoá này thì dọn bớt các xô đã đầy (idle). */
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final RateLimitProperties props;
    private final double refillTokensPerMilli;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitProperties props) {
        this.props = props;
        long periodMs = Math.max(1L, props.getRefillPeriod().toMillis());
        this.refillTokensPerMilli = (double) props.getCapacity() / periodMs;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Chỉ áp dụng cho POST tới đúng các path giới hạn (và khi tính năng đang bật).
        return !props.isEnabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String key = request.getRequestURI() + "|" + clientIp(request);
        TokenBucket bucket =
                buckets.computeIfAbsent(key, k -> new TokenBucket(props.getCapacity(), refillTokensPerMilli));

        if (bucket.tryConsume()) {
            filterChain.doFilter(request, response);
        } else {
            tooManyRequests(response);
        }

        // Dọn cache khi quá lớn: bỏ các xô đã hồi đầy (không còn ai bị giới hạn).
        if (buckets.size() > MAX_TRACKED_KEYS) {
            buckets.values().removeIf(TokenBucket::isFull);
        }
    }

    /**
     * Mặc định dùng remoteAddr (IP kết nối TCP thật — không giả mạo được). Chỉ đọc
     * X-Forwarded-For khi cấu hình {@code trust-forwarded-header=true} (đã đứng sau
     * reverse-proxy tin cậy); tin vô điều kiện thì client tự bịa header là né được limit.
     */
    private String clientIp(HttpServletRequest request) {
        if (props.isTrustForwardedHeader()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void tooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(props.getRefillPeriod().toSeconds()));
        // Trả JSON đúng dạng ErrorResponse {status, error, message} để FE xử lý đồng nhất.
        response.getWriter()
                .write("{\"status\":429,\"error\":\"Too Many Requests\","
                        + "\"message\":\"Bạn thao tác quá nhanh. Vui lòng thử lại sau ít phút.\"}");
    }
}
