package com.kdc.tsdms.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cấu hình bảo mật:
 * - Stateless (không session, mỗi request tự mang JWT).
 * - Tắt CSRF (API token, không dùng cookie session).
 * - Mở các endpoint auth công khai; /register chỉ cho ADMIN & EMPLOYEE; còn lại cần đăng nhập.
 * - Cắm JwtAuthenticationFilter trước filter username/password mặc định.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // bật @PreAuthorize nếu muốn phân quyền ở tầng method
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitingFilter rateLimitingFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    /** Mã hóa mật khẩu bằng BCrypt (khớp hash $2b$ đã seed trong DB). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Công khai: đăng nhập, làm mới token, đăng xuất, quên/đặt lại mật khẩu
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password")
                        .permitAll()
                        // Đăng ký tài khoản (GV/trường) chỉ dành cho ADMIN & EMPLOYEE
                        .requestMatchers("/api/v1/auth/register")
                        .hasAnyRole("ADMIN", "EMPLOYEE")
                        // Mọi request khác phải đăng nhập
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate limit chạy TRƯỚC cả filter JWT để chặn brute-force ngay từ sớm.
                .addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /** Cho phép frontend Vue (Vite dev server) gọi API. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
