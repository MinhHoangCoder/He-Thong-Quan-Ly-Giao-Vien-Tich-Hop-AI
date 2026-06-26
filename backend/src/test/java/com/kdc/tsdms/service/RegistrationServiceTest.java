package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.RegisterRequest;
import com.kdc.tsdms.dto.UserInfo;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Role;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.entity.UserRole;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.RoleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.repository.UserRoleRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit test cho RegistrationService — KHÔNG cần DB. Repo/encoder mock bằng Mockito;
 * quyền (SecurityUtils đọc SecurityContext) được dựng bằng Authentication thật nạp vào
 * SecurityContextHolder để test luôn logic phân quyền thật.
 *
 * <p>Trọng tâm: chốt 2 tầng /register — tạo GV cần TEACHER_MANAGE (HR), tạo trường cần
 * SCHOOL_MANAGE (SALES), ADMIN đi tắt; cộng các luật validate & chống trùng.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private AppUserRepository appUserRepo;

    @Mock
    private RoleRepository roleRepo;

    @Mock
    private UserRoleRepository userRoleRepo;

    @Mock
    private BranchRepository branchRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private SchoolRepository schoolRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Nạp người đăng nhập với các authority chỉ định (vd "ROLE_ADMIN", "TEACHER_MANAGE"). */
    private static void authWith(String... authorities) {
        List<SimpleGrantedAuthority> granted =
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("staff", "n/a", granted));
    }

    private static RegisterRequest teacherReq() {
        return new RegisterRequest(
                "TEACHER", "gv01", "gv01@tsdms.local", "An", "Nguyễn Văn", null, "Password1", "0900000000", 1);
    }

    private static RegisterRequest schoolReq() {
        return new RegisterRequest(
                "SCHOOL", "sch01", "sch01@tsdms.local", null, null, "Trường THCS Demo", "Password1", "0900000000", 1);
    }

    private static Role role(int id, String name) {
        Role r = new Role();
        r.setId(id);
        r.setName(name);
        return r;
    }

    /** Stub appUserRepo.save gán Id (mô phỏng IDENTITY) để các bước sau dùng user.getId(). */
    private void stubSaveAssignsId(int id) {
        when(appUserRepo.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId(id);
            return u;
        });
    }

    private static void assertStatus(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ApiException.class);
        assertThat(((ApiException) t).getStatus()).isEqualTo(expected);
    }

    @Test
    void register_invalidRole_throws400() {
        RegisterRequest req =
                new RegisterRequest("STUDENT", "x", "x@tsdms.local", "A", "B", null, "Password1", null, 1);

        assertThatThrownBy(() -> service.register(req)).satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(appUserRepo, never()).save(any());
    }

    @Test
    void register_teacherMissingName_throws400() {
        RegisterRequest req =
                new RegisterRequest("TEACHER", "gv01", "gv01@tsdms.local", " ", null, null, "Password1", null, 1);

        // Thiếu tên gọi/họ đệm -> chặn TRƯỚC cả bước phân quyền.
        assertThatThrownBy(() -> service.register(req)).satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_schoolMissingName_throws400() {
        RegisterRequest req =
                new RegisterRequest("SCHOOL", "sch01", "sch01@tsdms.local", null, null, "  ", "Password1", null, 1);

        assertThatThrownBy(() -> service.register(req)).satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_teacherByUserWithoutTeacherManage_throws403() {
        // Người dùng SALES (chỉ SCHOOL_MANAGE) cố tạo GIÁO VIÊN -> phải bị chặn.
        authWith("SCHOOL_MANAGE");

        assertThatThrownBy(() -> service.register(teacherReq())).satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(appUserRepo, never()).save(any());
    }

    @Test
    void register_schoolByUserWithoutSchoolManage_throws403() {
        // Người dùng HR (chỉ TEACHER_MANAGE) cố tạo TRƯỜNG -> phải bị chặn.
        authWith("TEACHER_MANAGE");

        assertThatThrownBy(() -> service.register(schoolReq())).satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(appUserRepo, never()).save(any());
    }

    @Test
    void register_adminBypassesPermissionChecks_createsTeacher() {
        // ADMIN không có TEACHER_MANAGE nhưng vẫn được tạo (đi tắt).
        authWith("ROLE_ADMIN");
        when(appUserRepo.existsByUsernameAndDeletedFalse("gv01")).thenReturn(false);
        when(appUserRepo.existsByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(false);
        when(branchRepo.existsById(1)).thenReturn(true);
        when(roleRepo.findByName("TEACHER")).thenReturn(Optional.of(role(5, "TEACHER")));
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        stubSaveAssignsId(42);

        UserInfo info = service.register(teacherReq());

        assertThat(info.roles()).containsExactly("TEACHER");
        verify(teacherRepo).save(any(Teacher.class));
        verify(schoolRepo, never()).save(any());
    }

    @Test
    void register_duplicateUsername_throws409() {
        authWith("TEACHER_MANAGE");
        when(appUserRepo.existsByUsernameAndDeletedFalse("gv01")).thenReturn(true);

        assertThatThrownBy(() -> service.register(teacherReq())).satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(appUserRepo, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throws409() {
        authWith("TEACHER_MANAGE");
        when(appUserRepo.existsByUsernameAndDeletedFalse("gv01")).thenReturn(false);
        when(appUserRepo.existsByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(true);

        assertThatThrownBy(() -> service.register(teacherReq())).satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(appUserRepo, never()).save(any());
    }

    @Test
    void register_branchNotFound_throws400() {
        authWith("TEACHER_MANAGE");
        when(appUserRepo.existsByUsernameAndDeletedFalse("gv01")).thenReturn(false);
        when(appUserRepo.existsByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(false);
        when(branchRepo.existsById(1)).thenReturn(false);

        assertThatThrownBy(() -> service.register(teacherReq()))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(appUserRepo, never()).save(any());
    }

    @Test
    void register_teacherSuccess_savesProfileAndReturnsDisplayName() {
        authWith("TEACHER_MANAGE");
        when(appUserRepo.existsByUsernameAndDeletedFalse("gv01")).thenReturn(false);
        when(appUserRepo.existsByEmailAndDeletedFalse("gv01@tsdms.local")).thenReturn(false);
        when(branchRepo.existsById(1)).thenReturn(true);
        when(roleRepo.findByName("TEACHER")).thenReturn(Optional.of(role(5, "TEACHER")));
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        stubSaveAssignsId(42);

        UserInfo info = service.register(teacherReq());

        assertThat(info.id()).isEqualTo(42);
        assertThat(info.username()).isEqualTo("gv01");
        assertThat(info.fullName()).isEqualTo("Nguyễn Văn An"); // lastName + " " + firstName
        assertThat(info.email()).isEqualTo("gv01@tsdms.local");
        assertThat(info.roles()).containsExactly("TEACHER");
        assertThat(info.perms()).isEmpty();

        // Mật khẩu được băm, status ACTIVE, gán đúng vai trò.
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepo).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getAppUserId()).isEqualTo(42);
        assertThat(roleCaptor.getValue().getRoleId()).isEqualTo(5);

        verify(teacherRepo).save(any(Teacher.class));
        verify(schoolRepo, never()).save(any());
    }

    @Test
    void register_schoolSuccess_savesSchoolProfileAndReturnsName() {
        authWith("SCHOOL_MANAGE");
        when(appUserRepo.existsByUsernameAndDeletedFalse("sch01")).thenReturn(false);
        when(appUserRepo.existsByEmailAndDeletedFalse("sch01@tsdms.local")).thenReturn(false);
        when(branchRepo.existsById(1)).thenReturn(true);
        when(roleRepo.findByName("SCHOOL")).thenReturn(Optional.of(role(3, "SCHOOL")));
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        stubSaveAssignsId(77);

        UserInfo info = service.register(schoolReq());

        assertThat(info.id()).isEqualTo(77);
        assertThat(info.fullName()).isEqualTo("Trường THCS Demo"); // tên hiển thị = tên trường
        assertThat(info.roles()).containsExactly("SCHOOL");
        verify(schoolRepo).save(any(School.class));
        verify(teacherRepo, never()).save(any());
    }
}
