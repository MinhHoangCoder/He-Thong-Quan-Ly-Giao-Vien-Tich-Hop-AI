package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.AttendanceChangeLogResponse;
import com.kdc.tsdms.dto.AttendanceRequest;
import com.kdc.tsdms.dto.AttendanceResponse;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Attendance;
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
import com.kdc.tsdms.repository.AttendanceChangeLogRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.PayrollRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Chấm công (Attendance).
 *
 * <p>Nguồn buổi dạy = {@link Schedule} đã duyệt; mỗi buổi tối đa MỘT dòng chấm công.
 *
 * <p>Từ V25, công chỉ sinh ra từ việc GIÁO VIÊN TỰ CHẤM. Nút "sinh hàng loạt từ lịch dạy"
 * đã bị bỏ vì nó tạo công cho những buổi chưa diễn ra — tính ra tiền cho việc chưa làm. Ba
 * đường ghi còn lại đều gắn với một buổi đã bắt đầu:
 *
 * <ul>
 *   <li>giáo viên check-in/check-out trong cửa sổ của buổi (nguồn {@code SELF})
 *   <li>{@link AttendanceSweepService} chốt giờ ra hộ khi giáo viên quên bấm, và ghi Vắng
 *       khi hết buổi không ai chấm (nguồn {@code SYSTEM})
 *   <li>admin sửa tay hoặc duyệt yêu cầu bổ sung — bắt buộc kèm lý do vào {@code AdjustReason}
 * </ul>
 *
 * <p>Số giờ dạy tự tính từ giờ vào/ra. Lưu ý: Bảng lương KHÔNG cộng số giờ đó — nó đếm mỗi
 * dòng PRESENT/LATE là một tiết.
 */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepo;
    private final ScheduleRepository scheduleRepo;
    private final TeacherRepository teacherRepo;
    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final PeriodRepository periodRepo;
    private final PayrollRepository payrollRepo;
    private final AttendanceChangeLogRepository changeLogRepo;
    private final AppUserRepository userRepo;
    private final DisplayNameResolver displayNameResolver;
    private final NotificationService notificationService;

    public AttendanceService(
            AttendanceRepository attendanceRepo,
            ScheduleRepository scheduleRepo,
            TeacherRepository teacherRepo,
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            PeriodRepository periodRepo,
            PayrollRepository payrollRepo,
            AttendanceChangeLogRepository changeLogRepo,
            AppUserRepository userRepo,
            DisplayNameResolver displayNameResolver,
            NotificationService notificationService) {
        this.attendanceRepo = attendanceRepo;
        this.scheduleRepo = scheduleRepo;
        this.teacherRepo = teacherRepo;
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.periodRepo = periodRepo;
        this.payrollRepo = payrollRepo;
        this.changeLogRepo = changeLogRepo;
        this.userRepo = userRepo;
        this.displayNameResolver = displayNameResolver;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> list(Integer teacherId, LocalDate from, LocalDate to) {
        // CHỐNG IDOR: GV (có ATTENDANCE_VIEW nhưng không phải staff) chỉ xem chấm công
        // của CHÍNH MÌNH — ép teacherId về hồ sơ người gọi, bỏ qua tham số client gửi.
        Integer scoped = scopedTeacherId(teacherId);
        List<Attendance> items = scoped != null
                ? attendanceRepo.findByTeacherIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(scoped, from, to)
                : attendanceRepo.findByWorkDateBetweenOrderByWorkDateDescIdDesc(from, to);
        Map<Integer, String> nameCache = new HashMap<>();
        return items.stream()
                .map(a -> AttendanceResponse.fromEntity(a, teacherName(a.getTeacherId(), nameCache)))
                .toList();
    }

    /**
     * Bảng chấm công của CHÍNH giáo viên đang đăng nhập (read-only). KHÔNG nhận teacherId từ
     * ngoài — giáo viên tuyệt đối không xem được chấm công người khác qua endpoint này (chống IDOR).
     * Mỗi dòng ghép sẵn trường/lớp/môn/tiết (từ buổi dạy) để frontend hiển thị trực tiếp.
     *
     * @param status lọc tùy chọn theo trạng thái (PRESENT | LATE | ABSENT | LEAVE); null = tất cả.
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> listMine(LocalDate from, LocalDate to, String status) {
        Integer teacherId = currentTeacherId();
        List<Attendance> items =
                attendanceRepo.findByTeacherIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(teacherId, from, to);

        String name =
                teacherRepo.findById(teacherId).map(AttendanceService::fullName).orElse("(GV #" + teacherId + ")");

        // Cache tra cứu (tránh N+1 khi nhiều dòng cùng buổi/trường/lớp/môn/tiết).
        Map<Long, Schedule> scheduleCache = new HashMap<>();
        Map<Integer, Assignment> assignmentCache = new HashMap<>();
        Map<Integer, School> schoolCache = new HashMap<>();
        Map<Integer, SchoolClass> classCache = new HashMap<>();
        Map<Integer, Subject> subjectCache = new HashMap<>();
        Map<Integer, Period> periodCache = new HashMap<>();
        PeriodSessionIndex sessionIndex = new PeriodSessionIndex(periodRepo);

        return items.stream()
                .filter(a -> status == null || status.isBlank() || status.equalsIgnoreCase(a.getStatus()))
                .map(a -> {
                    AttendanceResponse r = AttendanceResponse.fromEntity(a, name);
                    enrichWithSchedule(
                            r,
                            a.getScheduleId(),
                            scheduleCache,
                            assignmentCache,
                            schoolCache,
                            classCache,
                            subjectCache,
                            periodCache,
                            sessionIndex);
                    return r;
                })
                .toList();
    }

    /** Ghép trường/lớp/môn/tiết cho 1 dòng chấm công từ buổi dạy tương ứng (bỏ qua nếu không gắn buổi). */
    private void enrichWithSchedule(
            AttendanceResponse r,
            Long scheduleId,
            Map<Long, Schedule> scheduleCache,
            Map<Integer, Assignment> assignmentCache,
            Map<Integer, School> schoolCache,
            Map<Integer, SchoolClass> classCache,
            Map<Integer, Subject> subjectCache,
            Map<Integer, Period> periodCache,
            PeriodSessionIndex sessionIndex) {
        if (scheduleId == null) {
            return;
        }
        Schedule s = scheduleCache.computeIfAbsent(
                scheduleId, id -> scheduleRepo.findById(id).orElse(null));
        if (s == null) {
            return;
        }
        Assignment a = assignmentCache.computeIfAbsent(
                s.getAssignmentId(), id -> assignmentRepo.findById(id).orElse(null));
        if (a != null) {
            r.schoolId = a.getSchoolId();
            School school = schoolCache.computeIfAbsent(
                    a.getSchoolId(), id -> schoolRepo.findById(id).orElse(null));
            r.schoolName = school != null ? school.getName() : "(Trường #" + a.getSchoolId() + ")";
            // Lớp lấy từ ô lịch gốc của buổi (V16 — mỗi tiết một lớp), fallback lớp cấp
            // phân công cho dữ liệu cũ.
            Integer classId = a.getClassId();
            if (s.getSourceSlotId() != null) {
                AssignmentSlot slot = slotRepo.findById(s.getSourceSlotId()).orElse(null);
                if (slot != null && slot.getClassId() != null) {
                    classId = slot.getClassId();
                }
            }
            r.classId = classId;
            if (classId != null) {
                SchoolClass c = classCache.computeIfAbsent(
                        classId, id -> classRepo.findById(id).orElse(null));
                r.className = c != null ? c.getName() : null;
            }
            r.subjectId = a.getSubjectId();
            Subject subj = subjectCache.computeIfAbsent(
                    a.getSubjectId(), id -> subjectRepo.findById(id).orElse(null));
            r.subjectName = subj != null ? subj.getName() : "(Môn #" + a.getSubjectId() + ")";
        }
        if (s.getPeriodId() != null) {
            Period p = periodCache.computeIfAbsent(
                    s.getPeriodId(), id -> periodRepo.findById(id).orElse(null));
            if (p != null) {
                r.periodId = p.getId();
                r.periodNumber = p.getPeriodNumber();
                r.indexInSession = sessionIndex.of(p);
                r.sessionType = p.getSessionType();
            }
        }
    }

    /** Hồ sơ giáo viên của người đang đăng nhập (báo lỗi nếu tài khoản không phải giáo viên). */
    private Integer currentTeacherId() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .map(Teacher::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không có hồ sơ giáo viên"));
    }

    /** Staff xem tất (teacherId lọc tùy chọn); GV thường luôn bị ép về chính mình. */
    private Integer scopedTeacherId(Integer requested) {
        boolean isStaff = SecurityUtils.hasRole("ADMIN")
                || SecurityUtils.hasRole("EMPLOYEE")
                || SecurityUtils.hasRole("ACCOUNTANT")
                || SecurityUtils.hasRole("HR")
                || SecurityUtils.hasRole("ACADEMIC")
                || SecurityUtils.hasRole("SALES");
        if (isStaff) {
            return requested;
        }
        return currentTeacherId();
    }

    /* ══════════════ GV TỰ CHECK-IN/OUT ══════════════
     *
     * Luật duy nhất: chỉ check-in/out được vào buổi dạy ĐÃ DUYỆT của CHÍNH MÌNH
     * diễn ra HÔM NAY — ngày không có lịch thì không có gì để chấm.
     * Giờ vào/ra là GIỜ SERVER (client không gửi được); vào muộn >15' tự đánh LATE.
     */

    /**
     * Múi giờ nghiệp vụ: JVM bị pin UTC (TsdmsApplication) nhưng giờ buổi dạy
     * (Schedule.StartTime từ Period "07:00"...) là GIỜ TƯỜNG Việt Nam — nếu dùng
     * LocalDateTime.now() mặc định thì "hôm nay" và giờ vào/ra lệch 7 tiếng.
     */
    private static final java.time.ZoneId BUSINESS_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    /** Vào muộn quá ngưỡng này (phút) → trạng thái LATE. */
    private static final int LATE_THRESHOLD_MIN = 15;

    /** Cửa sổ check-in mở SỚM NHẤT bao nhiêu phút trước giờ bắt đầu buổi dạy. */
    private static final int EARLY_CHECKIN_MIN = 30;

    /**
     * Kỳ lương đã CHỐT (FINALIZED) hoặc ĐÃ TRẢ (PAID) thì chấm công tháng đó khóa lại.
     *
     * <p>Chấm công là đầu vào tính tiền: sửa ngược một dòng sau khi đã trả lương làm lệch sổ
     * mà không ai hay. Khóa ở tầng này vì luật cần join Attendance → Payroll theo tháng, DB
     * không diễn đạt được bằng ràng buộc.
     */
    private static final Set<String> LOCKED_PAYROLL_STATUS = Set.of("FINALIZED", "PAID");

    /**
     * Các dòng chấm công CẦN NGƯỜI SOÁT LẠI trong khoảng ngày — gom về một chỗ cho kế toán
     * thay vì bắt họ dò cả bảng bằng mắt. Gồm ba loại:
     *
     * <ul>
     *   <li>hệ thống chốt hộ giờ ra (giáo viên quên check-out)
     *   <li>hệ thống ghi Vắng (hết buổi không ai chấm)
     *   <li>còn treo: đã vào nhưng chưa có giờ ra và job chưa kịp chốt
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> attention(LocalDate from, LocalDate to) {
        Map<Integer, String> nameCache = new HashMap<>();
        return attendanceRepo.findByWorkDateBetweenOrderByWorkDateDescIdDesc(from, to).stream()
                .filter(a -> a.isAutoCheckOut()
                        || "SYSTEM".equals(a.getCheckInMethod())
                        || (a.getCheckIn() != null && a.getCheckOut() == null))
                .map(a -> AttendanceResponse.fromEntity(a, teacherName(a.getTeacherId(), nameCache)))
                .toList();
    }

    /** Nhật ký thay đổi của một dòng chấm công (do trigger DB ghi, chỉ đọc). */
    @Transactional(readOnly = true)
    public List<AttendanceChangeLogResponse> changeLog(Long attendanceId) {
        Map<Integer, String> userNames = new HashMap<>();
        return changeLogRepo.findByAttendanceIdOrderByChangedAtDescIdDesc(attendanceId).stream()
                .map(l -> AttendanceChangeLogResponse.fromEntity(
                        l,
                        l.getChangedByUserId() == null
                                ? null
                                : userNames.computeIfAbsent(l.getChangedByUserId(), id -> userRepo.findById(id)
                                        .map(displayNameResolver::resolve)
                                        .orElse("Người dùng #" + id))))
                .toList();
    }

    /** Kỳ lương của tháng chứa {@code workDate} đã chốt chưa? */
    @Transactional(readOnly = true)
    public boolean isPeriodLocked(Integer teacherId, LocalDate workDate) {
        if (teacherId == null || workDate == null) {
            return false;
        }
        return payrollRepo
                .findByTeacherIdAndPeriodYearAndPeriodMonth(
                        teacherId, (short) workDate.getYear(), (short) workDate.getMonthValue())
                .map(p -> LOCKED_PAYROLL_STATUS.contains(p.getStatus()))
                .orElse(false);
    }

    /** Chặn mọi thao tác ghi lên chấm công thuộc kỳ lương đã chốt. */
    private void assertPeriodOpen(Integer teacherId, LocalDate workDate) {
        if (isPeriodLocked(teacherId, workDate)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Bảng lương tháng " + workDate.getMonthValue() + "/" + workDate.getYear()
                            + " đã chốt — không sửa được chấm công của kỳ này."
                            + " Muốn điều chỉnh phải mở lại bảng lương trước.");
        }
    }

    /** Trạng thái check-in của các buổi dạy HÔM NAY của GV đang đăng nhập. */
    @Transactional(readOnly = true)
    public AttendanceResponse.CheckinToday checkinToday() {
        Integer teacherId = currentTeacherId();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDate today = now.toLocalDate();
        List<Schedule> sessions =
                scheduleRepo.findByTeacherIdAndStatusAndStartTimeBetweenAndDeletedFalseOrderByStartTime(
                        teacherId, "APPROVED", today.atStartOfDay(), today.atTime(LocalTime.MAX));

        List<AttendanceResponse.CheckinSession> list = new ArrayList<>();
        for (Schedule s : sessions) {
            Assignment asg = assignmentRepo.findById(s.getAssignmentId()).orElse(null);
            School school = asg != null ? schoolRepo.findById(asg.getSchoolId()).orElse(null) : null;
            Subject subj =
                    asg != null ? subjectRepo.findById(asg.getSubjectId()).orElse(null) : null;
            SchoolClass cls = asg != null && asg.getClassId() != null
                    ? classRepo.findById(asg.getClassId()).orElse(null)
                    : null;
            Attendance att =
                    attendanceRepo.findFirstByScheduleIdOrderByIdAsc(s.getId()).orElse(null);
            // Chỉ tính là "đã chấm" khi chính GV check-in (SELF) — dòng staff sinh sẵn không tính.
            boolean selfIn = att != null && "SELF".equals(att.getCheckInMethod()) && att.getCheckIn() != null;
            boolean selfOut = selfIn && att.getCheckOut() != null;
            boolean locked = att != null && ("ABSENT".equals(att.getStatus()) || "LEAVE".equals(att.getStatus()));

            // Trạng thái theo cửa sổ check-in (khớp luật chặn ở checkIn()):
            // DONE/CHECKED_IN → đã chấm; LOCKED → kế toán đã ghi Vắng/Nghỉ phép;
            // NOT_YET → chưa mở (sớm hơn EARLY_CHECKIN_MIN phút); MISSED → buổi đã
            // kết thúc mà chưa chấm; OPEN → đang trong cửa sổ, bấm được.
            String state;
            if (selfOut) state = "DONE";
            else if (selfIn) state = "CHECKED_IN";
            else if (locked) state = "LOCKED";
            else if (now.isBefore(s.getStartTime().minusMinutes(EARLY_CHECKIN_MIN))) state = "NOT_YET";
            else if (now.isAfter(s.getEndTime())) state = "MISSED";
            else state = "OPEN";
            list.add(new AttendanceResponse.CheckinSession(
                    s.getId(),
                    asg != null ? asg.getSchoolId() : null,
                    school != null ? school.getName() : null,
                    subj != null ? subj.getName() : null,
                    cls != null ? cls.getName() : null,
                    s.getStartTime(),
                    s.getEndTime(),
                    state,
                    selfIn ? att.getCheckIn() : null,
                    selfOut ? att.getCheckOut() : null));
        }
        return new AttendanceResponse.CheckinToday(today, list);
    }

    /** GV check-in một buổi dạy hôm nay — giờ vào = giờ server, chỉ trong cửa sổ của buổi. */
    @Transactional
    public AttendanceResponse checkIn(AttendanceRequest.Checkin req) {
        Integer teacherId = currentTeacherId();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Schedule s = requireMySessionToday(teacherId, req.scheduleId(), now.toLocalDate());

        // Cửa sổ check-in: từ EARLY_CHECKIN_MIN phút trước giờ vào đến hết giờ buổi —
        // chặn "chấm nhầm" buổi sáng đã trôi qua khi bấm nút lúc chiều (và ngược lại).
        if (now.isBefore(s.getStartTime().minusMinutes(EARLY_CHECKIN_MIN))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Chưa tới giờ check-in — buổi này mở từ "
                            + s.getStartTime().minusMinutes(EARLY_CHECKIN_MIN).toLocalTime()
                            + " (trước giờ dạy " + EARLY_CHECKIN_MIN + " phút)");
        }
        if (now.isAfter(s.getEndTime())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Buổi dạy đã kết thúc — liên hệ kế toán nếu cần bổ sung chấm công");
        }

        // Kỳ lương đã chốt thì cấm cả giáo viên, không riêng gì kế toán: chấm công là đầu vào
        // tính tiền, thêm một dòng vào tháng đã trả lương cũng làm lệch sổ y như sửa tay.
        assertPeriodOpen(teacherId, s.getStartTime().toLocalDate());

        Attendance a =
                attendanceRepo.findFirstByScheduleIdOrderByIdAsc(s.getId()).orElse(null);
        if (a != null && "SELF".equals(a.getCheckInMethod()) && a.getCheckIn() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Bạn đã check-in buổi này lúc " + a.getCheckIn());
        }
        // Kế toán đã ghi nhận Vắng/Nghỉ phép → GV không được check-in để lật thành Có mặt.
        if (a != null && ("ABSENT".equals(a.getStatus()) || "LEAVE".equals(a.getStatus()))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Buổi này đã được ghi nhận \"" + statusLabel(a.getStatus())
                            + "\" — liên hệ kế toán nếu có nhầm lẫn");
        }
        if (a == null) {
            a = new Attendance();
            a.setScheduleId(s.getId());
            a.setCreatedBy(SecurityUtils.currentUserId());
        } else {
            // Đè lên dòng có sẵn = một lần SỬA. Trigger TR_Attendance_ChangeLog đọc UpdatedBy
            // để biết ai làm; không set thì nhật ký ghi "hệ thống" trong khi là giáo viên bấm.
            a.setUpdatedAt(java.time.Instant.now());
            a.setUpdatedBy(SecurityUtils.currentUserId());
        }
        // LATE tính trên giờ ĐÃ CẮT GIÂY (đúng bằng giờ sẽ lưu/hiển thị) để không có chuyện
        // 07:15:30 bị LATE nhưng bảng lại hiện "vào 07:15" ngay ngưỡng.
        LocalDateTime nowMinute = now.withSecond(0).withNano(0);
        a.setTeacherId(teacherId);
        a.setWorkDate(s.getStartTime().toLocalDate());
        a.setCheckIn(nowMinute.toLocalTime());
        a.setCheckOut(null);
        a.setStatus(nowMinute.isAfter(s.getStartTime().plusMinutes(LATE_THRESHOLD_MIN)) ? "LATE" : "PRESENT");
        a.setCheckInMethod("SELF");
        if (req.note() != null && !req.note().isBlank()) {
            a.setNote(req.note().trim());
        }
        try {
            // flush ngay để unique index UX_Attendance_ScheduleId (V16) bắt được
            // 2 request check-in đồng thời cùng vượt qua bước kiểm tra ở trên.
            attendanceRepo.saveAndFlush(a);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "Buổi này vừa được check-in — tải lại để xem trạng thái");
        }

        String name =
                teacherRepo.findById(teacherId).map(AttendanceService::fullName).orElse("");
        return AttendanceResponse.fromEntity(a, name);
    }

    /** GV check-out buổi đã check-in — giờ ra = giờ server. */
    @Transactional
    public AttendanceResponse checkOut(AttendanceRequest.Checkin req) {
        Integer teacherId = currentTeacherId();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        // Cho check-out cả buổi HÔM QUA còn treo (dạy tối quên bấm / buổi vắt qua 0h)
        // thay vì kẹt CHECKED_IN vĩnh viễn vì luật "chỉ hôm nay".
        Schedule s = requireMySessionForCheckout(teacherId, req.scheduleId(), now.toLocalDate());

        Attendance a = attendanceRepo
                .findFirstByScheduleIdOrderByIdAsc(s.getId())
                .filter(x -> "SELF".equals(x.getCheckInMethod()) && x.getCheckIn() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Bạn chưa check-in buổi này"));
        if (a.getCheckOut() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Bạn đã check-out buổi này lúc " + a.getCheckOut());
        }
        // Kế toán đã chuyển buổi thành Vắng/Nghỉ phép sau khi GV check-in → không cho
        // check-out chồng lên (tránh dòng Vắng mà lại có đủ giờ vào/ra).
        if ("ABSENT".equals(a.getStatus()) || "LEAVE".equals(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Buổi này đã được ghi nhận \"" + statusLabel(a.getStatus())
                            + "\" — liên hệ kế toán nếu có nhầm lẫn");
        }
        assertPeriodOpen(a.getTeacherId(), a.getWorkDate());

        // Giờ vào/ra là TIME trong NGÀY của buổi dạy (WorkDate) + CHECK CheckIn < CheckOut:
        // nếu đã sang ngày mới thì chốt 23:59 của ngày dạy — ghi nhận gần đúng còn hơn kẹt.
        LocalTime out = now.toLocalDate().isAfter(a.getWorkDate())
                ? LocalTime.of(23, 59)
                : now.toLocalTime().withNano(0).withSecond(0);
        if (!out.isAfter(a.getCheckIn())) {
            // TIME(0) + CHECK (CheckIn < CheckOut): check-out cùng phút với check-in sẽ vỡ constraint.
            throw new ApiException(HttpStatus.BAD_REQUEST, "Check-out phải sau check-in ít nhất 1 phút");
        }

        a.setCheckOut(out);
        // Ghi ai vừa chốt giờ ra — trigger nhật ký đọc UpdatedBy, để trống là log ghi nhầm
        // thành việc của hệ thống (job nền cố tình để null, người thật thì không được).
        a.setUpdatedAt(java.time.Instant.now());
        a.setUpdatedBy(SecurityUtils.currentUserId());
        attendanceRepo.save(a);

        String name =
                teacherRepo.findById(teacherId).map(AttendanceService::fullName).orElse("");
        return AttendanceResponse.fromEntity(a, name);
    }

    /** Buổi dạy phải: tồn tại, ĐÃ DUYỆT, của CHÍNH GV này (chống IDOR), diễn ra HÔM NAY. */
    private Schedule requireMySessionToday(Integer teacherId, Long scheduleId, LocalDate today) {
        Schedule s = requireMySession(teacherId, scheduleId);
        if (!today.equals(s.getStartTime().toLocalDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ check-in/out buổi dạy diễn ra hôm nay");
        }
        return s;
    }

    /** Như trên nhưng cho CHECK-OUT: chấp nhận cả buổi HÔM QUA (check-in còn treo qua 0h). */
    private Schedule requireMySessionForCheckout(Integer teacherId, Long scheduleId, LocalDate today) {
        Schedule s = requireMySession(teacherId, scheduleId);
        LocalDate sessionDate = s.getStartTime().toLocalDate();
        if (!today.equals(sessionDate) && !today.minusDays(1).equals(sessionDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ check-out buổi dạy hôm nay hoặc hôm qua");
        }
        return s;
    }

    private Schedule requireMySession(Integer teacherId, Long scheduleId) {
        Schedule s = scheduleRepo
                .findById(scheduleId)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy buổi dạy"));
        if (!teacherId.equals(s.getTeacherId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Buổi dạy không thuộc về bạn");
        }
        if (!"APPROVED".equals(s.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Buổi dạy chưa được duyệt");
        }
        return s;
    }

    /**
     * BUỔI CHƯA TỚI GIỜ THÌ KHÔNG AI GHI ĐƯỢC — kể cả admin.
     *
     * <p>Ranh giới lấy đúng thời điểm cửa sổ check-in mở ({@value #EARLY_CHECKIN_MIN} phút
     * trước giờ dạy): sớm hơn thế thì buổi học chưa xảy ra, mà chấm công là ghi nhận việc ĐÃ
     * làm. Trước V25 chỗ này để hở nên có thể sinh sẵn cả tháng công cho tương lai.
     *
     * @return buổi dạy đã kiểm tra, để hàm gọi dùng tiếp mà không phải tra lại
     */
    private Schedule assertSessionStarted(Long scheduleId) {
        Schedule s = scheduleRepo
                .findById(scheduleId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy buổi dạy id=" + scheduleId));
        LocalDateTime open = s.getStartTime().minusMinutes(EARLY_CHECKIN_MIN);
        if (LocalDateTime.now(BUSINESS_ZONE).isBefore(open)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Buổi dạy chưa diễn ra — không ghi được chấm công trước " + open.toLocalTime() + " ngày "
                            + open.toLocalDate());
        }
        return s;
    }

    @Transactional
    public AttendanceResponse create(AttendanceRequest req) {
        Teacher teacher = validate(req);
        assertPeriodOpen(req.teacherId(), req.workDate());
        assertSessionStarted(req.scheduleId());
        // UX_Attendance_Schedule: mỗi buổi dạy tối đa 1 dòng — tạo trùng thì sửa dòng cũ thay vì thêm.
        if (attendanceRepo.existsByScheduleId(req.scheduleId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Buổi dạy này đã có dòng chấm công — hãy sửa dòng hiện có");
        }
        Attendance a = new Attendance();
        a.setCreatedBy(SecurityUtils.currentUserId());
        apply(a, req);
        attendanceRepo.save(a);
        notifyTeacherOfAttendance(a, teacher, "Bạn có kết quả chấm công mới");
        return AttendanceResponse.fromEntity(a, fullName(teacher));
    }

    @Transactional
    public AttendanceResponse update(Long id, AttendanceRequest req) {
        Attendance a = attendanceRepo
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy dòng chấm công id=" + id));
        String oldStatus = a.getStatus();
        Teacher teacher = validate(req);
        // Khóa cả kỳ CŨ lẫn kỳ MỚI: chuyển một dòng ra/vào tháng đã chốt cũng là làm lệch sổ.
        assertPeriodOpen(a.getTeacherId(), a.getWorkDate());
        assertPeriodOpen(req.teacherId(), req.workDate());
        assertSessionStarted(req.scheduleId());
        // Không cho trỏ sang buổi đã có dòng chấm công khác (vỡ unique index).
        if (!req.scheduleId().equals(a.getScheduleId())) {
            attendanceRepo.findFirstByScheduleIdOrderByIdAsc(req.scheduleId()).ifPresent(other -> {
                throw new ApiException(
                        HttpStatus.CONFLICT, "Buổi dạy này đã có dòng chấm công khác (id=" + other.getId() + ")");
            });
        }
        apply(a, req);
        // Ghi lại AI sửa — trigger TR_Attendance_ChangeLog đọc hai cột này để vào nhật ký.
        a.setUpdatedAt(java.time.Instant.now());
        a.setUpdatedBy(SecurityUtils.currentUserId());
        attendanceRepo.save(a);
        // Chỉ báo khi TRẠNG THÁI đổi (tránh spam khi chỉ sửa ghi chú/giờ vặt).
        if (!java.util.Objects.equals(oldStatus, a.getStatus())) {
            notifyTeacherOfAttendance(a, teacher, "Chấm công của bạn được cập nhật");
        }
        return AttendanceResponse.fromEntity(a, fullName(teacher));
    }

    /**
     * Ghi công cho một buổi sau khi admin DUYỆT yêu cầu bổ sung.
     *
     * <p>Buổi bị lỡ thường đã có sẵn dòng {@code ABSENT} do job nền sinh, nên ở đây SỬA dòng
     * đó chứ không thêm dòng mới — thêm là vỡ {@code UX_Attendance_ScheduleId}. Nguồn ghi
     * nhận để {@code EMPLOYEE}: công này do người duyệt xác nhận, không phải giáo viên tự
     * bấm, và {@code AdjustReason} giữ dấu vết vì sao.
     *
     * <p>Cố tình KHÔNG gọi {@code assertSessionStarted}: yêu cầu bổ sung chỉ tồn tại cho buổi
     * đã kết thúc, luật "chưa dạy thì không được chấm" đã chặn từ lúc gửi.
     *
     * @param statusOverride admin ép trạng thái; {@code null} thì suy từ giờ giáo viên khai
     */
    @Transactional
    public Attendance applyApprovedAmend(
            Schedule s, LocalTime checkIn, LocalTime checkOut, String statusOverride, String reason) {
        assertPeriodOpen(s.getTeacherId(), s.getStartTime().toLocalDate());
        Attendance a = attendanceRepo
                .findFirstByScheduleIdOrderByIdAsc(s.getId())
                .orElseGet(() -> {
                    Attendance fresh = new Attendance();
                    fresh.setScheduleId(s.getId());
                    fresh.setCreatedBy(SecurityUtils.currentUserId());
                    return fresh;
                });
        a.setTeacherId(s.getTeacherId());
        a.setWorkDate(s.getStartTime().toLocalDate());
        a.setCheckIn(checkIn);
        a.setCheckOut(checkOut);
        a.setStatus(statusOverride != null && !statusOverride.isBlank() ? statusOverride : deriveStatus(s, checkIn));
        a.setCheckInMethod("EMPLOYEE");
        a.setAdjustReason(reason);
        // Dòng Vắng cũ có thể đang mang cờ hệ thống chốt hộ — công đã được người duyệt xác
        // nhận thì không còn là dòng "cần soát lại" nữa.
        a.setAutoCheckOut(false);
        a.setUpdatedAt(java.time.Instant.now());
        a.setUpdatedBy(SecurityUtils.currentUserId());
        return attendanceRepo.save(a);
    }

    /** PRESENT hay LATE — cùng công thức với check-in thật để hai đường ghi không lệch nhau. */
    public static String deriveStatus(Schedule s, LocalTime checkIn) {
        if (checkIn == null) {
            return "PRESENT";
        }
        return checkIn.isAfter(s.getStartTime().toLocalTime().plusMinutes(LATE_THRESHOLD_MIN)) ? "LATE" : "PRESENT";
    }

    /** Phát thông báo (không hành động) cho GV về một dòng chấm công. */
    private void notifyTeacherOfAttendance(Attendance a, Teacher teacher, String title) {
        String content = "Ngày " + a.getWorkDate() + ": " + statusLabel(a.getStatus())
                + (a.getNote() != null && !a.getNote().isBlank() ? " — " + a.getNote() : "");
        notificationService.publish(
                teacher.getAppUserId(), title, content, "ATTENDANCE", "Attendance", a.getId(), false);
    }

    /** Nhãn tiếng Việt của trạng thái — dùng chung cho mọi thông báo về chấm công. */
    public static String statusLabel(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case "PRESENT" -> "Có mặt";
            case "LATE" -> "Đi muộn";
            case "ABSENT" -> "Vắng";
            case "LEAVE" -> "Nghỉ phép";
            default -> status;
        };
    }

    /**
     * Validate chung cho create/update: GV phải tồn tại (update trước đây bỏ sót —
     * đổi teacherId bậy vẫn lưu được), giờ ra phải SAU giờ vào (lọt thì hours=0 âm
     * thầm, lương sai), scheduleId phải có thật (không thì nổ FK 500).
     */
    private Teacher validate(AttendanceRequest req) {
        Teacher teacher = teacherRepo
                .findByIdAndDeletedFalse(req.teacherId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy giáo viên"));
        if (req.checkIn() != null && req.checkOut() != null && !req.checkOut().isAfter(req.checkIn())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Giờ ra phải sau giờ vào");
        }
        if (!scheduleRepo.existsById(req.scheduleId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy buổi dạy id=" + req.scheduleId());
        }
        return teacher;
    }

    private void apply(Attendance a, AttendanceRequest req) {
        a.setTeacherId(req.teacherId());
        a.setScheduleId(req.scheduleId());
        a.setWorkDate(req.workDate());
        a.setCheckIn(req.checkIn());
        a.setCheckOut(req.checkOut());
        a.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "PRESENT");
        a.setNote(req.note());
        a.setAdjustReason(req.adjustReason().trim());
        if (a.getCheckInMethod() == null) {
            a.setCheckInMethod("EMPLOYEE");
        }
    }

    private String teacherName(Integer teacherId, Map<Integer, String> cache) {
        return cache.computeIfAbsent(
                teacherId,
                id -> teacherRepo.findById(id).map(AttendanceService::fullName).orElse("(GV #" + id + ")"));
    }

    private static String fullName(Teacher t) {
        return (t.getLastName() + " " + t.getFirstName()).trim();
    }
}
