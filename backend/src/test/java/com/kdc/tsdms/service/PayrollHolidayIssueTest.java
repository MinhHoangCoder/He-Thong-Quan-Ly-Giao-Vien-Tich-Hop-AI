package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.PayrollHolidayIssueResponse;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * PHÁT HIỆN VẮNG-RƠI-VÀO-NGÀY-NGHỈ TRƯỚC KHI CHỐT LƯƠNG (HolidayService.holidayIssues).
 *
 * <p>Chốt lương là hành động một chiều: sau đó chấm công của kỳ bị khóa. Chốt khi kỳ còn dòng
 * VẮNG mà hệ thống ghi nhầm cho buổi rơi vào ngày lễ nghĩa là khóa luôn lỗi vào trong — và
 * (trước Flyway V32) không còn đường sửa.
 *
 * <p>Cái dễ hỏng ở đây không phải việc đếm, mà là PHÂN BIỆT: vắng thật trong tháng thì phải để
 * yên, chỉ vắng rơi đúng vào ngày nghỉ mới là lỗi. Cảnh báo kêu nhầm vài lần là lần sau không
 * ai đọc nữa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollHolidayIssueTest {

    private static final short YEAR = 2026;
    private static final short MONTH = 9;
    private static final LocalDate QUOC_KHANH = LocalDate.of(2026, 9, 2);
    private static final LocalDate NGAY_THUONG = LocalDate.of(2026, 9, 10);

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

    @Test
    void chi_dem_dong_vang_roi_dung_vao_ngay_nghi() {
        when(holidayRepo.findOverlapping(any(), any())).thenReturn(List.of(holiday(1, "Quốc khánh", QUOC_KHANH)));
        when(attendanceRepo.findSystemAbsencesBetween(any(), any()))
                .thenReturn(List.of(
                        absence(200L, 11, QUOC_KHANH), // lỗi: trường đóng cửa mà ghi vắng
                        absence(201L, 12, NGAY_THUONG))); // vắng THẬT, không được kêu

        PayrollHolidayIssueResponse res = service.holidayIssues(YEAR, MONTH);

        assertThat(res.absenceCount()).isEqualTo(1);
        assertThat(res.teacherCount()).isEqualTo(1);
        assertThat(res.holidays()).hasSize(1);
        assertThat(res.holidays().get(0).name()).isEqualTo("Quốc khánh");
        assertThat(res.holidays().get(0).absenceCount()).isEqualTo(1);
    }

    @Test
    void ky_sach_thi_khong_canh_bao() {
        when(holidayRepo.findOverlapping(any(), any())).thenReturn(List.of(holiday(1, "Quốc khánh", QUOC_KHANH)));
        when(attendanceRepo.findSystemAbsencesBetween(any(), any()))
                .thenReturn(List.of(absence(201L, 12, NGAY_THUONG)));

        PayrollHolidayIssueResponse res = service.holidayIssues(YEAR, MONTH);

        assertThat(res.absenceCount()).isZero();
        assertThat(res.holidays()).isEmpty();
    }

    @Test
    void thang_khong_co_ngay_nghi_thi_khong_hoi_bang_cham_cong() {
        when(holidayRepo.findOverlapping(any(), any())).thenReturn(List.of());

        PayrollHolidayIssueResponse res = service.holidayIssues(YEAR, MONTH);

        assertThat(res.absenceCount()).isZero();
        // Không có ngày nghỉ nào thì mọi dòng vắng đều là vắng thật — quét bảng chấm công chỉ
        // tốn công vô ích ở đúng màn hình người dùng đang chờ.
        org.mockito.Mockito.verify(attendanceRepo, org.mockito.Mockito.never()).findSystemAbsencesBetween(any(), any());
    }

    @Test
    void gop_theo_tung_ky_nghi_de_man_hinh_tro_dung_cho_can_sua() {
        Holiday tet = holiday(2, "Nghỉ Tết", LocalDate.of(2026, 9, 5));
        tet.setToDate(LocalDate.of(2026, 9, 7));
        when(holidayRepo.findOverlapping(any(), any())).thenReturn(List.of(holiday(1, "Quốc khánh", QUOC_KHANH), tet));
        when(attendanceRepo.findSystemAbsencesBetween(any(), any()))
                .thenReturn(List.of(
                        absence(200L, 11, QUOC_KHANH),
                        absence(202L, 11, LocalDate.of(2026, 9, 6)),
                        absence(203L, 13, LocalDate.of(2026, 9, 7))));

        PayrollHolidayIssueResponse res = service.holidayIssues(YEAR, MONTH);

        assertThat(res.absenceCount()).isEqualTo(3);
        assertThat(res.teacherCount()).isEqualTo(2); // GV 11 dính hai dòng, chỉ đếm một lần
        assertThat(res.holidays()).hasSize(2);
        assertThat(res.holidays())
                .extracting(PayrollHolidayIssueResponse.HolidayRef::absenceCount)
                .containsExactlyInAnyOrder(1, 2);
    }

    /* ─────────────────── helpers ─────────────────── */

    private static Holiday holiday(Integer id, String name, LocalDate day) {
        Holiday h = new Holiday();
        h.setId(id);
        h.setName(name);
        h.setFromDate(day);
        h.setToDate(day);
        h.setKind("NATIONAL");
        h.setSchoolId(null); // toàn hệ thống
        return h;
    }

    private static Attendance absence(Long id, Integer teacherId, LocalDate workDate) {
        Attendance a = new Attendance();
        a.setId(id);
        a.setTeacherId(teacherId);
        a.setScheduleId(id * 10);
        a.setWorkDate(workDate);
        a.setStatus("ABSENT");
        a.setCheckInMethod("SYSTEM");
        return a;
    }
}
