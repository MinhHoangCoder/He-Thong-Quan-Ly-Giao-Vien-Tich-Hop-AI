package com.kdc.tsdms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
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
                List<String> perms = claims.get("perms", List.class);
                Object uidClaim = claims.get("uid");
                Integer userId = (uidClaim instanceof Number num) ? num.intValue() : null;

                // Gộp 2 loại quyền vào SecurityContext:
                //  - roles -> thêm tiền tố "ROLE_" để hasRole('ADMIN') nhận diện.
                //  - perms -> GIỮ NGUYÊN mã (KHÔNG tiền tố) để hasAuthority('ATTENDANCE_VIEW') nhận diện.
                // null-check perms vì token cũ (phát trước khi có RBAC) sẽ không có claim này.
                var authorities = new ArrayList<GrantedAuthority>();
                if (roles != null) {
                    roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                }
                if (perms != null) {
                    perms.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                }

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
