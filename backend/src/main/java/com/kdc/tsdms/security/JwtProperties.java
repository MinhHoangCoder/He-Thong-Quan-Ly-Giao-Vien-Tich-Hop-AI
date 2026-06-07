package com.kdc.tsdms.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bind các thuộc tính "tsdms.jwt.*" trong application.yaml thành đối tượng Java. */
@Component
@ConfigurationProperties(prefix = "tsdms.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Khóa bí mật ký HS256 — tối thiểu 32 byte. Đổi ở môi trường thật. */
    private String secret;

    /** Access token sống ngắn (vd 15m). */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Refresh token sống dài (vd 7d). */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** issuer ghi vào token. */
    private String issuer = "tsdms";
}
