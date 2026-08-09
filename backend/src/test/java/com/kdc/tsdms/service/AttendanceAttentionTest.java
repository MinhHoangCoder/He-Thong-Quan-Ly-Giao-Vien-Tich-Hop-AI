package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.AttendanceResponse;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Hộp "Cần xử lý" của kế toán — cái gì lọt vào, cái gì không.
 *
 * <p>Giá trị của danh sách này nằm ở chỗ MỌI dòng trong đó đều đáng xem. Trước đây nó lấy cả
 * "đã vào lớp mà chưa có giờ ra" của buổi ĐANG DIỄN RA — tức là mọi giáo viên đang đứng lớp —
 * nên mở giữa giờ dạy là thấy một danh sách dài toàn chuyện bình thường, và người dùng học
 * cách phớt lờ nó.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceAttentionTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private AttendanceRepository attendanceRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private com.kdc.tsdms.repository.TeacherRepository teacherRepo;

    @InjectMocks
    private AttendanceService service;

    private final List<Attendance> rows = new ArrayList<>();
    private final List<Schedule> sessions = new ArrayList<>();

    /** Dựng một dòng chấm công gắn với buổi kết thúc cách đây {@code endedMinutesAgo} phút. */
    private Attendance row(long id, int endedMinutesAgo) {
        LocalDateTime end = LocalDateTime.now(VN).minusMinutes(endedMinutesAgo);
        Schedule s = new Schedule();
        s.setId(id);
        s.setStartTime(end.minusMinutes(45));
        s.setEndTime(end);
        sessions.add(s);

        Attendance a = new Attendance();
        a.setId(id);
        a.setScheduleId(id);
        a.setTeacherId(7);
        a.setWorkDate(LocalDate.now(VN));
        a.setStatus("PRESENT");
        a.setCheckInMethod("SELF");
        rows.add(a);
        return a;
    }

    private List<Long> attentionIds() {
        when(attendanceRepo.findByWorkDateBetweenOrderByWorkDateDescIdDesc(any(), any()))
                .thenReturn(rows);
        when(scheduleRepo.findAllById(any())).thenReturn(sessions);
        return service.attention(LocalDate.now(VN), LocalDate.now(VN)).stream()
                .map(r -> r.id)
                .toList();
    }

    @Test
    void dangDungLopThiKhongPhaiViecCanXuLy() {
        // Buổi còn 20 phút nữa mới tan, giáo viên đã vào lớp và dĩ nhiên chưa check-out.
        Attendance dangDay = row(1L, -20);
        dangDay.setCheckIn(LocalTime.of(7, 0));
        dangDay.setCheckOut(null);

        assertThat(attentionIds()).isEmpty();
    }

    @Test
    void treoSauKhiBuoiDaTanThiPhaiBao() {
        Attendance treo = row(2L, 30);
        treo.setCheckIn(LocalTime.of(7, 0));
        treo.setCheckOut(null);

        assertThat(attentionIds()).containsExactly(2L);
    }

    @Test
    void heThongChotHoGioRaThiPhaiBao() {
        Attendance auto = row(3L, 60);
        auto.setCheckIn(LocalTime.of(7, 0));
        auto.setCheckOut(LocalTime.of(7, 45));
        auto.setAutoCheckOut(true);

        assertThat(attentionIds()).containsExactly(3L);
    }

    @Test
    void heThongGhiVangThiPhaiBao() {
        Attendance vang = row(4L, 60);
        vang.setStatus("ABSENT");
        vang.setCheckInMethod("SYSTEM");

        assertThat(attentionIds()).containsExactly(4L);
    }

    @Test
    void dongDuGioVaoRaBinhThuongThiBoQua() {
        Attendance ok = row(5L, 60);
        ok.setCheckIn(LocalTime.of(7, 0));
        ok.setCheckOut(LocalTime.of(7, 45));

        assertThat(attentionIds()).isEmpty();
    }

    /** Không tra được buổi (dữ liệu lệch) thì báo còn hơn bỏ sót. */
    @Test
    void khongTraDuocBuoiThiVanBao() {
        Attendance treo = row(6L, 10);
        treo.setCheckIn(LocalTime.of(7, 0));
        treo.setCheckOut(null);
        sessions.clear();

        assertThat(attentionIds()).containsExactly(6L);
    }

    @Test
    void locDung_nhieuDongLanLon() {
        Attendance dangDay = row(10L, -20);
        dangDay.setCheckIn(LocalTime.of(7, 0));
        Attendance treo = row(11L, 40);
        treo.setCheckIn(LocalTime.of(7, 0));
        Attendance binhThuong = row(12L, 90);
        binhThuong.setCheckIn(LocalTime.of(7, 0));
        binhThuong.setCheckOut(LocalTime.of(7, 45));

        assertThat(attentionIds()).containsExactly(11L);
    }

    @Test
    void tenGiaoVienVanDuocGhepVaoKetQua() {
        Attendance treo = row(20L, 30);
        treo.setCheckIn(LocalTime.of(7, 0));
        when(attendanceRepo.findByWorkDateBetweenOrderByWorkDateDescIdDesc(any(), any()))
                .thenReturn(rows);
        when(scheduleRepo.findAllById(any())).thenReturn(sessions);

        List<AttendanceResponse> res = service.attention(LocalDate.now(VN), LocalDate.now(VN));
        assertThat(res).hasSize(1);
        assertThat(res.get(0).teacherName).isEqualTo("(GV #7)");
    }
}
