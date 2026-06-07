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
}
