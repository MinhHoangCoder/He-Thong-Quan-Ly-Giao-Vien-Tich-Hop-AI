package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.HolidayAbsenceResponse;
import com.kdc.tsdms.dto.HolidayFixAbsencesRequest;
import com.kdc.tsdms.dto.HolidayImpactResponse;
import com.kdc.tsdms.dto.HolidayRequest;
import com.kdc.tsdms.dto.HolidayResponse;
import com.kdc.tsdms.dto.PayrollHolidayIssueResponse;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LỊCH NGHỈ — ngày lễ và kỳ nghỉ mà hệ thống KHÔNG sinh buổi dạy (bảng Holiday, Flyway V29).
 *
 * <p>Ba việc tách bạch:
 *
 * <ul>
 *   <li><b>Khai báo</b> kỳ nghỉ — ảnh hưởng tới lịch sinh RA SAU đó
 *       ({@code AssignmentService.generateSchedules} hỏi bảng này mỗi lần trải ô thời khóa biểu).
 *   <li><b>Dọn buổi CHƯA diễn ra</b> đã sinh trước khi khai báo — không tự động, phải bấm. Xem
 *       {@link #impact(Integer)} và {@link #cancelSessions(Integer)}.
 *   <li><b>Sửa hậu quả của buổi ĐÃ diễn ra</b> — buổi "ma" đã qua thì {@code
 *       AttendanceSweepService} đã kịp ghi VẮNG cho giáo viên, và hủy buổi KHÔNG xóa dòng vắng
 *       đó. Xem {@link #absences(Integer)} và {@link #fixAbsences(Integer,
 *       HolidayFixAbsencesRequest)}.
 * </ul>
 *
 * <p>Vì sao không tự dọn: hủy hàng loạt buổi dạy là việc khó lùi lại, và một kỳ nghỉ nhập sai
 * ngày (gõ nhầm năm) sẽ quét sạch lịch mà không ai kịp nhìn. Cho người dùng thấy con số rồi
 * tự quyết.
 */
@Service
public class HolidayService {

    /** Kỳ nghỉ dài hơn ngần này gần như chắc chắn là gõ nhầm ngày, không phải nghỉ thật. */
    private static final int MAX_DAYS = 120;

    private final HolidayRepository holidayRepo;
    private final SchoolRepository schoolRepo;
    private final ScheduleRepository scheduleRepo;
    private final AssignmentSlotRepository slotRepo;
    private final AttendanceRepository attendanceRepo;
    private final TeacherRepository teacherRepo;
    private final AttendanceService attendanceService;
    private final NotificationService notificationService;

    public HolidayService(
            HolidayRepository holidayRepo,
            SchoolRepository schoolRepo,
            ScheduleRepository scheduleRepo,
            AssignmentSlotRepository slotRepo,
            AttendanceRepository attendanceRepo,
            TeacherRepository teacherRepo,
            AttendanceService attendanceService,
            NotificationService notificationService) {
        this.holidayRepo = holidayRepo;
        this.schoolRepo = schoolRepo;
        this.scheduleRepo = scheduleRepo;
        this.slotRepo = slotRepo;
        this.attendanceRepo = attendanceRepo;
        this.teacherRepo = teacherRepo;
        this.attendanceService = attendanceService;
        this.notificationService = notificationService;
    }

    /* ─────────────────────────── ĐỌC ─────────────────────────── */

    @Transactional(readOnly = true)
    public Page<HolidayResponse> search(
            String keyword, String kind, LocalDate from, LocalDate to, Integer schoolId, Pageable pageable) {
        Page<Holiday> page = holidayRepo.search(blankToNull(keyword), blankToNull(kind), from, to, schoolId, pageable);
        Map<Integer, String> schoolNames = schoolNameCache(page.getContent());
        return page.map(h -> HolidayResponse.fromEntity(h, schoolNames.get(h.getSchoolId())));
    }

    @Transactional(readOnly = true)
    public HolidayResponse getById(Integer id) {
        Holiday h = getOrThrow(id);
        return HolidayResponse.fromEntity(h, schoolNameOf(h.getSchoolId()));
    }

    /* ─────────────────────────── GHI ─────────────────────────── */

    @Transactional
    public HolidayResponse create(HolidayRequest req) {
        Holiday h = new Holiday();
        apply(h, req);
        h.setCreatedBy(SecurityUtils.currentUserId());
        return HolidayResponse.fromEntity(holidayRepo.save(h), schoolNameOf(h.getSchoolId()));
    }

    @Transactional
    public HolidayResponse update(Integer id, HolidayRequest req) {
        Holiday h = getOrThrow(id);
        apply(h, req);
        h.setUpdatedAt(Instant.now());
        h.setUpdatedBy(SecurityUtils.currentUserId());
        return HolidayResponse.fromEntity(holidayRepo.save(h), schoolNameOf(h.getSchoolId()));
    }

    /**
     * Xóa mềm. KHÔNG dựng lại các buổi dạy đã hủy theo kỳ nghỉ này: buổi đã hủy có thể đã được
     * xếp bù bằng phiếu khác, hồi sinh hàng loạt sẽ đẻ ra trùng lịch.
     */
    @Transactional
    public void delete(Integer id) {
        Holiday h = getOrThrow(id);
        h.setDeleted(true);
        h.setDeletedAt(Instant.now());
        h.setDeletedBy(SecurityUtils.currentUserId());
        holidayRepo.save(h);
    }

    /* ──────────────── DỌN BUỔI DẠY ĐÃ SINH TRƯỚC ĐÓ ──────────────── */

    /** Đếm buổi dạy đang rơi vào kỳ nghỉ — để màn hình hỏi trước khi hủy. */
    @Transactional(readOnly = true)
    public HolidayImpactResponse impact(Integer id) {
        Holiday h = getOrThrow(id);
        List<Schedule> affected = affectedSchedules(h);
        LocalDateTime now = BusinessTime.now();

        List<Schedule> future =
                affected.stream().filter(s -> s.getStartTime().isAfter(now)).toList();
        int past = affected.size() - future.size();

        Set<Integer> teachers = new HashSet<>();
        LocalDate first = null;
        LocalDate last = null;
        for (Schedule s : future) {
            teachers.add(s.getTeacherId());
            LocalDate d = s.getStartTime().toLocalDate();
            if (first == null || d.isBefore(first)) {
                first = d;
            }
            if (last == null || d.isAfter(last)) {
                last = d;
            }
        }
        return new HolidayImpactResponse(future.size(), teachers.size(), first, last, past);
    }

    /**
     * Hủy các buổi CHƯA diễn ra rơi vào kỳ nghỉ.
     *
     * <p>Cố ý không đụng buổi đã qua: chúng có thể đã gắn dòng chấm công và đã vào bảng lương
     * của kỳ trước. Hủy chúng là sửa lại quá khứ và làm lệch số tiền đã trả.
     *
     * @return số buổi đã hủy
     */
    @Transactional
    public int cancelSessions(Integer id) {
        Holiday h = getOrThrow(id);
        LocalDateTime now = BusinessTime.now();
        Integer userId = SecurityUtils.currentUserId();
        int count = 0;
        for (Schedule s : affectedSchedules(h)) {
            if (!s.getStartTime().isAfter(now)) {
                continue;
            }
            s.setStatus("CANCELLED");
            s.setUpdatedAt(Instant.now());
            s.setUpdatedBy(userId);
            scheduleRepo.save(s);
            count++;
        }
        return count;
    }

    /* ──────────────── SỬA DÒNG VẮNG GIẢ CỦA BUỔI ĐÃ QUA ──────────────── */

    /**
     * Các dòng chấm công VẮNG do job nền tự ghi cho buổi rơi vào kỳ nghỉ này.
     *
     * <p>{@link #cancelSessions(Integer)} chỉ cứu được buổi CHƯA diễn ra. Buổi đã qua thì
     * {@code AttendanceSweepService} ghi Vắng mất rồi, và dòng vắng đó KHÔNG biến mất khi buổi
     * bị hủy — nó nằm lại trong hồ sơ chuyên cần của giáo viên, còn job thì đã kịp nhắn cho họ
     * là "bạn vắng buổi này".
     *
     * <p>Chỉ lấy dòng nguồn SYSTEM: dòng kế toán ghi tay là một phán quyết có người chịu trách
     * nhiệm (giáo viên vẫn phải dạy bù hôm đó mà bỏ), không được đè lên.
     */
    @Transactional(readOnly = true)
    public HolidayAbsenceResponse absences(Integer id) {
        Holiday h = getOrThrow(id);
        List<Attendance> candidates =
                scopeFilter(h, attendanceRepo.findSystemAbsencesBetween(h.getFromDate(), h.getToDate()));

        Map<Integer, String> names = teacherNames(candidates);
        List<HolidayAbsenceResponse.Row> rows = new ArrayList<>();
        int lockedCount = 0;
        Set<String> lockedPeriods = new LinkedHashSet<>();

        for (Attendance a : candidates) {
            // Kỳ lương đã chốt thì mọi thao tác ghi lên chấm công bị chặn (assertPeriodOpen).
            // Tách ra báo riêng, thay vì để người dùng bấm rồi ăn lỗi 409 không hiểu vì sao.
            if (attendanceService.isPeriodLocked(a.getTeacherId(), a.getWorkDate())) {
                lockedCount++;
                lockedPeriods.add(
                        a.getWorkDate().getMonthValue() + "/" + a.getWorkDate().getYear());
                continue;
            }
            rows.add(new HolidayAbsenceResponse.Row(
                    a.getId(),
                    a.getTeacherId(),
                    names.getOrDefault(a.getTeacherId(), "(GV #" + a.getTeacherId() + ")"),
                    a.getWorkDate(),
                    a.getScheduleId(),
                    a.getNote()));
        }
        return new HolidayAbsenceResponse(rows, lockedCount, new ArrayList<>(lockedPeriods));
    }

    /**
     * Chuyển các dòng Vắng đã chọn sang NGHỈ PHÉP.
     *
     * <p>Vì sao là Nghỉ phép chứ không phải Có mặt: buổi đó KHÔNG diễn ra. Đánh Có mặt là khai
     * khống một tiết dạy và cộng thêm tiền cho buổi chưa từng tồn tại (PayrollService trả tiền
     * theo dòng PRESENT/LATE). Nghỉ phép cũng không được tính tiết nên số tiền giữ nguyên —
     * việc này chỉ làm sạch hồ sơ chuyên cần, không đụng tới lương.
     *
     * <p>Danh sách id gửi lên được LỌC LẠI theo đúng luật của {@link #absences(Integer)} chứ
     * không tin thẳng: id có thể đã cũ (kỳ lương vừa bị chốt trong lúc người dùng đang xem)
     * hoặc bị sửa tay để lôi vào một dòng chấm công không liên quan.
     *
     * @return số dòng đã sửa
     */
    @Transactional
    public int fixAbsences(Integer id, HolidayFixAbsencesRequest req) {
        Holiday h = getOrThrow(id);
        Set<Long> allowed = absences(id).rows().stream()
                .map(HolidayAbsenceResponse.Row::attendanceId)
                .collect(Collectors.toSet());

        Integer userId = SecurityUtils.currentUserId();
        String reason = req.reason().trim();
        Map<Integer, Integer> fixedByTeacher = new LinkedHashMap<>();
        int fixed = 0;

        for (Long attendanceId : req.attendanceIds()) {
            if (attendanceId == null || !allowed.contains(attendanceId)) {
                continue;
            }
            Attendance a = attendanceRepo.findById(attendanceId).orElse(null);
            if (a == null) {
                continue;
            }
            a.setStatus("LEAVE");
            a.setAdjustReason(reason);
            a.setUpdatedBy(userId);
            a.setUpdatedAt(Instant.now());
            // Trigger TR_Attendance_ChangeLog (V24) tự ghi vết theo UpdatedBy — không cần
            // chép tay vào AttendanceChangeLog ở đây.
            attendanceRepo.save(a);
            fixedByTeacher.merge(a.getTeacherId(), 1, Integer::sum);
            fixed++;
        }

        // Job nền đã nhắn cho giáo viên "buổi dạy chưa được chấm công". Không đóng lại vòng
        // thông tin đó thì người bị ghi oan không bao giờ biết là đã được xử lý.
        fixedByTeacher.forEach((teacherId, count) -> notificationService.publishToTeacher(
                teacherId,
                "Đã điều chỉnh chấm công ngày nghỉ",
                count + " buổi bị ghi Vắng nhầm trong kỳ nghỉ " + h.getName()
                        + " đã được chuyển thành Nghỉ phép — hôm đó trường không hoạt động.",
                "ATTENDANCE",
                "Attendance",
                null,
                false));
        return fixed;
    }

    /* ──────────────── CẢNH BÁO CHO BẢNG LƯƠNG ──────────────── */

    /**
     * Kỳ lương {@code month/year} còn dòng Vắng nào rơi vào ngày nghỉ không.
     *
     * <p>Dùng cho cảnh báo TRƯỚC khi chốt lương: chốt xong là chấm công của kỳ bị khóa, tức là
     * khóa luôn lỗi vào trong. Trả kèm id kỳ nghỉ để màn hình trỏ thẳng người dùng sang chỗ
     * sửa — phát hiện mà không chỉ được đường sửa thì cảnh báo chỉ làm người ta bực.
     */
    @Transactional(readOnly = true)
    public PayrollHolidayIssueResponse holidayIssues(short year, short month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        List<Holiday> holidays = holidayRepo.findOverlapping(from, to);
        List<Attendance> absences = holidays.isEmpty() ? List.of() : attendanceRepo.findSystemAbsencesBetween(from, to);
        if (absences.isEmpty()) {
            return new PayrollHolidayIssueResponse(0, 0, List.of());
        }
        Map<Long, Integer> schoolByAttendance = schoolOfAttendances(absences);

        Map<Integer, Integer> countByHoliday = new LinkedHashMap<>();
        Map<Integer, String> nameByHoliday = new HashMap<>();
        Set<Integer> teachers = new HashSet<>();
        int total = 0;

        for (Attendance a : absences) {
            Integer schoolId = schoolByAttendance.get(a.getId());
            Holiday hit = holidays.stream()
                    .filter(h -> h.getSchoolId() == null || h.getSchoolId().equals(schoolId))
                    .filter(h -> !a.getWorkDate().isBefore(h.getFromDate())
                            && !a.getWorkDate().isAfter(h.getToDate()))
                    .findFirst()
                    .orElse(null);
            if (hit == null) {
                continue; // dòng vắng thật, không dính ngày nghỉ
            }
            countByHoliday.merge(hit.getId(), 1, Integer::sum);
            nameByHoliday.putIfAbsent(hit.getId(), hit.getName());
            teachers.add(a.getTeacherId());
            total++;
        }

        List<PayrollHolidayIssueResponse.HolidayRef> refs = countByHoliday.entrySet().stream()
                .map(e -> new PayrollHolidayIssueResponse.HolidayRef(
                        e.getKey(), nameByHoliday.get(e.getKey()), e.getValue()))
                .toList();
        return new PayrollHolidayIssueResponse(total, teachers.size(), refs);
    }

    /* ─────────────────────────── PRIVATE ─────────────────────────── */

    /**
     * Buổi dạy còn hiệu lực nằm trong khoảng ngày của kỳ nghỉ, đã lọc theo phạm vi trường.
     *
     * <p>Trường của một buổi lấy từ Ô THỜI KHÓA BIỂU sinh ra nó (V27), không phải trường cấp
     * phiếu: một phiếu nay trải được nhiều trường, mà kỳ nghỉ riêng chỉ thuộc về một trường.
     */
    private List<Schedule> affectedSchedules(Holiday h) {
        LocalDateTime from = h.getFromDate().atStartOfDay();
        LocalDateTime to = h.getToDate().plusDays(1).atStartOfDay();
        List<Schedule> inRange = scheduleRepo.findByStartTimeBetweenAndDeletedFalse(from, to).stream()
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .toList();
        if (h.getSchoolId() == null || inRange.isEmpty()) {
            return inRange;
        }
        Map<Integer, Integer> schoolBySlot = new HashMap<>();
        for (AssignmentSlot slot : slotRepo.findAllById(inRange.stream()
                .map(Schedule::getSourceSlotId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList())) {
            schoolBySlot.put(slot.getId(), slot.getSchoolId());
        }
        List<Schedule> out = new ArrayList<>();
        for (Schedule s : inRange) {
            Integer schoolId = s.getSourceSlotId() == null ? null : schoolBySlot.get(s.getSourceSlotId());
            if (h.getSchoolId().equals(schoolId)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Giữ lại các dòng chấm công thuộc phạm vi trường của kỳ nghỉ (null = toàn hệ thống). */
    private List<Attendance> scopeFilter(Holiday h, List<Attendance> rows) {
        if (h.getSchoolId() == null || rows.isEmpty()) {
            return rows;
        }
        Map<Long, Integer> schoolByAttendance = schoolOfAttendances(rows);
        List<Attendance> out = new ArrayList<>();
        for (Attendance a : rows) {
            if (h.getSchoolId().equals(schoolByAttendance.get(a.getId()))) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * attendanceId → trường của buổi dạy tương ứng.
     *
     * <p>Đi đường buổi dạy → Ô THỜI KHÓA BIỂU sinh ra nó (V27), cùng lý do với {@link
     * #affectedSchedules(Holiday)}: một phiếu trải được nhiều trường nên trường cấp phiếu
     * không nói lên buổi đó thuộc trường nào.
     */
    private Map<Long, Integer> schoolOfAttendances(List<Attendance> rows) {
        List<Long> scheduleIds = rows.stream()
                .map(Attendance::getScheduleId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Integer> slotBySchedule = new HashMap<>();
        for (Schedule s : scheduleRepo.findAllById(scheduleIds)) {
            if (s.getSourceSlotId() != null) {
                slotBySchedule.put(s.getId(), s.getSourceSlotId());
            }
        }
        Map<Integer, Integer> schoolBySlot = new HashMap<>();
        for (AssignmentSlot slot :
                slotRepo.findAllById(slotBySchedule.values().stream().distinct().toList())) {
            schoolBySlot.put(slot.getId(), slot.getSchoolId());
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Attendance a : rows) {
            Integer slotId = slotBySchedule.get(a.getScheduleId());
            out.put(a.getId(), slotId == null ? null : schoolBySlot.get(slotId));
        }
        return out;
    }

    private Map<Integer, String> teacherNames(List<Attendance> rows) {
        List<Integer> ids =
                rows.stream().map(Attendance::getTeacherId).distinct().toList();
        Map<Integer, String> out = new HashMap<>();
        for (Teacher t : teacherRepo.findAllById(ids)) {
            out.put(t.getId(), (t.getLastName() + " " + t.getFirstName()).trim());
        }
        return out;
    }

    private void apply(Holiday h, HolidayRequest req) {
        if (req.toDate().isBefore(req.fromDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(req.fromDate(), req.toDate()) + 1;
        if (days > MAX_DAYS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Kỳ nghỉ dài " + days + " ngày — vượt mức cho phép " + MAX_DAYS
                            + " ngày. Kiểm tra lại năm của ngày kết thúc.");
        }
        if (req.schoolId() != null
                && schoolRepo
                        .findById(req.schoolId())
                        .filter(s -> !s.isDeleted())
                        .isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy trường đã chọn.");
        }
        h.setFromDate(req.fromDate());
        h.setToDate(req.toDate());
        h.setName(req.name().trim());
        h.setKind(req.kind() != null ? req.kind() : "NATIONAL");
        h.setSchoolId(req.schoolId());
        h.setNote(blankToNull(req.note()));
    }

    private Holiday getOrThrow(Integer id) {
        return holidayRepo
                .findById(id)
                .filter(h -> !h.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ nghỉ id=" + id));
    }

    private Map<Integer, String> schoolNameCache(List<Holiday> holidays) {
        List<Integer> ids = holidays.stream()
                .map(Holiday::getSchoolId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, String> out = new HashMap<>();
        for (School s : schoolRepo.findAllById(ids)) {
            out.put(s.getId(), s.getName());
        }
        return out;
    }

    private String schoolNameOf(Integer schoolId) {
        if (schoolId == null) {
            return null;
        }
        return schoolRepo.findById(schoolId).map(School::getName).orElse(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
