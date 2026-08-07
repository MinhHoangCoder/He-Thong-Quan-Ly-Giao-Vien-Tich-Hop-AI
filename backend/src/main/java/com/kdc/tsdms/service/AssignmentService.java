package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.AssignmentBulkResult;
import com.kdc.tsdms.dto.AssignmentConflict;
import com.kdc.tsdms.dto.AssignmentCreateRequest;
import com.kdc.tsdms.dto.AssignmentFormOptions;
import com.kdc.tsdms.dto.AssignmentResponse;
import com.kdc.tsdms.dto.AssignmentSlotRequest;
import com.kdc.tsdms.dto.AssignmentSlotResponse;
import com.kdc.tsdms.dto.AssignmentUpdateRequest;
import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.PeriodOption;
import com.kdc.tsdms.dto.SchoolScopedOptions;
import com.kdc.tsdms.dto.TeacherBusySlot;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.AssignmentStatus;
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
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationContext;
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

    /**
     * Cửa sổ thời gian giáo viên phải trả lời lời mời dạy. 48 giờ theo thông lệ mời nhận ca
     * (luật predictive scheduling của Mỹ quy định 24–48 giờ). Đổi số ở đây là đổi toàn hệ thống.
     */
    static final int CONFIRM_WINDOW_HOURS = 48;

    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final ScheduleRepository scheduleRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final PeriodRepository periodRepo;
    private final AssignmentApprovalService approvalService;
    private final TeacherTimeConflictChecker conflictChecker;
    private final ApplicationContext applicationContext;

    public AssignmentService(
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            ScheduleRepository scheduleRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            PeriodRepository periodRepo,
            AssignmentApprovalService approvalService,
            TeacherTimeConflictChecker conflictChecker,
            ApplicationContext applicationContext) {
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.scheduleRepo = scheduleRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.periodRepo = periodRepo;
        this.approvalService = approvalService;
        this.conflictChecker = conflictChecker;
        this.applicationContext = applicationContext;
    }

    /* ─────────────────────────── QUERY ─────────────────────────── */

    @Transactional(readOnly = true)
    public List<AssignmentResponse> list(Integer teacherId) {
        return list(teacherId, null, null);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> list(Integer teacherId, String keyword) {
        return list(teacherId, keyword, null);
    }

    /**
     * Danh sách phân công còn hoạt động. {@code keyword} tùy chọn: lọc KHÔNG phân biệt
     * hoa/thường và DẤU tiếng Việt trên tên GV / trường / lớp / môn. So khớp trên các tên
     * đã build sẵn trong response (không phụ thuộc collation của DB).
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> list(Integer teacherId, String keyword, String status) {
        // CHỐNG IDOR: GV (có ASSIGNMENT_VIEW nhưng không phải staff) chỉ được xem
        // phân công của CHÍNH MÌNH — ép teacherId về hồ sơ của người gọi, bỏ qua
        // teacherId client gửi lên.
        Integer scoped = scopedTeacherId(teacherId);
        List<Assignment> items = scoped != null
                ? assignmentRepo.findByTeacherIdAndDeletedFalseOrderByIdDesc(scoped)
                : assignmentRepo.findByDeletedFalseOrderByIdDesc();
        // Lọc theo trạng thái HIỂN THỊ (phiếu chờ quá hạn tính là EXPIRED) để tab "Hết hạn"
        // khớp đúng con số trên badge.
        if (status != null && !status.isBlank()) {
            items = items.stream()
                    .filter(a -> status.equals(effectiveStatus(a)))
                    .toList();
        }
        List<AssignmentResponse> responses =
                items.stream().map(this::toResponse).toList();

        String kw = normalizeSearch(keyword);
        if (kw.isEmpty()) {
            return responses;
        }
        return responses.stream().filter(r -> matchesKeyword(r, kw)).toList();
    }

    /** Chuẩn hóa chuỗi để so khớp tìm kiếm: bỏ dấu tiếng Việt + thường hóa + trim. */
    private static String normalizeSearch(String s) {
        if (s == null) {
            return "";
        }
        String noMark = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noMark.replace('đ', 'd').replace('Đ', 'D').toLowerCase().trim();
    }

    /** Một dòng phân công có khớp từ khóa (tên GV / trường / lớp / môn) không. */
    private static boolean matchesKeyword(AssignmentResponse r, String normKeyword) {
        return normalizeSearch(r.teacherName).contains(normKeyword)
                || normalizeSearch(r.schoolName).contains(normKeyword)
                || normalizeSearch(r.className).contains(normKeyword)
                || normalizeSearch(r.subjectName).contains(normKeyword);
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
        PeriodSessionIndex sessionIndex = new PeriodSessionIndex(periodRepo);
        List<PeriodOption> periods = periodRepo.findBySchoolIdAndDeletedFalseOrderByPeriodNumber(schoolId).stream()
                .map(p -> new PeriodOption(
                        p.getId(),
                        p.getPeriodNumber(),
                        sessionIndex.of(p),
                        p.getSessionType(),
                        p.getStartTime(),
                        p.getEndTime(),
                        "Tiết " + p.getPeriodNumber() + " (" + p.getStartTime() + "–" + p.getEndTime() + ")"))
                .toList();
        return new SchoolScopedOptions(classes, periods);
    }

    /**
     * Bảng "giờ bận" của một giáo viên: mọi ô lịch thuộc phân công ACTIVE, kèm KHUNG GIỜ
     * THẬT của tiết. Form phân công dùng nó để khóa sẵn những tiết không chọn được — quan
     * trọng nhất là các tiết ở TRƯỜNG KHÁC đè giờ, thứ mà form không thể tự suy ra từ
     * periodId (mỗi trường một bộ Period riêng).
     *
     * @param startDate/endDate giai đoạn đang định xếp — chỉ trả ô lịch của phân công có
     *     giai đoạn chồng lên khoảng này (null = không lọc theo ngày)
     */
    @Transactional(readOnly = true)
    public List<TeacherBusySlot> teacherBusy(Integer teacherId, LocalDate startDate, LocalDate endDate) {
        Map<Integer, Assignment> aCache = new HashMap<>();
        Map<Integer, Period> periodCache = new HashMap<>();
        Map<Integer, String> schoolNames = new HashMap<>();
        Map<Integer, String> classNames = new HashMap<>();
        Map<Integer, String> subjectNames = new HashMap<>();
        PeriodSessionIndex sessionIndex = new PeriodSessionIndex(periodRepo);

        List<TeacherBusySlot> out = new ArrayList<>();
        for (AssignmentSlot slot : slotRepo.findByTeacherIdAndDeletedFalse(teacherId)) {
            Assignment a = aCache.computeIfAbsent(
                    slot.getAssignmentId(),
                    id -> assignmentRepo.findByIdAndDeletedFalse(id).orElse(null));
            if (a == null || !a.holdsTimeSlot()) {
                continue;
            }
            if (startDate != null && !datesOverlap(startDate, endDate, a.getStartDate(), a.getEndDate())) {
                continue;
            }
            Period p = periodCache.computeIfAbsent(
                    slot.getPeriodId(), id -> periodRepo.findById(id).orElse(null));
            if (p == null) {
                continue;
            }
            Integer classId = slot.getClassId() != null ? slot.getClassId() : a.getClassId();

            TeacherBusySlot b = new TeacherBusySlot();
            b.assignmentId = a.getId();
            b.dayOfWeek = slot.getDayOfWeek();
            b.dayOfWeekLabel = dayLabelVi(slot.getDayOfWeek());
            b.periodId = p.getId();
            b.periodNumber = p.getPeriodNumber();
            b.indexInSession = sessionIndex.of(p);
            b.sessionType = p.getSessionType();
            b.startTime = p.getStartTime();
            b.endTime = p.getEndTime();
            b.schoolId = a.getSchoolId();
            b.schoolName = schoolNames.computeIfAbsent(
                    a.getSchoolId(),
                    id -> schoolRepo.findById(id).map(School::getName).orElse("(Trường #" + id + ")"));
            b.className = classId == null
                    ? null
                    : classNames.computeIfAbsent(classId, id -> classRepo
                            .findById(id)
                            .map(SchoolClass::getName)
                            .orElse(null));
            b.subjectName = subjectNames.computeIfAbsent(
                    a.getSubjectId(),
                    id -> subjectRepo.findById(id).map(Subject::getName).orElse(null));
            b.startDate = a.getStartDate();
            b.endDate = a.getEndDate();
            out.add(b);
        }
        return out;
    }

    /**
     * Quét TOÀN BỘ phân công đang chạy, tìm các cặp ô lịch của cùng một GV bị đè giờ lên
     * nhau. Cần thiết vì luật chống trùng cũ so theo {@code periodId} — hai trường khác
     * nhau có periodId khác nhau nên GV vẫn được xếp dạy hai nơi cùng khung giờ; những cặp
     * đã lọt lưới chỉ lộ ra khi so giờ thật như ở đây.
     */
    @Transactional(readOnly = true)
    public List<AssignmentConflict> scanConflicts() {
        Map<Integer, Assignment> aCache = new HashMap<>();
        Map<Integer, Period> periodCache = new HashMap<>();
        Map<Integer, String> schoolNames = new HashMap<>();
        Map<Integer, String> classNames = new HashMap<>();
        Map<Integer, String> subjectNames = new HashMap<>();
        Map<Integer, String> teacherNames = new HashMap<>();

        // Gom theo GV + Thứ: chỉ những ô lịch trong cùng nhóm mới có thể đè giờ nhau.
        Map<String, List<AssignmentSlot>> byTeacherDay = new LinkedHashMap<>();
        for (AssignmentSlot slot : slotRepo.findByDeletedFalse()) {
            Assignment a = aCache.computeIfAbsent(
                    slot.getAssignmentId(),
                    id -> assignmentRepo.findByIdAndDeletedFalse(id).orElse(null));
            if (a == null || !a.holdsTimeSlot()) {
                continue;
            }
            byTeacherDay
                    .computeIfAbsent(slot.getTeacherId() + "#" + slot.getDayOfWeek(), k -> new ArrayList<>())
                    .add(slot);
        }

        List<AssignmentConflict> out = new ArrayList<>();
        for (List<AssignmentSlot> group : byTeacherDay.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    AssignmentSlot s1 = group.get(i);
                    AssignmentSlot s2 = group.get(j);
                    Assignment a1 = aCache.get(s1.getAssignmentId());
                    Assignment a2 = aCache.get(s2.getAssignmentId());
                    if (!datesOverlap(a1.getStartDate(), a1.getEndDate(), a2.getStartDate(), a2.getEndDate())) {
                        continue;
                    }
                    Period p1 = periodCache.computeIfAbsent(
                            s1.getPeriodId(), id -> periodRepo.findById(id).orElse(null));
                    Period p2 = periodCache.computeIfAbsent(
                            s2.getPeriodId(), id -> periodRepo.findById(id).orElse(null));
                    if (!timeOverlaps(p1, p2)) {
                        continue;
                    }

                    AssignmentConflict c = new AssignmentConflict();
                    c.teacherId = s1.getTeacherId();
                    c.teacherName = teacherNames.computeIfAbsent(s1.getTeacherId(), id -> teacherRepo
                            .findById(id)
                            .map(AssignmentService::fullName)
                            .orElse("(GV #" + id + ")"));
                    c.dayOfWeek = s1.getDayOfWeek();
                    c.dayOfWeekLabel = dayLabelVi(s1.getDayOfWeek());
                    c.overlapStart = maxDate(a1.getStartDate(), a2.getStartDate());
                    c.overlapEnd = minEndDate(a1.getEndDate(), a2.getEndDate());
                    c.overlapFrom = maxTime(p1.getStartTime(), p2.getStartTime());
                    c.overlapTo = minTime(p1.getEndTime(), p2.getEndTime());
                    c.first = conflictSide(s1, a1, p1, schoolNames, classNames, subjectNames);
                    c.second = conflictSide(s2, a2, p2, schoolNames, classNames, subjectNames);
                    out.add(c);
                }
            }
        }
        out.sort(Comparator.comparing((AssignmentConflict c) -> c.teacherName).thenComparing(c -> c.dayOfWeek));
        return out;
    }

    private AssignmentConflict.Side conflictSide(
            AssignmentSlot slot,
            Assignment a,
            Period p,
            Map<Integer, String> schoolNames,
            Map<Integer, String> classNames,
            Map<Integer, String> subjectNames) {
        Integer classId = slot.getClassId() != null ? slot.getClassId() : a.getClassId();
        AssignmentConflict.Side s = new AssignmentConflict.Side();
        s.assignmentId = a.getId();
        s.slotId = slot.getId();
        s.schoolId = a.getSchoolId();
        s.schoolName = schoolNames.computeIfAbsent(
                a.getSchoolId(),
                id -> schoolRepo.findById(id).map(School::getName).orElse("(Trường #" + id + ")"));
        s.className = classId == null
                ? null
                : classNames.computeIfAbsent(
                        classId,
                        id -> classRepo.findById(id).map(SchoolClass::getName).orElse(null));
        s.subjectName = subjectNames.computeIfAbsent(
                a.getSubjectId(),
                id -> subjectRepo.findById(id).map(Subject::getName).orElse(null));
        s.periodNumber = p.getPeriodNumber();
        s.sessionType = p.getSessionType();
        s.startTime = p.getStartTime();
        s.endTime = p.getEndTime();
        return s;
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    /** null = vô thời hạn nên "sớm hơn" luôn là phía có ngày. */
    private static LocalDate minEndDate(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    private static LocalTime maxTime(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime minTime(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
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
        Subject subject = subjectRepo
                .findByIdAndDeletedFalse(req.subjectId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy môn học"));
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        Map<Integer, Period> periodById = new HashMap<>();
        List<Integer> slotClassIds = validateSlots(
                req.slots(), school, req.classId(), teacher.getId(), req.startDate(), req.endDate(), null, periodById);

        Integer userId = SecurityUtils.currentUserId();

        Assignment a = new Assignment();
        a.setTeacherId(teacher.getId());
        a.setSchoolId(school.getId());
        a.setSubjectId(req.subjectId());
        // Lớp cấp phân công = lớp mặc định (client cũ) hoặc lớp của tiết đầu tiên — chỉ còn
        // là giá trị đại diện/dự phòng, lớp thật nằm ở từng slot.
        a.setClassId(req.classId() != null ? req.classId() : slotClassIds.get(0));
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        // CHỜ XÁC NHẬN: lịch chưa có hiệu lực cho tới khi giáo viên đồng ý (hoặc admin ép duyệt).
        a.setStatus(AssignmentStatus.PENDING);
        a.setCreatedBy(userId);
        assignmentRepo.save(a);

        LocalDate end = req.endDate() != null ? req.endDate() : req.startDate().plusWeeks(DEFAULT_WEEKS);
        for (int i = 0; i < req.slots().size(); i++) {
            AssignmentSlotRequest slotReq = req.slots().get(i);
            AssignmentSlot slot = new AssignmentSlot();
            slot.setAssignmentId(a.getId());
            slot.setTeacherId(teacher.getId());
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setPeriodId(slotReq.periodId());
            slot.setClassId(slotClassIds.get(i));
            slot.setCreatedBy(userId);
            slotRepo.save(slot);

            generateSchedules(a, slot, periodById.get(slotReq.periodId()), req.startDate(), end, userId);
        }

        // Hạn trả lời phải biết buổi đầu tiên diễn ra lúc nào → tính SAU khi đã sinh buổi.
        a.setConfirmDeadline(computeConfirmDeadline(a.getId()));
        assignmentRepo.save(a);

        // Lời mời dạy (có nút Xác nhận / Từ chối) — cùng một mẫu nội dung với lúc nhắc lại.
        approvalService.publishInvite(a, false);

        return toResponse(a);
    }

    /**
     * Soát toàn bộ danh sách tiết của một phiếu (dùng chung cho tạo mới và sửa): tiết phải
     * thuộc đúng trường, mỗi tiết phải có lớp và lớp phải thuộc đúng trường, các tiết không
     * được trùng/đè giờ nhau, và không được đè lịch sẵn có của giáo viên ở BẤT KỲ trường nào.
     *
     * @param defaultClassId lớp mặc định cấp phiếu, dùng cho slot bỏ trống lớp (client cũ)
     * @param ignoreAssignmentId bỏ qua chính phiếu này khi dò trùng (lúc sửa), null khi tạo mới
     * @param periodByIdOut nhận các Period đã nạp để bên gọi tái sử dụng, khỏi truy vấn lại
     * @return classId đã chốt cho từng slot, cùng thứ tự với {@code slots}
     */
    private List<Integer> validateSlots(
            List<AssignmentSlotRequest> slots,
            School school,
            Integer defaultClassId,
            Integer teacherId,
            LocalDate startDate,
            LocalDate endDate,
            Integer ignoreAssignmentId,
            Map<Integer, Period> periodByIdOut) {
        // Chặn slot TRÙNG NHAU ngay trong request (cùng Thứ + Tiết) — nếu lọt sẽ sinh
        // đúp Schedule cho cùng một khung giờ.
        Set<String> seenSlots = new HashSet<>();
        for (AssignmentSlotRequest slot : slots) {
            if (!seenSlots.add(slot.dayOfWeek() + "#" + slot.periodId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Các tiết dạy bị trùng nhau (" + slot.dayOfWeek() + ")");
            }
        }

        // Nạp & validate TIẾT (phải thuộc đúng trường) và LỚP của TỪNG tiết (V16 — mỗi tiết
        // một lớp; slot bỏ trống lớp thì dùng lớp mặc định ở cấp phân công).
        Map<Integer, SchoolClass> classById = new HashMap<>();
        List<Integer> slotClassIds = new ArrayList<>(); // song song với slots
        for (AssignmentSlotRequest slot : slots) {
            Period p = periodRepo
                    .findById(slot.periodId())
                    .filter(x -> !x.isDeleted() && x.getSchoolId().equals(school.getId()))
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST, "Tiết id=" + slot.periodId() + " không thuộc trường đã chọn"));
            periodByIdOut.put(p.getId(), p);

            Integer classId = slot.classId() != null ? slot.classId() : defaultClassId;
            if (classId == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Vui lòng chọn lớp cho " + dayLabelVi(slot.dayOfWeek()) + " tiết " + p.getPeriodNumber());
            }
            SchoolClass c = classById.computeIfAbsent(classId, id -> classRepo
                    .findById(id)
                    .filter(x -> !x.isDeleted())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy lớp học id=" + id)));
            if (!c.getSchoolId().equals(school.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Lớp " + c.getName() + " không thuộc trường đã chọn");
            }
            slotClassIds.add(classId);
        }

        // Hai tiết trong CÙNG request đè giờ nhau (khác periodId nhưng khung giờ giao nhau).
        checkSelfTimeConflicts(slots, periodByIdOut);

        // DÒ TRÙNG LỊCH GV THEO GIỜ THẬT: quét mọi ô lịch của GV trong cùng Thứ ở MỌI
        // trường, so khoảng giờ của tiết. KHÔNG so periodId — Period thuộc về từng trường
        // nên "tiết 1" của hai trường là hai id khác nhau mà giờ vẫn đè nhau.
        for (AssignmentSlotRequest slot : slots) {
            checkTeacherTimeConflict(
                    teacherId,
                    slot.dayOfWeek(),
                    periodByIdOut.get(slot.periodId()),
                    startDate,
                    endDate,
                    ignoreAssignmentId);
        }
        return slotClassIds;
    }

    /**
     * Hạn giáo viên phải trả lời = SỚM HƠN của (bây giờ + {@value #CONFIRM_WINDOW_HOURS} giờ)
     * và (giờ bắt đầu buổi dạy ĐẦU TIÊN).
     *
     * <p>Vế thứ hai không bỏ được: phân công gấp trong ngày mà để hạn 48 giờ thì hạn rơi vào
     * lúc buổi học đã dạy xong — xác nhận lúc đó chẳng còn nghĩa gì.
     */
    private LocalDateTime computeConfirmDeadline(Integer assignmentId) {
        LocalDateTime byWindow = LocalDateTime.now().plusHours(CONFIRM_WINDOW_HOURS);
        LocalDateTime firstLesson = scheduleRepo.findByAssignmentIdAndDeletedFalse(assignmentId).stream()
                .map(Schedule::getStartTime)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        return firstLesson != null && firstLesson.isBefore(byWindow) ? firstLesson : byWindow;
    }

    /**
     * Hai tiết trong CÙNG một request có đè giờ nhau không. Bình thường khung tiết của một
     * trường không giao nhau, nhưng dữ liệu Period do người dùng nhập nên vẫn phải chặn —
     * nếu lọt, một GV bị sinh hai buổi chồng giờ ngay trong cùng phân công.
     */
    private void checkSelfTimeConflicts(List<AssignmentSlotRequest> slots, Map<Integer, Period> periodById) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                if (!slots.get(i).dayOfWeek().equals(slots.get(j).dayOfWeek())) {
                    continue;
                }
                Period p1 = periodById.get(slots.get(i).periodId());
                Period p2 = periodById.get(slots.get(j).periodId());
                if (timeOverlaps(p1, p2)) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            dayLabelVi(slots.get(i).dayOfWeek()) + ": tiết " + p1.getPeriodNumber() + " ("
                                    + timeRange(p1) + ") và tiết " + p2.getPeriodNumber() + " (" + timeRange(p2)
                                    + ") bị đè giờ lên nhau");
                }
            }
        }
    }

    /** Chặn GV bị xếp dạy hai nơi cùng khung giờ — luật dùng chung, xem {@link TeacherTimeConflictChecker}. */
    private void checkTeacherTimeConflict(
            Integer teacherId,
            String dayOfWeek,
            Period period,
            LocalDate startDate,
            LocalDate endDate,
            Integer ignoreAssignmentId) {
        conflictChecker.check(teacherId, dayOfWeek, period, startDate, endDate, ignoreAssignmentId);
    }

    private static boolean timeOverlaps(Period a, Period b) {
        return TeacherTimeConflictChecker.timeOverlaps(a, b);
    }

    private static boolean datesOverlap(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return TeacherTimeConflictChecker.datesOverlap(aStart, aEnd, bStart, bEnd);
    }

    private static String timeRange(Period p) {
        return TeacherTimeConflictChecker.timeRange(p);
    }

    private static String dayLabelVi(String code) {
        return TeacherTimeConflictChecker.dayLabelVi(code);
    }

    /** Trải 1 slot (Thứ+Tiết) thành các buổi Schedule hằng tuần trong [from, to]. */
    private void generateSchedules(
            Assignment a, AssignmentSlot slot, Period period, LocalDate from, LocalDate to, Integer userId) {
        DayOfWeek target = toDayOfWeek(slot.getDayOfWeek());
        LocalDate d = from;
        while (d.getDayOfWeek() != target) {
            d = d.plusDays(1);
        }
        for (; !d.isAfter(to); d = d.plusWeeks(1)) {
            Schedule s = new Schedule();
            s.setAssignmentId(a.getId());
            s.setTeacherId(a.getTeacherId());
            s.setStartTime(d.atTime(period.getStartTime()));
            s.setEndTime(d.atTime(period.getEndTime()));
            // CHƯA duyệt: buổi chỉ lên APPROVED khi giáo viên xác nhận phiếu. Mọi màn hình
            // của giáo viên + chấm công + lương + thống kê đều lọc APPROVED nên buổi PENDING
            // tự động không lộ ra chỗ nào.
            s.setStatus("PENDING");
            s.setSource("MANUAL");
            s.setPeriodId(period.getId());
            s.setSourceSlotId(slot.getId());
            s.setCreatedByUserId(userId);
            // approvedBy/approvedAt để trống — sẽ ghi khi phiếu được xác nhận.
            scheduleRepo.save(s);
        }
    }

    /* ─────────────────────────── UPDATE (sửa & gửi lại) ─────────────────────────── */

    /**
     * Sửa một phiếu CHƯA được xác nhận rồi GỬI LẠI lời mời: phiếu quay về Chờ xác nhận, hạn trả
     * lời tính lại từ đầu, giáo viên nhận thông báo mới.
     *
     * <p>Đổi được giáo viên (phiếu bị từ chối thì xếp cho người khác — giáo viên cũ vẫn chọn lại
     * được phòng khi bấm nhầm), nhưng KHÔNG đổi trường/môn: khung tiết và lớp gắn với trường nên
     * đổi trường là một phiếu khác hẳn.
     *
     * <p>Toàn bộ tiết + buổi cũ bị xóa cứng rồi sinh lại. An toàn vì phiếu chưa từng được xác
     * nhận nên chưa buổi nào APPROVED → chưa có dòng chấm công/lương nào bám vào.
     */
    @Transactional
    public AssignmentResponse update(Integer id, AssignmentUpdateRequest req) {
        Assignment a = getOrThrow(id);
        if (!AssignmentStatus.isEditable(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chỉ sửa được phiếu đang chờ xác nhận, bị từ chối hoặc đã hết hạn. "
                            + "Phiếu đã có hiệu lực thì hãy hủy rồi tạo phiếu mới.");
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }
        Teacher teacher = teacherRepo
                .findByIdAndDeletedFalse(req.teacherId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy giáo viên"));
        School school = schoolRepo
                .findById(a.getSchoolId())
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Trường của phân công không còn tồn tại"));

        Map<Integer, Period> periodById = new HashMap<>();
        List<Integer> slotClassIds = validateSlots(
                req.slots(), school, null, teacher.getId(), req.startDate(), req.endDate(), id, periodById);

        Integer userId = SecurityUtils.currentUserId();

        // Dọn sạch tiết + buổi cũ (đúng thứ tự khóa ngoại) trước khi sinh lại theo nội dung mới.
        scheduleRepo.deleteStatusLogsByAssignmentId(id);
        scheduleRepo.deleteByAssignmentId(id);
        slotRepo.deleteByAssignmentId(id);

        a.setTeacherId(teacher.getId());
        a.setClassId(slotClassIds.get(0));
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setStatus(AssignmentStatus.PENDING);
        a.setRejectionReason(null);
        a.setApprovalNote(null);
        a.setConfirmedAt(null);
        a.setConfirmedByUserId(null);
        a.setConfirmSource(null);
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);

        LocalDate end = req.endDate() != null ? req.endDate() : req.startDate().plusWeeks(DEFAULT_WEEKS);
        for (int i = 0; i < req.slots().size(); i++) {
            AssignmentSlotRequest slotReq = req.slots().get(i);
            AssignmentSlot slot = new AssignmentSlot();
            slot.setAssignmentId(a.getId());
            slot.setTeacherId(teacher.getId());
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setPeriodId(slotReq.periodId());
            slot.setClassId(slotClassIds.get(i));
            slot.setCreatedBy(userId);
            slotRepo.save(slot);
            generateSchedules(a, slot, periodById.get(slotReq.periodId()), req.startDate(), end, userId);
        }

        a.setConfirmDeadline(computeConfirmDeadline(a.getId()));
        assignmentRepo.save(a);

        // Lời mời cũ (nếu còn treo trong chuông) không còn đúng nội dung → đóng rồi gửi lại.
        approvalService.closeOpenInvites(a.getId(), "CANCELLED");
        approvalService.publishInvite(a, true);
        return toResponse(a);
    }

    /**
     * Hủy nhiều phiếu một lượt (đưa vào thùng rác). Không dừng ở phiếu lỗi đầu tiên — lỗi từng
     * phiếu gom lại trả về để người dùng biết cái nào không hủy được và vì sao.
     */
    public AssignmentBulkResult bulkCancel(List<Integer> ids) {
        AssignmentBulkResult result = new AssignmentBulkResult();
        AssignmentService proxy = applicationContext.getBean(AssignmentService.class);
        for (Integer id : ids) {
            try {
                proxy.softDelete(id); // qua proxy để mỗi phiếu là một giao dịch riêng
                result.ok();
            } catch (ApiException e) {
                result.fail(id, e.getMessage());
            }
        }
        return result;
    }

    /** Số phiếu theo từng trạng thái — badge/tab trên màn Phân công. */
    @Transactional(readOnly = true)
    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Assignment a : assignmentRepo.findByDeletedFalseOrderByIdDesc()) {
            counts.merge(effectiveStatus(a), 1L, Long::sum);
        }
        return counts;
    }

    /**
     * Trạng thái NHÌN THẤY của phiếu: phiếu chờ đã quá hạn hiện ngay là "Hết hạn" dù tác vụ nền
     * chưa kịp ghi lại DB — màn hình không được nói dối trong lúc chờ job chạy.
     */
    private static String effectiveStatus(Assignment a) {
        return a.isExpiredPending() ? AssignmentStatus.EXPIRED : a.getStatus();
    }

    /* ─────────────────────────── CANCEL ─────────────────────────── */

    @Transactional
    public AssignmentResponse cancel(Integer id) {
        Assignment a = getOrThrow(id);
        Integer userId = SecurityUtils.currentUserId();
        // Phiếu CHƯA từng được xác nhận thì không buổi nào có hiệu lực → hủy sạch, kể cả buổi
        // quá khứ. Chỉ phiếu đã chạy mới phải giữ buổi đã dạy để không mất chấm công/lương.
        boolean neverConfirmed = a.getConfirmedAt() == null;
        a.setStatus(AssignmentStatus.CANCELLED);
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);
        LocalDateTime now = LocalDateTime.now();
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
            if (!"CANCELLED".equals(s.getStatus())
                    && (neverConfirmed || s.getStartTime().isAfter(now))) {
                s.setUpdatedBy(userId);
                s.setStatus("CANCELLED");
                s.setUpdatedAt(Instant.now());
                scheduleRepo.save(s);
            }
        }
        return toResponse(a);
    }

    /**
     * BỎ HỦY: khôi phục phân công ĐÃ HỦY (khi lỡ bấm Hủy). Đảo ngược {@link #cancel(Integer)}.
     * Trước khi bật lại phải DÒ TRÙNG LỊCH: khung Thứ+Tiết có thể đã bị phiếu khác chiếm mất
     * trong lúc nó đang bị hủy.
     *
     * <p>Phiếu quay về đúng chỗ nó đứng trước khi hủy: đã từng được giáo viên xác nhận thì trả
     * về ACTIVE (buổi lên APPROVED), chưa từng xác nhận thì trả về CHỜ XÁC NHẬN (buổi giữ
     * PENDING) — không được lén cho lịch chưa ai đồng ý chạy thẳng.
     */
    @Transactional
    public AssignmentResponse reactivate(Integer id) {
        Assignment a = getOrThrow(id);
        if (!AssignmentStatus.CANCELLED.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ khôi phục được phân công đã hủy");
        }
        for (AssignmentSlot slot : slotRepo.findByAssignmentIdAndDeletedFalse(id)) {
            Period p = periodRepo
                    .findById(slot.getPeriodId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT, "Không khôi phục được: tiết của phân công không còn tồn tại"));
            // Cùng luật trùng giờ với lúc tạo mới (so giờ thật, mọi trường), nhưng bỏ qua
            // chính phân công đang khôi phục.
            checkTeacherTimeConflict(a.getTeacherId(), slot.getDayOfWeek(), p, a.getStartDate(), a.getEndDate(), id);
        }
        Integer userId = SecurityUtils.currentUserId();
        boolean wasConfirmed = a.getConfirmedAt() != null;
        a.setStatus(wasConfirmed ? AssignmentStatus.ACTIVE : AssignmentStatus.PENDING);
        if (!wasConfirmed) {
            a.setConfirmDeadline(computeConfirmDeadline(id)); // hạn trả lời tính lại từ bây giờ
        }
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);
        // Đưa lại các buổi đã bị hủy theo phân công (đảo ngược cancel()) — về APPROVED nếu
        // phiếu đã được xác nhận, ngược lại chỉ về PENDING chờ giáo viên đồng ý.
        String restored = wasConfirmed ? "APPROVED" : "PENDING";
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
            if ("CANCELLED".equals(s.getStatus())) {
                s.setUpdatedBy(userId);
                s.setStatus(restored);
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
        // Phiếu còn "sống" (đang dạy HOẶC đang chờ xác nhận) phải hủy trước khi vào thùng rác.
        if (AssignmentStatus.ACTIVE.equals(a.getStatus()) || AssignmentStatus.PENDING.equals(a.getStatus())) {
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

        List<AssignmentSlot> slotEntities = includeDeletedSlots
                ? slotRepo.findByAssignmentId(a.getId())
                : slotRepo.findByAssignmentIdAndDeletedFalse(a.getId());
        Map<Integer, String> classNameCache = new HashMap<>();
        // Một phân công nay trải nhiều lớp (mỗi tiết một lớp) → cột "Lớp" ở danh sách là
        // tập hợp các lớp KHÁC NHAU của các tiết, giữ thứ tự xuất hiện. LinkedHashSet vừa
        // khử trùng vừa giữ thứ tự.
        Set<String> classNames = new LinkedHashSet<>();
        PeriodSessionIndex sessionIndex = new PeriodSessionIndex(periodRepo);
        List<AssignmentSlotResponse> slots = new ArrayList<>();
        for (AssignmentSlot slot : slotEntities) {
            Period p = periodRepo.findById(slot.getPeriodId()).orElse(null);
            Integer classId = slot.getClassId() != null ? slot.getClassId() : a.getClassId();
            String slotClassName = classId == null
                    ? null
                    : classNameCache.computeIfAbsent(classId, id -> classRepo
                            .findById(id)
                            .map(SchoolClass::getName)
                            .orElse(null));
            if (slotClassName != null) {
                classNames.add(slotClassName);
            }
            slots.add(AssignmentSlotResponse.fromEntity(slot, p, slotClassName, sessionIndex.of(p)));
        }
        String className = classNames.isEmpty()
                ? (a.getClassId() == null
                        ? null
                        : classRepo
                                .findById(a.getClassId())
                                .map(SchoolClass::getName)
                                .orElse(null))
                : String.join(", ", classNames);
        return AssignmentResponse.fromEntity(
                a, teacherName, schoolName, subjectName, className, slots, effectiveStatus(a));
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
