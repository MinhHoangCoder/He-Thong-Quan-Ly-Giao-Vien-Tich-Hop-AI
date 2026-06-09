package com.kdc.tsdms.security;

import java.security.Principal;

/**
 * Danh tính người dùng đã xác thực, được đặt làm "principal" trong SecurityContext.
 *
 * <p>Khác với cách cũ (principal chỉ là chuỗi username), bản này mang thêm {@code userId}
 * để tầng service kiểm tra QUYỀN SỞ HỮU dữ liệu (chống IDOR) mà KHÔNG phải truy vấn DB lại.
 * {@link #getName()} vẫn trả về username để tương thích với {@link Principal} — ví dụ tham số
 * {@code Principal} trong controller hay {@code authentication.getName()} đều hoạt động như trước.
 */
public record AuthPrincipal(Integer userId, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
