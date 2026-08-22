package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.PayrollRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * XÓA GIÁO VIÊN PHẢI KHÓA LUÔN TÀI KHOẢN ĐĂNG NHẬP (Flyway V34).
 *
 * <p>Lỗi từng gặp thật: {@code deleteTeacher} chỉ đặt {@code Teacher.deleted = true} và không
 * đụng gì tới {@link AppUser}. Mà {@code AuthService.login} chỉ hỏi AppUser — còn sống không,
 * đúng mật khẩu không, {@code ACTIVE} không — chứ KHÔNG hỏi "tài khoản này còn hồ sơ không".
 * Hệ quả: giáo viên "đã xóa" vẫn đăng nhập bình thường, rồi mọi màn hình của họ ném 403 "Tài
 * khoản không có hồ sơ giáo viên". Người bấm xóa không hề biết.
 *
 * <p>Test đi qua chính {@code deleteTeacher}/{@code restoreTeacher}/{@code deleteTrueTeacher}
 * chứ không gọi thẳng hàm khóa tài khoản: bản thân việc đặt cờ thì dễ đúng, cái đã hỏng là nó
 * có được NỐI vào luồng xóa hay không.
 *
 * <p>Ba luật ở đây đối xứng nhau và dễ phá lẫn nhau khi sửa sau này:
 *
 * <ul>
 *   <li>Xóa mềm → KHÓA tài khoản nhưng KHÔNG xóa nó (hồ sơ còn khôi phục được).
 *   <li>Khôi phục → MỞ LẠI tài khoản (nếu chỉ khóa mà quên mở, giáo viên có tên trong danh
 *       sách nhưng không vào được hệ thống, và không màn hình nào giải thích vì sao).
 *   <li>Xóa vĩnh viễn → xóa mềm luôn tài khoản, nếu không sẽ đẻ ra tài khoản MỒ CÔI.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherDeleteLocksAccountTest {

    private static final int TEACHER_ID = 5;
    private static final int USER_ID = 42;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private CertificateRepository ceRepo;

    @Mock
    private ContractRepository contractRepo;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepo;

    @Mock
    private AssignmentRepository assignmentRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private AttendanceRepository attendanceRepo;

    @Mock
    private PayrollRepository payrollRepo;

    @InjectMocks
    private TeacherService service;

    @Test
    void xoa_mem_thi_khoa_tai_khoan_va_thu_hoi_refresh_token() {
        Teacher t = teacher(false);
        AppUser au = account();
        when(teacherRepo.findByIdAndDeletedFalse(TEACHER_ID)).thenReturn(Optional.of(t));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(au));

        service.deleteTeacher(TEACHER_ID);

        assertThat(t.isDeleted()).isTrue();
        assertThat(au.getStatus()).isEqualTo("INACTIVE");
        // Hồ sơ còn nằm trong thùng rác → tài khoản KHÔNG được xóa mềm theo, nếu không thì
        // khôi phục xong vẫn không đăng nhập được.
        assertThat(au.isDeleted()).isFalse();
        // Đổi Status chỉ chặn lần đăng nhập SAU; phiên đang mở phải bị cắt ngay.
        verify(refreshTokenRepo).revokeAllActiveByAppUserId(eq(USER_ID), any(Instant.class));
    }

    @Test
    void khoi_phuc_thi_mo_lai_tai_khoan() {
        Teacher t = teacher(true);
        AppUser au = account();
        au.setStatus("INACTIVE");
        when(teacherRepo.findByIdAndDeletedTrue(TEACHER_ID)).thenReturn(Optional.of(t));
        when(teacherRepo.save(any(Teacher.class))).thenAnswer(i -> i.getArgument(0));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(au));

        service.restoreTeacher(TEACHER_ID);

        assertThat(t.isDeleted()).isFalse();
        assertThat(au.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void ho_so_khong_gan_tai_khoan_thi_khong_no() {
        Teacher t = teacher(false);
        t.setAppUserId(null); // hồ sơ nhập tay, chưa cấp tài khoản đăng nhập
        when(teacherRepo.findByIdAndDeletedFalse(TEACHER_ID)).thenReturn(Optional.of(t));

        service.deleteTeacher(TEACHER_ID);

        assertThat(t.isDeleted()).isTrue();
        verify(appUserRepository, never()).save(any());
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(anyInt(), any(Instant.class));
    }

    /* ─────────────────── helpers ─────────────────── */

    private static Teacher teacher(boolean deleted) {
        Teacher t = new Teacher();
        t.setId(TEACHER_ID);
        t.setAppUserId(USER_ID);
        t.setFirstName("An");
        t.setLastName("Nguyễn Văn");
        t.setStatus(deleted ? "RETIRED" : "ACTIVE");
        t.setDeleted(deleted);
        return t;
    }

    private static AppUser account() {
        AppUser au = new AppUser();
        au.setId(USER_ID);
        au.setUsername("gv.nguyenvanan");
        au.setStatus("ACTIVE");
        au.setDeleted(false);
        return au;
    }
}
