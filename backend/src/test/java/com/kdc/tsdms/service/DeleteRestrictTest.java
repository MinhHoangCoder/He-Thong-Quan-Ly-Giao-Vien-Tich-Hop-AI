package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.Certificate;
import com.kdc.tsdms.entity.Employee;
import com.kdc.tsdms.entity.Lesson;
import com.kdc.tsdms.entity.LessonFile;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Room;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Student;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.SubjectCategory;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.ClassEnrollmentRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.EmployeeRepository;
import com.kdc.tsdms.repository.EmployeeScheduleRepository;
import com.kdc.tsdms.repository.LessonFileRepository;
import com.kdc.tsdms.repository.LessonRepository;
import com.kdc.tsdms.repository.PartTimeShiftRequestRepository;
import com.kdc.tsdms.repository.PayrollRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.RoomRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.ServiceContractRepository;
import com.kdc.tsdms.repository.StudentRepository;
import com.kdc.tsdms.repository.SubjectCategoryRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.repository.TeacherSubjectRepository;
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

        @Mock
        private AssignmentSlotRepository slotRepo;

        @InjectMocks
        private SchoolService service;

        /**
         * Trường đã NGỪNG hợp tác — mốc xuất phát của mọi ca xóa.
         *
         * <p>Từ V40 trường còn hợp tác là một rào xóa, nên muốn kiểm các rào KHÁC thì phải dựng
         * trường ở trạng thái đã ngừng; để mặc {@code new School()} là dính ngay rào đầu tiên và
         * không bao giờ chạm tới phần đang muốn kiểm.
         */
        private void givenSchool() {
            School s = new School();
            s.setId(1);
            s.setName("THCS Ba Đình");
            s.setStatus(School.INACTIVE);
            when(schoolRepo.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(s));
        }

        @Test
        void truongDangHoatDong_thiCam() {
            School s = new School();
            s.setId(1);
            s.setName("THCS Ba Đình");
            s.setStatus(School.ACTIVE); // còn hợp tác
            when(schoolRepo.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(s));

            assertThatThrownBy(() -> service.delete(1))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("THCS Ba Đình")
                    .hasMessageContaining("đang hoạt động");
            verify(schoolRepo, never()).save(any());
        }

        @Test
        void conLopDangHoatDong_thiCam() {
            givenSchool();
            when(classRepo.countBySchoolIdAndDeletedFalseAndStatus(1, "ACTIVE")).thenReturn(3L);

            assertThatThrownBy(() -> service.delete(1))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("THCS Ba Đình")
                    .hasMessageContaining("3 lớp đang hoạt động");
            verify(schoolRepo, never()).save(any());
        }

        @Test
        void conPhanCongConHieuLuc_thiCam() {
            givenSchool();
            when(assignmentRepo.countBySchoolIdAndStatusInAndDeletedFalse(anyInt(), any()))
                    .thenReturn(2L);

            assertThatThrownBy(() -> service.delete(1)).hasMessageContaining("2 phân công còn hiệu lực");
        }

        @Test
        void nhieuRaoCungLuc_thiKeHET_trongMotLan() {
            // Người dùng phải thấy hết trong một lần, không phải sửa xong bấm lại rồi gặp rào mới.
            // Kể cả rào "trường đang hoạt động" cũng nằm chung danh sách, không chặn sớm.
            School s = new School();
            s.setId(1);
            s.setName("THCS Ba Đình");
            s.setStatus(School.ACTIVE);
            when(schoolRepo.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(s));
            when(classRepo.countBySchoolIdAndDeletedFalseAndStatus(1, "ACTIVE")).thenReturn(3L);
            when(assignmentRepo.countBySchoolIdAndStatusInAndDeletedFalse(anyInt(), any()))
                    .thenReturn(2L);
            when(schoolRepo.demOLichConHieuLuc(anyInt(), any())).thenReturn(9L);

            assertThatThrownBy(() -> service.delete(1))
                    .hasMessageContaining("đang hoạt động")
                    .hasMessageContaining("3 lớp đang hoạt động")
                    .hasMessageContaining("2 phân công còn hiệu lực")
                    .hasMessageContaining("9 ô thời khóa biểu còn hiệu lực");
        }

        @Test
        void chiCon_oThoiKhoaBieu_thiVanCam() {
            // Từ V27 trường thật nằm ở TỪNG Ô LỊCH: một phiếu trải nhiều trường thì các trường
            // phụ không xuất hiện ở Assignment.SchoolId nào cả. Chỉ đếm cấp phiếu là chặn hụt —
            // đo trên dữ liệu demo có 8 trường lọt lưới đúng kiểu này.
            givenSchool();
            when(schoolRepo.demOLichConHieuLuc(anyInt(), any())).thenReturn(12L);

            assertThatThrownBy(() -> service.delete(1)).hasMessageContaining("12 ô thời khóa biểu còn hiệu lực");
            verify(schoolRepo, never()).save(any());
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

        @Mock
        private AttendanceRepository attendanceRepo;

        @Mock
        private PayrollRepository payrollRepo;

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

    /* ══════════════════ ĐỢT 2 — NHÓM B: BẢO VỆ DỮ LIỆU TIỀN BẠC ══════════════════ */

    /** Chứng chỉ/bằng cấp là hồ sơ pháp lý → xóa MỀM, và file PDF phải ở nguyên trên đĩa. */
    @Nested
    class XoaChungChi {

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

        @Test
        void chiDanhDauMem_khongXoaDongKhoiDb() {
            Certificate c = new Certificate();
            c.setId(4);
            c.setTeacherId(7);
            c.setFileUrl("7/bang-dai-hoc.pdf");
            when(ceRepo.findByIdAndTeacherIdAndDeletedFalse(4, 7)).thenReturn(Optional.of(c));

            service.deleteCertificate(7, 4);

            assertThat(c.isDeleted()).isTrue();
            assertThat(c.getDeletedAt())
                    .as("phải trả lời được câu ai gỡ, gỡ lúc nào")
                    .isNotNull();
            assertThat(c.getFileUrl())
                    .as("dòng DB còn sống thì file PDF phải còn nguyên đường dẫn")
                    .isEqualTo("7/bang-dai-hoc.pdf");
            verify(ceRepo).save(c);
            verify(ceRepo, never()).delete(any());
        }
    }

    /* ══════════════════ ĐỢT 3 — NHÓM D: CHỐT PHÒNG NGỪA ══════════════════ */

    /** Chi nhánh là gốc của gần nửa hệ thống — 5 bảng con cùng treo vào BranchId. */
    @Nested
    class XoaChiNhanh {

        @Mock
        private BranchRepository branchRepo;

        @Mock
        private SchoolRepository schoolRepo;

        @Mock
        private TeacherRepository teacherRepo;

        @Mock
        private EmployeeRepository employeeRepo;

        @Mock
        private LessonRepository lessonRepo;

        @Mock
        private ServiceContractRepository serviceContractRepo;

        @InjectMocks
        private BranchService service;

        private Branch givenBranch() {
            Branch b = new Branch();
            b.setId(2);
            b.setName("Chi nhánh Lê Chân");
            when(branchRepo.findByIdAndDeletedFalse(2)).thenReturn(Optional.of(b));
            return b;
        }

        @Test
        void conBatCuGi_thiCam_vaKeDuCa5Loai() {
            givenBranch();
            when(schoolRepo.countByBranchIdAndDeletedFalse(2)).thenReturn(4L);
            when(teacherRepo.countByBranchIdAndDeletedFalse(2)).thenReturn(30L);
            when(employeeRepo.countByBranchIdAndDeletedFalse(2)).thenReturn(6L);
            when(lessonRepo.countByBranchIdAndDeletedFalse(2)).thenReturn(12L);
            when(serviceContractRepo.countByBranchIdAndDeletedFalse(2)).thenReturn(3L);

            assertThatThrownBy(() -> service.delete(2))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("Chi nhánh Lê Chân")
                    .hasMessageContaining("4 trường đang hợp tác")
                    .hasMessageContaining("30 giáo viên")
                    .hasMessageContaining("6 nhân viên")
                    .hasMessageContaining("12 bài giảng")
                    .hasMessageContaining("3 hợp đồng dịch vụ");
            verify(branchRepo, never()).save(any());
        }

        @Test
        void rongThatSu_thiXoaMemDuoc() {
            Branch b = givenBranch();

            service.delete(2);

            assertThat(b.isDeleted()).isTrue();
            assertThat(b.getDeletedAt()).isNotNull();
            verify(branchRepo).save(b);
        }
    }

    /**
     * Nhân viên: chỉ chặn NGHĨA VỤ TƯƠNG LAI (ca đã xếp, đơn treo). Dấu vết ai-phân-công /
     * ai-duyệt cố tình KHÔNG chặn — service không hề hỏi tới các bảng đó.
     */
    @Nested
    class XoaNhanVien {

        @Mock
        private EmployeeRepository employeeRepo;

        @Mock
        private EmployeeScheduleRepository scheduleRepo;

        @Mock
        private PartTimeShiftRequestRepository shiftRequestRepo;

        @InjectMocks
        private EmployeeService service;

        private Employee givenEmployee() {
            Employee e = new Employee();
            e.setId(9);
            e.setLastName("Phạm Văn");
            e.setFirstName("Cường");
            when(employeeRepo.findByIdAndDeletedFalse(9)).thenReturn(Optional.of(e));
            return e;
        }

        @Test
        void conCaLamSapToi_hoacDonTreo_thiCam() {
            givenEmployee();
            when(scheduleRepo.countByEmployeeIdAndWorkDateGreaterThanEqualAndStatusAndDeletedFalse(
                            anyInt(), any(), any()))
                    .thenReturn(3L);
            when(shiftRequestRepo.countByEmployeeIdAndStatusAndDeletedFalse(9, "PENDING"))
                    .thenReturn(1L);

            assertThatThrownBy(() -> service.delete(9))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("Phạm Văn Cường")
                    .hasMessageContaining("3 ca làm sắp tới")
                    .hasMessageContaining("1 đơn xin ca chờ duyệt");
            verify(employeeRepo, never()).save(any());
        }

        @Test
        void hetNghiaVu_thiXoaMemDuoc_duCoDayLichSuPhanCong() {
            Employee e = givenEmployee();

            service.delete(9);

            assertThat(e.isDeleted()).isTrue();
            verify(employeeRepo).save(e);
        }
    }

    /** Phòng học: chặn theo thứ SẮP DÙNG; buổi đã dạy là lịch sử, không chặn. */
    @Nested
    class XoaPhongHoc {

        @Mock
        private RoomRepository roomRepo;

        @Mock
        private ScheduleRepository scheduleRepo;

        @Mock
        private AssignmentSlotRepository slotRepo;

        @InjectMocks
        private RoomService service;

        private Room givenRoom() {
            Room r = new Room();
            r.setId(15);
            r.setName("P.301");
            when(roomRepo.findByIdAndDeletedFalse(15)).thenReturn(Optional.of(r));
            return r;
        }

        @Test
        void conLichSapToi_hoacTkbDangGan_thiCam() {
            givenRoom();
            when(scheduleRepo.countByRoomIdAndStartTimeAfterAndStatusInAndDeletedFalse(
                            anyInt(), any(LocalDateTime.class), any()))
                    .thenReturn(7L);
            when(slotRepo.countByRoomIdAndDeletedFalse(15)).thenReturn(2L);

            assertThatThrownBy(() -> service.delete(15))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("P.301")
                    .hasMessageContaining("7 buổi dạy sắp tới")
                    .hasMessageContaining("2 ô thời khóa biểu hằng tuần");
            verify(roomRepo, never()).save(any());
        }

        @Test
        void chiConBuoiDaDayTrongQuaKhu_thiVanXoaDuoc() {
            // Hai count đều đếm TƯƠNG LAI/đang sống — quá khứ trả 0 là xóa được, đúng luật.
            Room r = givenRoom();

            service.delete(15);

            assertThat(r.isDeleted()).isTrue();
            verify(roomRepo).save(r);
        }
    }

    /** Học sinh: còn dòng ghi danh nghĩa là còn đang học — rút khỏi lớp trước, xóa hồ sơ sau. */
    @Nested
    class XoaHocSinh {

        @Mock
        private StudentRepository studentRepo;

        @Mock
        private ClassEnrollmentRepository enrollmentRepo;

        @InjectMocks
        private StudentService service;

        private Student givenStudent() {
            Student st = new Student();
            st.setId(88);
            st.setLastName("Lê Thị");
            st.setFirstName("Duyên");
            when(studentRepo.findByIdAndDeletedFalse(88)).thenReturn(Optional.of(st));
            return st;
        }

        @Test
        void conGhiDanhLop_thiCam() {
            givenStudent();
            when(enrollmentRepo.countByStudentId(88)).thenReturn(1L);

            assertThatThrownBy(() -> service.delete(88))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("Lê Thị Duyên")
                    .hasMessageContaining("1 lượt ghi danh lớp đang học");
            verify(studentRepo, never()).save(any());
        }

        @Test
        void daRutKhoiMoiLop_thiXoaMemDuoc() {
            Student st = givenStudent();

            service.delete(88);

            assertThat(st.isDeleted()).isTrue();
            verify(studentRepo).save(st);
        }
    }

    /** Tiết học là chỗ NEO của thời khóa biểu — rút tiết đang có lớp là sập cả cột lịch. */
    @Nested
    class XoaTietHoc {

        @Mock
        private PeriodRepository periodRepo;

        @Mock
        private SchoolRepository schoolRepo;

        @Mock
        private SchoolClassRepository classRepo;

        @Mock
        private AssignmentSlotRepository slotRepo;

        @Mock
        private ScheduleRepository scheduleRepo;

        @InjectMocks
        private PeriodService service;

        private Period givenPeriod() {
            Period p = new Period();
            p.setId(41);
            p.setSchoolId(3);
            p.setPeriodNumber((short) 3);
            p.setSessionType("MORNING");
            when(periodRepo.findByIdAndDeletedFalse(41)).thenReturn(Optional.of(p));
            School truong = new School();
            truong.setName("THCS Ngô Quyền");
            when(schoolRepo.findById(3)).thenReturn(Optional.of(truong));
            return p;
        }

        @Test
        void tkbDangDungTiet_thiCam_vaNoiRoTietNaoTruongNao() {
            givenPeriod();
            when(slotRepo.countByPeriodIdAndDeletedFalse(41)).thenReturn(5L);

            assertThatThrownBy(() -> service.delete(41))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("tiết 3 (buổi sáng) của THCS Ngô Quyền")
                    .hasMessageContaining("5 ô thời khóa biểu hằng tuần");
            verify(periodRepo, never()).save(any());
        }

        @Test
        void khongAiDung_thiXoaMemDuoc() {
            Period p = givenPeriod();

            service.delete(41);

            assertThat(p.isDeleted()).isTrue();
            verify(periodRepo).save(p);
        }
    }

    /**
     * Môn học: từ 2026-08-22 xóa MỀM trở lại. Bản hard delete trước đó xóa hẳn dòng Subject
     * và cuốn theo toàn bộ Lesson + LessonFile + TeacherSubject, nên test này canh hai thứ:
     * còn dữ liệu con thì chặn, và khi xóa được thì KHÔNG có bản ghi nào khác bị đụng vào.
     */
    @Nested
    class XoaMonHoc {

        @Mock
        private SubjectRepository subjectRepo;

        @Mock
        private SubjectCategoryRepository categoryRepo;

        @Mock
        private LessonRepository lessonRepo;

        @Mock
        private TeacherSubjectRepository teacherSubjectRepo;

        @Mock
        private AssignmentRepository assignmentRepo;

        @InjectMocks
        private SubjectService service;

        private Subject monDaTat() {
            Subject s = new Subject();
            s.setId(4);
            s.setName("Lập trình Scratch");
            s.setStatus("DISABLED");
            when(subjectRepo.findByIdAndDeletedFalse(4)).thenReturn(Optional.of(s));
            return s;
        }

        @Test
        void monDangHoatDong_thiChan() {
            Subject s = new Subject();
            s.setId(4);
            s.setStatus("ACTIVE");
            when(subjectRepo.findByIdAndDeletedFalse(4)).thenReturn(Optional.of(s));

            assertThatThrownBy(() -> service.delete(4))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("tắt trạng thái hoạt động");
            verify(subjectRepo, never()).save(any());
        }

        @Test
        void conDuLieuCon_thiChanVaKeHetLyDoTrongMotLan() {
            // DeleteGuard gom hết rào rồi mới báo: người dùng biết còn bao nhiêu việc phải xử
            // lý thay vì bấm xóa ba lần để lần lượt gặp ba câu lỗi khác nhau.
            monDaTat();
            when(assignmentRepo.countBySubjectId(4)).thenReturn(6L);
            when(lessonRepo.countBySubjectIdAndDeletedFalse(4)).thenReturn(2L);
            when(teacherSubjectRepo.demGiaoVienConSongTheoMon(4)).thenReturn(3L);

            assertThatThrownBy(() -> service.delete(4))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("6 phân công giảng dạy")
                    .hasMessageContaining("2 bài giảng")
                    .hasMessageContaining("3 giáo viên đang phụ trách môn");
            verify(subjectRepo, never()).save(any());
        }

        @Test
        void monSachDuLieu_thiXoaMemVaKhongDungToiBaiGiang() {
            Subject s = monDaTat();
            when(assignmentRepo.countBySubjectId(4)).thenReturn(0L);
            when(lessonRepo.countBySubjectIdAndDeletedFalse(4)).thenReturn(0L);
            when(teacherSubjectRepo.demGiaoVienConSongTheoMon(4)).thenReturn(0L);

            service.delete(4);

            assertThat(s.isDeleted()).isTrue();
            assertThat(s.getDeletedAt()).isNotNull();
            verify(subjectRepo).save(s);
            // Điểm chính của lần sửa này: không xóa hộ thứ gì của bảng khác nữa.
            verify(lessonRepo, never()).deleteAll(any());
        }

        /**
         * ĐỢT 5: rào "giáo viên đang phụ trách môn" phải hỏi câu ĐẾM NGƯỜI CÒN SỐNG.
         *
         * <p>Bản cũ gọi {@code countBySubjectId} — đếm cả dòng TeacherSubject của giáo viên đã
         * nằm trong thùng rác (bảng nối không có cờ xóa mềm và không ai dọn nó khi xóa giáo
         * viên). Hậu quả là môn học BẾ TẮC VĨNH VIỄN: báo còn giáo viên phụ trách, người dùng
         * mở danh sách giáo viên tìm mãi không ra ai, mà cũng không có màn hình nào gỡ liên kết.
         */
        @Test
        void giaoVienDaVaoThungRac_thiKhongConGiuMonHocLai() {
            Subject s = monDaTat();
            when(assignmentRepo.countBySubjectId(4)).thenReturn(0L);
            when(lessonRepo.countBySubjectIdAndDeletedFalse(4)).thenReturn(0L);
            // Bảng nối vẫn còn dòng, nhưng chủ nhân của chúng đều đã bị xóa mềm.
            when(teacherSubjectRepo.demGiaoVienConSongTheoMon(4)).thenReturn(0L);

            service.delete(4);

            assertThat(s.isDeleted()).isTrue();
            verify(subjectRepo).save(s);
        }
    }

    /**
     * ĐỢT 5 — Nhóm môn học. Đây là lỗ hổng NẶNG NHẤT của cả chuỗi ràng buộc xóa: xóa nhóm môn
     * cascade thẳng xuống môn con rồi bài giảng mà không hỏi rào chắn nào, tức đi vòng qua trọn
     * vẹn DeleteGuard của SubjectService. Bộ test này canh đúng chỗ đó.
     */
    @Nested
    class XoaNhomMonHoc {

        @Mock
        private SubjectCategoryRepository categoryRepo;

        @Mock
        private SubjectRepository subjectRepo;

        @Mock
        private SubjectService subjectService;

        @InjectMocks
        private SubjectCategoryService service;

        private SubjectCategory nhomDaTat() {
            SubjectCategory sc = new SubjectCategory();
            sc.setId(9);
            sc.setName("Tin học");
            sc.setStatus("DISABLED");
            when(categoryRepo.findByIdAndDeletedFalse(9)).thenReturn(Optional.of(sc));
            return sc;
        }

        private Subject mon(Integer id, String ten) {
            Subject s = new Subject();
            s.setId(id);
            s.setName(ten);
            return s;
        }

        @Test
        void nhomDangHoatDong_thiChan() {
            SubjectCategory sc = new SubjectCategory();
            sc.setId(9);
            sc.setStatus("ACTIVE");
            when(categoryRepo.findByIdAndDeletedFalse(9)).thenReturn(Optional.of(sc));

            assertThatThrownBy(() -> service.delete(9))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("tắt trạng thái hoạt động");
            verify(categoryRepo, never()).save(any());
        }

        /**
         * Trọng tâm: một môn con còn dữ liệu là CẢ nhóm không xóa được, và thông báo phải chỉ
         * đích danh môn nào vướng cái gì. Trước Đợt 5, ca này xóa trót lọt cả môn lẫn bài giảng.
         */
        @Test
        void monConConDuLieu_thiChanCaNhomVaGoiTenTungMon() {
            nhomDaTat();
            Subject tin6 = mon(11, "Tin học 6");
            Subject tin7 = mon(12, "Tin học 7");
            when(subjectRepo.findByCategoryIdAndDeletedFalseOrderByName(9)).thenReturn(List.of(tin6, tin7));
            when(subjectService.raoChanXoaMon(11, "Tin học 6"))
                    .thenReturn(DeleteGuard.of("môn học Tin học 6")
                            .blockIf(3, "phân công giảng dạy")
                            .blockIf(120, "bài giảng"));
            when(subjectService.raoChanXoaMon(12, "Tin học 7")).thenReturn(DeleteGuard.of("môn học Tin học 7"));

            assertThatThrownBy(() -> service.delete(9))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("nhóm môn học Tin học")
                    .hasMessageContaining("môn Tin học 6")
                    .hasMessageContaining("3 phân công giảng dạy")
                    .hasMessageContaining("120 bài giảng");

            // Không được đụng vào dòng nào: môn sạch nằm cùng nhóm cũng phải nguyên vẹn.
            assertThat(tin6.isDeleted()).isFalse();
            assertThat(tin7.isDeleted()).isFalse();
            verify(subjectRepo, never()).saveAll(any());
            verify(categoryRepo, never()).save(any());
        }

        /** Mọi môn con đều rỗng thì cascade chạy: nhóm và các môn cùng vào thùng rác một lượt. */
        @Test
        void moiMonConDeuRong_thiXoaMemCaNhomLanMonCon() {
            SubjectCategory sc = nhomDaTat();
            Subject tin6 = mon(11, "Tin học 6");
            when(subjectRepo.findByCategoryIdAndDeletedFalseOrderByName(9)).thenReturn(List.of(tin6));
            when(subjectService.raoChanXoaMon(11, "Tin học 6")).thenReturn(DeleteGuard.of("môn học Tin học 6"));

            service.delete(9);

            assertThat(tin6.isDeleted()).isTrue();
            assertThat(tin6.getDeletedAt()).isNotNull();
            assertThat(sc.isDeleted()).isTrue();
            verify(subjectRepo).saveAll(List.of(tin6));
            verify(categoryRepo).save(sc);
        }

        /** Nhóm rỗng thì xóa thẳng, không hỏi SubjectService câu nào. */
        @Test
        void nhomRong_thiXoaThang() {
            SubjectCategory sc = nhomDaTat();
            when(subjectRepo.findByCategoryIdAndDeletedFalseOrderByName(9)).thenReturn(List.of());

            service.delete(9);

            assertThat(sc.isDeleted()).isTrue();
            verify(categoryRepo).save(sc);
            verify(subjectService, never()).raoChanXoaMon(any(), any());
        }
    }
}
