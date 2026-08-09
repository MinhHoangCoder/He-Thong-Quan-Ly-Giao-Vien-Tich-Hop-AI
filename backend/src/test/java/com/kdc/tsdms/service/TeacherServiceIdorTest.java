package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.TeacherResponse;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.AuthPrincipal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit test cho chốt chặn CHỐNG IDOR của {@code TeacherService} — KHÔNG cần DB.
 *
 * <p>Phủ 2 endpoint {@code GET /teacher/{id}} và {@code GET /teacher/{id}/account}. Chúng là
 * hai endpoint DUY NHẤT của controller cố tình KHÔNG gắn {@code @PreAuthorize} — để giáo viên
 * tự xem được hồ sơ/tài khoản của chính mình — nên toàn bộ việc phân quyền dồn hết vào đúng
 * đoạn if trong service. Mà dữ liệu sau cánh cửa đó là CCCD, ngày sinh, địa chỉ, lương hợp
 * đồng và username/email. Ai lỡ xóa đoạn if thì mọi tài khoản đã đăng nhập đều đọc được của
 * tất cả giáo viên, và trước bộ test này thì không có gì kêu lên cả.
 *
 * <p>Luật: ADMIN hoặc người có quyền TEACHER_VIEW xem được mọi hồ sơ; giáo viên thường CHỈ
 * xem được hồ sơ gắn với tài khoản của chính mình; còn lại 403. Cửa mở theo QUYỀN chứ không
 * theo tên role — mang tên role không kèm quyền thì vẫn bị chặn.
 */
@ExtendWith(MockitoExtension.class)
class TeacherServiceIdorTest {

    private static final int TEACHER_ID = 5;

    /** AppUserId gắn với hồ sơ giáo viên đang bị truy cập. */
    private static final int OWNER_USER_ID = 42;

    private static final int OTHER_USER_ID = 99;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private CertificateRepository ceRepo;

    @Mock
    private ContractRepository contractRepo;

    @Mock
    private AppUserRepository appUserRepository;

    /** Không dùng trong các test ở đây, nhưng constructor của service đòi (luồng đặt lại mật khẩu). */
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TeacherService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Nạp người đang đăng nhập: userId + authority (vd "ROLE_ADMIN", "TEACHER_VIEW"). */
    private static void loginAs(int userId, String... authorities) {
        List<SimpleGrantedAuthority> granted =
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "actor"), "n/a", granted));
    }

    /** Hồ sơ GV đang hoạt động, gắn với tài khoản {@link #OWNER_USER_ID}. */
    private void givenTeacherExists() {
        Teacher t = new Teacher();
        t.setId(TEACHER_ID);
        t.setAppUserId(OWNER_USER_ID);
        t.setFirstName("An");
        t.setLastName("Nguyễn Văn");
        t.setIdCardNo("012345678901");
        when(teacherRepo.findByIdAndDeletedFalse(TEACHER_ID)).thenReturn(Optional.of(t));
    }

    private static void assertStatus(Throwable e, HttpStatus expected) {
        assertThat(e).isInstanceOf(ApiException.class);
        assertThat(((ApiException) e).getStatus()).isEqualTo(expected);
    }

    // ---------- Được phép xem ----------

    @Test
    void admin_canViewAnyProfile() {
        givenTeacherExists();
        loginAs(OTHER_USER_ID, "ROLE_ADMIN");

        assertThat(service.getTeacherById(TEACHER_ID).getIdCardNo()).isEqualTo("012345678901");
    }

    @Test
    void staffWithTeacherViewPermission_canViewAnyProfile() {
        // Chức danh Nhân sự: không phải ADMIN, không phải chính chủ, nhưng có quyền nghiệp vụ.
        givenTeacherExists();
        loginAs(OTHER_USER_ID, "TEACHER_VIEW");

        assertThat(service.getTeacherById(TEACHER_ID).getId()).isEqualTo(TEACHER_ID);
    }

    @Test
    void owner_canViewOwnProfile() {
        // Giáo viên thường, không quyền gì, nhưng hồ sơ gắn đúng tài khoản của họ.
        givenTeacherExists();
        loginAs(OWNER_USER_ID);

        assertThat(service.getTeacherById(TEACHER_ID).getAppUserId()).isEqualTo(OWNER_USER_ID);
    }

    // ---------- Bị chặn ----------

    @Test
    void otherTeacher_cannotViewSomeoneElseProfile_throws403() {
        // Đây chính là kịch bản IDOR: đăng nhập bằng tài khoản GV khác rồi đổi id trên URL.
        givenTeacherExists();
        loginAs(OTHER_USER_ID);

        assertThatThrownBy(() -> service.getTeacherById(TEACHER_ID))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }

    @Test
    void employeeRoleWithoutPermission_cannotViewProfile_throws403() {
        // Role EMPLOYEE không được cấp permission nào trong ma trận RolePermission (V3), nên
        // MANG tên role đó không nói lên được người này có phần việc với hồ sơ giáo viên hay
        // không. Nhân viên tạo thiếu UserRole từng đọc được cả CCCD lẫn lương hợp đồng chỉ vì
        // service hỏi tên role thay vì hỏi quyền.
        givenTeacherExists();
        loginAs(OTHER_USER_ID, "ROLE_EMPLOYEE");

        assertThatThrownBy(() -> service.getTeacherById(TEACHER_ID))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }

    @Test
    void teacherWithoutLinkedAccount_isNotTreatedAsOwner_throws403() {
        // appUserId = null. Nếu code so sánh kiểu t.getAppUserId().equals(...) mà không chặn
        // null thì chỗ này nổ NPE -> 500; còn nếu lỡ coi "null == chưa đăng nhập là khớp"
        // thì thành lỗ hổng. Cả hai đều phải ra 403.
        Teacher orphan = new Teacher();
        orphan.setId(TEACHER_ID);
        orphan.setAppUserId(null);
        when(teacherRepo.findByIdAndDeletedFalse(TEACHER_ID)).thenReturn(Optional.of(orphan));
        loginAs(OTHER_USER_ID);

        assertThatThrownBy(() -> service.getTeacherById(TEACHER_ID))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }

    // ---------- GET /teacher/{id}/account ----------
    // Endpoint này cũng KHÔNG có @PreAuthorize (để GV tự xem tài khoản mình), nên chốt chặn
    // duy nhất cũng nằm trong service — cùng lý do phải có test như getTeacherById.

    @Test
    void getAccount_owner_seesOwnUsernameAndEmail() {
        givenTeacherExists();
        AppUser au = new AppUser();
        au.setId(OWNER_USER_ID);
        au.setUsername("gv01");
        au.setEmail("gv01@tsdms.local");
        when(appUserRepository.findById(OWNER_USER_ID)).thenReturn(Optional.of(au));
        loginAs(OWNER_USER_ID);

        TeacherResponse.AccountInfo info = service.getAccount(TEACHER_ID);

        assertThat(info.getUsername()).isEqualTo("gv01");
        assertThat(info.getEmail()).isEqualTo("gv01@tsdms.local");
    }

    @Test
    void getAccount_otherTeacher_throws403() {
        givenTeacherExists();
        loginAs(OTHER_USER_ID);

        assertThatThrownBy(() -> service.getAccount(TEACHER_ID)).satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }

    // ---------- Không tìm thấy ----------

    @Test
    void missingTeacher_throws404BeforeCheckingPermission() {
        // 404 phải bắn TRƯỚC bước phân quyền, nhưng vì cả 2 nhánh đều không lộ dữ liệu nên
        // thứ tự này an toàn; test để khóa lại hành vi (đổi sang 403 sẽ làm FE hiểu nhầm).
        when(teacherRepo.findByIdAndDeletedFalse(TEACHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTeacherById(TEACHER_ID))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }
}
