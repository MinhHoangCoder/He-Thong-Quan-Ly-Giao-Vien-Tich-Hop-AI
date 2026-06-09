package com.kdc.tsdms.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bind "tsdms.rate-limit.*" — giới hạn tần suất cho các endpoint auth nhạy cảm. */
@Component
@ConfigurationProperties(prefix = "tsdms.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    /** Bật/tắt rate limit (có thể tắt khi chạy test tích hợp). */
    private boolean enabled = true;

    /** Số request tối đa dồn dập (đỉnh burst) cho mỗi IP + endpoint. */
    private int capacity = 5;

    /** Chu kỳ hồi đầy token. capacity=5 + refillPeriod=1m → ~5 request/phút/IP. */
    private Duration refillPeriod = Duration.ofMinutes(1);
}
