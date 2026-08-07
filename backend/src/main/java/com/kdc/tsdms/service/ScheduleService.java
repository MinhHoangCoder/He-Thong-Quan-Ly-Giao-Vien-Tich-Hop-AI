package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.MyScheduleFilters;
import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.ScheduleEventResponse;
import com.kdc.tsdms.dto.ScheduleFilterOptions;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lịch dạy (read-only) — trả các buổi ĐÃ DUYỆT trong khoảng ngày, ghép sẵn tên
 * GV/trường/lớp/môn/tiết để frontend vẽ lịch tháng & thời khóa biểu tuần.
 *
 * <p>Phân quyền dữ liệu: staff xem tất (lọc tùy ý); giáo viên thường chỉ thấy lịch của
 * chính mình (ép teacherId về hồ sơ người gọi — chống IDOR, cùng cơ chế với Chấm công).
 */
@Service
public class ScheduleService {

    private static final String APPROVED = "APPROVED";

    private final ScheduleRepository scheduleRepo;
    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final PeriodRepository periodRepo;

    public ScheduleService(
            ScheduleRepository scheduleRepo,
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            PeriodRepository periodRepo) {
        this.scheduleRepo = scheduleRepo;
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.periodRepo = periodRepo;
    }

    /**
     * Lớp THẬT của một buổi dạy: lấy theo ô lịch gốc đã sinh ra buổi ({@code SourceSlotId})
     * — từ V16 mỗi tiết mang lớp riêng, nên KHÔNG được đọc {@code Assignment.ClassId} nữa
     * (đọc ở đó thì cả 3 tiết sáng đều hiện cùng một lớp). Fallback về lớp cấp phân công
     * chỉ dành cho dữ liệu cũ chưa có slot/lớp.
     */
    private Integer classIdOf(Schedule s, Assignment a, Map<Integer, AssignmentSlot> slotCache) {
        if (s.getSourceSlotId() != null) {
            AssignmentSlot slot = slotCache.computeIfAbsent(
                    s.getSourceSlotId(), id -> slotRepo.findById(id).orElse(null));
            if (slot != null && slot.getClassId() != null) {
                return slot.getClassId();
            }
        }
        return a.getClassId();
    }

    @Transactional(readOnly = true)
    public List<ScheduleEventResponse> list(
            LocalDate from, LocalDate to, Integer teacherId, Integer schoolId, Integer classId) {
        Integer effectiveTeacher = scopedTeacherId(teacherId);

        List<Schedule> schedules = effectiveTeacher != null
                ? scheduleRepo.findByTeacherIdAndStatusAndStartTimeBetweenAndDeletedFalseOrderByStartTime(
                        effectiveTeacher, APPROVED, from.atStartOfDay(), to.atTime(LocalTime.MAX))
                : scheduleRepo.findByStartTimeBetweenAndStatusAndDeletedFalseOrderByStartTime(
                        from.atStartOfDay(), to.atTime(LocalTime.MAX), APPROVED);
        return buildEvents(schedules, schoolId, classId);
    }

    /**
     * Lịch dạy của CHÍNH giáo viên đang đăng nhập (chỉ buổi ĐÃ DUYỆT). KHÔNG nhận teacherId từ
     * ngoài — giáo viên tuyệt đối không thể xem lịch người khác qua endpoint này (chống IDOR).
     */
    @Transactional(readOnly = true)
    public List<ScheduleEventResponse> listMine(LocalDate from, LocalDate to, Integer schoolId, Integer classId) {
        Integer teacherId = currentTeacherId();
        List<Schedule> schedules =
                scheduleRepo.findByTeacherIdAndStatusAndStartTimeBetweenAndDeletedFalseOrderByStartTime(
                        teacherId, APPROVED, from.atStartOfDay(), to.atTime(LocalTime.MAX));
        return buildEvents(schedules, schoolId, classId);
    }

    /** Ghép các buổi dạy thành sự kiện lịch (tên GV/trường/lớp/môn/tiết), lọc theo trường/lớp. */
    private List<ScheduleEventResponse> buildEvents(List<Schedule> schedules, Integer schoolId, Integer classId) {
        // Cache tra cứu (tránh N+1 khi nhiều buổi trùng GV/trường/lớp/môn/tiết).
        Map<Integer, Assignment> aCache = new HashMap<>();
        Map<Integer, String> teacherName = new HashMap<>();
        Map<Integer, School> schoolCache = new HashMap<>();
        Map<Integer, SchoolClass> classCache = new HashMap<>();
        Map<Integer, Subject> subjectCache = new HashMap<>();
        Map<Integer, Period> periodCache = new HashMap<>();
        Map<Integer, AssignmentSlot> slotCache = new HashMap<>();
        PeriodSessionIndex sessionIndex = new PeriodSessionIndex(periodRepo);

        List<ScheduleEventResponse> out = new ArrayList<>();
        for (Schedule s : schedules) {
            Assignment a = aCache.computeIfAbsent(
                    s.getAssignmentId(), id -> assignmentRepo.findById(id).orElse(null));
            if (a == null) {
                continue;
            }
            if (schoolId != null && !schoolId.equals(a.getSchoolId())) {
                continue;
            }
            Integer eventClassId = classIdOf(s, a, slotCache);
            if (classId != null && !classId.equals(eventClassId)) {
                continue;
            }

            ScheduleEventResponse e = new ScheduleEventResponse();
            e.id = s.getId();
            e.date = s.getStartTime().toLocalDate();
            e.startTime = s.getStartTime().toLocalTime();
            e.endTime = s.getEndTime().toLocalTime();
            e.dayOfWeek = dayCode(s.getStartTime().getDayOfWeek());
            e.teacherId = s.getTeacherId();
            e.teacherName = teacherName.computeIfAbsent(s.getTeacherId(), id -> teacherRepo
                    .findById(id)
                    .map(ScheduleService::fullName)
                    .orElse("(GV #" + id + ")"));
            e.schoolId = a.getSchoolId();
            School school = schoolCache.computeIfAbsent(
                    a.getSchoolId(), id -> schoolRepo.findById(id).orElse(null));
            e.schoolName = school != null ? school.getName() : "(Trường #" + a.getSchoolId() + ")";
            e.classId = eventClassId;
            if (eventClassId != null) {
                SchoolClass c = classCache.computeIfAbsent(
                        eventClassId, id -> classRepo.findById(id).orElse(null));
                e.className = c != null ? c.getName() : null;
            }
            e.subjectId = a.getSubjectId();
            Subject subj = subjectCache.computeIfAbsent(
                    a.getSubjectId(), id -> subjectRepo.findById(id).orElse(null));
            e.subjectName = subj != null ? subj.getName() : "(Môn #" + a.getSubjectId() + ")";
            if (s.getPeriodId() != null) {
                Period p = periodCache.computeIfAbsent(
                        s.getPeriodId(), id -> periodRepo.findById(id).orElse(null));
                if (p != null) {
                    e.periodId = p.getId();
                    e.periodNumber = p.getPeriodNumber();
                    e.indexInSession = sessionIndex.of(p);
                    e.sessionType = p.getSessionType();
                }
            }
            e.status = s.getStatus();
            out.add(e);
        }
        return out;
    }

    /** Options lọc: GV + trường (chỉ staff cần; giáo viên xem lịch của mình). */
    @Transactional(readOnly = true)
    public ScheduleFilterOptions filterOptions() {
        List<OptionItem> teachers = teacherRepo.findByDeletedFalse().stream()
                .map(t -> new OptionItem(t.getId(), fullName(t)))
                .toList();
        List<OptionItem> schools = schoolRepo.findAll().stream()
                .filter(s -> !s.isDeleted())
                .map(s -> new OptionItem(s.getId(), s.getName()))
                .toList();
        return new ScheduleFilterOptions(teachers, schools);
    }

    /** Lớp của một trường (dropdown lọc cấp 2). */
    @Transactional(readOnly = true)
    public List<OptionItem> classesOf(Integer schoolId) {
        return classRepo.findAll().stream()
                .filter(c -> !c.isDeleted() && c.getSchoolId().equals(schoolId))
                .map(c -> new OptionItem(c.getId(), c.getName()))
                .toList();
    }

    /**
     * Options lọc cho trang "Lịch dạy của tôi": chỉ TRƯỜNG + LỚP mà chính giáo viên đang đăng
     * nhập có buổi dạy ĐÃ DUYỆT. Lấy trên MỌI thời điểm (không theo khoảng đang xem) để danh
     * sách lọc ổn định khi GV lật tuần/tháng.
     */
    @Transactional(readOnly = true)
    public MyScheduleFilters myFilterOptions() {
        Integer teacherId = currentTeacherId();
        List<Schedule> schedules = scheduleRepo.findByTeacherIdAndStatusAndDeletedFalse(teacherId, APPROVED);

        Map<Integer, Assignment> aCache = new HashMap<>();
        Map<Integer, AssignmentSlot> slotCache = new HashMap<>();
        Map<Integer, String> schools = new LinkedHashMap<>(); // schoolId -> tên trường
        Map<Integer, MyScheduleFilters.ClassOption> classes = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            Assignment a = aCache.computeIfAbsent(
                    s.getAssignmentId(), id -> assignmentRepo.findById(id).orElse(null));
            if (a == null) {
                continue;
            }
            schools.computeIfAbsent(
                    a.getSchoolId(),
                    id -> schoolRepo.findById(id).map(School::getName).orElse("(Trường #" + id + ")"));
            Integer eventClassId = classIdOf(s, a, slotCache);
            if (eventClassId != null) {
                classes.computeIfAbsent(eventClassId, id -> {
                    String name =
                            classRepo.findById(id).map(SchoolClass::getName).orElse("(Lớp #" + id + ")");
                    return new MyScheduleFilters.ClassOption(id, name, a.getSchoolId());
                });
            }
        }

        List<OptionItem> schoolOpts = schools.entrySet().stream()
                .map(e -> new OptionItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(OptionItem::name))
                .toList();
        List<MyScheduleFilters.ClassOption> classOpts = classes.values().stream()
                .sorted(Comparator.comparing(MyScheduleFilters.ClassOption::name))
                .toList();
        return new MyScheduleFilters(schoolOpts, classOpts);
    }

    /* ── helpers ── */

    /** Hồ sơ giáo viên của người đang đăng nhập (báo lỗi nếu tài khoản không phải giáo viên). */
    private Integer currentTeacherId() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .map(Teacher::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không có hồ sơ giáo viên"));
    }

    /** Staff → teacherId tùy chọn (null = tất cả); GV thường → ép về chính mình. */
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

    private static String dayCode(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

    private static String fullName(Teacher t) {
        return (t.getLastName() + " " + t.getFirstName()).trim();
    }
}
