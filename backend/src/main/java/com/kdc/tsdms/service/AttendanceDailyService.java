package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.AttendanceTodayResponse;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BỨC TRANH CHẤM CÔNG TRONG NGÀY — dựng từ LỊCH DẠY chứ không từ bảng chấm công.
 *
 * <p>Đây là điểm khác cốt lõi so với trang Chấm công: buổi giáo viên chưa bấm gì thì chưa
 * có dòng chấm công nào, nhìn bảng chấm công sẽ không thấy nó tồn tại — mà đó lại đúng là
 * buổi cần để mắt tới. Đi từ lịch dạy thì mọi buổi đều xuất hiện, chấm rồi hay chưa.
 *
 * <p>Phục vụ hai chỗ dùng chung một phép tính: tab "Hôm nay" của admin và thông báo tổng kết
 * cuối ngày. Một công thức, hai đầu ra — số trên màn hình và số trong tin nhắn không lệch nhau.
 */
@Service
public class AttendanceDailyService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceDailyService.class);

    /** Giờ buổi dạy là giờ TƯỜNG Việt Nam, còn JVM bị pin UTC — xem AttendanceService. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Cửa sổ check-in mở sớm bao nhiêu phút — khớp AttendanceService để hai nơi cùng nhãn. */
    private static final int EARLY_CHECKIN_MIN = 30;

    private final ScheduleRepository scheduleRepo;
    private final AttendanceRepository attendanceRepo;
    private final TeacherRepository teacherRepo;
    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final NotificationService notificationService;

    public AttendanceDailyService(
            ScheduleRepository scheduleRepo,
            AttendanceRepository attendanceRepo,
            TeacherRepository teacherRepo,
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            NotificationService notificationService) {
        this.scheduleRepo = scheduleRepo;
        this.attendanceRepo = attendanceRepo;
        this.teacherRepo = teacherRepo;
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.notificationService = notificationService;
    }

    /** Toàn cảnh một ngày: mọi buổi đã duyệt + trạng thái chấm công của từng buổi. */
    @Transactional(readOnly = true)
    public AttendanceTodayResponse forDate(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        List<Schedule> sessions = scheduleRepo.findByStartTimeBetweenAndStatusAndDeletedFalseOrderByStartTime(
                date.atStartOfDay(), date.atTime(LocalTime.MAX), "APPROVED");

        Map<Integer, String> teacherNames = new HashMap<>();
        Map<Integer, Assignment> assignmentCache = new HashMap<>();
        Map<Integer, String> schoolNames = new HashMap<>();
        Map<Integer, String> classNames = new HashMap<>();
        Map<Integer, String> subjectNames = new HashMap<>();

        List<AttendanceTodayResponse.Row> rows = new ArrayList<>();
        int marked = 0;
        int late = 0;
        int absent = 0;
        int missing = 0;
        int autoClosed = 0;

        for (Schedule s : sessions) {
            Attendance a =
                    attendanceRepo.findFirstByScheduleIdOrderByIdAsc(s.getId()).orElse(null);
            String state = stateOf(s, a, now);
            if (a != null && ("PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus()))) {
                marked++;
                if ("LATE".equals(a.getStatus())) {
                    late++;
                }
            } else if (a != null) {
                absent++;
            } else if (now.isAfter(s.getEndTime())) {
                missing++;
            }
            if (a != null && a.isAutoCheckOut()) {
                autoClosed++;
            }

            Assignment asg = assignmentCache.computeIfAbsent(
                    s.getAssignmentId(), id -> assignmentRepo.findById(id).orElse(null));
            rows.add(new AttendanceTodayResponse.Row(
                    s.getId(),
                    s.getTeacherId(),
                    teacherNames.computeIfAbsent(s.getTeacherId(), this::teacherName),
                    asg == null ? null : schoolNames.computeIfAbsent(asg.getSchoolId(), this::schoolName),
                    className(s, asg, classNames),
                    asg == null ? null : subjectNames.computeIfAbsent(asg.getSubjectId(), this::subjectName),
                    s.getStartTime(),
                    s.getEndTime(),
                    state,
                    a == null ? null : a.getStatus(),
                    a == null ? null : a.getCheckInMethod(),
                    a == null ? null : a.getCheckIn(),
                    a == null ? null : a.getCheckOut(),
                    a != null && a.isAutoCheckOut()));
        }

        return new AttendanceTodayResponse(
                date,
                new AttendanceTodayResponse.Summary(sessions.size(), marked, late, absent, missing, autoClosed),
                rows);
    }

    /**
     * Tổng kết cuối ngày gửi cho người phụ trách chấm công.
     *
     * <p>20:00 giờ Việt Nam: sau cả ca chiều lẫn ca tối, và job khép sổ (chạy mỗi 15 phút) đã
     * kịp chốt giờ ra cho buổi cuối — báo sớm hơn thì số liệu còn đang động.
     *
     * <p>Ngày không có buổi dạy nào thì im lặng: một tin nhắn "hôm nay 0 buổi" mỗi Chủ nhật
     * chỉ dạy người ta bỏ qua thông báo của hệ thống.
     */
    // KHÔNG readOnly: hàm này GHI thông báo. Đặt readOnly thì Hibernate chuyển sang
    // FlushMode.MANUAL và các bản ghi mới lặng lẽ không bao giờ xuống DB.
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void sendDailySummary() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        AttendanceTodayResponse.Summary sum = forDate(today).summary();
        if (sum.total() == 0) {
            return;
        }
        StringBuilder content = new StringBuilder()
                .append("Hôm nay ")
                .append(sum.marked())
                .append("/")
                .append(sum.total())
                .append(" buổi đã được chấm công");
        if (sum.late() > 0) {
            content.append(", ").append(sum.late()).append(" đi muộn");
        }
        if (sum.absent() > 0) {
            content.append(", ").append(sum.absent()).append(" vắng");
        }
        if (sum.missing() > 0) {
            content.append(", ").append(sum.missing()).append(" chưa có ai chấm");
        }
        if (sum.autoClosed() > 0) {
            content.append(", ").append(sum.autoClosed()).append(" buổi hệ thống chốt hộ giờ ra");
        }
        content.append(". Mở trang Chấm công, tab Hôm nay để xem chi tiết.");

        notificationService.publishToPermission(
                "ATTENDANCE_MANAGE",
                "Tổng kết chấm công ngày " + today,
                content.toString(),
                "ATTENDANCE",
                "Attendance",
                null);
        log.info("Da gui tong ket cham cong ngay {}: {}/{} buoi", today, sum.marked(), sum.total());
    }

    /* ══════════════ helpers ══════════════ */

    /**
     * Trạng thái hiển thị của một buổi. Khớp với luật chặn ở {@code AttendanceService.checkIn}
     * để admin nhìn thấy đúng cái giáo viên đang nhìn thấy.
     */
    private static String stateOf(Schedule s, Attendance a, LocalDateTime now) {
        if (a != null) {
            if ("ABSENT".equals(a.getStatus()) || "LEAVE".equals(a.getStatus())) {
                return "ABSENT";
            }
            if (a.getCheckIn() != null && a.getCheckOut() == null) {
                return "CHECKED_IN";
            }
            return "DONE";
        }
        if (now.isBefore(s.getStartTime().minusMinutes(EARLY_CHECKIN_MIN))) {
            return "NOT_YET";
        }
        return now.isAfter(s.getEndTime()) ? "MISSED" : "OPEN";
    }

    private String className(Schedule s, Assignment asg, Map<Integer, String> cache) {
        if (asg == null) {
            return null;
        }
        Integer classId = asg.getClassId();
        if (s.getSourceSlotId() != null) {
            AssignmentSlot slot = slotRepo.findById(s.getSourceSlotId()).orElse(null);
            if (slot != null && slot.getClassId() != null) {
                classId = slot.getClassId();
            }
        }
        return classId == null ? null : cache.computeIfAbsent(classId, this::classNameById);
    }

    private String teacherName(Integer id) {
        return teacherRepo
                .findById(id)
                .map(t -> (t.getLastName() + " " + t.getFirstName()).trim())
                .orElse("(GV #" + id + ")");
    }

    private String schoolName(Integer id) {
        return schoolRepo.findById(id).map(School::getName).orElse(null);
    }

    private String classNameById(Integer id) {
        return classRepo.findById(id).map(SchoolClass::getName).orElse(null);
    }

    private String subjectName(Integer id) {
        return subjectRepo.findById(id).map(Subject::getName).orElse(null);
    }
}
