package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.AttendanceAmendCreateRequest;
import com.kdc.tsdms.dto.AttendanceAmendReviewRequest;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.AttendanceAmendRequest;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AttendanceAmendRequestRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
 * Luật của luồng xin bổ sung chấm công.
 *
 * <p>Đây là cửa duy nhất còn lại để một buổi đã lỡ được ghi công, nên nó cũng là chỗ dễ bị
 * lạm dụng nhất: khai khống giờ cho buổi chưa dạy xong, xin bù cho sổ đã chốt lương, hoặc
 * gửi tràn cho admin bấm nhầm. Test giữ ba hàng rào đó cùng luật "từ chối phải nói vì sao".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceAmendServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int GV = 7;
    private static final long BUOI = 100L;

    @Mock
    private AttendanceAmendRequestRepository amendRepo;

    @Mock
    private AttendanceRepository attendanceRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private AssignmentRepository assignmentRepo;

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AttendanceAmendService service;

    @BeforeEach
    void setUp() {
        Teacher t = new Teacher();
        t.setId(GV);
        when(teacherRepo.findByAppUserIdAndDeletedFalse(any())).thenReturn(Optional.of(t));
        when(teacherRepo.findById(GV)).thenReturn(Optional.of(t));
        when(attendanceRepo.findFirstByScheduleIdOrderByIdAsc(anyLong())).thenReturn(Optional.empty());
        when(amendRepo.findFirstByScheduleIdAndStatus(anyLong(), anyString())).thenReturn(Optional.empty());
        when(attendanceService.isPeriodLocked(any(), any())).thenReturn(false);
        endedDaysAgo(1);
    }

    /** Buổi dạy kết thúc cách đây {@code days} ngày (âm = còn ở tương lai). */
    private Schedule endedDaysAgo(int days) {
        LocalDateTime end = LocalDateTime.now(VN).minusDays(days);
        Schedule s = new Schedule();
        s.setId(BUOI);
        s.setTeacherId(GV);
        s.setStartTime(end.minusMinutes(45));
        s.setEndTime(end);
        s.setStatus("APPROVED");
        when(scheduleRepo.findById(BUOI)).thenReturn(Optional.of(s));
        return s;
    }

    private static AttendanceAmendCreateRequest req() {
        return new AttendanceAmendCreateRequest(BUOI, "Máy hết pin", LocalTime.of(7, 0), LocalTime.of(7, 45));
    }

    private AttendanceAmendRequest pending() {
        AttendanceAmendRequest r = new AttendanceAmendRequest();
        r.setId(1L);
        r.setTeacherId(GV);
        r.setScheduleId(BUOI);
        r.setReason("Máy hết pin");
        r.setProposedCheckIn(LocalTime.of(7, 0));
        r.setProposedCheckOut(LocalTime.of(7, 45));
        r.setStatus("PENDING");
        when(amendRepo.findById(1L)).thenReturn(Optional.of(r));
        return r;
    }

    /* ══════════ Giáo viên gửi ══════════ */

    @Test
    void guiHopLeThiBaoChoNguoiPhuTrachChamCong() {
        assertThatCode(() -> service.create(req())).doesNotThrowAnyException();
        verify(notificationService)
                .publishToPermission(
                        eq("ATTENDANCE_MANAGE"), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void buoiChuaKetThucThiChuaXinDuoc() {
        endedDaysAgo(-1); // buổi kết thúc vào ngày mai
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chưa kết thúc");
    }

    @Test
    void quaBayNgayThiHetHanXin() {
        endedDaysAgo(8);
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("quá hạn");
    }

    @Test
    void dungBayNgayThiVanConHan() {
        endedDaysAgo(7);
        assertThatCode(() -> service.create(req())).doesNotThrowAnyException();
    }

    @Test
    void kyLuongDaChotThiKhongXinDuoc() {
        when(attendanceService.isPeriodLocked(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void buoiDaCoCongThiKhongCanXin() {
        Attendance a = new Attendance();
        a.setStatus("PRESENT");
        when(attendanceRepo.findFirstByScheduleIdOrderByIdAsc(BUOI)).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không cần xin bổ sung");
    }

    /** Dòng Vắng là đúng trường hợp cần xin — không được chặn nhầm nó. */
    @Test
    void buoiDangVangThiVanXinDuoc() {
        Attendance a = new Attendance();
        a.setStatus("ABSENT");
        when(attendanceRepo.findFirstByScheduleIdOrderByIdAsc(BUOI)).thenReturn(Optional.of(a));
        assertThatCode(() -> service.create(req())).doesNotThrowAnyException();
    }

    @Test
    void dangChoDuyetThiKhongGuiTrung() {
        when(amendRepo.findFirstByScheduleIdAndStatus(BUOI, "PENDING"))
                .thenReturn(Optional.of(new AttendanceAmendRequest()));
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đang chờ duyệt");
    }

    @Test
    void buoiCuaNguoiKhacThiKhongXinHo() {
        Schedule s = endedDaysAgo(1);
        s.setTeacherId(99);
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    /* ══════════ Admin xử lý ══════════ */

    @Test
    void duyetThiGhiCongVaBaoGiaoVien() {
        AttendanceAmendRequest r = pending();
        Attendance ghi = new Attendance();
        ghi.setId(55L);
        ghi.setWorkDate(LocalDate.of(2026, 5, 20));
        ghi.setStatus("PRESENT");
        ghi.setCheckIn(LocalTime.of(7, 0));
        ghi.setCheckOut(LocalTime.of(7, 45));
        when(attendanceService.applyApprovedAmend(any(), any(), any(), any(), anyString()))
                .thenReturn(ghi);

        service.approve(1L, new AttendanceAmendReviewRequest(null, null));

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getReviewedAt()).isNotNull();
        verify(notificationService)
                .publishToTeacher(eq(GV), anyString(), anyString(), anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    void duyetLaiYeuCauDaXuLyThiBaoXungDot() {
        AttendanceAmendRequest r = pending();
        r.setStatus("APPROVED");
        assertThatThrownBy(() -> service.approve(1L, new AttendanceAmendReviewRequest(null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đã được xử lý");
    }

    @Test
    void tuChoiPhaiNeuLyDo() {
        pending();
        assertThatThrownBy(() -> service.reject(1L, new AttendanceAmendReviewRequest(null, "   ")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("lý do từ chối");
        verify(notificationService, never())
                .publishToTeacher(anyInt(), anyString(), anyString(), anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    void tuChoiCoLyDoThiLuuVaBaoGiaoVien() {
        AttendanceAmendRequest r = pending();
        service.reject(1L, new AttendanceAmendReviewRequest(null, "Buổi này lớp nghỉ, không có tiết"));

        assertThat(r.getStatus()).isEqualTo("REJECTED");
        assertThat(r.getReviewNote()).isEqualTo("Buổi này lớp nghỉ, không có tiết");
        verify(notificationService)
                .publishToTeacher(eq(GV), anyString(), anyString(), anyString(), anyString(), any(), anyBoolean());
    }
}
