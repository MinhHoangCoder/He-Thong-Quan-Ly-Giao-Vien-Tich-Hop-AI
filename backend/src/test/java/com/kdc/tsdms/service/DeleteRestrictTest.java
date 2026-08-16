package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Lesson;
import com.kdc.tsdms.entity.LessonFile;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.LessonFileRepository;
import com.kdc.tsdms.repository.LessonRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.ServiceContractRepository;
import com.kdc.tsdms.repository.StudentRepository;
import com.kdc.tsdms.repository.SubjectCategoryRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ĐỢT 1 — luật RESTRICT: còn dữ liệu con đang sống thì cấm xóa bản ghi cha.
 *
 * <p>Vì sao phải có test riêng cho việc này: khóa ngoại của SQL Server KHÔNG bảo vệ được ở đây.
 * Dự án xóa MỀM (một câu {@code UPDATE IsDeleted = 1}) mà khóa ngoại chỉ chặn {@code DELETE},
 * nên nếu ai đó lỡ gỡ đoạn guard trong service thì không có lớp nào phía dưới đỡ, và cũng không
 * có gì báo động: dữ liệu vẫn hiện đầy đủ ở mọi màn hình, chỉ là cha đã biến mất. Bộ test này
 * là lớp phòng thủ duy nhất.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeleteRestrictTest {

    private static void assertConflict(Throwable e) {
        assertThat(e).isInstanceOf(ApiException.class);
        assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    /* ══════════════════ TRƯỜNG ══════════════════ */

    @Nested
    class XoaTruong {

        @Mock
        private SchoolRepository schoolRepo;

        @Mock
        private BranchRepository branchRepo;

        @Mock
        private SchoolClassRepository classRepo;

        @Mock
        private AssignmentRepository assignmentRepo;

        @Mock
        private ServiceContractRepository serviceContractRepo;

        @Mock
        private StudentRepository studentRepo;

        @InjectMocks
        private SchoolService service;

        private void givenSchool() {
            School s = new School();
            s.setId(1);
            s.setName("THCS Ba Đình");
            when(schoolRepo.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(s));
        }

        @Test
        void conLopHoc_thiCam() {
            givenSchool();
            when(classRepo.countBySchoolIdAndDeletedFalse(1)).thenReturn(3L);

            assertThatThrownBy(() -> service.delete(1))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("THCS Ba Đình")
                    .hasMessageContaining("3 lớp học");
            verify(schoolRepo, never()).save(any());
        }

        @Test
        void conPhanCongDangChay_thiCam() {
            givenSchool();
            when(assignmentRepo.countBySchoolIdAndStatusInAndDeletedFalse(anyInt(), any()))
                    .thenReturn(2L);

            assertThatThrownBy(() -> service.delete(1)).hasMessageContaining("2 phân công đang chạy");
        }

        @Test
        void nhieuRaoCungLuc_thiKeHET_trongMotLan() {
            // Người dùng phải thấy hết trong một lần, không phải sửa xong bấm lại rồi gặp rào mới.
            givenSchool();
            when(classRepo.countBySchoolIdAndDeletedFalse(1)).thenReturn(3L);
            when(assignmentRepo.countBySchoolIdAndStatusInAndDeletedFalse(anyInt(), any()))
                    .thenReturn(2L);
            when(serviceContractRepo.countBySchoolIdAndDeletedFalse(1)).thenReturn(1L);
            when(studentRepo.countBySchoolIdAndDeletedFalse(1)).thenReturn(40L);

            assertThatThrownBy(() -> service.delete(1))
                    .hasMessageContaining("3 lớp học")
                    .hasMessageContaining("2 phân công đang chạy")
                    .hasMessageContaining("1 hợp đồng dịch vụ")
                    .hasMessageContaining("40 hồ sơ học sinh");
        }

        @Test
        void sachSe_thiXoaDuoc() {
            givenSchool();

            assertThatCode(() -> service.delete(1)).doesNotThrowAnyException();
            verify(schoolRepo).save(any());
        }
    }

    /* ══════════════════ GIÁO VIÊN ══════════════════ */

    @Nested
    class XoaGiaoVien {

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

        @Mock
        private AssignmentRepository assignmentRepo;

        @Mock
        private ScheduleRepository scheduleRepo;

        @InjectMocks
        private TeacherService service;

        private void givenTeacher() {
            Teacher t = new Teacher();
            t.setId(5);
            t.setLastName("Nguyễn Văn");
            t.setFirstName("An");
            when(teacherRepo.findByIdAndDeletedFalse(5)).thenReturn(Optional.of(t));
        }

        @Test
        void conPhanCongDangChay_thiCam() {
            // Đây là ca đắt tiền nhất: xóa mềm không đụng tới Schedule nên buổi dạy vẫn nằm đó,
            // vẫn được chấm công và vẫn chảy vào bảng lương của một người đã "nghỉ".
            givenTeacher();
            when(assignmentRepo.countByTeacherIdAndStatusInAndDeletedFalse(anyInt(), any()))
                    .thenReturn(2L);

            assertThatThrownBy(() -> service.deleteTeacher(5))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("Nguyễn Văn An")
                    .hasMessageContaining("2 phân công đang chạy");
            verify(teacherRepo, never()).save(any());
        }

        @Test
        void conBuoiDaySapToi_thiCam() {
            givenTeacher();
            when(scheduleRepo.countByTeacherIdAndStartTimeAfterAndStatusInAndDeletedFalse(
                            anyInt(), any(LocalDateTime.class), any()))
                    .thenReturn(5L);

            assertThatThrownBy(() -> service.deleteTeacher(5)).hasMessageContaining("5 buổi dạy sắp tới");
        }

        @Test
        void sachSe_thiXoaDuoc() {
            givenTeacher();

            assertThatCode(() -> service.deleteTeacher(5)).doesNotThrowAnyException();
            verify(teacherRepo).save(any());
        }
    }

    /* ══════════════════ BÀI GIẢNG (CASCADE, không phải RESTRICT) ══════════════════ */

    @Nested
    class XoaBaiGiang {

        @Mock
        private LessonRepository lessonRepo;

        @Mock
        private LessonFileRepository lessonFileRepo;

        @Mock
        private BranchRepository branchRepo;

        @Mock
        private TeacherRepository teacherRepo;

        @Mock
        private SubjectRepository subjectRepo;

        @Mock
        private SubjectCategoryRepository subjectCategoryRepo;

        @InjectMocks
        private LessonService service;

        @Test
        void xoaBaiGiang_thiFileDinhKemBiDanhDauTheo() {
            // LessonFile không có đời sống riêng nên CASCADE mới đúng, khác School/Teacher.
            Lesson l = new Lesson();
            l.setId(9);
            when(lessonRepo.findByIdAndDeletedFalse(9)).thenReturn(Optional.of(l));
            LessonFile f1 = new LessonFile();
            LessonFile f2 = new LessonFile();
            when(lessonFileRepo.findByLessonIdAndDeletedFalse(9)).thenReturn(List.of(f1, f2));

            service.delete(9);

            assertThat(l.isDeleted()).isTrue();
            assertThat(f1.isDeleted()).as("file con phải bị đánh dấu theo").isTrue();
            assertThat(f2.isDeleted()).isTrue();
            assertThat(f1.getDeletedAt()).isNotNull();
            verify(lessonFileRepo).saveAll(any());
        }
    }
}
