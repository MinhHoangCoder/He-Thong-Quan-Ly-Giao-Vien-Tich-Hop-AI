package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.AttendanceRequest;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.PayRateRepository;
import com.kdc.tsdms.repository.PayrollRepository;
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
 * BUỔI CHƯA DIỄN RA THÌ KHÔNG AI GHI CHẤM CÔNG ĐƯỢC — kể cả admin.
 *
 * <p>Đây là luật trung tâm của V25. Trước đó nút "Sinh từ lịch dạy" ghi sẵn công cả tháng
 * tương lai, tức trả tiền cho việc chưa làm; bỏ nút thôi chưa đủ, vì POST/PUT thủ công vẫn
 * là một đường vòng. Test này khóa đúng cái ranh giới đó.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceFutureSessionTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private AttendanceRepository attendanceRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private PayrollRepository payrollRepo;

    @Mock
    private NotificationService notificationService;

    /*
     * Ba kho dưới đây phục vụ việc ĐÓNG BĂNG ĐƠN GIÁ vào dòng chấm công (V40): hợp đồng có đơn
     * giá riêng thì thắng, không thì tra barem theo khối của lớp. Lớp test này không kiểm phần
     * tiền nong, nhưng thiếu mock là create() ném NullPointerException trước khi tới được điều
     * cần kiểm — mock rỗng cho ra "không tra được đơn giá", đúng thứ ta muốn ở đây.
     */
    @Mock
    private ContractRepository contractRepo;

    @Mock
    private AssignmentRepository assignmentRepo;

    @Mock
    private PayRateRepository payRateRepo;

    @InjectMocks
    private AttendanceService service;

    @BeforeEach
    void setUp() {
        when(teacherRepo.findByIdAndDeletedFalse(any())).thenReturn(Optional.of(new Teacher()));
        when(scheduleRepo.existsById(anyLong())).thenReturn(true);
        when(payrollRepo.findByTeacherIdAndPeriodYearAndPeriodMonth(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(attendanceRepo.existsByScheduleId(anyLong())).thenReturn(false);
    }

    /**
     * NGÀY của buổi dạy vừa dựng — KHÔNG phải "hôm nay".
     *
     * <p>Hai thứ này khác nhau ở gần nửa đêm, và đó từng làm cả build đỏ: buổi dạy dựng bằng
     * {@code now().plusMinutes(20)} lúc 23:50 rơi sang NGÀY MAI, trong khi dòng chấm công vẫn
     * mang ngày HÔM NAY — service chặn đúng luật "ngày làm việc phải trùng ngày buổi dạy", còn
     * test thì tưởng luật cửa sổ thời gian hỏng.
     */
    private LocalDate sessionDate = LocalDate.now(VN);

    /** Buổi dạy bắt đầu sau {@code minutes} phút kể từ bây giờ (âm = đã bắt đầu). */
    private void sessionStartingIn(int minutes) {
        LocalDateTime start = LocalDateTime.now(VN).plusMinutes(minutes);
        sessionDate = start.toLocalDate();
        Schedule s = new Schedule();
        s.setId(100L);
        s.setTeacherId(7);
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(45));
        s.setStatus("APPROVED");
        when(scheduleRepo.findById(100L)).thenReturn(Optional.of(s));
    }

    private AttendanceRequest req() {
        return new AttendanceRequest(
                7, 100L, sessionDate, LocalTime.of(7, 0), LocalTime.of(7, 45), "PRESENT", null, "Sửa tay");
    }

    @Test
    void buoiConCachHaiTiengThiKhongGhiDuoc() {
        sessionStartingIn(120);
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chưa diễn ra");
    }

    /** Ranh giới trên: 40 phút nữa mới dạy, cửa sổ mở trước 30 phút → vẫn chặn. */
    @Test
    void truocCuaSoBaMuoiPhutVanBiChan() {
        sessionStartingIn(40);
        assertThatThrownBy(() -> service.create(req())).isInstanceOf(ApiException.class);
    }

    /** Ranh giới dưới: còn 20 phút nữa dạy — cửa sổ đã mở, admin ghi được. */
    @Test
    void trongCuaSoThiGhiDuoc() {
        sessionStartingIn(20);
        assertThatCode(() -> service.create(req())).doesNotThrowAnyException();
    }

    @Test
    void buoiDaBatDauThiGhiDuoc() {
        sessionStartingIn(-10);
        assertThatCode(() -> service.create(req())).doesNotThrowAnyException();
    }

    /* ── Dòng chấm công phải KHỚP buổi dạy nó trỏ tới ──
     * Lương suy đơn giá qua Attendance.scheduleId → Schedule → lớp → khối, nên một dòng mang
     * tên GV này mà trỏ vào buổi của GV kia sẽ tính tiền theo cấp lớp của người khác.
     */

    @Test
    void gvTrongDongPhaiTrungGvCuaBuoiDay() {
        sessionStartingIn(-10); // buổi thuộc GV #7
        AttendanceRequest khac = new AttendanceRequest(
                99, 100L, sessionDate, LocalTime.of(7, 0), LocalTime.of(7, 45), "PRESENT", null, "Sửa tay");
        assertThatThrownBy(() -> service.create(khac))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("thuộc về giáo viên khác");
    }

    @Test
    void ngayLamViecPhaiTrungNgayBuoiDay() {
        sessionStartingIn(-10);
        AttendanceRequest lechNgay = new AttendanceRequest(
                7, 100L, sessionDate.plusDays(1), LocalTime.of(7, 0), LocalTime.of(7, 45), "PRESENT", null, "Sửa tay");
        assertThatThrownBy(() -> service.create(lechNgay))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("trùng ngày diễn ra buổi dạy");
    }

    /**
     * Bất biến chống lỗi "đỏ lúc nửa đêm": dòng chấm công phải mang ĐÚNG ngày của buổi dạy nó
     * trỏ tới, bất kể chạy vào giờ nào.
     *
     * <p>Trước đây các test ở trên dựng buổi bằng {@code now().plusMinutes(n)} nhưng lại gửi
     * {@code LocalDate.now()}. Hai giá trị đó chỉ khác nhau trong khoảng 23:30–24:00 — mỗi đêm
     * CI đỏ một lần rồi sáng ra tự xanh, kiểu lỗi tốn thời gian nhất để truy. Test này kiểm
     * thẳng bất biến nên đúng ở mọi thời điểm, không phải chờ tới nửa đêm mới biết.
     */
    @Test
    void ngayCuaDongChamCong_luonBamTheoBuoiDay() {
        sessionStartingIn(20);
        LocalDate ngayBuoiDay =
                scheduleRepo.findById(100L).orElseThrow().getStartTime().toLocalDate();

        assertThat(req().workDate()).isEqualTo(ngayBuoiDay);
    }

    @Test
    void buoiKhongTonTaiThiBaoLoiRoRang() {
        when(scheduleRepo.findById(100L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Không tìm thấy buổi dạy");
    }

    /** Kỳ lương đã chốt vẫn chặn trước, không phụ thuộc buổi đã bắt đầu hay chưa. */
    @Test
    void kyLuongDaChotThiChanTruoc() {
        sessionStartingIn(-10);
        com.kdc.tsdms.entity.Payroll p = new com.kdc.tsdms.entity.Payroll();
        p.setStatus("PAID");
        when(payrollRepo.findByTeacherIdAndPeriodYearAndPeriodMonth(any(), any(), any()))
                .thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    /* ── Suy trạng thái từ giờ vào: dùng chung cho check-in thật lẫn yêu cầu bổ sung ── */

    @Test
    void vaoSomHoacDungGioLaCoMat() {
        Schedule s = new Schedule();
        s.setStartTime(LocalDateTime.of(2026, 5, 20, 7, 0));
        assertThat(AttendanceService.deriveStatus(s, LocalTime.of(6, 55))).isEqualTo("PRESENT");
        assertThat(AttendanceService.deriveStatus(s, LocalTime.of(7, 15))).isEqualTo("PRESENT");
    }

    @Test
    void quaMuoiLamPhutLaDiMuon() {
        Schedule s = new Schedule();
        s.setStartTime(LocalDateTime.of(2026, 5, 20, 7, 0));
        assertThat(AttendanceService.deriveStatus(s, LocalTime.of(7, 16))).isEqualTo("LATE");
    }
}
