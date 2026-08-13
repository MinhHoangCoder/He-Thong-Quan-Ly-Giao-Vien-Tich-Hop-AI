package com.kdc.tsdms.service;

import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KHÉP SỔ CHẤM CÔNG TỰ ĐỘNG — chạy nền, dọn những buổi giáo viên quên bấm.
 *
 * <p>Hai lỗ hổng nó vá:
 *
 * <ul>
 *   <li><b>Quên check-out</b> — dòng chấm công treo không có giờ ra, kẹt vô thời hạn. Job
 *       chốt bằng GIỜ TAN TIẾT và bật cờ {@code AutoCheckOut} để kế toán biết dòng nào là
 *       hệ thống chốt hộ chứ không phải giáo viên bấm.
 *   <li><b>Quên cả check-in</b> — buổi dạy không có dòng nào, không ai biết mà truy. Job ghi
 *       Vắng với nguồn {@code SYSTEM} sau khi hết buổi {@value #ABSENT_GRACE_MIN} phút.
 * </ul>
 *
 * <p>Ân hạn có mặt vì giáo viên hay dạy xong mới nhớ bấm — đánh Vắng ngay lúc chuông reo là
 * oan. Ngược lại, giáo viên KHÔNG bị mất quyền tự chấm trong ân hạn đó: cửa sổ check-in
 * đóng khi hết tiết, nên buổi lỡ rồi thì đằng nào cũng phải nhờ kế toán.
 *
 * <p>Không đụng dòng nào giáo viên/kế toán đã ghi Vắng hoặc Nghỉ phép, và không đụng kỳ
 * lương đã chốt (xem {@link AttendanceService#assertPeriodOpen}).
 */
@Service
public class AttendanceSweepService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSweepService.class);

    /** Giờ buổi dạy là giờ TƯỜNG Việt Nam, còn JVM bị pin UTC — xem AttendanceService. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Hết buổi bao nhiêu phút thì mới ghi Vắng (chừa cho giáo viên dạy xong mới nhớ bấm). */
    private static final int ABSENT_GRACE_MIN = 30;

    /** Nhịp quét — tiết chỉ 35–45 phút nên quét thưa hơn là buổi trôi mất cả tiếng mới thấy. */
    private static final long SWEEP_INTERVAL_MS = 15 * 60_000L;

    /** Chỉ soát lùi trong ngần này ngày: buổi cũ hơn là việc của kế toán, không phải của job. */
    private static final int LOOKBACK_DAYS = 3;

    private final AttendanceRepository attendanceRepo;
    private final ScheduleRepository scheduleRepo;
    private final AttendanceService attendanceService;
    private final NotificationService notificationService;

    public AttendanceSweepService(
            AttendanceRepository attendanceRepo,
            ScheduleRepository scheduleRepo,
            AttendanceService attendanceService,
            NotificationService notificationService) {
        this.attendanceRepo = attendanceRepo;
        this.scheduleRepo = scheduleRepo;
        this.attendanceService = attendanceService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS, initialDelay = 120_000)
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        List<Schedule> ended = scheduleRepo.findByStartTimeBetweenAndStatusAndDeletedFalseOrderByStartTime(
                now.minusDays(LOOKBACK_DAYS).toLocalDate().atStartOfDay(), now, "APPROVED");

        Map<Integer, Integer> closedByTeacher = new HashMap<>();
        Map<Integer, Integer> absentByTeacher = new HashMap<>();

        for (Schedule s : ended) {
            if (now.isBefore(s.getEndTime())) {
                continue; // buổi chưa xong
            }
            Attendance a =
                    attendanceRepo.findFirstByScheduleIdOrderByIdAsc(s.getId()).orElse(null);

            if (a == null) {
                if (now.isAfter(s.getEndTime().plusMinutes(ABSENT_GRACE_MIN)) && markAbsent(s)) {
                    absentByTeacher.merge(s.getTeacherId(), 1, Integer::sum);
                }
            } else if (closeCheckOut(a, s)) {
                closedByTeacher.merge(a.getTeacherId(), 1, Integer::sum);
            }
        }

        notify(
                closedByTeacher,
                "Hệ thống đã chốt giờ ra giúp bạn",
                " buổi dạy bạn quên check-out đã được chốt"
                        + " bằng giờ tan tiết. Vào Chấm công kiểm tra, sai thì báo kế toán.");
        notify(
                absentByTeacher,
                "Buổi dạy chưa được chấm công",
                " buổi dạy đã kết thúc mà không có check-in nên"
                        + " hệ thống ghi nhận Vắng. Nếu bạn có dạy, hãy báo kế toán để chỉnh lại.");
    }

    /** Giáo viên đã check-in nhưng chưa check-out → chốt bằng giờ tan tiết. */
    private boolean closeCheckOut(Attendance a, Schedule s) {
        if (a.getCheckIn() == null || a.getCheckOut() != null) {
            return false;
        }
        // Kế toán đã ghi Vắng/Nghỉ phép thì để yên — dòng đó không nên có giờ ra.
        if ("ABSENT".equals(a.getStatus()) || "LEAVE".equals(a.getStatus())) {
            return false;
        }
        if (attendanceService.isPeriodLocked(a.getTeacherId(), a.getWorkDate())) {
            return false;
        }
        java.time.LocalTime out = s.getEndTime().toLocalTime().withSecond(0).withNano(0);
        // CK_Attendance_Time đòi CheckIn < CheckOut: vào muộn hơn cả giờ tan (dạy bù, sửa
        // tay lệch) thì cộng 1 phút cho hợp lệ còn hơn để dòng kẹt mãi.
        if (!out.isAfter(a.getCheckIn())) {
            out = a.getCheckIn().plusMinutes(1);
        }
        a.setCheckOut(out);
        a.setAutoCheckOut(true);
        a.setUpdatedAt(java.time.Instant.now());
        a.setUpdatedBy(null); // null = hệ thống, trigger ghi vào nhật ký đúng như vậy
        attendanceRepo.save(a);
        log.info("Tu chot gio ra cho buoi #{} (GV #{})", s.getId(), a.getTeacherId());
        return true;
    }

    /** Buổi đã hết mà không ai chấm → ghi Vắng, nguồn SYSTEM. */
    private boolean markAbsent(Schedule s) {
        if (attendanceService.isPeriodLocked(s.getTeacherId(), s.getStartTime().toLocalDate())) {
            return false;
        }
        Attendance a = new Attendance();
        a.setTeacherId(s.getTeacherId());
        a.setScheduleId(s.getId());
        a.setWorkDate(s.getStartTime().toLocalDate());
        a.setStatus("ABSENT");
        a.setCheckInMethod("SYSTEM");
        a.setNote("Hệ thống ghi nhận: hết buổi không có check-in");
        try {
            attendanceRepo.saveAndFlush(a);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Giáo viên vừa check-in đúng lúc job chạy — unique index chặn, bỏ qua là đúng.
            return false;
        }
        log.info("Ghi Vang cho buoi #{} (GV #{})", s.getId(), s.getTeacherId());
        return true;
    }

    private void notify(Map<Integer, Integer> byTeacher, String title, String tail) {
        byTeacher.forEach((teacherId, count) -> notificationService.publishToTeacher(
                teacherId, title, count + tail, "ATTENDANCE", "Attendance", null, false));
    }
}
