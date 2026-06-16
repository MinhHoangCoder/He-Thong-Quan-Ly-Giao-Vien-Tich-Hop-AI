package com.kdc.tsdms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tiện ích đọc danh tính người đang đăng nhập từ SecurityContext.
 *
 * <p>Phục vụ phân quyền theo SỞ HỮU (chống IDOR). Mẫu chuẩn khi viết endpoint lấy theo id:
 *
 * <pre>{@code
 * Teacher t = teacherRepo.findById(id)
 *         .orElseThrow(() -> new ApiException(NOT_FOUND, "Không tìm thấy"));
 * boolean isOwner = t.getAppUserId().equals(SecurityUtils.currentUserId());
 * boolean isStaff = SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasRole("EMPLOYEE");
 * if (!isOwner && !isStaff) {
 *     throw new ApiException(FORBIDDEN, "Bạn không có quyền xem hồ sơ này");
 * }
 * }</pre>
 *
 * Xem docs/dev-notes về IDOR để hiểu vì sao chỉ dựa vào id trên URL là KHÔNG đủ.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** Id của AppUser đang đăng nhập, hoặc {@code null} nếu ẩn danh. */
    public static Integer currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    /** Username đang đăng nhập, hoặc {@code null} nếu ẩn danh. */
    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    /** {@code true} nếu người dùng có vai trò chỉ định (truyền tên KHÔNG kèm tiền tố {@code ROLE_}). */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String target = "ROLE_" + role;
        return auth.getAuthorities().stream().anyMatch(a -> target.equals(a.getAuthority()));
    }

    /**
     * {@code true} nếu người dùng có QUYỀN (permission) chỉ định. Khác {@link #hasRole(String)}:
     * perms nằm trong authority KHÔNG kèm tiền tố {@code ROLE_} (vd {@code TEACHER_MANAGE}).
     * Dùng khi cần kiểm tra quyền ở tầng service (ngoài {@code @PreAuthorize} của controller),
     * vd /register phân biệt tạo GV (TEACHER_MANAGE) hay tạo trường (SCHOOL_MANAGE).
     */
    public static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
