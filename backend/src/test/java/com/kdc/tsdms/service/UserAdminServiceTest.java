package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Role;
import com.kdc.tsdms.entity.UserRole;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.RolePermissionRepository;
import com.kdc.tsdms.repository.RoleRepository;
import com.kdc.tsdms.repository.UserRoleRepository;
import com.kdc.tsdms.security.AuthPrincipal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit test cho UserAdminService — KHÔNG cần DB. Repo mock bằng Mockito; người đang đăng nhập
 * dựng bằng Authentication thật nạp vào SecurityContextHolder (giống RegistrationServiceTest)
 * để chạy đúng logic phân quyền thật của SecurityUtils.
 *
 * <p>Trọng tâm là 3 luật CHỐNG LEO QUYỀN ở khu Cài đặt hệ thống — đây là chỗ nguy hiểm nhất
 * của hệ thống vì ai qua được là tự cấp cho mình mọi quyền còn lại:
 *
 * <ul>
 *   <li>Không thao tác lên chính mình (tự khóa / tự đổi vai trò).
 *   <li>Chỉ ADMIN được đụng vào tài khoản đang giữ ADMIN.
 *   <li>Chỉ ADMIN được CẤP vai trò ADMIN cho người khác.
 * </ul>
 *
 * <p>Kèm 2 hệ quả bắt buộc: khóa tài khoản và đổi vai trò đều phải THU HỒI refresh token,
 * nếu không phiên cũ vẫn sống và quyền cũ vẫn dùng được.
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    private static final int ACTOR_ID = 1;
    private static final int TARGET_ID = 7;

    @Mock
    private AppUserRepository appUserRepo;

    @Mock
    private RoleRepository roleRepo;

    @Mock
    private UserRoleRepository userRoleRepo;

    @Mock
    private RolePermissionRepository rolePermissionRepo;

    @Mock
    private RefreshTokenRepository refreshTokenRepo;

    @Mock
    private DisplayNameResolver displayNameResolver;

    @InjectMocks
    private UserAdminService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Nạp người đang đăng nhập: userId + các authority (vd "ROLE_ADMIN", "USER_MANAGE"). */
    private static void loginAs(int userId, String... authorities) {
        List<SimpleGrantedAuthority> granted =
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "actor"), "n/a", granted));
    }

    private static AppUser user(int id) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername("user" + id);
        u.setStatus("ACTIVE");
        return u;
    }

    private static void assertStatus(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ApiException.class);
        assertThat(((ApiException) t).getStatus()).isEqualTo(expected);
    }

    // ---------- updateStatus(): khóa / mở tài khoản ----------

    @Test
    void updateStatus_lockOther_setsLockedAndRevokesSessions() {
        AppUser target = user(TARGET_ID);
        loginAs(ACTOR_ID, "USER_MANAGE");
        when(appUserRepo.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRoleRepo.findRoleNamesByAppUserId(TARGET_ID)).thenReturn(List.of("TEACHER"));

        service.updateStatus(TARGET_ID, "locked"); // nhận cả chữ thường

        assertThat(target.getStatus()).isEqualTo("LOCKED");
        // Khóa mà không thu hồi thì refresh token còn sống vẫn xin được access token mới.
        verify(refreshTokenRepo).revokeAllActiveByAppUserId(eq(TARGET_ID), any(Instant.class));
    }

    @Test
    void updateStatus_unlock_doesNotRevokeSessions() {
        AppUser target = user(TARGET_ID);
        target.setStatus("LOCKED");
        loginAs(ACTOR_ID, "USER_MANAGE");
        when(appUserRepo.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRoleRepo.findRoleNamesByAppUserId(TARGET_ID)).thenReturn(List.of("TEACHER"));

        service.updateStatus(TARGET_ID, "ACTIVE");

        assertThat(target.getStatus()).isEqualTo("ACTIVE");
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void updateStatus_self_throws400() {
        // Tự khóa mình = tự nhốt ngoài cửa, chặn luôn.
        loginAs(ACTOR_ID, "ROLE_ADMIN");
        when(appUserRepo.findById(ACTOR_ID)).thenReturn(Optional.of(user(ACTOR_ID)));

        assertThatThrownBy(() -> service.updateStatus(ACTOR_ID, "LOCKED"))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void updateStatus_nonAdminTouchingAdmin_throws403() {
        // Trưởng phòng nhân sự (USER_MANAGE) KHÔNG được khóa tài khoản ADMIN.
        AppUser target = user(TARGET_ID);
        loginAs(ACTOR_ID, "USER_MANAGE");
        when(appUserRepo.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRoleRepo.findRoleNamesByAppUserId(TARGET_ID)).thenReturn(List.of("ADMIN"));

        assertThatThrownBy(() -> service.updateStatus(TARGET_ID, "LOCKED"))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));

        assertThat(target.getStatus()).isEqualTo("ACTIVE"); // không đổi gì
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void updateStatus_invalidValue_throws400BeforeTouchingDb() {
        assertThatThrownBy(() -> service.updateStatus(TARGET_ID, "DELETED"))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        verify(appUserRepo, never()).findById(any());
    }

    @Test
    void updateStatus_deletedAccount_throws404() {
        AppUser deleted = user(TARGET_ID);
        deleted.setDeleted(true);
        when(appUserRepo.findById(TARGET_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.updateStatus(TARGET_ID, "LOCKED"))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    // ---------- updateRoles(): gán chức danh ----------

    @Test
    void updateRoles_replacesRolesAndRevokesSessions() {
        AppUser target = user(TARGET_ID);
        Role hr = new Role();
        hr.setId(3);
        hr.setName("HR");
        loginAs(ACTOR_ID, "ROLE_MANAGE");
        when(appUserRepo.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRoleRepo.findRoleNamesByAppUserId(TARGET_ID)).thenReturn(List.of("TEACHER"));
        when(roleRepo.findByName("HR")).thenReturn(Optional.of(hr));

        List<String> assigned = service.updateRoles(TARGET_ID, List.of(" hr ")); // tự trim + hoa

        assertThat(assigned).containsExactly("HR");
        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepo).save(captor.capture());
        assertThat(captor.getValue().getAppUserId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().getRoleId()).isEqualTo(3);
        // Quyền nằm trong token -> phải thu hồi để bộ quyền mới có hiệu lực.
        verify(refreshTokenRepo).revokeAllActiveByAppUserId(eq(TARGET_ID), any(Instant.class));
    }

    @Test
    void updateRoles_nonAdminGrantingAdmin_throws403() {
        // Luật quan trọng nhất: có ROLE_MANAGE cũng KHÔNG được tự tạo thêm ADMIN.
        AppUser target = user(TARGET_ID);
        loginAs(ACTOR_ID, "ROLE_MANAGE");
        when(appUserRepo.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRoleRepo.findRoleNamesByAppUserId(TARGET_ID)).thenReturn(List.of("TEACHER"));

        assertThatThrownBy(() -> service.updateRoles(TARGET_ID, List.of("ADMIN")))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));

        verify(userRoleRepo, never()).save(any());
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void updateRoles_adminGrantingAdmin_isAllowed() {
        AppUser target = user(TARGET_ID);
        Role admin = new Role();
        admin.setId(1);
        admin.setName("ADMIN");
        loginAs(ACTOR_ID, "ROLE_ADMIN");
        when(appUserRepo.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRoleRepo.findRoleNamesByAppUserId(TARGET_ID)).thenReturn(List.of("TEACHER"));
        when(roleRepo.findByName("ADMIN")).thenReturn(Optional.of(admin));

        assertThat(service.updateRoles(TARGET_ID, List.of("ADMIN"))).containsExactly("ADMIN");
    }

    @Test
    void updateRoles_self_throws400() {
        loginAs(ACTOR_ID, "ROLE_ADMIN");
        when(appUserRepo.findById(ACTOR_ID)).thenReturn(Optional.of(user(ACTOR_ID)));

        assertThatThrownBy(() -> service.updateRoles(ACTOR_ID, List.of("HR")))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        verify(userRoleRepo, never()).save(any());
    }

    @Test
    void updateRoles_unknownRole_throws400AndKeepsOldRoles() {
        AppUser target = user(TARGET_ID);
        loginAs(ACTOR_ID, "ROLE_MANAGE");
        when(appUserRepo.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRoleRepo.findRoleNamesByAppUserId(TARGET_ID)).thenReturn(List.of("TEACHER"));
        when(roleRepo.findByName("GIAM_DOC")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRoles(TARGET_ID, List.of("giam_doc")))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        // Ném lỗi TRƯỚC khi xóa role cũ -> tài khoản không bị mất sạch quyền giữa chừng.
        verify(userRoleRepo, never()).deleteAll(any());
        verify(userRoleRepo, never()).save(any());
    }
}
