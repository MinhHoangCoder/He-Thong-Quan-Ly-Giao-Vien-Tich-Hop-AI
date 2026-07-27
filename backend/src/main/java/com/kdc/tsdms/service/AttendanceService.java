package com.kdc.tsdms.service;

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
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Chấm công (Attendance).
 *
 * <p>Nguồn buổi dạy = {@link Schedule} đã duyệt. Có thể "sinh" chấm công hàng loạt từ lịch
 * (mỗi buổi 1 dòng, mặc định PRESENT theo giờ tiết) rồi kế toán chỉnh lại; hoặc ghi/sửa lẻ.
 * Số giờ dạy tự tính từ giờ vào/ra — làm đầu vào cho Bảng lương.
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
                            periodCache);
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
            Map<Integer, Period> periodCache) {
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

    /**
     * Sinh chấm công từ các buổi ĐÃ DUYỆT trong [from, to] chưa có bản ghi.
     *
     * @return số dòng chấm công vừa tạo
     */
    @Transactional
    public int generateFromSchedules(LocalDate from, LocalDate to) {
        List<Schedule> schedules = scheduleRepo.findByStartTimeBetweenAndStatusAndDeletedFalseOrderByStartTime(
                from.atStartOfDay(), to.atTime(LocalTime.MAX), "APPROVED");
        Integer userId = SecurityUtils.currentUserId();
        int created = 0;
        // Gom số buổi vừa sinh theo từng GV → mỗi GV chỉ nhận MỘT thông báo tổng (chống spam).
        Map<Integer, Integer> createdByTeacher = new HashMap<>();
        for (Schedule s : schedules) {
            if (attendanceRepo.existsByScheduleId(s.getId())) {
                continue;
            }
            Attendance a = new Attendance();
            a.setTeacherId(s.getTeacherId());
            a.setScheduleId(s.getId());
            a.setWorkDate(s.getStartTime().toLocalDate());
            a.setCheckIn(s.getStartTime().toLocalTime());
            a.setCheckOut(s.getEndTime().toLocalTime());
            a.setStatus("PRESENT");
            a.setCheckInMethod("EMPLOYEE");
            a.setCreatedBy(userId);
            attendanceRepo.save(a);
            created++;
            createdByTeacher.merge(s.getTeacherId(), 1, Integer::sum);
        }
        createdByTeacher.forEach((teacherId, count) -> notificationService.publishToTeacher(
                teacherId,
                "Đã chấm công theo lịch dạy",
                "Hệ thống đã ghi nhận chấm công cho " + count + " buổi dạy của bạn (mặc định Có mặt). "
                        + "Vào Bảng chấm công để kiểm tra chi tiết.",
                "ATTENDANCE",
                "Attendance",
                null,
                false));
        return created;
    }

    @Transactional
    public AttendanceResponse create(AttendanceRequest req) {
        Teacher teacher = validate(req);
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
        apply(a, req);
        attendanceRepo.save(a);
        // Chỉ báo khi TRẠNG THÁI đổi (tránh spam khi chỉ sửa ghi chú/giờ vặt).
        if (!java.util.Objects.equals(oldStatus, a.getStatus())) {
            notifyTeacherOfAttendance(a, teacher, "Chấm công của bạn được cập nhật");
        }
        return AttendanceResponse.fromEntity(a, fullName(teacher));
    }

    /** Phát thông báo (không hành động) cho GV về một dòng chấm công. */
    private void notifyTeacherOfAttendance(Attendance a, Teacher teacher, String title) {
        String content = "Ngày " + a.getWorkDate() + ": " + statusLabel(a.getStatus())
                + (a.getNote() != null && !a.getNote().isBlank() ? " — " + a.getNote() : "");
        notificationService.publish(
                teacher.getAppUserId(), title, content, "ATTENDANCE", "Attendance", a.getId(), false);
    }

    private static String statusLabel(String status) {
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
     * thầm, lương sai), scheduleId nếu gửi phải có thật (không thì nổ FK 500).
     */
    private Teacher validate(AttendanceRequest req) {
        Teacher teacher = teacherRepo
                .findByIdAndDeletedFalse(req.teacherId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy giáo viên"));
        if (req.checkIn() != null && req.checkOut() != null && !req.checkOut().isAfter(req.checkIn())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Giờ ra phải sau giờ vào");
        }
        if (req.scheduleId() != null && !scheduleRepo.existsById(req.scheduleId())) {
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
