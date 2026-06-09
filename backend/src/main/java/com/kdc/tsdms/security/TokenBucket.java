package com.kdc.tsdms.security;

/**
 * Token bucket (xô token) đơn giản, an toàn luồng, lưu trong bộ nhớ.
 *
 * <p>Mỗi "khách" (IP + endpoint) giữ một xô tối đa {@code capacity} token; token tự hồi
 * dần theo thời gian. Mỗi request tốn 1 token; hết token → bị chặn. Ưu điểm so với đếm
 * cửa sổ cố định: cho phép burst nhỏ nhưng vẫn chặn spam kéo dài, không bị "lỗ hổng biên".
 *
 * <p>Đây là bản tối giản phục vụ đồ án. Production nhiều node nên dùng Redis/Bucket4j để
 * chia sẻ trạng thái (in-memory thì mỗi node đếm riêng).
 */
final class TokenBucket {

    private final double capacity;
    private final double refillTokensPerMilli;
    private double tokens;
    private long lastRefillAt;

    TokenBucket(double capacity, double refillTokensPerMilli) {
        this.capacity = capacity;
        this.refillTokensPerMilli = refillTokensPerMilli;
        this.tokens = capacity;
        this.lastRefillAt = System.currentTimeMillis();
    }

    /** Cố tiêu 1 token. {@code true} = cho qua, {@code false} = vượt giới hạn. */
    synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1d) {
            tokens -= 1d;
            return true;
        }
        return false;
    }

    /** {@code true} nếu xô đang đầy (idle) → có thể dọn khỏi cache cho nhẹ bộ nhớ. */
    synchronized boolean isFull() {
        refill();
        return tokens >= capacity;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        double added = (now - lastRefillAt) * refillTokensPerMilli;
        if (added > 0) {
            tokens = Math.min(capacity, tokens + added);
            lastRefillAt = now;
        }
    }
}
