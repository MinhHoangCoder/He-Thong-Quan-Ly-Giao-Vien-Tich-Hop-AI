package com.kdc.tsdms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chạy 1 lần/ request: đọc header "Authorization: Bearer <token>", xác thực access
 * token, rồi nạp thông tin user + quyền vào SecurityContext để các @PreAuthorize /
 * cấu hình phân quyền hoạt động. Token sai/thiếu -> request đi tiếp ở trạng thái ẩn danh.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token).getPayload();
                String username = claims.getSubject();
                List<String> roles = claims.get("roles", List.class);
                Object uidClaim = claims.get("uid");
                Integer userId = (uidClaim instanceof Number num) ? num.intValue() : null;

                // Spring quy ước quyền vai trò bắt đầu bằng "ROLE_" để hasRole(...) nhận diện.
                var authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();

                // Principal mang theo userId -> tầng service kiểm tra quyền SỞ HỮU dữ liệu
                // (chống IDOR) mà không phải truy vấn DB lại. getName() vẫn trả về username.
                var principal = new AuthPrincipal(userId, username);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Token không hợp lệ -> không set authentication (coi như ẩn danh).
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
