package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.AssignmentCreateRequest;
import com.kdc.tsdms.dto.AssignmentSlotRequest;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
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
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;

/**
 * NGÀY NGHỈ THÌ KHÔNG SINH BUỔI DẠY (Flyway V29 + AssignmentService.generateSchedules).
 *
 * <p>Generator vốn trải ô lịch thành buổi bằng vòng lặp "cộng thêm 1 tuần" và không hỏi ngày
 * đó có phải ngày nghỉ không, nên mọi phiếu kéo dài một học kỳ đều đẻ ra buổi rơi vào 30/4,
 * 2/9, Tết. Buổi "ma" đó không nằm yên: job khép sổ chấm công quét buổi đã qua mà không ai
 * chấm rồi ghi VẮNG, còn PayrollService chỉ trả tiền cho buổi PRESENT/LATE — tức là trừ thẳng
 * vào lương giáo viên vì một buổi chưa từng tồn tại.
 *
 * <p>Test đi qua {@code create()} chứ không gọi thẳng bộ lọc: bản thân luật lọc thì dễ đúng,
 * cái dễ hỏng là nó có được NỐI vào luồng ghi hay không — cùng lý do với
 * {@code AssignmentReactivateConflictTest}.
 *
 * <p>Mốc ngày tính từ {@link BusinessTime#today()} chứ không ghi cứng: phiếu tạo mới bị chặn
 * nếu ngày bắt đầu nằm trong quá khứ, ghi cứng ngày là hẹn giờ cho test tự hỏng.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignmentHolidaySkipTest {

    private static final int TEACHER_ID = 3;
    private static final int SCHOOL_ID = 1;
    private static final int OTHER_SCHOOL_ID = 2;
    private static final int SUBJECT_ID = 9;
    private static final int CLASS_ID = 77;
    private static final int PERIOD_ID = 5;

    /** Thứ Hai gần nhất SAU hôm nay — phiếu mới không được bắt đầu trong quá khứ. */
    private static final LocalDate MON_1 = BusinessTime.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    private static final LocalDate MON_2 = MON_1.plusWeeks(1);
    private static final LocalDate MON_3 = MON_1.plusWeeks(2);
    private static final LocalDate MON_4 = MON_1.plusWeeks(3);

    @Mock
    private AssignmentRepository assignmentRepo;

    @Mock
    private AssignmentSlotRepository slotRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

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
    private AppUserRepository userRepo;

    @Mock
    private AssignmentApprovalService approvalService;

    @Mock
    private TeacherTimeConflictChecker conflictChecker;

    @Mock
    private HolidayRepository holidayRepo;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private AssignmentService service;

    @BeforeEach
    void setUp() {
        Teacher t = new Teacher();
        t.setId(TEACHER_ID);
        when(teacherRepo.findByIdAndDeletedFalse(TEACHER_ID)).thenReturn(Optional.of(t));

        School s = new School();
        s.setId(SCHOOL_ID);
        s.setName("TH Dư Hàng");
        when(schoolRepo.findById(SCHOOL_ID)).thenReturn(Optional.of(s));

        Subject sub = new Subject();
        sub.setId(SUBJECT_ID);
        sub.setName("Lập trình Scratch");
        when(subjectRepo.findByIdAndDeletedFalse(SUBJECT_ID)).thenReturn(Optional.of(sub));

        Period p = new Period();
        p.setId(PERIOD_ID);
        p.setSchoolId(SCHOOL_ID);
        p.setPeriodNumber((short) 6);
        p.setStartTime(LocalTime.of(14, 0));
        p.setEndTime(LocalTime.of(14, 35));
        when(periodRepo.findById(PERIOD_ID)).thenReturn(Optional.of(p));

        SchoolClass c = new SchoolClass();
        c.setId(CLASS_ID);
        c.setSchoolId(SCHOOL_ID);
        c.setName("5A1");
        when(classRepo.findById(CLASS_ID)).thenReturn(Optional.of(c));

        // Phiếu "đã sinh được buổi": chốt chặn phiếu rỗng và hạn xác nhận đều đọc qua đây.
        Schedule daSinh = new Schedule();
        daSinh.setStartTime(MON_1.atTime(14, 0));
        when(scheduleRepo.findByAssignmentIdAndDeletedFalse(any())).thenReturn(List.of(daSinh));
    }

    /** Bốn thứ Hai liên tiếp, một tiết chiều, tại trường {@link #SCHOOL_ID}. */
    private AssignmentCreateRequest phieuBonThuHai() {
        return new AssignmentCreateRequest(
                TEACHER_ID,
                SCHOOL_ID,
                SUBJECT_ID,
                CLASS_ID,
                MON_1,
                MON_4,
                List.of(new AssignmentSlotRequest("MON", SCHOOL_ID, PERIOD_ID, CLASS_ID)));
    }

    private static Holiday nghi(LocalDate tu, LocalDate den, Integer schoolId) {
        Holiday h = new Holiday();
        h.setFromDate(tu);
        h.setToDate(den);
        h.setName("Nghỉ");
        h.setKind(schoolId == null ? "NATIONAL" : "CENTER");
        h.setSchoolId(schoolId);
        return h;
    }

    private List<LocalDate> ngayCacBuoiDaSinh() {
        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepo, atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream()
                .map(s -> s.getStartTime().toLocalDate())
                .toList();
    }

    @Test
    void ngayLeToanHeThongThiBoQuaBuoiDo() {
        when(holidayRepo.findOverlapping(any(), any())).thenReturn(List.of(nghi(MON_2, MON_2, null)));

        service.create(phieuBonThuHai());

        assertThat(ngayCacBuoiDaSinh()).containsExactly(MON_1, MON_3, MON_4);
    }

    /** Kỳ nghỉ RIÊNG của một trường không được đụng tới lịch của trường khác. */
    @Test
    void kyNghiCuaTruongKhacThiVanDay() {
        when(holidayRepo.findOverlapping(any(), any())).thenReturn(List.of(nghi(MON_2, MON_2, OTHER_SCHOOL_ID)));

        service.create(phieuBonThuHai());

        assertThat(ngayCacBuoiDaSinh()).containsExactly(MON_1, MON_2, MON_3, MON_4);
    }

    /** Kỳ nghỉ dài phủ trọn giai đoạn: phiếu không có buổi nào → chặn, không để lại phiếu rỗng. */
    @Test
    void giaiDoanRoiTronVaoKyNghiThiChan() {
        when(holidayRepo.findOverlapping(any(), any())).thenReturn(List.of(nghi(MON_1, MON_4, null)));
        when(scheduleRepo.findByAssignmentIdAndDeletedFalse(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(phieuBonThuHai()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("rơi vào ngày nghỉ");

        verify(scheduleRepo, never()).save(any());
    }
}
