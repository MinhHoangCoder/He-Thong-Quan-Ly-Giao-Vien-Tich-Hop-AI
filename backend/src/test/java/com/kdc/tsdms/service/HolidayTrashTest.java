package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.HolidayDeleteImpactResponse;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/**
 * THÙNG RÁC LỊCH NGHỈ — xóa mềm, khôi phục, và cảnh báo trước khi xóa.
 *
 * <p>Kỳ nghỉ cố ý KHÔNG bị chặn xóa theo dữ liệu con như Trường/Lớp: kỳ gõ nhầm năm (2027
 * thành 2026) vừa là kỳ để lại nhiều hậu quả nhất vừa là kỳ cần xóa gấp nhất. Đổi lại,
 * {@code deleteImpact} phải kể đủ những gì KHÔNG hoàn lại được — nếu con số đó sai thì cảnh
 * báo còn tệ hơn không có, vì người dùng tin nó rồi mới bấm.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HolidayTrashTest {

    private static final int ID = 7;
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
        holiday.setId(ID);
        holiday.setName("Nghỉ lễ 30/4 - 1/5");
        holiday.setFromDate(FROM);
        holiday.setToDate(TO);
        holiday.setKind("NATIONAL");
        holiday.setSchoolId(null);
        when(holidayRepo.save(any(Holiday.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void xoa_chi_gan_co_chu_khong_mat_dong() {
        when(holidayRepo.findById(ID)).thenReturn(Optional.of(holiday));

        service.delete(ID);

        assertThat(holiday.isDeleted()).isTrue();
        assertThat(holiday.getDeletedAt()).isNotNull();
    }

    @Test
    void khoi_phuc_dua_ky_nghi_ve_lai_danh_sach_chinh() {
        holiday.setDeleted(true);
        when(holidayRepo.findById(ID)).thenReturn(Optional.of(holiday));

        service.restore(ID);

        assertThat(holiday.isDeleted()).isFalse();
        assertThat(holiday.getDeletedAt()).isNull();
        assertThat(holiday.getDeletedBy()).isNull();
    }

    @Test
    void khoi_phuc_ky_nghi_dang_dung_thi_bao_khong_co_trong_thung_rac() {
        // Bấm hai lần liên tiếp trên tab Thùng rác là ra tình huống này. Báo 404 với câu
        // nói rõ "trong thùng rác" chứ không âm thầm thành công lần thứ hai.
        when(holidayRepo.findById(ID)).thenReturn(Optional.of(holiday)); // deleted = false

        assertThatThrownBy(() -> service.restore(ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("thùng rác");
    }

    @Test
    void canh_bao_truoc_khi_xoa_ke_du_buoi_da_huy_dong_nghi_phep_va_buoi_sap_toi() {
        when(holidayRepo.findById(ID)).thenReturn(Optional.of(holiday));
        // Đếm theo CHÍNH kỳ nghỉ này (Schedule.HolidayId của V40), không còn quét mù theo
        // khoảng ngày: buổi admin hủy tay trong cùng khoảng đó không phải việc của kỳ nghỉ,
        // và xóa kỳ nghỉ cũng không được trả chúng về lịch.
        when(holidayRepo.countSessionsCancelledByHoliday(ID)).thenReturn(128L);
        when(attendanceRepo.countByStatusAndWorkDateBetween("LEAVE", FROM, TO)).thenReturn(40L);
        // Một buổi ở quá khứ và một buổi ở tương lai: chỉ buổi tương lai được đếm, vì chỉ nó
        // mới "chạy lại bình thường" sau khi kỳ nghỉ bị xóa.
        when(scheduleRepo.findByStartTimeBetweenAndDeletedFalse(any(), any()))
                .thenReturn(List.of(
                        session(LocalDateTime.now().minusDays(1)),
                        session(LocalDateTime.now().plusDays(1))));

        HolidayDeleteImpactResponse r = service.deleteImpact(ID);

        assertThat(r.restorableSessions()).isEqualTo(128);
        assertThat(r.leaveAttendances()).isEqualTo(40);
        assertThat(r.futureSessions()).isEqualTo(1);
    }

    private static Schedule session(LocalDateTime start) {
        Schedule s = new Schedule();
        s.setStartTime(start);
        s.setStatus("APPROVED");
        return s;
    }
}
