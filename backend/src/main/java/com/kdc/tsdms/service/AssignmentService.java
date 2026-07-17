package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.AssignmentCreateRequest;
import com.kdc.tsdms.dto.AssignmentFormOptions;
import com.kdc.tsdms.dto.AssignmentResponse;
import com.kdc.tsdms.dto.AssignmentSlotRequest;
import com.kdc.tsdms.dto.AssignmentSlotResponse;
import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.PeriodOption;
import com.kdc.tsdms.dto.SchoolScopedOptions;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Phân công giảng dạy (Assignment).
 *
 * <p>Một phân công = GV ↔ trường ↔ môn ↔ lớp trong một giai đoạn, kèm các slot (Thứ+Tiết)
 * lặp hằng tuần. Khi tạo, Service đồng thời TRẢI các slot thành {@link Schedule} (buổi dạy
 * cụ thể từng tuần) — đây là mức "từng buổi" của phân công, làm nguồn cho Chấm công.
 */
@Service
public class AssignmentService {

    /** Số tuần sinh lịch mặc định khi phân công không có ngày kết thúc. */
    private static final int DEFAULT_WEEKS = 8;

    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final ScheduleRepository scheduleRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final PeriodRepository periodRepo;

    public AssignmentService(
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            ScheduleRepository scheduleRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            PeriodRepository periodRepo) {
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.scheduleRepo = scheduleRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.periodRepo = periodRepo;
    }

    /* ─────────────────────────── QUERY ─────────────────────────── */

    @Transactional(readOnly = true)
    public List<AssignmentResponse> list(Integer teacherId) {
        // CHỐNG IDOR: GV (có ASSIGNMENT_VIEW nhưng không phải staff) chỉ được xem
        // phân công của CHÍNH MÌNH — ép teacherId về hồ sơ của người gọi, bỏ qua
        // teacherId client gửi lên.
        Integer scoped = scopedTeacherId(teacherId);
        List<Assignment> items = scoped != null
                ? assignmentRepo.findByTeacherIdAndDeletedFalseOrderByIdDesc(scoped)
                : assignmentRepo.findByDeletedFalseOrderByIdDesc();
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getById(Integer id) {
        Assignment a = getOrThrow(id);
        Integer scoped = scopedTeacherId(null);
        if (scoped != null && !scoped.equals(a.getTeacherId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem phân công này");
        }
        return toResponse(a);
    }

    /**
     * Xác định phạm vi dữ liệu theo người gọi: staff (ADMIN/EMPLOYEE/phòng ban) xem
     * tất → trả về teacherId lọc tự chọn; GV thường → luôn trả teacherId của CHÍNH
     * MÌNH; còn lại (không hồ sơ GV, không staff) → chặn.
     */
    private Integer scopedTeacherId(Integer requested) {
        if (isStaff()) {
            return requested; // staff lọc tùy ý (null = tất cả)
        }
        Teacher own = teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không có hồ sơ giáo viên"));
        return own.getId();
    }

    private static boolean isStaff() {
        return SecurityUtils.hasRole("ADMIN")
                || SecurityUtils.hasRole("EMPLOYEE")
                || SecurityUtils.hasRole("ACCOUNTANT")
                || SecurityUtils.hasRole("HR")
                || SecurityUtils.hasRole("ACADEMIC")
                || SecurityUtils.hasRole("SALES");
    }

    /** Danh sách GV / môn / trường cho form tạo phân công. */
    @Transactional(readOnly = true)
    public AssignmentFormOptions formOptions() {
        List<OptionItem> teachers = teacherRepo.findByDeletedFalse().stream()
                .map(t -> new OptionItem(t.getId(), fullName(t)))
                .toList();
        List<OptionItem> subjects = subjectRepo.findByDeletedFalseOrderByName().stream()
                .map(s -> new OptionItem(s.getId(), s.getName()))
                .toList();
        List<OptionItem> schools = schoolRepo.findAll().stream()
                .filter(s -> !s.isDeleted())
                .map(s -> new OptionItem(s.getId(), s.getName()))
                .toList();
        return new AssignmentFormOptions(teachers, subjects, schools);
    }

    /** Lớp + khung tiết của một trường (dropdown cấp 2). */
    @Transactional(readOnly = true)
    public SchoolScopedOptions schoolOptions(Integer schoolId) {
        List<OptionItem> classes = classRepo.findAll().stream()
                .filter(c -> !c.isDeleted() && c.getSchoolId().equals(schoolId))
                .map(c -> new OptionItem(c.getId(), c.getName()))
                .toList();
        List<PeriodOption> periods = periodRepo.findBySchoolIdAndDeletedFalseOrderByPeriodNumber(schoolId).stream()
                .map(p -> new PeriodOption(
                        p.getId(),
                        p.getPeriodNumber(),
                        p.getSessionType(),
                        p.getStartTime(),
                        p.getEndTime(),
                        "Tiết " + p.getPeriodNumber() + " (" + p.getStartTime() + "–" + p.getEndTime() + ")"))
                .toList();
        return new SchoolScopedOptions(classes, periods);
    }

    /* ─────────────────────────── CREATE ─────────────────────────── */

    @Transactional
    public AssignmentResponse create(AssignmentCreateRequest req) {
        Teacher teacher = teacherRepo
                .findByIdAndDeletedFalse(req.teacherId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy giáo viên"));
        School school = schoolRepo
                .findById(req.schoolId())
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy trường"));
        subjectRepo
                .findByIdAndDeletedFalse(req.subjectId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy môn học"));
        SchoolClass clazz = classRepo
                .findById(req.classId())
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy lớp học"));
        if (!clazz.getSchoolId().equals(school.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lớp không thuộc trường đã chọn");
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        // Chặn slot TRÙNG NHAU ngay trong request (cùng Thứ + Tiết) — nếu lọt sẽ sinh
        // đúp Schedule cho cùng một khung giờ.
        Set<String> seenSlots = new HashSet<>();
        for (AssignmentSlotRequest slot : req.slots()) {
            if (!seenSlots.add(slot.dayOfWeek() + "#" + slot.periodId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Các tiết dạy bị trùng nhau (" + slot.dayOfWeek() + ")");
            }
        }

        // Nạp & validate các tiết (phải thuộc đúng trường) + DÒ TRÙNG LỊCH GV:
        // cùng GV đã có slot Thứ+Tiết này ở một phân công ACTIVE khác mà giai đoạn
        // hai phân công CHỒNG NHAU -> GV phải dạy 2 nơi cùng một khung giờ.
        Map<Integer, Period> periodById = new HashMap<>();
        for (AssignmentSlotRequest slot : req.slots()) {
            Period p = periodRepo
                    .findById(slot.periodId())
                    .filter(x -> !x.isDeleted() && x.getSchoolId().equals(school.getId()))
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST, "Tiết id=" + slot.periodId() + " không thuộc trường đã chọn"));
            periodById.put(p.getId(), p);

            for (AssignmentSlot existing : slotRepo.findByTeacherIdAndDayOfWeekAndPeriodIdAndDeletedFalse(
                    teacher.getId(), slot.dayOfWeek(), slot.periodId())) {
                Assignment other = assignmentRepo
                        .findByIdAndDeletedFalse(existing.getAssignmentId())
                        .orElse(null);
                if (other == null || !"ACTIVE".equals(other.getStatus())) {
                    continue;
                }
                boolean overlaps =
                        !(other.getEndDate() != null && other.getEndDate().isBefore(req.startDate())
                                || req.endDate() != null && req.endDate().isBefore(other.getStartDate()));
                if (overlaps) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "Giáo viên đã có lịch dạy " + AssignmentSlotResponse.fromEntity(existing, p).dayOfWeekLabel
                                    + " tiết " + p.getPeriodNumber() + " ở phân công #" + other.getId()
                                    + " trong cùng giai đoạn");
                }
            }
        }

        Integer userId = SecurityUtils.currentUserId();

        Assignment a = new Assignment();
        a.setTeacherId(teacher.getId());
        a.setSchoolId(school.getId());
        a.setSubjectId(req.subjectId());
        a.setClassId(clazz.getId());
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setStatus("ACTIVE");
        a.setCreatedBy(userId);
        assignmentRepo.save(a);

        LocalDate end = req.endDate() != null ? req.endDate() : req.startDate().plusWeeks(DEFAULT_WEEKS);
        for (AssignmentSlotRequest slotReq : req.slots()) {
            AssignmentSlot slot = new AssignmentSlot();
            slot.setAssignmentId(a.getId());
            slot.setTeacherId(teacher.getId());
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setPeriodId(slotReq.periodId());
            slot.setCreatedBy(userId);
            slotRepo.save(slot);

            generateSchedules(a, slot, periodById.get(slotReq.periodId()), req.startDate(), end, userId);
        }

        return toResponse(a);
    }

    /** Trải 1 slot (Thứ+Tiết) thành các buổi Schedule hằng tuần trong [from, to]. */
    private void generateSchedules(
            Assignment a, AssignmentSlot slot, Period period, LocalDate from, LocalDate to, Integer userId) {
        DayOfWeek target = toDayOfWeek(slot.getDayOfWeek());
        LocalDate d = from;
        while (d.getDayOfWeek() != target) {
            d = d.plusDays(1);
        }
        Instant now = Instant.now();
        for (; !d.isAfter(to); d = d.plusWeeks(1)) {
            Schedule s = new Schedule();
            s.setAssignmentId(a.getId());
            s.setTeacherId(a.getTeacherId());
            s.setStartTime(d.atTime(period.getStartTime()));
            s.setEndTime(d.atTime(period.getEndTime()));
            s.setStatus("APPROVED"); // sinh trực tiếp trạng thái duyệt để phục vụ chấm công demo
            s.setSource("MANUAL");
            s.setPeriodId(period.getId());
            s.setSourceSlotId(slot.getId());
            s.setCreatedByUserId(userId);
            s.setApprovedByUserId(userId);
            s.setApprovedAt(now);
            scheduleRepo.save(s);
        }
    }

    /* ─────────────────────────── CANCEL ─────────────────────────── */

    @Transactional
    public AssignmentResponse cancel(Integer id) {
        Assignment a = getOrThrow(id);
        Integer userId = SecurityUtils.currentUserId();
        a.setStatus("CANCELLED");
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);
        // CHỈ hủy các buổi TƯƠNG LAI chưa diễn ra — buổi quá khứ đã dạy phải giữ
        // nguyên để không mất dữ liệu chấm công / bảng lương đã tính.
        LocalDateTime now = LocalDateTime.now();
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
            if (!"CANCELLED".equals(s.getStatus()) && s.getStartTime().isAfter(now)) {
                s.setUpdatedBy(userId);
                s.setStatus("CANCELLED");
                s.setUpdatedAt(Instant.now());
                scheduleRepo.save(s);
            }
        }
        return toResponse(a);
    }

    /**
     * BỎ HỦY: đưa phân công ĐÃ HỦY về lại ACTIVE (khôi phục khi lỡ bấm Hủy). Đảo ngược
     * {@link #cancel(Integer)} — các buổi đã bị hủy theo phân công được đưa lại APPROVED.
     * Trước khi bật lại phải DÒ TRÙNG LỊCH: giai đoạn phân công có thể đã bị một phân công
     * ACTIVE khác chiếm mất khung Thứ+Tiết trong lúc nó đang bị hủy.
     */
    @Transactional
    public AssignmentResponse reactivate(Integer id) {
        Assignment a = getOrThrow(id);
        if (!"CANCELLED".equals(a.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ khôi phục được phân công đã hủy");
        }
        for (AssignmentSlot slot : slotRepo.findByAssignmentIdAndDeletedFalse(id)) {
            for (AssignmentSlot existing : slotRepo.findByTeacherIdAndDayOfWeekAndPeriodIdAndDeletedFalse(
                    a.getTeacherId(), slot.getDayOfWeek(), slot.getPeriodId())) {
                if (existing.getAssignmentId().equals(id)) {
                    continue; // bỏ qua slot của chính phân công đang khôi phục
                }
                Assignment other = assignmentRepo
                        .findByIdAndDeletedFalse(existing.getAssignmentId())
                        .orElse(null);
                if (other == null || !"ACTIVE".equals(other.getStatus())) {
                    continue;
                }
                boolean overlaps = !(a.getEndDate() != null && a.getEndDate().isBefore(other.getStartDate())
                        || other.getEndDate() != null && other.getEndDate().isBefore(a.getStartDate()));
                if (overlaps) {
                    Period p = periodRepo.findById(slot.getPeriodId()).orElse(null);
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "Không khôi phục được: giáo viên đã có lịch dạy "
                                    + AssignmentSlotResponse.fromEntity(existing, p).dayOfWeekLabel
                                    + (p != null ? " tiết " + p.getPeriodNumber() : "")
                                    + " ở phân công #" + other.getId() + " trong cùng giai đoạn");
                }
            }
        }
        Integer userId = SecurityUtils.currentUserId();
        a.setStatus("ACTIVE");
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);
        // Đưa lại các buổi đã bị hủy theo phân công về APPROVED (đảo ngược cancel()).
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
            if ("CANCELLED".equals(s.getStatus())) {
                s.setUpdatedBy(userId);
                s.setStatus("APPROVED");
                s.setUpdatedAt(Instant.now());
                scheduleRepo.save(s);
            }
        }
        return toResponse(a);
    }

    /* ─────────────────────── XÓA MỀM → THÙNG RÁC ─────────────────────── */

    /**
     * HỦY + đưa vào Thùng rác trong MỘT thao tác (nút "Hủy" của trang phân công). Nếu phân
     * công đang chạy thì HỦY trước (đổi CANCELLED + hủy các buổi tương lai qua {@link #cancel}),
     * rồi gắn cờ {@code deleted} và CASCADE xóa mềm toàn bộ AssignmentSlot + Schedule của nó.
     * Khôi phục lại từ Thùng rác (xem {@link #restore}).
     */
    @Transactional
    public void softDelete(Integer id) {
        Assignment a = getOrThrow(id);
        if ("ACTIVE".equals(a.getStatus())) {
            cancel(id); // hủy trước: đổi CANCELLED + hủy các buổi tương lai
        }
        Integer userId = SecurityUtils.currentUserId();
        Instant now = Instant.now();
        a.setDeleted(true);
        a.setDeletedAt(now);
        a.setDeletedBy(userId);
        assignmentRepo.save(a);
        for (AssignmentSlot slot : slotRepo.findByAssignmentIdAndDeletedFalse(id)) {
            slot.setDeleted(true);
            slot.setDeletedAt(now);
            slot.setDeletedBy(userId);
            slotRepo.save(slot);
        }
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
            s.setDeleted(true);
            s.setDeletedAt(now);
            s.setDeletedBy(userId);
            scheduleRepo.save(s);
        }
    }

    /** Danh sách phân công trong thùng rác (đã xóa mềm). */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> listTrash() {
        return assignmentRepo.findByDeletedTrueOrderByIdDesc().stream()
                .map(a -> toResponse(a, true))
                .toList();
    }

    /**
     * Khôi phục phân công từ Thùng rác về lại <b>Đang chạy</b>: bỏ cờ {@code deleted} cho
     * Assignment + slot + buổi, rồi BỎ HỦY (đưa về ACTIVE, kích hoạt lại buổi dạy) qua
     * {@link #reactivate} — có DÒ TRÙNG LỊCH nên nếu khung Thứ+Tiết đã bị phân công ACTIVE
     * khác chiếm thì báo lỗi và không khôi phục.
     */
    @Transactional
    public AssignmentResponse restore(Integer id) {
        Assignment a = assignmentRepo
                .findByIdAndDeletedTrue(id)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công trong thùng rác id=" + id));
        Integer userId = SecurityUtils.currentUserId();
        a.setDeleted(false);
        a.setDeletedAt(null);
        a.setDeletedBy(null);
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);
        for (AssignmentSlot slot : slotRepo.findByAssignmentId(id)) {
            if (slot.isDeleted()) {
                slot.setDeleted(false);
                slot.setDeletedAt(null);
                slot.setDeletedBy(null);
                slotRepo.save(slot);
            }
        }
        for (Schedule s : scheduleRepo.findByAssignmentId(id)) {
            if (s.isDeleted()) {
                s.setDeleted(false);
                s.setDeletedAt(null);
                s.setDeletedBy(null);
                scheduleRepo.save(s);
            }
        }
        // Bỏ hủy → Đang chạy (nếu đang CANCELLED). Dò trùng lịch bên trong reactivate; nếu
        // đụng lịch sẽ ném lỗi và rollback cả thao tác bỏ cờ deleted ở trên.
        if ("CANCELLED".equals(a.getStatus())) {
            return reactivate(id);
        }
        return toResponse(a, false);
    }

    /**
     * Xóa VĨNH VIỄN khỏi DB (chỉ khi phân công đang ở thùng rác). Xóa theo đúng thứ tự khóa
     * ngoại: nhật ký trạng thái &amp; chấm công (→ Schedule) → buổi → slot → phân công.
     */
    @Transactional
    public void purge(Integer id) {
        Assignment a = assignmentRepo
                .findByIdAndDeletedTrue(id)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công trong thùng rác id=" + id));
        scheduleRepo.deleteStatusLogsByAssignmentId(id);
        scheduleRepo.deleteAttendanceByAssignmentId(id);
        scheduleRepo.deleteByAssignmentId(id);
        slotRepo.deleteByAssignmentId(id);
        assignmentRepo.delete(a);
    }

    /* ─────────────────────────── HELPERS ─────────────────────────── */

    private Assignment getOrThrow(Integer id) {
        return assignmentRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công id=" + id));
    }

    private AssignmentResponse toResponse(Assignment a) {
        return toResponse(a, false);
    }

    /**
     * @param includeDeletedSlots true = lấy cả slot đã xóa mềm (dùng cho thùng rác, nơi
     *     Assignment và slot đều đã bị gắn cờ deleted nhưng vẫn cần hiển thị các tiết/tuần).
     */
    private AssignmentResponse toResponse(Assignment a, boolean includeDeletedSlots) {
        String teacherName = teacherRepo
                .findById(a.getTeacherId())
                .map(AssignmentService::fullName)
                .orElse("(GV #" + a.getTeacherId() + ")");
        String schoolName =
                schoolRepo.findById(a.getSchoolId()).map(School::getName).orElse("(Trường #" + a.getSchoolId() + ")");
        String subjectName =
                subjectRepo.findById(a.getSubjectId()).map(Subject::getName).orElse("(Môn #" + a.getSubjectId() + ")");
        String className = a.getClassId() == null
                ? null
                : classRepo.findById(a.getClassId()).map(SchoolClass::getName).orElse(null);

        List<AssignmentSlot> slotEntities = includeDeletedSlots
                ? slotRepo.findByAssignmentId(a.getId())
                : slotRepo.findByAssignmentIdAndDeletedFalse(a.getId());
        List<AssignmentSlotResponse> slots = new ArrayList<>();
        for (AssignmentSlot slot : slotEntities) {
            Period p = periodRepo.findById(slot.getPeriodId()).orElse(null);
            slots.add(AssignmentSlotResponse.fromEntity(slot, p));
        }
        return AssignmentResponse.fromEntity(a, teacherName, schoolName, subjectName, className, slots);
    }

    private static String fullName(Teacher t) {
        return (t.getLastName() + " " + t.getFirstName()).trim();
    }

    private static DayOfWeek toDayOfWeek(String code) {
        return switch (code) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Thứ không hợp lệ: " + code);
        };
    }
}
