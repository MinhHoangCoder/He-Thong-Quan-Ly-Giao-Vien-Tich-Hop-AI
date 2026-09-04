package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Luật khép sổ chấm công tự động.
 *
 * <p>Điểm cần khóa: job chỉ được đụng vào đúng hai tình huống "giáo viên quên bấm", và phải
 * chừa ra những dòng con người đã quyết (Vắng/Nghỉ phép) lẫn kỳ lương đã chốt — nếu không
 * nó tự tay sửa số liệu đã trả tiền.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceSweepServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private AttendanceRepository attendanceRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AttendanceSweepService sweep;

    /**
     * Buổi dạy kết thúc cách đây {@code minutesAgo} phút, và LUÔN nằm gọn trong một ngày.
     *
     * <p>Buổi dạy thật không vắt qua nửa đêm. Nếu để nó vắt qua — chạy test lúc 00:12 thì
     * "kết thúc 5 phút trước" cho ra buổi 23:32 hôm qua → 00:07 hôm nay — thì giờ vào (23:32)
     * LỚN HƠN giờ tan (00:07) khi so kiểu {@code LocalTime}, rơi đúng vào nhánh dự phòng "vào
     * muộn hơn cả giờ tan" của {@code closeCheckOut} và test đỏ. Kẹp giờ bắt đầu về đầu ngày
     * giữ nguyên ý đồ "kết thúc N phút trước" mà không tạo ra buổi dạy không có thật.
     */
    private Schedule endedMinutesAgo(int minutesAgo) {
        LocalDateTime end = LocalDateTime.now(VN).minusMinutes(minutesAgo);
        LocalDateTime start = end.minusMinutes(35);
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            start = end.toLocalDate().atStartOfDay();
        }
        Schedule s = new Schedule();
        s.setId(100L);
        s.setTeacherId(7);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setStatus("APPROVED");
        return s;
    }

    /** Job lấy chấm công của cả lô theo khoảng ngày, nên stub trả về danh sách chứ không phải Optional. */
    private void given(Schedule s, Attendance a) {
        when(scheduleRepo.findByStartTimeBetweenAndStatusAndDeletedFalseOrderByStartTime(any(), any(), any()))
                .thenReturn(List.of(s));
        when(attendanceRepo.findByWorkDateBetweenOrderByWorkDateDescIdDesc(any(), any()))
                .thenReturn(a == null ? List.of() : List.of(a));
    }

    private Attendance checkedInOnly(Schedule s) {
        Attendance a = new Attendance();
        a.setTeacherId(s.getTeacherId());
        a.setScheduleId(s.getId());
        a.setWorkDate(s.getStartTime().toLocalDate());
        a.setCheckIn(s.getStartTime().toLocalTime());
        a.setStatus("PRESENT");
        a.setCheckInMethod("SELF");
        return a;
    }

    @Test
    void quenCheckOutThiChotBangGioTanTiet() {
        Schedule s = endedMinutesAgo(5);
        Attendance a = checkedInOnly(s);
        given(s, a);

        sweep.sweep();

        // Cắt giây/nano: cột CheckOut là TIME(0), giữ nguyên là lưu xuống bị làm tròn lệch
        // với giá trị vừa tính ra.
        assertThat(a.getCheckOut())
                .isEqualTo(s.getEndTime().toLocalTime().withSecond(0).withNano(0));
        assertThat(a.isAutoCheckOut()).isTrue();
        assertThat(a.getUpdatedBy()).isNull(); // null = hệ thống, để nhật ký ghi đúng
        verify(attendanceRepo).save(a);
    }

    @Test
    void quenCheckInQuaAnHanThiGhiVang() {
        Schedule s = endedMinutesAgo(45); // ân hạn 30 phút đã hết
        given(s, null);

        sweep.sweep();

        ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("ABSENT");
        assertThat(saved.getValue().getCheckInMethod()).isEqualTo("SYSTEM");
        assertThat(saved.getValue().getCheckIn()).isNull();
    }

    @Test
    void trongAnHanThiChuaGhiVang() {
        given(endedMinutesAgo(10), null); // mới hết buổi 10 phút

        sweep.sweep();

        verify(attendanceRepo, never()).saveAndFlush(any());
    }

    @Test
    void buoiChuaKetThucThiKhongDungToi() {
        Schedule s = endedMinutesAgo(-20); // còn 20 phút nữa mới hết
        given(s, checkedInOnly(s));

        sweep.sweep();

        verify(attendanceRepo, never()).save(any());
        verify(attendanceRepo, never()).saveAndFlush(any());
    }

    @Test
    void dongDaGhiVangHoacNghiPhepThiDeYen() {
        Schedule s = endedMinutesAgo(60);
        Attendance a = checkedInOnly(s);
        a.setStatus("LEAVE"); // kế toán đã quyết
        given(s, a);

        sweep.sweep();

        assertThat(a.getCheckOut()).isNull();
        verify(attendanceRepo, never()).save(any());
    }

    @Test
    void kyLuongDaChotThiKhongDungToi() {
        Schedule s = endedMinutesAgo(60);
        Attendance a = checkedInOnly(s);
        given(s, a);
        when(attendanceService.isPeriodLocked(any(), any())).thenReturn(true);

        sweep.sweep();

        assertThat(a.getCheckOut()).isNull();
        verify(attendanceRepo, never()).save(any());
    }

    @Test
    void vaoLopMuonHonGioTanThiVanChotDuocHopLe() {
        // CK_Attendance_Time đòi CheckIn < CheckOut; dữ liệu lệch không được làm job kẹt.
        Schedule s = endedMinutesAgo(5);
        Attendance a = checkedInOnly(s);
        a.setCheckIn(s.getEndTime().toLocalTime().plusMinutes(10));
        given(s, a);

        sweep.sweep();

        assertThat(a.getCheckOut()).isAfter(a.getCheckIn());
    }

    @Test
    void gioRaDaCoThiKhongGhiDe() {
        Schedule s = endedMinutesAgo(30);
        Attendance a = checkedInOnly(s);
        LocalTime out = s.getEndTime().toLocalTime().minusMinutes(5);
        a.setCheckOut(out);
        given(s, a);

        sweep.sweep();

        assertThat(a.getCheckOut()).isEqualTo(out);
        assertThat(a.isAutoCheckOut()).isFalse();
    }
}
