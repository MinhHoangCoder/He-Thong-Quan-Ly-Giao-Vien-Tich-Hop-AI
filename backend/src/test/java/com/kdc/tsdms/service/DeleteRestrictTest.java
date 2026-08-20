package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.Certificate;
import com.kdc.tsdms.entity.Employee;
import com.kdc.tsdms.entity.Lesson;
import com.kdc.tsdms.entity.LessonFile;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Room;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Student;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
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
import org.springframework.context.ApplicationContext;
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

    /* ══════════════════ ĐỢT 2 — NHÓM B: BẢO VỆ DỮ LIỆU TIỀN BẠC ══════════════════ */

    /**
     * Xóa vĩnh viễn phân công là chỗ DUY NHẤT trong dự án xóa CỨNG bảng {@code Attendance} —
     * mà chấm công là bằng chứng gốc của con số trên phiếu lương. Nếu kỳ lương đã chốt/đã trả
     * thì đây là rào chắn KHÔNG GỠ ĐƯỢC, cố ý: hệ thống không có nút mở lại kỳ lương.
     */
    @Nested
    class XoaVinhVienPhanCong {

        @Mock
        private AssignmentRepository assignmentRepo;

        @Mock
        private AssignmentSlotRepository slotRepo;

        @Mock
        private ScheduleRepository scheduleRepo;

        @Mock
        private PayrollRepository payrollRepo;

        @Mock
        private TeacherRepository teacherRepo;

        @Mock
        private SchoolRepository schoolRepo;

        @Mock
        private SchoolClassRepository classRepo;

        @Mock
        private SubjectRepository subjectRepo;

        @Mock
        private PeriodRepository periodRepo;

        @Mock
        private AssignmentApprovalService approvalService;

        @Mock
        private TeacherTimeConflictChecker conflictChecker;

        @Mock
        private ApplicationContext applicationContext;

        @InjectMocks
        private AssignmentService service;

        private void givenTrashedAssignment() {
            Assignment a = new Assignment();
            a.setId(11);
            when(assignmentRepo.findByIdAndDeletedTrue(11)).thenReturn(Optional.of(a));
        }

        @Test
        void chamCongThuocKyLuongDaChot_thiCam() {
            givenTrashedAssignment();
            when(payrollRepo.findKyLuongDaChotTheoPhanCong(11)).thenReturn(List.of("7/2026", "8/2026"));

            assertThatThrownBy(() -> service.purge(11))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("kỳ lương đã chốt")
                    .hasMessageContaining("7/2026")
                    .hasMessageContaining("8/2026");

            // Quan trọng hơn cả cái exception: KHÔNG được xóa gì trước khi ném.
            verify(scheduleRepo, never()).deleteAttendanceByAssignmentId(anyInt());
            verify(scheduleRepo, never()).deleteByAssignmentId(anyInt());
            verify(assignmentRepo, never()).delete(any());
        }

        @Test
        void khongDinhKyLuongDaChot_thiXoaHanDuoc() {
            givenTrashedAssignment();
            when(payrollRepo.findKyLuongDaChotTheoPhanCong(11)).thenReturn(List.of());

            service.purge(11);

            verify(scheduleRepo).deleteStatusLogsByAssignmentId(11);
            verify(scheduleRepo).deleteAttendanceByAssignmentId(11);
            verify(scheduleRepo).deleteByAssignmentId(11);
            verify(slotRepo).deleteByAssignmentId(11);
            verify(assignmentRepo).delete(any());
        }

        @Test
        void loiPhaiNoiRoKhongCoCachGo_thayVi_vuiLongXuLyTruocKhiXoa() {
            // Câu mặc định của DeleteGuard hứa một việc người dùng làm được. Ở đây thì không:
            // không có nút mở lại kỳ lương, nên hứa suông là đẩy họ đi tìm cái không tồn tại.
            givenTrashedAssignment();
            when(payrollRepo.findKyLuongDaChotTheoPhanCong(11)).thenReturn(List.of("8/2026"));

            assertThatThrownBy(() -> service.purge(11))
                    .hasMessageContaining("chỉ có thể nằm lại trong thùng rác")
                    .hasMessageNotContaining("Vui lòng xử lý");
        }
    }

    /**
     * Xóa vĩnh viễn giáo viên: trước đây tự tay xóa CỨNG hết chứng chỉ + hợp đồng để dọn đường
     * cho câu {@code DELETE Teacher}, rồi nuốt {@code DataIntegrityViolationException} thành một
     * câu "Không thể xóa vĩnh viễn: giáo viên id=7" chẳng nói gì.
     */
    @Nested
    class XoaVinhVienGiaoVien {

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

        private void givenTrashedTeacher() {
            Teacher t = new Teacher();
            t.setId(7);
            t.setLastName("Trần Thị");
            t.setFirstName("Bình");
            when(teacherRepo.findByIdAndDeletedTrue(7)).thenReturn(Optional.of(t));
        }

        @Test
        void conDuLieuCon_thiCam_vaKeDUNG_TENtungLoai() {
            givenTrashedTeacher();
            when(teacherRepo.countChildRowsByTeacherId(7))
                    .thenReturn(List.<Object[]>of(
                            new Object[] {"certificate", 2},
                            new Object[] {"contract", 1},
                            new Object[] {"attendance", 96},
                            new Object[] {"payroll", 3}));

            assertThatThrownBy(() -> service.deleteTrueTeacher(7))
                    .satisfies(DeleteRestrictTest::assertConflict)
                    .hasMessageContaining("Trần Thị Bình")
                    .hasMessageContaining("2 chứng chỉ")
                    .hasMessageContaining("1 hợp đồng")
                    .hasMessageContaining("96 bản ghi chấm công")
                    .hasMessageContaining("3 phiếu lương");
            verify(teacherRepo, never()).delete(any());
        }

        @Test
        void khongDuocTuXoaHoChungChiVaHopDong() {
            // Đây mới là điểm chốt của Đợt 2: hồ sơ pháp lý không được hủy như hiệu ứng phụ của
            // một thao tác dọn dẹp. Đỏ ở đây = ai đó khôi phục lại hành vi cũ.
            givenTrashedTeacher();
            when(teacherRepo.countChildRowsByTeacherId(7)).thenReturn(List.<Object[]>of(new Object[] {"contract", 1}));

            assertThatThrownBy(() -> service.deleteTrueTeacher(7));

            verify(ceRepo, never()).deleteAll(any());
            verify(contractRepo, never()).deleteAll(any());
        }

        @Test
        void hoSoTrong_thiXoaHanDuoc() {
            // Hồ sơ tạo nhầm, chưa gắn gì — đúng thứ tính năng xóa vĩnh viễn cần phục vụ.
            givenTrashedTeacher();
            when(teacherRepo.countChildRowsByTeacherId(7)).thenReturn(List.of());

            service.deleteTrueTeacher(7);

            verify(teacherRepo).delete(any());
            // Kể cả trên đường đi trót lọt cũng không được tự tay dọn hồ sơ pháp lý — bản cũ xóa
            // chứng chỉ/hợp đồng TRƯỚC khi thử DELETE nên rào chắn đặt sau không đỡ được gì.
            verify(ceRepo, never()).deleteAll(any());
            verify(contractRepo, never()).deleteAll(any());
        }

        @Test
        void bangConLaChuaKhaiNhan_vanPhaiDuocKeRa() {
            // Thành viên thêm bảng con mới vào câu SQL mà quên khai nhãn tiếng Việt: thà hiện
            // mã thô còn hơn im lặng bỏ sót một rào chắn rồi cho xóa.
            givenTrashedTeacher();
            when(teacherRepo.countChildRowsByTeacherId(7))
                    .thenReturn(List.<Object[]>of(new Object[] {"bang_moi_toanh", 5}));

            assertThatThrownBy(() -> service.deleteTrueTeacher(7)).hasMessageContaining("5 bang_moi_toanh");
            verify(teacherRepo, never()).delete(any());
        }
    }

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
}
