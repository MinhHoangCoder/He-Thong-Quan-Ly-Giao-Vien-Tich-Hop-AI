package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.HolidayAbsenceResponse;
import com.kdc.tsdms.dto.HolidayFixAbsencesRequest;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * DỌN DÒNG VẮNG GIẢ CỦA NGÀY NGHỈ (HolidayService.absences + fixAbsences).
 *
 * <p>Bối cảnh: hủy buổi dạy chỉ cứu được buổi CHƯA diễn ra. Buổi "ma" ngày lễ đã qua thì
 * {@code AttendanceSweepService} ghi VẮNG mất rồi, và dòng vắng đó KHÔNG biến mất khi buổi bị
 * hủy — nó nằm lại trong hồ sơ chuyên cần của giáo viên, kèm một thông báo đã gửi tới họ.
 *
 * <p>Ba luật đáng test vì cả ba đều là quyết định nghiệp vụ, không phải hệ quả tự nhiên của code:
 *
 * <ul>
 *   <li>Chỉ đụng dòng nguồn SYSTEM — dòng kế toán ghi tay là phán quyết có người chịu trách
 *       nhiệm, đè lên là xóa mất một quyết định đúng.
 *   <li>Dòng thuộc kỳ lương ĐÃ CHỐT bị tách ra báo riêng, không âm thầm bỏ qua.
 *   <li>Chuyển sang NGHỈ PHÉP chứ không phải CÓ MẶT — Có mặt là khai khống một tiết dạy và
 *       cộng tiền cho buổi chưa từng tồn tại.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HolidayAbsenceFixTest {

    private static final int HOLIDAY_ID = 7;
    private static final int TEACHER_A = 11;
    private static final int TEACHER_B = 12;
    private static final LocalDate FROM = LocalDate.of(2026, 4, 30);
    private static final LocalDate TO = LocalDate.of(2026, 5, 1);

    @Mock
    private HolidayRepository holidayRepo;

    @Mock
    private SchoolRepository schoolRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private AssignmentSlotRepository slotRepo;

    @Mock
    private AttendanceRepository attendanceRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private HolidayService service;

    private Holiday holiday;

    @BeforeEach
    void setUp() {
        holiday = new Holiday();
        holiday.setId(HOLIDAY_ID);
        holiday.setName("Nghỉ lễ 30/4 - 1/5");
        holiday.setFromDate(FROM);
        holiday.setToDate(TO);
        holiday.setKind("NATIONAL");
        holiday.setSchoolId(null); // toàn hệ thống → không phải lọc theo trường
        when(holidayRepo.findById(HOLIDAY_ID)).thenReturn(Optional.of(holiday));

        Teacher a = teacher(TEACHER_A, "Nguyễn Văn", "An");
        Teacher b = teacher(TEACHER_B, "Trần Thị", "Bình");
        when(teacherRepo.findAllById(any())).thenReturn(List.of(a, b));
    }

    /* ─────────────────── absences() ─────────────────── */

    @Test
    void chi_lay_dong_trong_ky_nghi_va_tach_rieng_ky_luong_da_chot() {
        Attendance sua_duoc = absence(100L, TEACHER_A, FROM);
        Attendance da_khoa = absence(101L, TEACHER_B, TO);
        when(attendanceRepo.findSystemAbsencesBetween(FROM, TO)).thenReturn(List.of(sua_duoc, da_khoa));
        // Kỳ lương của GV B đã chốt → mọi thao tác ghi lên chấm công bị chặn.
        when(attendanceService.isPeriodLocked(TEACHER_A, FROM)).thenReturn(false);
        when(attendanceService.isPeriodLocked(TEACHER_B, TO)).thenReturn(true);

        HolidayAbsenceResponse res = service.absences(HOLIDAY_ID);

        assertThat(res.rows()).hasSize(1);
        assertThat(res.rows().get(0).attendanceId()).isEqualTo(100L);
        assertThat(res.rows().get(0).teacherName()).isEqualTo("Nguyễn Văn An");
        // Dòng bị khóa KHÔNG được im lặng bỏ qua: màn hình phải chỉ ra kỳ nào cần mở lại.
        assertThat(res.lockedCount()).isEqualTo(1);
        assertThat(res.lockedPeriods()).containsExactly("5/2026");
    }

    @Test
    void khong_co_dong_nao_thi_tra_ve_rong_chu_khong_no() {
        when(attendanceRepo.findSystemAbsencesBetween(FROM, TO)).thenReturn(List.of());

        HolidayAbsenceResponse res = service.absences(HOLIDAY_ID);

        assertThat(res.rows()).isEmpty();
        assertThat(res.lockedCount()).isZero();
    }

    /* ─────────────────── fixAbsences() ─────────────────── */

    @Test
    void chuyen_sang_nghi_phep_va_ghi_ly_do_vao_tung_dong() {
        Attendance a = absence(100L, TEACHER_A, FROM);
        when(attendanceRepo.findSystemAbsencesBetween(FROM, TO)).thenReturn(List.of(a));
        when(attendanceRepo.findById(100L)).thenReturn(Optional.of(a));

        int fixed = service.fixAbsences(HOLIDAY_ID, new HolidayFixAbsencesRequest(List.of(100L), "Ngày lễ 30/4"));

        assertThat(fixed).isEqualTo(1);
        ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepo).save(saved.capture());
        // NGHỈ PHÉP, không phải CÓ MẶT: buổi không diễn ra thì không được trả tiền cho nó.
        assertThat(saved.getValue().getStatus()).isEqualTo("LEAVE");
        assertThat(saved.getValue().getAdjustReason()).isEqualTo("Ngày lễ 30/4");
    }

    @Test
    void bo_qua_id_khong_nam_trong_danh_sach_duoc_phep() {
        // Client gửi lên một id lạ (dòng chấm công thật của tháng khác, hoặc id bị sửa tay).
        Attendance hop_le = absence(100L, TEACHER_A, FROM);
        when(attendanceRepo.findSystemAbsencesBetween(FROM, TO)).thenReturn(List.of(hop_le));
        when(attendanceRepo.findById(100L)).thenReturn(Optional.of(hop_le));

        int fixed = service.fixAbsences(HOLIDAY_ID, new HolidayFixAbsencesRequest(List.of(100L, 999L), "Ngày lễ 30/4"));

        assertThat(fixed).isEqualTo(1);
        verify(attendanceRepo, never()).findById(999L);
    }

    @Test
    void khong_sua_dong_thuoc_ky_luong_da_chot() {
        Attendance da_khoa = absence(101L, TEACHER_B, TO);
        when(attendanceRepo.findSystemAbsencesBetween(FROM, TO)).thenReturn(List.of(da_khoa));
        when(attendanceService.isPeriodLocked(TEACHER_B, TO)).thenReturn(true);

        int fixed = service.fixAbsences(HOLIDAY_ID, new HolidayFixAbsencesRequest(List.of(101L), "Ngày lễ 1/5"));

        assertThat(fixed).isZero();
        verify(attendanceRepo, never()).save(any());
    }

    @Test
    void gop_mot_thong_bao_cho_moi_giao_vien_du_sua_nhieu_dong() {
        Attendance a1 = absence(100L, TEACHER_A, FROM);
        Attendance a2 = absence(102L, TEACHER_A, TO);
        Attendance b1 = absence(103L, TEACHER_B, FROM);
        when(attendanceRepo.findSystemAbsencesBetween(FROM, TO)).thenReturn(List.of(a1, a2, b1));
        when(attendanceRepo.findById(100L)).thenReturn(Optional.of(a1));
        when(attendanceRepo.findById(102L)).thenReturn(Optional.of(a2));
        when(attendanceRepo.findById(103L)).thenReturn(Optional.of(b1));

        service.fixAbsences(HOLIDAY_ID, new HolidayFixAbsencesRequest(List.of(100L, 102L, 103L), "Ngày lễ"));

        // 3 dòng nhưng chỉ 2 thông báo — giáo viên A không nhận hai tin cho cùng một đợt sửa.
        verify(notificationService, times(2))
                .publishToTeacher(anyInt(), any(), any(), eq("ATTENDANCE"), eq("Attendance"), isNull(), anyBoolean());
    }

    /* ─────────────────── helpers ─────────────────── */

    private static Attendance absence(Long id, Integer teacherId, LocalDate workDate) {
        Attendance a = new Attendance();
        a.setId(id);
        a.setTeacherId(teacherId);
        a.setScheduleId(id * 10);
        a.setWorkDate(workDate);
        a.setStatus("ABSENT");
        a.setCheckInMethod("SYSTEM");
        a.setNote("Hệ thống ghi nhận: hết buổi không có check-in");
        return a;
    }

    private static Teacher teacher(Integer id, String lastName, String firstName) {
        Teacher t = new Teacher();
        t.setId(id);
        t.setLastName(lastName);
        t.setFirstName(firstName);
        return t;
    }
}
