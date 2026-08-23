package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BatchIn;
import com.kdc.tsdms.common.SearchText;
import com.kdc.tsdms.dto.MyScheduleFilters;
import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.ScheduleEventResponse;
import com.kdc.tsdms.dto.ScheduleFilterOptions;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.HolidayRepository;
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
import java.util.Objects;
import java.util.Set;
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
    private final HolidayRepository holidayRepo;

    public ScheduleService(
            ScheduleRepository scheduleRepo,
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            PeriodRepository periodRepo,
            HolidayRepository holidayRepo) {
        this.scheduleRepo = scheduleRepo;
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.periodRepo = periodRepo;
        this.holidayRepo = holidayRepo;
    }

    /**
     * Khoảng ngày dài nhất một lần gọi được phép hỏi.
     *
     * <p>Lịch dạy là màn hình LỊCH — nó luôn hiển thị một tuần hoặc một lưới tháng, tức là
     * nhiều nhất 42 ngày. Trần 45 ngày chừa đúng một chút chỗ xoay.
     *
     * <p>Vì sao phải có trần: {@code ?from=2025-09-01&to=2027-06-30} gom trọn 86.745 buổi
     * dạy, dựng từng ấy đối tượng trong bộ nhớ rồi đổ ra JSON vài chục megabyte — không cần
     * sửa dòng code nào, chỉ cần sửa URL trên thanh địa chỉ. Đo thực tế: 45 ngày trả 1,5 MB
     * trong 0,5 giây, còn 100 ngày đã là 8,3 MB và 1,5 giây.
     */
    private static final int SO_NGAY_TOI_DA = 45;

    /** Khoảng ngày phải có, phải thuận chiều, và không dài quá {@link #SO_NGAY_TOI_DA}. */
    private static void assertKhoangNgayHopLe(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn khoảng ngày cần xem.");
        }
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > SO_NGAY_TOI_DA) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ xem được tối đa " + SO_NGAY_TOI_DA + " ngày một lần. Hãy thu hẹp khoảng ngày.");
        }
    }

    /**
     * Lớp THẬT của một buổi dạy: lấy theo ô lịch gốc đã sinh ra buổi ({@code SourceSlotId})
     * — từ V16 mỗi tiết mang lớp riêng, nên KHÔNG được đọc {@code Assignment.ClassId} nữa
     * (đọc ở đó thì cả 3 tiết sáng đều hiện cùng một lớp). Fallback về lớp cấp phân công
     * chỉ dành cho dữ liệu cũ chưa có slot/lớp.
     */
    private Integer classIdOf(Schedule s, Assignment a, Map<Integer, AssignmentSlot> slotCache) {
        if (s.getSourceSlotId() != null) {
            AssignmentSlot slot = slotCache.get(s.getSourceSlotId());
            if (slot != null && slot.getClassId() != null) {
                return slot.getClassId();
            }
        }
        return a.getClassId();
    }

    /**
     * Trường THẬT của một buổi dạy — cùng lý lẽ với {@link #classIdOf}: từ V27 mỗi tiết mang
     * trường riêng, nên đọc {@code Assignment.SchoolId} sẽ dán nhầm tên trường lên những buổi
     * mà giáo viên chạy sang trường khác. Fallback về trường cấp phân công cho dữ liệu cũ.
     */
    private Integer schoolIdOf(Schedule s, Assignment a, Map<Integer, AssignmentSlot> slotCache) {
        if (s.getSourceSlotId() != null) {
            AssignmentSlot slot = slotCache.get(s.getSourceSlotId());
            if (slot != null && slot.getSchoolId() != null) {
                return slot.getSchoolId();
            }
        }
        return a.getSchoolId();
    }

    /**
     * Buổi dạy trong khoảng ngày.
     *
     * @param status trạng thái cần xem; null/rỗng = {@value #APPROVED}. Điều phối viên cần
     *     nhìn được cả buổi PENDING ("tuần sau còn 12 buổi chưa ai xác nhận") ngay trên lịch,
     *     nhưng mặc định vẫn chỉ hiện buổi đã duyệt — lịch là thứ đã chốt.
     * @param keyword lọc theo tên GV / trường / lớp / môn, bỏ dấu vẫn khớp. Lọc Ở ĐÂY chứ
     *     không để trình duyệt tự lọc sau khi tải: một tháng của trung tâm là vài nghìn buổi,
     *     gửi hết về rồi giấu đi 99% là trả giá đường truyền cho thứ người dùng không nhìn.
     */
    @Transactional(readOnly = true)
    public List<ScheduleEventResponse> list(
            LocalDate from,
            LocalDate to,
            Integer teacherId,
            Integer schoolId,
            Integer classId,
            String status,
            String keyword) {
        assertKhoangNgayHopLe(from, to);
        Integer effectiveTeacher = scopedTeacherId(teacherId);
        String wanted =
                status == null || status.isBlank() ? APPROVED : status.trim().toUpperCase();

        List<Schedule> schedules = effectiveTeacher != null
                ? scheduleRepo.findByTeacherIdAndStatusAndStartTimeBetweenAndDeletedFalseOrderByStartTime(
                        effectiveTeacher, wanted, from.atStartOfDay(), to.atTime(LocalTime.MAX))
                : scheduleRepo.findByStartTimeBetweenAndStatusAndDeletedFalseOrderByStartTime(
                        from.atStartOfDay(), to.atTime(LocalTime.MAX), wanted);
        List<ScheduleEventResponse> events = buildEvents(schedules, schoolId, classId);
        String kw = SearchText.normalize(keyword);
        if (kw.isEmpty()) {
            return events;
        }
        return events.stream()
                .filter(e -> SearchText.matchesAny(kw, e.teacherName, e.schoolName, e.className, e.subjectName))
                .toList();
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

    /**
     * Ghép các buổi dạy thành sự kiện lịch (tên GV/trường/lớp/môn/tiết), lọc theo trường/lớp.
     *
     * <p>Mọi thứ cần tra đều NẠP SẴN trước vòng lặp. Bản cũ có cache nhưng để nó tự lười nạp,
     * mà khóa cache là {@code SourceSlotId} — mỗi buổi dạy sinh ra từ một ô lịch RIÊNG nên
     * cache không bao giờ trúng. Một tuần 1.575 buổi thành hơn 1.700 câu SQL và 3,6 giây.
     *
     * <p>Các bảng tra cứu (giáo viên, trường, lớp, môn, tiết) đều nhỏ và ổn định nên nạp trọn;
     * riêng phân công và ô lịch chỉ nạp đúng những dòng mà khoảng ngày này đụng tới.
     */
    private List<ScheduleEventResponse> buildEvents(List<Schedule> schedules, Integer schoolId, Integer classId) {
        Map<Integer, Assignment> aCache = new HashMap<>();
        Map<Integer, String> teacherName = new HashMap<>();
        Map<Integer, School> schoolCache = new HashMap<>();
        Map<Integer, SchoolClass> classCache = new HashMap<>();
        Map<Integer, Subject> subjectCache = new HashMap<>();
        Map<Integer, Period> periodCache = new HashMap<>();
        Map<Integer, AssignmentSlot> slotCache = new HashMap<>();
        PeriodSessionIndex sessionIndex = new PeriodSessionIndex(periodRepo);

        if (!schedules.isEmpty()) {
            Set<Integer> assignmentIds = schedules.stream()
                    .map(Schedule::getAssignmentId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            for (Assignment x : BatchIn.theoLo(assignmentIds, assignmentRepo::findAllById)) {
                aCache.put(x.getId(), x);
            }
            Set<Integer> slotIds = schedules.stream()
                    .map(Schedule::getSourceSlotId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            for (AssignmentSlot x : BatchIn.theoLo(slotIds, slotRepo::findAllById)) {
                slotCache.put(x.getId(), x);
            }
            for (Teacher x : teacherRepo.findAll()) {
                teacherName.put(x.getId(), fullName(x));
            }
            for (School x : schoolRepo.findAll()) {
                schoolCache.put(x.getId(), x);
            }
            for (SchoolClass x : classRepo.findAll()) {
                classCache.put(x.getId(), x);
            }
            for (Subject x : subjectRepo.findAll()) {
                subjectCache.put(x.getId(), x);
            }
            for (Period x : periodRepo.findAll()) {
                periodCache.put(x.getId(), x);
            }
            sessionIndex.napTruoc(periodCache.values());
        }

        List<ScheduleEventResponse> out = new ArrayList<>();
        for (Schedule s : schedules) {
            Assignment a = aCache.get(s.getAssignmentId());
            if (a == null) {
                continue;
            }
            // Lọc theo trường phải hỏi TRƯỜNG CỦA BUỔI, không phải trường của phiếu: một phiếu
            // nay trải nhiều trường nên so với phiếu sẽ kéo theo cả buổi ở trường khác.
            Integer eventSchoolId = schoolIdOf(s, a, slotCache);
            if (schoolId != null && !schoolId.equals(eventSchoolId)) {
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
            e.teacherName = teacherName.getOrDefault(s.getTeacherId(), "(GV #" + s.getTeacherId() + ")");
            e.schoolId = eventSchoolId;
            School school = schoolCache.get(eventSchoolId);
            e.schoolName = school != null ? school.getName() : "(Trường #" + eventSchoolId + ")";
            e.classId = eventClassId;
            if (eventClassId != null) {
                SchoolClass c = classCache.get(eventClassId);
                e.className = c != null ? c.getName() : null;
            }
            e.subjectId = a.getSubjectId();
            Subject subj = subjectCache.get(a.getSubjectId());
            e.subjectName = subj != null ? subj.getName() : "(Môn #" + a.getSubjectId() + ")";
            if (s.getPeriodId() != null) {
                Period p = periodCache.get(s.getPeriodId());
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
        List<OptionItem> schools = schoolRepo.findByDeletedFalseOrderByNameAsc().stream()
                .map(s -> new OptionItem(s.getId(), s.getName()))
                .toList();
        return new ScheduleFilterOptions(teachers, schools);
    }

    /**
     * Lớp của một trường (dropdown lọc cấp 2).
     *
     * <p>Lọc bằng câu WHERE chứ không {@code findAll()} rồi lọc trong Java: bản cũ nạp cả
     * gần 400 lớp của mọi trường về bộ nhớ mỗi lần người dùng đổi dropdown, để lấy ra hơn
     * chục dòng.
     */
    @Transactional(readOnly = true)
    public List<OptionItem> classesOf(Integer schoolId) {
        return classRepo.findBySchoolIdAndDeletedFalseOrderByName(schoolId).stream()
                .map(c -> new OptionItem(c.getId(), c.getName()))
                .toList();
    }

    /**
     * Ngày nghỉ chạm vào khoảng đang xem, để lịch tô màu và ghi tên kỳ nghỉ.
     *
     * <p>Không có nó thì ngày 2/9 trên lịch trông y hệt một ngày bị quên xếp lịch — người
     * dùng không phân biệt được "trường đóng cửa" với "ai đó làm thiếu việc".
     */
    @Transactional(readOnly = true)
    public List<HolidayMark> holidaysIn(LocalDate from, LocalDate to, Integer schoolId) {
        List<HolidayMark> out = new ArrayList<>();
        for (Holiday h : holidayRepo.findOverlapping(from, to)) {
            // schoolId null = nghỉ toàn hệ thống, luôn tính. Có giá trị = chỉ trường đó nghỉ,
            // nên chỉ đánh dấu khi người dùng đang xem đúng trường ấy (hoặc xem tất cả).
            if (h.getSchoolId() != null && schoolId != null && !h.getSchoolId().equals(schoolId)) {
                continue;
            }
            out.add(new HolidayMark(h.getId(), h.getFromDate(), h.getToDate(), h.getName(), h.getKind()));
        }
        return out;
    }

    /** Một khoảng ngày nghỉ, đủ để frontend tô lịch. */
    public record HolidayMark(Integer id, LocalDate fromDate, LocalDate toDate, String name, String kind) {}

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
            // Theo TRƯỜNG CỦA BUỔI: giáo viên dạy nhiều trường trong cùng một phiếu thì bộ lọc
            // phải liệt kê đủ, và lớp phải gắn đúng trường của nó (FE lọc lớp theo schoolId).
            Integer eventSchoolId = schoolIdOf(s, a, slotCache);
            schools.computeIfAbsent(
                    eventSchoolId,
                    id -> schoolRepo.findById(id).map(School::getName).orElse("(Trường #" + id + ")"));
            Integer eventClassId = classIdOf(s, a, slotCache);
            if (eventClassId != null) {
                classes.computeIfAbsent(eventClassId, id -> {
                    String name =
                            classRepo.findById(id).map(SchoolClass::getName).orElse("(Lớp #" + id + ")");
                    return new MyScheduleFilters.ClassOption(id, name, eventSchoolId);
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
        if (SecurityUtils.isCentreStaff()) {
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
