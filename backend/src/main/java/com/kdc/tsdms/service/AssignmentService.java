package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BatchIn;
import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.common.SearchText;
import com.kdc.tsdms.dto.AssignmentBulkResult;
import com.kdc.tsdms.dto.AssignmentCreateRequest;
import com.kdc.tsdms.dto.AssignmentFormOptions;
import com.kdc.tsdms.dto.AssignmentInviteDetail;
import com.kdc.tsdms.dto.AssignmentResponse;
import com.kdc.tsdms.dto.AssignmentSlotRequest;
import com.kdc.tsdms.dto.AssignmentSlotResponse;
import com.kdc.tsdms.dto.AssignmentUpdateRequest;
import com.kdc.tsdms.dto.ClassBusySlot;
import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.PeriodOption;
import com.kdc.tsdms.dto.SchoolScopedOptions;
import com.kdc.tsdms.dto.TeacherBusySlot;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.entity.TeacherSubject;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.PayrollRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.repository.TeacherSubjectRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    /**
     * Độ dài tối đa của MỘT phiếu phân công, tính bằng tháng.
     *
     * <p>Mười hai tháng = trọn một năm học. Trần này không phải để làm khó người xếp lịch mà
     * để chặn cú gõ nhầm năm: "31/12/2099" trong ô ngày kết thúc sẽ trải slot thành hàng trăm
     * nghìn buổi dạy, treo request và bơm phồng bảng Schedule trước khi ai kịp nhận ra.
     */
    private static final int MAX_MONTHS = 12;

    /**
     * Cửa sổ thời gian giáo viên phải trả lời lời mời dạy. 48 giờ theo thông lệ mời nhận ca
     * (luật predictive scheduling của Mỹ quy định 24–48 giờ). Đổi số ở đây là đổi toàn hệ thống.
     */
    static final int CONFIRM_WINDOW_HOURS = 48;

    /** Giáo viên đang làm việc (khác RETIRED / SUSPENDED). */
    private static final String TEACHER_ACTIVE = "ACTIVE";

    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final ScheduleRepository scheduleRepo;
    private final TeacherRepository teacherRepo;
    private final TeacherSubjectRepository teacherSubjectRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final PeriodRepository periodRepo;
    private final PayrollRepository payrollRepo;
    private final AttendanceRepository attendanceRepo;
    private final AppUserRepository userRepo;
    private final AssignmentApprovalService approvalService;
    private final TeacherTimeConflictChecker conflictChecker;
    private final HolidayRepository holidayRepo;
    private final ApplicationContext applicationContext;

    public AssignmentService(
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            ScheduleRepository scheduleRepo,
            TeacherRepository teacherRepo,
            TeacherSubjectRepository teacherSubjectRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            PeriodRepository periodRepo,
            PayrollRepository payrollRepo,
            AttendanceRepository attendanceRepo,
            AppUserRepository userRepo,
            AssignmentApprovalService approvalService,
            TeacherTimeConflictChecker conflictChecker,
            HolidayRepository holidayRepo,
            ApplicationContext applicationContext) {
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.scheduleRepo = scheduleRepo;
        this.teacherRepo = teacherRepo;
        this.teacherSubjectRepo = teacherSubjectRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.periodRepo = periodRepo;
        this.payrollRepo = payrollRepo;
        this.attendanceRepo = attendanceRepo;
        this.userRepo = userRepo;
        this.approvalService = approvalService;
        this.conflictChecker = conflictChecker;
        this.holidayRepo = holidayRepo;
        this.applicationContext = applicationContext;
    }

    /* ─────────────────────────── QUERY ─────────────────────────── */

    /**
     * Danh sách phân công còn hoạt động, PHÂN TRANG PHÍA SERVER.
     *
     * <p>{@code keyword} tùy chọn: lọc KHÔNG phân biệt hoa/thường và DẤU tiếng Việt trên tên
     * GV / trường / lớp / môn. So khớp trên các tên đã dựng sẵn (không phụ thuộc collation
     * của DB), nên phải dựng tên TRƯỚC khi cắt trang.
     *
     * <p>Vì sao trả {@link Page} chứ không phải {@code List}: bản cũ trả cả 444 phiếu kèm
     * toàn bộ ô lịch của chúng — 1,5 MB JSON và 4,8 giây cho một màn hình chỉ hiện 10 dòng.
     * Trình duyệt tải hết về rồi cắt trang bằng JavaScript, tức là 97% dữ liệu truyền đi chỉ
     * để bị giấu đi.
     */
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> list(Integer teacherId, String keyword, String status, Pageable pageable) {
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

        TenGoi ten = napTenGoi(items);
        String kw = SearchText.normalize(keyword);
        List<Assignment> khop = kw.isEmpty()
                ? items
                : items.stream().filter(a -> matchesKeyword(a, ten, kw)).toList();

        // Cắt trang SAU khi lọc, rồi mới dựng response — chỉ 10 dòng phải dựng đầy đủ.
        int from = (int) Math.min(pageable.getOffset(), khop.size());
        int to = Math.min(from + pageable.getPageSize(), khop.size());
        List<AssignmentResponse> trang = khop.subList(from, to).stream()
                .map(a -> toResponse(a, ten, false))
                .toList();
        return new PageImpl<>(trang, pageable, khop.size());
    }

    /** Một phiếu có khớp từ khóa (tên GV / trường / lớp / môn) không. */
    private static boolean matchesKeyword(Assignment a, TenGoi ten, String normKeyword) {
        return SearchText.matchesAny(
                normKeyword,
                ten.teacher.get(a.getTeacherId()),
                ten.tenTruongCua(a),
                ten.tenLopCua(a),
                ten.subject.get(a.getSubjectId()));
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getById(Integer id) {
        Assignment a = getOrThrow(id);
        Integer scoped = scopedTeacherId(null);
        if (scoped != null && !scoped.equals(a.getTeacherId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền xem phân công này.");
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
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN, "Tài khoản này chưa được liên kết với hồ sơ giáo viên."));
        return own.getId();
    }

    private static boolean isStaff() {
        return SecurityUtils.isCentreStaff();
    }

    /**
     * Danh sách GV / môn / trường cho form tạo phân công, kèm thông tin phụ để ô tìm kiếm hiện
     * dòng hai (tải hiện tại của GV, cấp học của trường…).
     *
     * <p>Gom sẵn ở server bằng vài truy vấn "lấy tất rồi gộp trong bộ nhớ": dữ liệu ở quy mô vài
     * chục trường / vài chục giáo viên nên rẻ hơn nhiều so với để frontend gọi lắt nhắt.
     */
    @Transactional(readOnly = true)
    public AssignmentFormOptions formOptions() {
        // Tải hiện tại của từng GV: đếm ô lịch của các phiếu CÒN GIỮ CHỖ, gom theo giáo viên.
        Map<Integer, Integer> weeklyPeriods = new HashMap<>();
        Map<Integer, Set<Integer>> schoolsByTeacher = new HashMap<>();
        Map<Integer, Assignment> aCache = new HashMap<>();
        for (AssignmentSlot slot : slotRepo.findByDeletedFalse()) {
            Assignment a = aCache.computeIfAbsent(
                    slot.getAssignmentId(),
                    id -> assignmentRepo.findByIdAndDeletedFalse(id).orElse(null));
            if (a == null || !a.holdsTimeSlot()) {
                continue;
            }
            weeklyPeriods.merge(slot.getTeacherId(), 1, Integer::sum);
            Integer schoolId = slot.getSchoolId() != null ? slot.getSchoolId() : a.getSchoolId();
            if (schoolId != null) {
                schoolsByTeacher
                        .computeIfAbsent(slot.getTeacherId(), k -> new LinkedHashSet<>())
                        .add(schoolId);
            }
        }

        // Tên đăng nhập làm "mã giáo viên" — Teacher không có cột mã riêng.
        Map<Integer, String> usernameByUserId = new HashMap<>();
        userRepo.findAll().forEach(u -> usernameByUserId.put(u.getId(), u.getUsername()));

        // CHỈ giáo viên đang làm việc. Hồ sơ nghỉ hưu/đình chỉ vẫn giữ lại để tra lịch sử chấm
        // công và lương nên deleted=false — lọt vào dropdown là mời người xếp lịch chọn nhầm
        // một người sẽ không bao giờ tới lớp.
        // Môn mỗi giáo viên DẠY ĐƯỢC — nạp một lượt rồi gom, thay vì hỏi DB cho từng người.
        Map<Integer, List<Integer>> subjectsByTeacher = new HashMap<>();
        for (TeacherSubject ts : teacherSubjectRepo.findAll()) {
            subjectsByTeacher
                    .computeIfAbsent(ts.getTeacherId(), k -> new ArrayList<>())
                    .add(ts.getSubjectId());
        }

        List<AssignmentFormOptions.TeacherOption> teachers = teacherRepo.findByDeletedFalse().stream()
                .filter(t -> TEACHER_ACTIVE.equals(t.getStatus()))
                .map(t -> new AssignmentFormOptions.TeacherOption(
                        t.getId(),
                        fullName(t),
                        usernameByUserId.get(t.getAppUserId()),
                        weeklyPeriods.getOrDefault(t.getId(), 0),
                        schoolsByTeacher.getOrDefault(t.getId(), Set.of()).size(),
                        subjectsByTeacher.getOrDefault(t.getId(), List.of())))
                .toList();

        List<AssignmentFormOptions.SubjectOption> subjects = subjectRepo.findByDeletedFalseOrderByName().stream()
                .map(s -> new AssignmentFormOptions.SubjectOption(
                        s.getId(),
                        s.getName(),
                        s.getCode(),
                        s.getCategory() == null ? null : s.getCategory().getName()))
                .toList();

        // Lớp + khung tiết gom theo trường để tính cấp học / số lớp / số tiết một lượt.
        // CHỈ lớp ACTIVE: con số này là thứ frontend dùng để làm mờ ô chọn trường, nên nó phải
        // đếm đúng cái mà assertSchoolHasClass() chặn ở dưới. Đếm cả lớp đã đóng thì màn hình
        // cho chọn một trường mà server sẽ từ chối — người dùng xếp xong cả thời khóa biểu
        // mới biết mình đi nhầm đường.
        Map<Integer, List<SchoolClass>> classesBySchool = new HashMap<>();
        for (SchoolClass c : classRepo.findAll()) {
            if (!c.isDeleted() && "ACTIVE".equals(c.getStatus())) {
                classesBySchool
                        .computeIfAbsent(c.getSchoolId(), k -> new ArrayList<>())
                        .add(c);
            }
        }
        Map<Integer, Integer> periodCountBySchool = new HashMap<>();
        for (Period p : periodRepo.findAll()) {
            if (!p.isDeleted()) {
                periodCountBySchool.merge(p.getSchoolId(), 1, Integer::sum);
            }
        }

        // CHỈ trường đang hợp tác — xem School.conHopTac (tính cả trường hết hạn hợp đồng theo
        // NGÀY, không chỉ theo cột Status). Trường hết hạn/ngừng hợp tác vẫn nằm
        // trong hệ thống để giữ lịch sử hợp đồng, nhưng không được nhận phân công MỚI —
        // để lọt vào dropdown là mời người xếp lịch điều giáo viên tới một nơi trung tâm
        // không còn dạy nữa.
        LocalDate homNay = BusinessTime.today();
        List<AssignmentFormOptions.SchoolOption> schools = schoolRepo.findAll().stream()
                .filter(s -> !s.isDeleted() && s.conHopTac(homNay))
                .map(s -> {
                    List<SchoolClass> cls = classesBySchool.getOrDefault(s.getId(), List.of());
                    return new AssignmentFormOptions.SchoolOption(
                            s.getId(),
                            s.getName(),
                            PeriodService.levelLabel(cls),
                            cls.size(),
                            periodCountBySchool.getOrDefault(s.getId(), 0));
                })
                .toList();
        return new AssignmentFormOptions(teachers, subjects, schools);
    }

    /** Lớp + khung tiết của một trường (dropdown cấp 2). */
    @Transactional(readOnly = true)
    public SchoolScopedOptions schoolOptions(Integer schoolId) {
        // Nhãn PHẢI kèm năm học: mỗi lớp có một bản ghi riêng cho từng năm, nên không gắn năm
        // vào thì danh sách hiện hai mục "1A1" giống hệt nhau và người xếp lịch không có cách
        // nào phân biệt — chọn nhầm là xếp vào lớp năm ngoái. Cùng cách làm với
        // SchoolClassService.listActiveBySchool. Năm mới lên trước cho đỡ phải cuộn.
        List<OptionItem> classes = classRepo.findAll().stream()
                .filter(c -> !c.isDeleted() && c.getSchoolId().equals(schoolId))
                .sorted(Comparator.comparing((SchoolClass c) -> c.getSchoolYear() == null ? "" : c.getSchoolYear())
                        .reversed()
                        .thenComparing(SchoolClass::getName))
                .map(c -> new OptionItem(
                        c.getId(),
                        c.getSchoolYear() == null ? c.getName() : c.getName() + " (" + c.getSchoolYear() + ")"))
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
     * Bảng "giờ bận" của một giáo viên: mọi ô lịch thuộc phân công ACTIVE, kèm KHUNG GIỜ THẬT
     * của tiết. Form dùng để khóa sẵn tiết không chọn được — nhất là tiết ở TRƯỜNG KHÁC đè giờ,
     * mà form không suy ra được từ periodId vì mỗi trường một bộ Period riêng.
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
            // Trường của CHÍNH ô lịch này (V27): bảng "giờ bận" dùng để báo cho người xếp lịch
            // biết giáo viên đang vướng Ở ĐÂU, mà một phiếu nay trải nhiều trường.
            Integer slotSchoolId = slot.getSchoolId() != null ? slot.getSchoolId() : a.getSchoolId();
            b.schoolId = slotSchoolId;
            b.schoolName = schoolNames.computeIfAbsent(
                    slotSchoolId,
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
     * Bảng "lớp đang bận" của một TRƯỜNG: mọi ô lịch còn giữ chỗ, kèm khung giờ thật và tên
     * giáo viên đang dạy. Lưới xếp phân công dùng nó để tô xám sẵn ô đã có người.
     *
     * <p>Chiều ngược của {@link #teacherBusy}, để form biết trước luật {@code checkClass} (một
     * lớp không hai giáo viên cùng giờ) thay vì để người dùng xếp xong mới nhận 409.
     *
     * @param startDate/endDate giai đoạn đang định xếp — chỉ trả ô lịch của phân công có giai
     *     đoạn chồng lên khoảng này (null = không lọc theo ngày)
     */
    @Transactional(readOnly = true)
    public List<ClassBusySlot> classBusy(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        Map<Integer, Assignment> aCache = new HashMap<>();
        Map<Integer, Period> periodCache = new HashMap<>();
        Map<Integer, String> teacherNames = new HashMap<>();
        Map<Integer, String> subjectNames = new HashMap<>();

        List<ClassBusySlot> out = new ArrayList<>();
        for (AssignmentSlot slot : slotRepo.findBySchoolIdAndDeletedFalse(schoolId)) {
            Assignment a = aCache.computeIfAbsent(
                    slot.getAssignmentId(),
                    id -> assignmentRepo.findByIdAndDeletedFalse(id).orElse(null));
            // Phiếu hết hạn / đã hủy KHÔNG còn giữ chỗ → lớp trống trở lại, đừng khóa oan.
            if (a == null || !a.holdsTimeSlot()) {
                continue;
            }
            if (startDate != null && !datesOverlap(startDate, endDate, a.getStartDate(), a.getEndDate())) {
                continue;
            }
            Integer classId = slot.getClassId() != null ? slot.getClassId() : a.getClassId();
            if (classId == null) {
                continue;
            }
            Period p = periodCache.computeIfAbsent(
                    slot.getPeriodId(), id -> periodRepo.findById(id).orElse(null));
            if (p == null) {
                continue;
            }

            ClassBusySlot b = new ClassBusySlot();
            b.classId = classId;
            b.dayOfWeek = slot.getDayOfWeek();
            b.periodId = p.getId();
            b.periodNumber = p.getPeriodNumber();
            b.startTime = p.getStartTime();
            b.endTime = p.getEndTime();
            b.teacherId = slot.getTeacherId();
            b.teacherName = teacherNames.computeIfAbsent(slot.getTeacherId(), id -> teacherRepo
                    .findById(id)
                    .map(AssignmentService::fullName)
                    .orElse("(GV #" + id + ")"));
            b.subjectName = subjectNames.computeIfAbsent(
                    a.getSubjectId(),
                    id -> subjectRepo.findById(id).map(Subject::getName).orElse(null));
            b.assignmentId = a.getId();
            b.pending = AssignmentStatus.PENDING.equals(a.getStatus());
            b.startDate = a.getStartDate();
            b.endDate = a.getEndDate();
            out.add(b);
        }
        return out;
    }

    /* ─────────────────────────── CREATE ─────────────────────────── */

    @Transactional
    public AssignmentResponse create(AssignmentCreateRequest req) {
        Teacher teacher = teacherRepo
                .findByIdAndDeletedFalse(req.teacherId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy giáo viên đã chọn."));
        assertTeacherAvailable(teacher);
        School school = schoolRepo
                .findById(req.schoolId())
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy trường đã chọn."));
        assertSchoolActive(school);
        Subject subject = subjectRepo
                .findByIdAndDeletedFalse(req.subjectId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy môn học đã chọn."));
        assertPeriodValid(req.startDate(), req.endDate());
        // Phiếu MỚI không được bắt đầu trong quá khứ: sinh buổi cho những ngày đã trôi qua thì
        // giáo viên không thể chấm công cho chúng, mà chúng vẫn nằm trong lịch và bị đếm là
        // "vắng". Sửa phiếu cũ thì KHÔNG áp luật này — xem update().
        if (req.startDate().isBefore(BusinessTime.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được là ngày trong quá khứ.");
        }

        Map<Integer, Period> periodById = new HashMap<>();
        List<ResolvedSlot> resolved = validateSlots(
                req.slots(), school, req.classId(), teacher.getId(), req.startDate(), req.endDate(), null, periodById);

        Integer userId = SecurityUtils.currentUserId();

        LocalDate effectiveEnd = req.endDate();
        HolidayCalendar holidays = loadHolidays(req.startDate(), effectiveEnd);

        Assignment a = new Assignment();
        a.setTeacherId(teacher.getId());
        // Trường cấp phân công = TRƯỜNG CHÍNH (trường của tiết đầu tiên) — cột này NOT NULL và
        // vẫn là chỗ dựa của dữ liệu cũ, nhưng trường thật của mỗi buổi nằm ở từng slot (V27).
        a.setSchoolId(resolved.get(0).schoolId());
        a.setSubjectId(req.subjectId());
        // Lớp cấp phân công = lớp mặc định (client cũ) hoặc lớp của tiết đầu tiên — chỉ còn
        // là giá trị đại diện/dự phòng, lớp thật nằm ở từng slot.
        a.setClassId(req.classId() != null ? req.classId() : resolved.get(0).classId());
        a.setStartDate(req.startDate());
        a.setEndDate(effectiveEnd);
        // CHỜ XÁC NHẬN: lịch chưa có hiệu lực cho tới khi giáo viên đồng ý (hoặc admin ép duyệt).
        a.setStatus(AssignmentStatus.PENDING);
        a.setCreatedBy(userId);
        assignmentRepo.save(a);

        for (int i = 0; i < req.slots().size(); i++) {
            AssignmentSlotRequest slotReq = req.slots().get(i);
            AssignmentSlot slot = new AssignmentSlot();
            slot.setAssignmentId(a.getId());
            slot.setTeacherId(teacher.getId());
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setPeriodId(slotReq.periodId());
            slot.setClassId(resolved.get(i).classId());
            slot.setSchoolId(resolved.get(i).schoolId());
            slot.setCreatedBy(userId);
            slotRepo.save(slot);

            generateSchedules(
                    a, slot, periodById.get(slotReq.periodId()), req.startDate(), effectiveEnd, holidays, userId);
        }

        // Cả giai đoạn rơi trọn vào ngày nghỉ thì không sinh được buổi nào. Phiếu rỗng như vậy
        // vô dụng mà vẫn có hại: giáo viên không có gì để xác nhận, còn luật chống trùng vẫn coi
        // khung giờ đó là đã bị chiếm suốt cả giai đoạn.
        if (scheduleRepo.findByAssignmentIdAndDeletedFalse(a.getId()).isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Toàn bộ giai đoạn đã chọn rơi vào ngày nghỉ nên không sinh được buổi dạy nào — "
                            + "hãy chọn lại ngày bắt đầu / ngày kết thúc.");
        }

        // Hạn trả lời phải biết buổi đầu tiên diễn ra lúc nào → tính SAU khi đã sinh buổi.
        a.setConfirmDeadline(computeConfirmDeadline(a.getId()));
        assignmentRepo.save(a);

        // Lời mời dạy (có nút Xác nhận / Từ chối) — cùng một mẫu nội dung với lúc nhắc lại.
        approvalService.publishInvite(a, false);

        return toResponse(a);
    }

    /** Trường + lớp đã chốt cho một tiết sau khi soát xong (V27). */
    private record ResolvedSlot(Integer schoolId, Integer classId) {}

    /**
     * Chặn xếp giáo viên vào lớp của NĂM HỌC ĐÃ QUA.
     *
     * <p>Mỗi lớp có bản ghi riêng cho từng năm học ({@code SchoolClass.SchoolYear}) nên "1A1"
     * tồn tại nhiều lần với id khác nhau. Hệ quả nguy hiểm: {@code
     * TeacherTimeConflictChecker.checkClass} dò trùng theo {@code classId}, hai bản ghi của cùng
     * một lớp thật là hai id nên xếp hai giáo viên vào cùng phòng cùng giờ mà không bị chặn.
     *
     * <p>So theo NĂM BẮT ĐẦU của năm học chứ không so ngày — mốc "từ tháng 8 là năm học mới" bao
     * được giai đoạn tựu trường cuối tháng 8 (Hải Phòng 2026: tựu trường 24/8, khai giảng 5/9).
     * Lớp thiếu hoặc sai định dạng {@code SchoolYear} thì bỏ qua: đây là luật phòng nhầm, không
     * phải ràng buộc toàn vẹn.
     */
    static void assertClassNotStale(SchoolClass c, LocalDate startDate) {
        Integer classYear = schoolYearStartOf(c.getSchoolYear());
        if (classYear == null || startDate == null) {
            return;
        }
        int assignmentYear = startDate.getMonthValue() >= 8 ? startDate.getYear() : startDate.getYear() - 1;
        if (classYear < assignmentYear) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Lớp " + c.getName() + " thuộc năm học " + c.getSchoolYear()
                            + ", không xếp được cho phiếu bắt đầu ngày " + startDate
                            + ". Chọn lớp của năm học " + assignmentYear + "-" + (assignmentYear + 1) + ".");
        }
    }

    /**
     * Giáo viên phải đang LÀM VIỆC mới nhận được phân công mới.
     *
     * <p>Hồ sơ RETIRED / SUSPENDED vẫn giữ lại để có lịch sử chấm công và lương, nên
     * {@code deleted = false} KHÔNG đủ để kết luận là dùng được — phải soi cả {@code status}.
     */
    private static void assertTeacherAvailable(Teacher t) {
        if (!TEACHER_ACTIVE.equals(t.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Giáo viên " + fullName(t) + " đang ở trạng thái " + statusLabelVi(t.getStatus())
                            + " nên không nhận phân công mới.");
        }
    }

    /**
     * Trường phải đang hợp tác. Cùng lý do với {@link #assertTeacherAvailable(Teacher)}: trường
     * hết hạn hợp đồng vẫn được giữ lại để tra cứu lịch sử, không phải để xếp lịch mới vào.
     */
    private static void assertSchoolActive(School s) {
        LocalDate homNay = BusinessTime.today();
        if (!s.conHopTac(homNay)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Trường " + s.getName() + " đang ở trạng thái " + statusLabelVi(s.effectiveStatus(homNay))
                            + " nên không nhận phân công mới.");
        }
    }

    /**
     * Trường phải có ít nhất một lớp ĐANG HOẠT ĐỘNG.
     *
     * <p>Không có lớp thì không có chỗ nào để xếp tiết vào — phiếu tạo ra sẽ trỏ vào khoảng
     * không. Trước đây luật này chỉ tồn tại ở frontend dưới dạng làm mờ ô chọn, tức là gọi
     * thẳng API là đi vòng qua được.
     *
     * <p>Đếm theo Status ACTIVE chứ không phải "còn tồn tại": lớp đã đóng vẫn nằm trong bảng
     * nhưng không nhận được buổi dạy mới.
     */
    private void assertSchoolHasClass(School s) {
        if (classRepo.countBySchoolIdAndDeletedFalseAndStatus(s.getId(), "ACTIVE") == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Trường " + s.getName() + " chưa có lớp học nào đang hoạt động nên chưa nhận được phân công. "
                            + "Vui lòng thêm lớp cho trường này trước.");
        }
    }

    /**
     * Giai đoạn của phiếu: phải có ngày kết thúc, phải sau ngày bắt đầu, và không dài quá
     * {@link #MAX_MONTHS} tháng.
     *
     * <p>Gọi ở CẢ create lẫn update để hai đường vào không lệch luật nhau.
     */
    static void assertPeriodValid(LocalDate startDate, LocalDate endDate) {
        if (endDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng nhập ngày kết thúc.");
        }
        if (endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        if (endDate.isAfter(startDate.plusMonths(MAX_MONTHS))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Một phân công không kéo dài quá " + MAX_MONTHS + " tháng. "
                            + "Cần dạy lâu hơn thì tạo phiếu mới cho giai đoạn tiếp theo.");
        }
    }

    private static String statusLabelVi(String status) {
        if (status == null) {
            return "không xác định";
        }
        return switch (status) {
            case "RETIRED" -> "đã nghỉ việc";
            case "SUSPENDED" -> "tạm đình chỉ";
            case "EXPIRED" -> "hết hạn hợp đồng";
            case "INACTIVE" -> "ngừng hợp tác";
            default -> status;
        };
    }

    /** "2026-2027" → 2026. Trả null nếu trống hoặc không đúng định dạng. */
    private static Integer schoolYearStartOf(String schoolYear) {
        if (schoolYear == null || schoolYear.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(schoolYear.trim().substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Soát toàn bộ danh sách tiết của một phiếu (dùng chung cho tạo mới và sửa): mỗi tiết mang
     * TRƯỜNG riêng, tiết và lớp phải cùng thuộc đúng trường đó, các tiết không được trùng/đè
     * giờ nhau, và không được đè lịch sẵn có của giáo viên ở BẤT KỲ trường nào.
     *
     * @param defaultSchool trường mặc định cấp phiếu, dùng cho slot bỏ trống trường (client cũ)
     * @param defaultClassId lớp mặc định cấp phiếu, dùng cho slot bỏ trống lớp (client cũ)
     * @param ignoreAssignmentId bỏ qua chính phiếu này khi dò trùng (lúc sửa), null khi tạo mới
     * @param periodByIdOut nhận các Period đã nạp để bên gọi tái sử dụng, khỏi truy vấn lại
     * @return trường + lớp đã chốt cho từng slot (cùng thứ tự với {@code slots})
     */
    private List<ResolvedSlot> validateSlots(
            List<AssignmentSlotRequest> slots,
            School defaultSchool,
            Integer defaultClassId,
            Integer teacherId,
            LocalDate startDate,
            LocalDate endDate,
            Integer ignoreAssignmentId,
            Map<Integer, Period> periodByIdOut) {
        // KHÔNG xếp lịch Chủ nhật. Chặn ở đây chứ không ở @Pattern của DTO: mã SUN vẫn phải hợp
        // lệ ở tầng dữ liệu để phiếu cũ (nếu có) còn đọc/sửa được, chỉ cấm đường TẠO MỚI.
        for (AssignmentSlotRequest slot : slots) {
            if ("SUN".equals(slot.dayOfWeek())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Không xếp lịch dạy vào Chủ nhật.");
            }
        }

        // Chặn slot TRÙNG NHAU ngay trong request (cùng Thứ + Tiết) — nếu lọt sẽ sinh
        // đúp Schedule cho cùng một khung giờ. PeriodId là khóa toàn cục nên vẫn phân biệt
        // được tiết của hai trường khác nhau.
        Set<String> seenSlots = new HashSet<>();
        for (AssignmentSlotRequest slot : slots) {
            if (!seenSlots.add(slot.dayOfWeek() + "#" + slot.periodId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Không thể tạo phân công — " + dayLabelVi(slot.dayOfWeek()) + " có hai tiết dạy trùng nhau.");
            }
        }

        // Nạp & validate TRƯỜNG (V27), TIẾT và LỚP của TỪNG tiết (V16). Ba thứ này phải khớp
        // nhau: khung tiết và danh sách lớp đều thuộc về một trường cụ thể, lệch một cái là
        // buổi dạy sinh ra sai chỗ.
        Map<Integer, School> schoolById = new HashMap<>();
        Map<Integer, SchoolClass> classById = new HashMap<>();
        List<ResolvedSlot> resolved = new ArrayList<>(); // song song với slots
        for (AssignmentSlotRequest slot : slots) {
            Integer schoolId =
                    slot.schoolId() != null ? slot.schoolId() : (defaultSchool == null ? null : defaultSchool.getId());
            if (schoolId == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Vui lòng chọn trường cho " + dayLabelVi(slot.dayOfWeek()) + ".");
            }
            School school = schoolById.computeIfAbsent(schoolId, id -> schoolRepo
                    .findById(id)
                    .filter(x -> !x.isDeleted())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy trường đã chọn.")));
            // Soát trạng thái ở ĐÂY chứ không chỉ ở create(): từ V27 mỗi tiết tự mang trường
            // của nó, nên kiểm tra mỗi trường "chính" của phiếu là bỏ lọt các trường phụ. Đặt
            // trong validateSlots thì cả tạo mới lẫn sửa phiếu đều đi qua cùng một luật.
            assertSchoolActive(school);
            assertSchoolHasClass(school);

            Period p = periodRepo
                    .findById(slot.periodId())
                    .filter(x -> !x.isDeleted() && x.getSchoolId().equals(school.getId()))
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "Tiết đã chọn không thuộc khung tiết của trường " + school.getName() + "."));
            periodByIdOut.put(p.getId(), p);

            Integer classId = slot.classId() != null ? slot.classId() : defaultClassId;
            if (classId == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Vui lòng chọn lớp cho " + dayLabelVi(slot.dayOfWeek()) + ", tiết " + p.getPeriodNumber()
                                + ".");
            }
            SchoolClass c = classById.computeIfAbsent(classId, id -> classRepo
                    .findById(id)
                    .filter(x -> !x.isDeleted())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy lớp học đã chọn.")));
            if (!c.getSchoolId().equals(school.getId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Lớp " + c.getName() + " không thuộc trường " + school.getName() + ".");
            }
            assertClassNotStale(c, startDate);
            resolved.add(new ResolvedSlot(school.getId(), classId));
        }

        // Hai tiết trong CÙNG request đè giờ nhau (khác periodId nhưng khung giờ giao nhau).
        // Nay còn quan trọng hơn: một phiếu trải nhiều trường thì hai tiết ở hai trường hoàn
        // toàn có thể cùng rơi vào 07:00 mà không có gì ngoài phép so giờ này bắt được.
        checkSelfTimeConflicts(slots, periodByIdOut);

        // DÒ TRÙNG LỊCH GV THEO GIỜ THẬT: quét mọi ô lịch của GV trong cùng Thứ ở MỌI
        // trường, so khoảng giờ của tiết. KHÔNG so periodId — Period thuộc về từng trường
        // nên "tiết 1" của hai trường là hai id khác nhau mà giờ vẫn đè nhau.
        for (int i = 0; i < slots.size(); i++) {
            AssignmentSlotRequest slot = slots.get(i);
            Period p = periodByIdOut.get(slot.periodId());
            checkTeacherTimeConflict(teacherId, slot.dayOfWeek(), p, startDate, endDate, ignoreAssignmentId);
            // DÒ TRÙNG PHÍA LỚP: luật trên chỉ hỏi "giáo viên có bận không", không ai hỏi
            // "lớp này đã có ai dạy chưa" — nên xếp được ba giáo viên vào cùng một lớp cùng
            // một tiết, và cả ba đều sinh buổi dạy rồi đều được tính công.
            conflictChecker.checkClass(
                    resolved.get(i).classId(), teacherId, slot.dayOfWeek(), p, startDate, endDate, ignoreAssignmentId);
        }
        return resolved;
    }

    /**
     * Hạn giáo viên phải trả lời = SỚM HƠN của (bây giờ + {@value #CONFIRM_WINDOW_HOURS} giờ)
     * và (giờ bắt đầu buổi dạy ĐẦU TIÊN).
     *
     * <p>Vế thứ hai không bỏ được: phân công gấp trong ngày mà để hạn 48 giờ thì hạn rơi vào
     * lúc buổi học đã dạy xong — xác nhận lúc đó chẳng còn nghĩa gì.
     */
    private LocalDateTime computeConfirmDeadline(Integer assignmentId) {
        LocalDateTime byWindow = BusinessTime.now().plusHours(CONFIRM_WINDOW_HOURS);
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
                            "Không thể tạo phân công — "
                                    + dayLabelVi(slots.get(i).dayOfWeek()) + ", tiết "
                                    + p1.getPeriodNumber() + " (" + timeRange(p1) + ") và tiết "
                                    + p2.getPeriodNumber() + " (" + timeRange(p2) + ") bị trùng giờ.");
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

    /** Nạp lịch nghỉ đúng một lần cho cả giai đoạn của phiếu, thay vì hỏi DB cho từng ngày. */
    private HolidayCalendar loadHolidays(LocalDate from, LocalDate to) {
        return new HolidayCalendar(holidayRepo.findOverlapping(from, to));
    }

    /**
     * Lịch nghỉ đã nạp sẵn của một đoạn thời gian — trả lời "ngày này, trường này có nghỉ không".
     *
     * <p>So theo trường của TỪNG TIẾT chứ không phải trường cấp phiếu: từ V27 một phiếu trải được
     * nhiều trường, mà kỳ nghỉ riêng (sửa chữa, lịch riêng) chỉ thuộc về một trường.
     */
    private record HolidayCalendar(List<Holiday> ranges) {
        boolean isOff(LocalDate day, Integer schoolId) {
            for (Holiday h : ranges) {
                if (h.getSchoolId() != null && !h.getSchoolId().equals(schoolId)) {
                    continue; // kỳ nghỉ riêng của trường khác
                }
                if (!day.isBefore(h.getFromDate()) && !day.isAfter(h.getToDate())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Trải 1 slot (Thứ+Tiết) thành các buổi Schedule hằng tuần trong [from, to], BỎ QUA ngày nghỉ.
     */
    private void generateSchedules(
            Assignment a,
            AssignmentSlot slot,
            Period period,
            LocalDate from,
            LocalDate to,
            HolidayCalendar holidays,
            Integer userId) {
        DayOfWeek target = toDayOfWeek(slot.getDayOfWeek());
        LocalDate d = from;
        while (d.getDayOfWeek() != target) {
            d = d.plusDays(1);
        }
        for (; !d.isAfter(to); d = d.plusWeeks(1)) {
            // Ngày lễ / kỳ nghỉ thì KHÔNG sinh buổi. Sinh ra rồi hủy sau là quá muộn: job khép sổ
            // chấm công chạy nền (AttendanceSweepService) tới trước mắt người, buổi "ma" ngày lễ
            // đã kịp thành một dòng VẮNG thật và bị trừ vào lương.
            if (holidays.isOff(d, slot.getSchoolId())) {
                continue;
            }
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
     * Sửa phiếu CHƯA xác nhận rồi gửi lại lời mời: phiếu về Chờ xác nhận, hạn trả lời tính lại,
     * giáo viên nhận thông báo mới.
     *
     * <p>Đổi được GIÁO VIÊN, KHÔNG đổi trường/môn — khung tiết và lớp gắn với trường nên đổi
     * trường là một phiếu khác hẳn.
     *
     * <p>Tiết + buổi cũ bị xóa CỨNG rồi sinh lại. Trạng thái phiếu đã chặn phần lớn rủi ro
     * (chưa xác nhận thì chưa buổi nào APPROVED nên chưa có chấm công/lương bám vào), nhưng từ
     * Đợt 5 còn hỏi thẳng dữ liệu trước khi xóa — xem ghi chú tại chỗ gọi
     * {@code demChamCongTheoPhanCong}.
     */
    @Transactional
    public AssignmentResponse update(Integer id, AssignmentUpdateRequest req) {
        Assignment a = getOrThrow(id);
        if (!AssignmentStatus.isEditable(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chỉ sửa được phân công đang chờ xác nhận, bị từ chối hoặc đã hết hạn. "
                            + "Phân công đã có hiệu lực cần được hủy và tạo lại.");
        }
        // ĐỢT 5 — chốt bằng DỮ LIỆU chứ không chỉ bằng TRẠNG THÁI. Ba dòng dưới xóa CỨNG, mà
        // Attendance.ScheduleId là khóa ngoại không có ON DELETE: còn một dòng chấm công là câu
        // DELETE bung ra lỗi 500 SQL thô. Hôm nay chuyện đó không xảy ra được (chỉ phiếu chưa
        // xác nhận mới sửa được, buổi của nó chưa APPROVED nên không đường nào sinh chấm công),
        // nhưng đó là suy luận bắc cầu qua ba file khác — thêm một trạng thái vào isEditable
        // hoặc một đường ghi Attendance mới là nó gãy trong im lặng. Hỏi thẳng dữ liệu thì rào
        // này không phụ thuộc vào ai nhớ gì.
        long soChamCong = attendanceRepo.demChamCongTheoPhanCong(id);
        if (soChamCong > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Không sửa được phân công này: đã có " + soChamCong
                            + " bản ghi chấm công gắn với các buổi dạy của nó. Sửa phiếu là xóa và "
                            + "sinh lại toàn bộ buổi, làm mất luôn phần chấm công đó. "
                            + "Hãy hủy phiếu này rồi tạo phiếu mới cho giai đoạn còn lại.");
        }
        assertPeriodValid(req.startDate(), req.endDate());
        // Sửa phiếu: chỉ chặn khi ĐỔI sang một ngày quá khứ KHÁC. Giữ nguyên ngày cũ (dù đã qua)
        // phải cho phép — nếu không thì phiếu tạo hôm qua sẽ không bao giờ đổi được giáo viên
        // nữa, mà thay người dạy gấp lại đúng là lúc cần sửa nhất.
        if (!req.startDate().equals(a.getStartDate()) && req.startDate().isBefore(BusinessTime.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu mới không được là ngày trong quá khứ.");
        }
        Teacher teacher = teacherRepo
                .findByIdAndDeletedFalse(req.teacherId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy giáo viên đã chọn."));
        // Sửa phiếu ĐỔI ĐƯỢC giáo viên (xem ghi chú dưới), nên luật "chỉ giao cho người đang
        // làm việc" phải áp ở đây y như lúc tạo mới. Thiếu chỗ này thì cấm cửa đằng trước mà
        // vẫn mở đằng sau: không tạo được phiếu cho người đã nghỉ, nhưng sửa một phiếu sẵn có
        // sang tên họ thì lọt.
        assertTeacherAvailable(teacher);
        // Trường CŨ chỉ còn là giá trị mặc định cho slot không ghi trường (client cũ). Từ V27 mỗi
        // tiết tự mang trường nên sửa phiếu ĐỔI ĐƯỢC cả trường — không tìm thấy trường cũ (đã bị
        // xóa mềm) cũng không chặn nữa, miễn là các tiết mới đều chỉ rõ trường của mình.
        School defaultSchool =
                schoolRepo.findById(a.getSchoolId()).filter(s -> !s.isDeleted()).orElse(null);

        Map<Integer, Period> periodById = new HashMap<>();
        List<ResolvedSlot> resolved = validateSlots(
                req.slots(), defaultSchool, null, teacher.getId(), req.startDate(), req.endDate(), id, periodById);

        Integer userId = SecurityUtils.currentUserId();

        // Dọn sạch tiết + buổi cũ (đúng thứ tự khóa ngoại) trước khi sinh lại theo nội dung mới.
        scheduleRepo.deleteStatusLogsByAssignmentId(id);
        scheduleRepo.deleteByAssignmentId(id);
        slotRepo.deleteByAssignmentId(id);

        a.setTeacherId(teacher.getId());
        a.setSchoolId(resolved.get(0).schoolId());
        a.setClassId(resolved.get(0).classId());
        a.setStartDate(req.startDate());
        LocalDate effectiveEnd = req.endDate();
        a.setEndDate(effectiveEnd);
        HolidayCalendar holidays = loadHolidays(req.startDate(), effectiveEnd);
        a.setStatus(AssignmentStatus.PENDING);
        a.setRejectionReason(null);
        a.setApprovalNote(null);
        a.setConfirmedAt(null);
        a.setConfirmedByUserId(null);
        a.setConfirmSource(null);
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);

        for (int i = 0; i < req.slots().size(); i++) {
            AssignmentSlotRequest slotReq = req.slots().get(i);
            AssignmentSlot slot = new AssignmentSlot();
            slot.setAssignmentId(a.getId());
            slot.setTeacherId(teacher.getId());
            slot.setDayOfWeek(slotReq.dayOfWeek());
            slot.setPeriodId(slotReq.periodId());
            slot.setClassId(resolved.get(i).classId());
            slot.setSchoolId(resolved.get(i).schoolId());
            slot.setCreatedBy(userId);
            slotRepo.save(slot);
            generateSchedules(
                    a, slot, periodById.get(slotReq.periodId()), req.startDate(), effectiveEnd, holidays, userId);
        }

        // Cả giai đoạn rơi trọn vào ngày nghỉ thì không sinh được buổi nào. Phiếu rỗng như vậy
        // vô dụng mà vẫn có hại: giáo viên không có gì để xác nhận, còn luật chống trùng vẫn coi
        // khung giờ đó là đã bị chiếm suốt cả giai đoạn.
        if (scheduleRepo.findByAssignmentIdAndDeletedFalse(a.getId()).isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Toàn bộ giai đoạn đã chọn rơi vào ngày nghỉ nên không sinh được buổi dạy nào — "
                            + "hãy chọn lại ngày bắt đầu / ngày kết thúc.");
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
        // Giáo viên đang đứng lớp thì KHÔNG hủy. Hủy giữa tiết để lại một dòng chấm công không
        // bao giờ khép được: người dạy bấm check-out thì buổi đã bị đánh CANCELLED nên hệ thống
        // từ chối, và cuối tháng buổi ấy biến mất khỏi phiếu lương của người đã dạy thật. Đợi
        // hết tiết rồi hủy thì buổi đó khép sổ bình thường, các buổi còn lại vẫn hủy được.
        if (attendanceRepo.countDangDayDoTheoPhanCong(id) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Giáo viên đang trong tiết dạy của phân công này (đã điểm danh vào, chưa điểm danh ra). "
                            + "Vui lòng đợi buổi dạy kết thúc rồi hủy.");
        }
        Integer userId = SecurityUtils.currentUserId();
        // Phiếu CHƯA từng được xác nhận thì không buổi nào có hiệu lực → hủy sạch, kể cả buổi
        // quá khứ. Chỉ phiếu đã chạy mới phải giữ buổi đã dạy để không mất chấm công/lương.
        boolean neverConfirmed = a.getConfirmedAt() == null;
        a.setStatus(AssignmentStatus.CANCELLED);
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);
        LocalDateTime now = BusinessTime.now();
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
            if (!"CANCELLED".equals(s.getStatus())
                    && (neverConfirmed || s.getStartTime().isAfter(now))) {
                s.setUpdatedBy(userId);
                s.setStatus("CANCELLED");
                s.setUpdatedAt(Instant.now());
                scheduleRepo.save(s);
            }
        }
        // Đóng lời mời còn treo trong chuông của giáo viên. Thiếu bước này thì admin hủy phiếu
        // xong giáo viên VẪN thấy nút "Xác nhận" cho một phân công không còn tồn tại — bấm vào
        // chỉ nhận lỗi, và tệ hơn là họ tưởng mình vẫn phải đi dạy buổi đó.
        approvalService.closeOpenInvites(id, "CANCELLED");
        return toResponse(a);
    }

    /**
     * BỎ HỦY: khôi phục phân công ĐÃ HỦY (khi lỡ bấm Hủy). Đảo ngược {@link #cancel(Integer)}.
     * Trước khi bật lại phải DÒ TRÙNG LỊCH: khung Thứ+Tiết có thể đã bị phiếu khác chiếm mất
     * trong lúc nó đang bị hủy.
     *
     * <p>Phiếu về đúng chỗ trước khi hủy: từng được xác nhận thì về ACTIVE (buổi lên APPROVED),
     * chưa xác nhận thì về CHỜ XÁC NHẬN (buổi giữ PENDING) — không cho lịch chưa ai đồng ý chạy.
     */
    @Transactional
    public AssignmentResponse reactivate(Integer id) {
        Assignment a = getOrThrow(id);
        if (!AssignmentStatus.CANCELLED.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ khôi phục được phân công đã hủy.");
        }
        for (AssignmentSlot slot : slotRepo.findByAssignmentIdAndDeletedFalse(id)) {
            Period p = periodRepo
                    .findById(slot.getPeriodId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT, "Không thể khôi phục — tiết của phân công không còn tồn tại."));
            // Cùng luật trùng giờ với lúc tạo mới (so giờ thật, mọi trường), nhưng bỏ qua
            // chính phân công đang khôi phục.
            checkTeacherTimeConflict(a.getTeacherId(), slot.getDayOfWeek(), p, a.getStartDate(), a.getEndDate(), id);
            // Và soát cả phía LỚP. Lúc phiếu nằm trong thùng rác, ô lịch của nó bị xóa mềm nên
            // khung giờ được nhả ra — lớp hoàn toàn có thể đã được giao cho giáo viên khác.
            // Chỉ hỏi "giáo viên này có bận không" thì khôi phục xong lớp có hai giáo viên
            // cùng một tiết, cả hai đều sinh buổi dạy và đều được tính công.
            conflictChecker.checkClass(
                    slot.getClassId(), a.getTeacherId(), slot.getDayOfWeek(), p, a.getStartDate(), a.getEndDate(), id);
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
     * rồi gắn cờ {@code deleted} cho phiếu và CASCADE xóa mềm các ô thời khóa biểu.
     * Khôi phục lại từ Thùng rác (xem {@link #restore}).
     *
     * <p><b>BUỔI ĐÃ DẠY THÌ Ở LẠI.</b> Đợt 5 (2026-08-24) vá một mâu thuẫn nội bộ: {@link
     * #cancel} rất cẩn thận, chỉ hủy buổi TƯƠNG LAI và cố ý giữ buổi quá khứ để không mất chấm
     * công/lương — rồi ngay sau đó {@code softDelete} gắn cờ xóa lên TẤT CẢ buổi, kể cả buổi
     * đã dạy xong đã có chấm công, xóa sạch sự thận trọng vừa rồi.
     *
     * <p>Hậu quả của bản cũ: dòng {@code Attendance} vẫn còn (Attendance không có xóa mềm và
     * không nằm trong cascade này) nhưng buổi dạy sinh ra nó đã ẩn khỏi mọi màn hình. Đúng thứ
     * mà {@code PayrollRepository.demChamCongMoCoi} đếm để cảnh báo trước khi chốt lương — mà
     * mức của nó chỉ là CẢNH BÁO, nên kế toán vẫn chốt kỳ được và phiếu lương trả tiền cho
     * những buổi dạy không còn tồn tại ở đâu cả (tiền KHÔNG mất: câu tính lương join Schedule
     * nhưng không lọc {@code IsDeleted}).
     *
     * <p>Nay dùng ĐÚNG luật của {@link #cancel}: phiếu CHƯA TỪNG được xác nhận thì không buổi
     * nào có hiệu lực nên xóa sạch, kể cả buổi quá khứ; phiếu đã từng chạy thì buổi đã tới giờ
     * là bằng chứng, giữ nguyên. Buổi giữ lại vẫn hiện ở Lịch dạy và Chấm công dù phiếu đã nằm
     * trong thùng rác — đó là chủ đích: tiền đã trả cho buổi nào thì buổi ấy phải tra ra được.
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
        // Đọc TRƯỚC khi gắn cờ, và đọc lại từ entity đã qua cancel() — cancel không đụng
        // ConfirmedAt nên giá trị vẫn đúng.
        boolean chuaTungXacNhan = a.getConfirmedAt() == null;
        LocalDateTime bayGio = BusinessTime.now();
        a.setDeleted(true);
        a.setDeletedAt(now);
        a.setDeletedBy(userId);
        assignmentRepo.save(a);
        // Ô thời khóa biểu là MẪU LẶP TUẦN, không phải bằng chứng của buổi nào — phiếu vào
        // thùng rác thì mẫu phải biến khỏi thời khóa biểu, nên vẫn cascade đủ.
        for (AssignmentSlot slot : slotRepo.findByAssignmentIdAndDeletedFalse(id)) {
            slot.setDeleted(true);
            slot.setDeletedAt(now);
            slot.setDeletedBy(userId);
            slotRepo.save(slot);
        }
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
            if (!chuaTungXacNhan && !s.getStartTime().isAfter(bayGio)) {
                continue; // buổi đã tới giờ của phiếu đã từng chạy: để lại làm bằng chứng
            }
            s.setDeleted(true);
            s.setDeletedAt(now);
            s.setDeletedBy(userId);
            scheduleRepo.save(s);
        }
    }

    /** Danh sách phân công trong thùng rác (đã xóa mềm). */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> listTrash() {
        List<Assignment> items = assignmentRepo.findByDeletedTrueOrderByIdDesc();
        TenGoi ten = napTenGoi(items);
        return items.stream().map(a -> toResponse(a, ten, true)).toList();
    }

    /**
     * Khôi phục phân công từ Thùng rác về lại <b>Đang chạy</b>: bỏ cờ {@code deleted} cho
     * Assignment + slot + buổi, rồi BỎ HỦY (đưa về ACTIVE, kích hoạt lại buổi dạy) qua
     * {@link #reactivate} — có DÒ TRÙNG LỊCH nên nếu khung Thứ+Tiết đã bị phân công ACTIVE
     * khác chiếm thì báo lỗi và không khôi phục.
     *
     * <p>Chỉ bỏ cờ cho dòng NÀO ĐANG bị gắn cờ, nên vẫn đúng sau thay đổi ở Đợt 5: buổi đã dạy
     * của phiếu đã từng chạy không hề bị {@link #softDelete} đụng tới, ở đây cũng không phải
     * bỏ cờ cho chúng. Ngược lại, file đính kèm hay ô lịch bị xóa RIÊNG trước đó thì đã mang cờ
     * sẵn và sẽ bị khôi phục nhầm — chấp nhận được vì ô lịch không có nút xóa riêng.
     */
    @Transactional
    public AssignmentResponse restore(Integer id) {
        Assignment a = assignmentRepo
                .findByIdAndDeletedTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công trong thùng rác."));
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
        return toResponse(a);
    }

    /* ─────────────────────────── HELPERS ─────────────────────────── */

    private Assignment getOrThrow(Integer id) {
        return assignmentRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công."));
    }

    /**
     * Bảng lịch dạy chi tiết của một lời mời — góc nhìn GIÁO VIÊN.
     *
     * <p><b>KHÔNG tự kiểm quyền.</b> Chỉ dùng cho luồng thông báo: {@code NotificationController}
     * đã xác thực thông báo thuộc người gọi rồi mới lấy ra assignmentId, nên ở đây không cần
     * (và không được) kiểm lại theo quyền quản trị — giáo viên vốn không có ASSIGNMENT_VIEW.
     */
    @Transactional(readOnly = true)
    public AssignmentInviteDetail inviteDetail(Integer assignmentId) {
        // Thông báo sống lâu hơn phiếu: phiếu bị hủy/xóa mà lời mời cũ vẫn nằm ở chuông. Nói
        // thẳng bằng tiếng người thay vì ném "Không tìm thấy phân công id=473" ra màn giáo viên.
        Assignment a = assignmentRepo
                .findByIdAndDeletedFalse(assignmentId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "Phân công này đã bị hủy hoặc xóa — lời mời không còn hiệu lực."));
        PeriodSessionIndex sessionIndex = new PeriodSessionIndex(periodRepo);
        Map<Integer, String> classNameCache = new HashMap<>();
        // Lớp hiển thị ở dòng tóm tắt: khử trùng, giữ thứ tự xuất hiện trong tuần.
        Set<String> classNames = new LinkedHashSet<>();
        // Lời mời phải kể ĐỦ các trường: giáo viên cần biết hôm đó phải chạy mấy nơi trước khi
        // bấm Xác nhận — đó chính là thông tin quyết định họ nhận hay từ chối.
        Set<String> schoolNames = new LinkedHashSet<>();
        Map<Integer, String> schoolNameCache = new HashMap<>();
        List<AssignmentSlotResponse> slots = new ArrayList<>();
        for (AssignmentSlot slot : slotRepo.findByAssignmentIdAndDeletedFalse(a.getId())) {
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
            String slotSchoolName = schoolNameOfSlot(slot, a, schoolNameCache);
            if (slotSchoolName != null) {
                schoolNames.add(slotSchoolName);
            }
            slots.add(AssignmentSlotResponse.fromEntity(slot, p, slotClassName, slotSchoolName, sessionIndex.of(p)));
        }
        return new AssignmentInviteDetail(
                a.getId(),
                schoolNames.isEmpty() ? schoolNameById(a.getSchoolId()) : String.join(", ", schoolNames),
                subjectRepo.findById(a.getSubjectId()).map(Subject::getName).orElse("(Môn #" + a.getSubjectId() + ")"),
                teacherRepo
                        .findById(a.getTeacherId())
                        .map(AssignmentService::fullName)
                        .orElse("(GV #" + a.getTeacherId() + ")"),
                a.getStartDate(),
                a.getEndDate(),
                effectiveStatus(a),
                a.getConfirmDeadline(),
                List.copyOf(classNames),
                slots);
    }

    /**
     * TÚI TÊN: mọi tên cần cho một danh sách phân công, nạp SẴN bằng vài câu SQL.
     *
     * <p>Bản cũ tra tên ngay trong vòng lặp dựng response: mỗi phiếu hỏi giáo viên, môn, ô
     * lịch, rồi mỗi ô lại hỏi tiết + lớp + trường. Cache có, nhưng nằm BÊN TRONG vòng lặp nên
     * reset theo từng phiếu. Đo trên dữ liệu thật (444 phiếu, 4.749 ô): khoảng 15.000 câu SQL
     * và 4,8 giây cho một lần mở màn Phân công.
     *
     * <p>Nạp cả bảng là chấp nhận được vì đây đều là bảng TRA CỨU nhỏ và ổn định: 150 giáo
     * viên, 23 môn, 31 trường, 864 lớp, 294 tiết — cộng lại chưa tới 1.400 dòng, rẻ hơn nhiều
     * so với hàng nghìn câu lẻ.
     */
    private final class TenGoi {
        final Map<Integer, String> teacher = new HashMap<>();
        final Map<Integer, String> subject = new HashMap<>();
        final Map<Integer, String> school = new HashMap<>();
        final Map<Integer, String> clazz = new HashMap<>();
        final Map<Integer, Period> period = new HashMap<>();
        final Map<Integer, List<AssignmentSlot>> slotsByAssignment = new HashMap<>();
        final PeriodSessionIndex sessionIndex = new PeriodSessionIndex(periodRepo);

        /** Ô lịch của một phiếu, đã lọc theo cờ xóa mềm. */
        List<AssignmentSlot> slotsCua(Assignment a, boolean includeDeleted) {
            List<AssignmentSlot> all = slotsByAssignment.getOrDefault(a.getId(), List.of());
            return includeDeleted
                    ? all
                    : all.stream().filter(s -> !s.isDeleted()).toList();
        }

        String tenTruong(Integer id) {
            return id == null ? null : school.getOrDefault(id, "(Trường #" + id + ")");
        }

        /**
         * Tên trường của MỘT TIẾT: trường của ô lịch (V27) trước, trường cấp phiếu sau.
         *
         * <p>Trường ở đây có thể ĐÃ BỊ XÓA MỀM — vẫn phải trả tên ra: phiếu đang chạy trỏ tới
         * một trường đã đóng, giấu tên đi thì cả cột "Trường" của dòng đó thành trống.
         */
        String tenTruongCuaO(AssignmentSlot slot, Assignment a) {
            return tenTruong(slot.getSchoolId() != null ? slot.getSchoolId() : a.getSchoolId());
        }

        /** Tập hợp tên trường của cả phiếu — ô tìm kiếm dùng, không phải dựng response. */
        String tenTruongCua(Assignment a) {
            Set<String> ten = new LinkedHashSet<>();
            for (AssignmentSlot s : slotsCua(a, false)) {
                String t = tenTruongCuaO(s, a);
                if (t != null) {
                    ten.add(t);
                }
            }
            if (ten.isEmpty() && a.getSchoolId() != null) {
                ten.add(tenTruong(a.getSchoolId()));
            }
            return String.join(", ", ten);
        }

        /** Tập hợp tên lớp của cả phiếu. */
        String tenLopCua(Assignment a) {
            Set<String> ten = new LinkedHashSet<>();
            for (AssignmentSlot s : slotsCua(a, false)) {
                Integer id = s.getClassId() != null ? s.getClassId() : a.getClassId();
                if (id != null && clazz.get(id) != null) {
                    ten.add(clazz.get(id));
                }
            }
            if (ten.isEmpty() && a.getClassId() != null && clazz.get(a.getClassId()) != null) {
                ten.add(clazz.get(a.getClassId()));
            }
            return String.join(", ", ten);
        }
    }

    /**
     * Tên trường theo Id — đường lẻ, chỉ dùng cho màn CHI TIẾT một phiếu (lời mời).
     *
     * <p>Danh sách thì đi qua {@link TenGoi}; ở đây một phiếu một lần nên tra lẻ là đủ.
     * Trường có thể ĐÃ BỊ XÓA MỀM — vẫn trả tên ra, giấu đi thì cột "Trường" thành trống.
     */
    private String schoolNameById(Integer id) {
        return id == null ? null : schoolRepo.findById(id).map(School::getName).orElse("(Trường #" + id + ")");
    }

    /** Tên trường của MỘT TIẾT: trường của ô lịch (V27) trước, trường cấp phiếu sau. */
    private String schoolNameOfSlot(AssignmentSlot slot, Assignment a, Map<Integer, String> cache) {
        Integer schoolId = slot.getSchoolId() != null ? slot.getSchoolId() : a.getSchoolId();
        return schoolId == null ? null : cache.computeIfAbsent(schoolId, this::schoolNameById);
    }

    /** Nạp túi tên cho một danh sách phiếu. */
    private TenGoi napTenGoi(List<Assignment> items) {
        TenGoi t = new TenGoi();
        for (Teacher x : teacherRepo.findAll()) {
            t.teacher.put(x.getId(), fullName(x));
        }
        for (Subject x : subjectRepo.findAll()) {
            t.subject.put(x.getId(), x.getName());
        }
        for (School x : schoolRepo.findAll()) {
            t.school.put(x.getId(), x.getName());
        }
        for (SchoolClass x : classRepo.findAll()) {
            t.clazz.put(x.getId(), x.getName());
        }
        for (Period x : periodRepo.findAll()) {
            t.period.put(x.getId(), x);
        }
        t.sessionIndex.napTruoc(t.period.values());
        if (!items.isEmpty()) {
            List<Integer> ids = items.stream().map(Assignment::getId).toList();
            for (AssignmentSlot s : BatchIn.theoLo(ids, slotRepo::findByAssignmentIdIn)) {
                t.slotsByAssignment
                        .computeIfAbsent(s.getAssignmentId(), k -> new ArrayList<>())
                        .add(s);
            }
        }
        return t;
    }

    /** Dựng response cho MỘT phiếu lẻ (chi tiết, tạo, sửa, duyệt...). */
    private AssignmentResponse toResponse(Assignment a) {
        return toResponse(a, napTenGoi(List.of(a)), false);
    }

    /**
     * @param includeDeletedSlots true = lấy cả ô lịch đã xóa mềm (dùng cho thùng rác, nơi
     *     Assignment và ô lịch đều đã bị gắn cờ deleted nhưng vẫn cần hiển thị các tiết/tuần).
     */
    private AssignmentResponse toResponse(Assignment a, TenGoi ten, boolean includeDeletedSlots) {
        String teacherName = ten.teacher.getOrDefault(a.getTeacherId(), "(GV #" + a.getTeacherId() + ")");
        String subjectName = ten.subject.getOrDefault(a.getSubjectId(), "(Môn #" + a.getSubjectId() + ")");

        // Một phân công nay trải nhiều lớp (mỗi tiết một lớp) → cột "Lớp" ở danh sách là tập
        // hợp các lớp KHÁC NHAU của các tiết, giữ thứ tự xuất hiện. LinkedHashSet vừa khử
        // trùng vừa giữ thứ tự. Y hệt cho TRƯỜNG (V27).
        Set<String> classNames = new LinkedHashSet<>();
        Set<String> schoolNames = new LinkedHashSet<>();
        List<AssignmentSlotResponse> slots = new ArrayList<>();
        for (AssignmentSlot slot : ten.slotsCua(a, includeDeletedSlots)) {
            Period p = ten.period.get(slot.getPeriodId());
            Integer classId = slot.getClassId() != null ? slot.getClassId() : a.getClassId();
            String slotClassName = classId == null ? null : ten.clazz.get(classId);
            if (slotClassName != null) {
                classNames.add(slotClassName);
            }
            String slotSchoolName = ten.tenTruongCuaO(slot, a);
            if (slotSchoolName != null) {
                schoolNames.add(slotSchoolName);
            }
            slots.add(
                    AssignmentSlotResponse.fromEntity(slot, p, slotClassName, slotSchoolName, ten.sessionIndex.of(p)));
        }
        String className = classNames.isEmpty()
                ? (a.getClassId() == null ? null : ten.clazz.get(a.getClassId()))
                : String.join(", ", classNames);
        String schoolName = schoolNames.isEmpty() ? ten.tenTruong(a.getSchoolId()) : String.join(", ", schoolNames);
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
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Thứ trong tuần không hợp lệ.");
        };
    }
}
