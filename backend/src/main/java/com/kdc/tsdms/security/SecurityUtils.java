package com.kdc.tsdms.security;

import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
 * boolean isStaff = SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasAuthority("TEACHER_VIEW");
 * if (!isOwner && !isStaff) {
 *     throw new ApiException(FORBIDDEN, "Bạn không có quyền xem hồ sơ này");
 * }
 * }</pre>
 *
 * Xem docs/dev-notes về IDOR để hiểu vì sao chỉ dựa vào id trên URL là KHÔNG đủ.
 */
public final class SecurityUtils {

    /**
     * Các role KHÔNG phải nhân sự trung tâm: hai cổng ngoài (giáo viên, trường khách hàng)
     * và role ẩn danh của Spring. Mọi role còn lại đều là người của trung tâm.
     */
    private static final Set<String> NON_STAFF_ROLES = Set.of("ROLE_TEACHER", "ROLE_ANONYMOUS");

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

    /**
     * {@code true} nếu người đăng nhập là NHÂN SỰ TRUNG TÂM — tức có ít nhất một role
     * không nằm trong {@link #NON_STAFF_ROLES}.
     *
     * <p>Chỉ dùng cho câu hỏi "xem toàn hệ thống hay chỉ xem phần của mình" (mẫu
     * {@code scopedTeacherId}: staff giữ nguyên bộ lọc client gửi lên, giáo viên thì bị ép
     * về hồ sơ của chính họ). KHÔNG dùng thay cho kiểm tra quyền: được vào tới tầng service
     * hay không vẫn do {@code @PreAuthorize} của controller quyết định.
     *
     * <p>Vì sao là danh sách LOẠI TRỪ chứ không liệt kê ADMIN/EMPLOYEE/HR/...: role của dự án
     * đang chuyển dần từ phòng-ban sang chức danh. Danh sách liệt kê thì cứ thêm một chức danh
     * là phải nhớ sửa lại đủ 5 chỗ, quên chỗ nào thì người đó lặng lẽ bị ép về phạm vi cá nhân
     * và không có lỗi nào bắn ra. Danh sách loại trừ chỉ có 2 cổng ngoài — vốn không đổi — nên
     * chức danh mới mặc định được tính là nhân sự trung tâm.
     */
    public static boolean isCentreStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.startsWith("ROLE_") && !NON_STAFF_ROLES.contains(a));
    }
}
