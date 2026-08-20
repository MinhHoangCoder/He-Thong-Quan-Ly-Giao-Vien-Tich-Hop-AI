package com.kdc.tsdms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit test cho {@link SecurityUtils#isCentreStaff()} — KHÔNG cần DB, không cần Spring context.
 *
 * <p>Hàm này quyết định một câu hỏi lặp lại ở 4 service: người đang gọi được xem TOÀN hệ thống
 * hay bị ép về đúng phần dữ liệu của mình ({@code scopedTeacherId}). Trước đây mỗi service tự
 * liệt kê tên role — thêm một chức danh mới mà quên sửa đủ 4 danh sách thì người đó lặng lẽ bị
 * ép về phạm vi cá nhân, không có lỗi nào bắn ra và test cũ cũng không kêu. Nay đảo thành danh
 * sách LOẠI TRỪ, và bộ test này khóa đúng hai điều: chức danh mới mặc định là nhân sự trung tâm,
 * còn hai cổng ngoài (giáo viên / trường khách hàng) thì không bao giờ là.
 */
class SecurityUtilsStaffTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Nạp người đang đăng nhập với đúng bộ authority chỉ định (role phải kèm tiền tố ROLE_). */
    private static void loginWith(String... authorities) {
        List<SimpleGrantedAuthority> granted =
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(new AuthPrincipal(7, "actor"), "n/a", granted));
    }

    // ---------- Là nhân sự trung tâm ----------

    @Test
    void admin_isStaff() {
        loginWith("ROLE_ADMIN");

        assertThat(SecurityUtils.isCentreStaff()).isTrue();
    }

    @Test
    void departmentRole_isStaff() {
        loginWith("ROLE_HR", "TEACHER_VIEW", "TEACHER_MANAGE");

        assertThat(SecurityUtils.isCentreStaff()).isTrue();
    }

    @Test
    void brandNewJobTitleRole_isStaffWithoutTouchingThisCode() {
        // Đây là lý do tồn tại của cả lần sửa: role chức danh chưa hề có trong dự án lúc
        // viết test này vẫn phải được tính là nhân sự trung tâm.
        loginWith("ROLE_TRUONG_PHONG_DAO_TAO", "SCHEDULE_VIEW");

        assertThat(SecurityUtils.isCentreStaff()).isTrue();
    }

    @Test
    void teacherWhoAlsoHoldsStaffRole_isStaff() {
        // Giáo viên kiêm nhiệm học vụ: vẫn phải xem được lịch của cả trung tâm.
        loginWith("ROLE_TEACHER", "ROLE_ACADEMIC", "SCHEDULE_VIEW");

        assertThat(SecurityUtils.isCentreStaff()).isTrue();
    }

    // ---------- KHÔNG phải nhân sự trung tâm ----------

    @Test
    void pureTeacher_isNotStaff() {
        loginWith("ROLE_TEACHER", "SCHEDULE_VIEW", "ATTENDANCE_VIEW");

        assertThat(SecurityUtils.isCentreStaff()).isFalse();
    }

    @Test
    void anonymous_isNotStaff() {
        // ROLE_ANONYMOUS cũng là một authority mở đầu bằng ROLE_ — nếu quên loại trừ thì
        // khách vãng lai lọt vào nhánh "xem toàn hệ thống".
        loginWith("ROLE_ANONYMOUS");

        assertThat(SecurityUtils.isCentreStaff()).isFalse();
    }

    @Test
    void permissionWithoutAnyRole_isNotStaff() {
        loginWith("TEACHER_VIEW", "SCHEDULE_VIEW");

        assertThat(SecurityUtils.isCentreStaff()).isFalse();
    }

    @Test
    void notLoggedIn_isNotStaff() {
        SecurityContextHolder.clearContext();

        assertThat(SecurityUtils.isCentreStaff()).isFalse();
    }
}
