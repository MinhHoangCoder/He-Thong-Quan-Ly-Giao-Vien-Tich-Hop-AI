package com.kdc.tsdms.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cấu hình "tsdms.reset.*": hạn token đặt lại mật khẩu + base url của FE để ghép link. */
@Component
@ConfigurationProperties(prefix = "tsdms.reset")
@Getter
@Setter
public class ResetProperties {

    private Duration tokenTtl = Duration.ofMinutes(30);

    private String baseUrl = "http://localhost:5173";

    /**
     * Khoảng nghỉ tối thiểu giữa 2 email đặt lại mật khẩu của CÙNG một tài khoản.
     * Chặn kiểu quấy rối "spam nút Quên mật khẩu" mà rate limit theo IP không đỡ được
     * (kẻ tấn công đổi IP/dùng proxy thì mỗi IP vẫn dưới ngưỡng).
     */
    private Duration resendCooldown = Duration.ofMinutes(1);

    /** Số email đặt lại mật khẩu tối đa cho một tài khoản trong 24 giờ. */
    private int maxPerDay = 5;
}
