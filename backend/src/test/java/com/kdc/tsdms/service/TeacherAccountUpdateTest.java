package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.TeacherResponse;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit test cho {@code PUT /teacher/{id}/account} — admin/nhân sự sửa tài khoản đăng nhập của
 * giáo viên (username / email / đặt lại mật khẩu hộ). KHÔNG cần DB.
 *
 * <p>Trọng tâm là 2 luật dễ làm sai:
 *
 * <ul>
 *   <li>Ô mật khẩu để TRỐNG nghĩa là "giữ nguyên mật khẩu cũ" — không được băm chuỗi rỗng
 *       rồi ghi đè, vì như vậy là vô tình khoá tài khoản của giáo viên.
 *   <li>Có đổi mật khẩu thì PHẢI thu hồi refresh token. Không thu hồi thì phiên cũ sống tiếp
 *       7 ngày, trong khi lý do đổi thường là nghi tài khoản đã bị lộ.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TeacherAccountUpdateTest {

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
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepo;

    @InjectMocks
    private TeacherService service;

    private AppUser givenTeacherWithAccount() {
        Teacher t = new Teacher();
        t.setId(TEACHER_ID);
        t.setAppUserId(USER_ID);
        t.setFirstName("An");
        t.setLastName("Nguyễn Văn");
        when(teacherRepo.findByIdAndDeletedFalse(TEACHER_ID)).thenReturn(Optional.of(t));

        AppUser au = new AppUser();
        au.setId(USER_ID);
        au.setUsername("gv01");
        au.setEmail("gv01@tsdms.local");
        au.setPasswordHash("oldHash");
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(au));
        return au;
    }

    private static TeacherResponse.AccountUpdateRequest request(String username, String email, String password) {
        TeacherResponse.AccountUpdateRequest req = new TeacherResponse.AccountUpdateRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    @Test
    void newPassword_isHashedAndAllSessionsRevoked() {
        AppUser au = givenTeacherWithAccount();
        when(passwordEncoder.encode("NewPass1")).thenReturn("newHash");

        service.updateAccount(TEACHER_ID, request(null, "gv01@tsdms.local", "NewPass1"));

        assertThat(au.getPasswordHash()).isEqualTo("newHash"); // băm, không lưu thô
        verify(refreshTokenRepo).revokeAllActiveByAppUserId(eq(USER_ID), any(Instant.class));
    }

    @Test
    void blankPassword_keepsOldHashAndDoesNotRevokeSessions() {
        // Ô mật khẩu để trống = chỉ sửa username/email. Nếu code băm luôn chuỗi rỗng thì giáo
        // viên mất quyền đăng nhập mà không ai biết vì sao.
        AppUser au = givenTeacherWithAccount();

        service.updateAccount(TEACHER_ID, request(null, "moi@tsdms.local", "  "));

        assertThat(au.getPasswordHash()).isEqualTo("oldHash");
        assertThat(au.getEmail()).isEqualTo("moi@tsdms.local");
        verify(passwordEncoder, never()).encode(any());
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }

    @Test
    void duplicateUsername_throws409BeforeTouchingPassword() {
        givenTeacherWithAccount();
        when(appUserRepository.existsByUsernameAndDeletedFalseAndIdNot("trung", USER_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateAccount(TEACHER_ID, request("trung", "gv01@tsdms.local", "NewPass1")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(passwordEncoder, never()).encode(any());
        verify(refreshTokenRepo, never()).revokeAllActiveByAppUserId(any(), any());
    }
}
