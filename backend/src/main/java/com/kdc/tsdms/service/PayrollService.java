package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.PayrollChangeLogResponse;
import com.kdc.tsdms.dto.PayrollHolidayIssueResponse;
import com.kdc.tsdms.dto.PayrollResponse;
import com.kdc.tsdms.dto.PayrollUpdateRequest;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Payroll;
import com.kdc.tsdms.entity.PayrollChangeLog;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.PayrollChangeLogRepository;
import com.kdc.tsdms.repository.PayrollRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Bảng lương (Payroll) — tính theo TIẾT (buổi dạy), đơn giá theo CẤP.
 *
 * <p>Trung tâm trả lương theo tiết: mỗi buổi chấm công (PRESENT/LATE) = 1 tiết. Đơn giá
 * phụ thuộc CẤP của lớp buổi đó: Tiểu học (khối 1–5) {@value #TH_RATE_STR}đ/tiết, THCS
 * (khối 6–9) {@value #THCS_RATE_STR}đ/tiết. Trung tâm CHỈ dạy khối 1–9 — không có cấp 3.
 * Cấp suy ra từ buổi → phân công → lớp → khối
 * ({@code Attendance.scheduleId → Schedule → Assignment → SchoolClass.gradeLevel}).
 *
 * <p>Vì mỗi GV chỉ dạy 1 cấp (quy ước dữ liệu) nên toàn bộ tiết của một GV cùng một đơn
 * giá → lưu {@code TaughtHours} = SỐ TIẾT, {@code RatePerHour} = ĐƠN GIÁ/TIẾT. Cột computed
 * {@code NetAmount} của DB (= base + TaughtHours×RatePerHour + phụ cấp + thưởng − khấu trừ)
 * cho ra đúng tiền lương theo tiết mà không cần đổi schema.
 */
@Service
public class PayrollService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PayrollService.class);

    static final String TH_RATE_STR = "115000";
    static final String THCS_RATE_STR = "125000";

    /** Trạng thái phiếu lương được phép cho GV tự xem (đã chốt/đã trả) — KHÔNG lộ bản nháp. */
    private static final Set<String> TEACHER_VISIBLE_STATUS = Set.of("FINALIZED", "PAID");

    /** Đơn giá 1 tiết theo cấp học. */
    private static final BigDecimal TH_RATE = new BigDecimal(TH_RATE_STR); // Tiểu học (khối 1–5)

    private static final BigDecimal THCS_RATE = new BigDecimal(THCS_RATE_STR); // THCS (khối 6–9)

    private static final Pattern DIGITS = Pattern.compile("(\\d{1,2})");

    /**
     * Mở lại được kỳ lương lùi tối đa ngần này tháng (Flyway V32).
     *
     * <p>Luật nghiệp vụ chứ không phải ràng buộc kỹ thuật: đủ rộng cho mọi sai sót thực tế
     * (lỗi ngày nghỉ luôn lộ ra trong vòng một hai kỳ), nhưng vẫn có một điểm coi là đã quyết
     * toán xong — không có mốc nào thì "sổ đã chốt" chẳng còn nghĩa gì.
     */
    private static final int REOPEN_MAX_MONTHS_BACK = 3;

    private final PayrollRepository payrollRepo;
    private final AttendanceRepository attendanceRepo;
    private final TeacherRepository teacherRepo;
    private final ScheduleRepository scheduleRepo;
    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final SchoolClassRepository classRepo;
    private final PayrollChangeLogRepository changeLogRepo;
    private final AppUserRepository userRepo;
    private final DisplayNameResolver displayNameResolver;
    private final HolidayService holidayService;
    private final EntityManager em;
    private final NotificationService notificationService;

    public PayrollService(
            PayrollRepository payrollRepo,
            AttendanceRepository attendanceRepo,
            TeacherRepository teacherRepo,
            ScheduleRepository scheduleRepo,
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            SchoolClassRepository classRepo,
            PayrollChangeLogRepository changeLogRepo,
            AppUserRepository userRepo,
            DisplayNameResolver displayNameResolver,
            HolidayService holidayService,
            EntityManager em,
            NotificationService notificationService) {
        this.payrollRepo = payrollRepo;
        this.attendanceRepo = attendanceRepo;
        this.teacherRepo = teacherRepo;
        this.scheduleRepo = scheduleRepo;
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.classRepo = classRepo;
        this.changeLogRepo = changeLogRepo;
        this.userRepo = userRepo;
        this.displayNameResolver = displayNameResolver;
        this.holidayService = holidayService;
        this.em = em;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<PayrollResponse> list(short year, short month) {
        Map<Integer, String> cache = new HashMap<>();
        return payrollRepo.findByPeriodYearAndPeriodMonthOrderByTeacherId(year, month).stream()
                .map(p -> PayrollResponse.fromEntity(p, teacherName(p.getTeacherId(), cache)))
                .toList();
    }

    /**
     * Phiếu lương của CHÍNH giáo viên đang đăng nhập (read-only). KHÔNG nhận teacherId từ ngoài
     * (chống IDOR) và CHỈ trả phiếu đã chốt/đã trả — số nháp (DRAFT) không bao giờ lộ cho GV.
     *
     * @param year năm cần xem
     * @param month tháng cụ thể (null = cả năm, mới nhất trước)
     */
    @Transactional(readOnly = true)
    public List<PayrollResponse> listMine(short year, Short month) {
        Integer teacherId = currentTeacherId();
        String name =
                teacherRepo.findById(teacherId).map(PayrollService::fullName).orElse("(GV #" + teacherId + ")");
        List<Payroll> items = month != null
                ? payrollRepo
                        .findByTeacherIdAndPeriodYearAndPeriodMonth(teacherId, year, month)
                        .map(List::of)
                        .orElseGet(List::of)
                : payrollRepo.findByTeacherIdAndPeriodYearOrderByPeriodMonthDesc(teacherId, year);
        return items.stream()
                .filter(p -> TEACHER_VISIBLE_STATUS.contains(p.getStatus()))
                .map(p -> PayrollResponse.fromEntity(p, name))
                .toList();
    }

    /** Hồ sơ giáo viên của người đang đăng nhập (báo lỗi nếu tài khoản không phải giáo viên). */
    private Integer currentTeacherId() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .map(Teacher::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không có hồ sơ giáo viên"));
    }

    /**
     * Sinh/tính lại bảng lương một kỳ từ chấm công: đếm SỐ TIẾT mỗi GV và cộng tiền theo
     * đơn giá cấp của từng buổi. Chỉ ghi đè các dòng NHÁP (DRAFT).
     */
    @Transactional
    public List<PayrollResponse> generate(short year, short month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        // Gom theo GV: số tiết + tổng tiền (đơn giá tra theo cấp từng buổi).
        Map<Integer, int[]> periodsByTeacher = new LinkedHashMap<>();
        Map<Integer, BigDecimal> payByTeacher = new LinkedHashMap<>();
        Map<Integer, BigDecimal> gradeRateCache = new HashMap<>(); // scheduleId → đơn giá (tránh join lặp)

        for (Attendance a : attendanceRepo.findByWorkDateBetweenOrderByWorkDateDescIdDesc(from, to)) {
            if (!("PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus()))) {
                continue;
            }
            BigDecimal rate = rateForAttendance(a, gradeRateCache);
            periodsByTeacher.computeIfAbsent(a.getTeacherId(), k -> new int[1])[0]++;
            payByTeacher.merge(a.getTeacherId(), rate, BigDecimal::add);
        }

        Integer userId = SecurityUtils.currentUserId();
        for (Map.Entry<Integer, int[]> e : periodsByTeacher.entrySet()) {
            Integer teacherId = e.getKey();
            int periods = e.getValue()[0];
            BigDecimal pay = payByTeacher.getOrDefault(teacherId, BigDecimal.ZERO);

            Payroll p = payrollRepo
                    .findByTeacherIdAndPeriodYearAndPeriodMonth(teacherId, year, month)
                    .orElseGet(() -> {
                        Payroll np = new Payroll();
                        np.setTeacherId(teacherId);
                        np.setPeriodYear(year);
                        np.setPeriodMonth(month);
                        np.setCreatedBy(userId);
                        return np;
                    });
            if (!"DRAFT".equals(p.getStatus())) {
                continue; // đã chốt/đã trả thì không ghi đè
            }
            // Đơn giá HIỆU DỤNG = tổng tiền / số tiết. GV dạy 1 cấp → đúng bằng đơn giá cấp đó;
            // trường hợp hiếm dạy 2 cấp → trung bình có trọng số (NetAmount vẫn = tổng tiền).
            BigDecimal effRate =
                    periods > 0 ? pay.divide(BigDecimal.valueOf(periods), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            p.setTaughtHours(BigDecimal.valueOf(periods)); // repurpose: SỐ TIẾT
            p.setRatePerHour(effRate); // ĐƠN GIÁ / TIẾT
            p.setUpdatedBy(userId);
            p.setUpdatedAt(Instant.now());
            payrollRepo.save(p);
        }

        // Dòng nháp của GV KHÔNG còn tiết nào trong kỳ → reset 0, tránh giữ số cũ đã sai.
        for (Payroll p : payrollRepo.findByPeriodYearAndPeriodMonthOrderByTeacherId(year, month)) {
            if ("DRAFT".equals(p.getStatus())
                    && !periodsByTeacher.containsKey(p.getTeacherId())
                    && p.getTaughtHours() != null
                    && p.getTaughtHours().signum() != 0) {
                p.setTaughtHours(BigDecimal.ZERO);
                p.setRatePerHour(BigDecimal.ZERO);
                p.setUpdatedBy(userId);
                p.setUpdatedAt(Instant.now());
                payrollRepo.save(p);
            }
        }
        // Flush + clear để list() đọc lại cột computed NetAmount từ DB.
        em.flush();
        em.clear();
        return list(year, month);
    }

    @Transactional
    public PayrollResponse update(Integer id, PayrollUpdateRequest req) {
        Payroll p = getOrThrow(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Dòng lương đã chốt/đã trả — không thể chỉnh sửa");
        }
        if (req.baseSalary() != null) p.setBaseSalary(req.baseSalary());
        if (req.ratePerHour() != null) p.setRatePerHour(req.ratePerHour());
        if (req.allowance() != null) p.setAllowance(req.allowance());
        if (req.bonus() != null) p.setBonus(req.bonus());
        if (req.deduction() != null) p.setDeduction(req.deduction());
        p.setUpdatedBy(SecurityUtils.currentUserId());
        p.setUpdatedAt(Instant.now());
        payrollRepo.saveAndFlush(p);
        em.refresh(p); // đọc lại NetAmount (cột computed trong DB)
        return toResponse(p);
    }

    @Transactional
    public PayrollResponse finalizePayroll(Integer id) {
        Payroll p = getOrThrow(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ chốt được dòng lương đang ở trạng thái nháp");
        }
        p.setStatus("FINALIZED");
        p.setUpdatedBy(SecurityUtils.currentUserId());
        p.setUpdatedAt(Instant.now());
        payrollRepo.saveAndFlush(p);
        em.refresh(p);
        writeLog(p, "FINALIZE", null, "DRAFT", "FINALIZED", null, p.getNetAmount());
        // Bảng lương đã chốt → giáo viên xem được: báo cho GV.
        notificationService.publishToTeacher(
                p.getTeacherId(),
                "Phiếu lương kỳ " + p.getPeriodMonth() + "/" + p.getPeriodYear() + " đã sẵn sàng",
                "Phiếu lương tháng " + p.getPeriodMonth() + "/" + p.getPeriodYear()
                        + " đã được chốt. Thực nhận: " + formatVnd(p.getNetAmount())
                        + ". Vào Phiếu lương để xem chi tiết.",
                "PAYROLL",
                "Payroll",
                p.getId().longValue(),
                false);
        return toResponse(p);
    }

    /* ──────────────── MỞ LẠI KỲ LƯƠNG ĐÃ CHỐT (V32) ──────────────── */

    /**
     * Đưa một phiếu lương ĐÃ CHỐT về lại trạng thái nháp để sửa được.
     *
     * <p>Vì sao cần: chốt lương khóa luôn chấm công của kỳ đó ({@code
     * AttendanceService.assertPeriodOpen}). Trước V32 điều đó biến một lỗi dữ liệu hoàn toàn có
     * thật — dòng VẮNG mà hệ thống ghi nhầm cho buổi "ma" ngày lễ — thành lỗi KHÔNG THỂ SỬA,
     * mà người dùng không làm sai bước nào: họ chỉ chốt lương đúng hạn.
     *
     * <p>CHỈ mở được phiếu "đã chốt", không mở phiếu "đã trả": tiền đã ra khỏi quỹ thì sửa số
     * trên hệ thống mà không sửa được thực tế chỉ tạo ra lệch sổ sách.
     */
    @Transactional
    public PayrollResponse reopen(Integer id, String reason) {
        Payroll p = getOrThrow(id);
        assertReopenable(p);

        BigDecimal before = p.getNetAmount();
        p.setStatus("DRAFT");
        p.setUpdatedBy(SecurityUtils.currentUserId());
        p.setUpdatedAt(Instant.now());
        payrollRepo.saveAndFlush(p);
        em.refresh(p);
        writeLog(p, "REOPEN", reason, "FINALIZED", "DRAFT", before, null);

        // Phiếu về nháp là GV mất quyền xem (TEACHER_VISIBLE_STATUS). Phiếu tự biến mất mà
        // không một lời nào là thứ chắc chắn sinh ra thắc mắc — nói trước thì không.
        notificationService.publishToTeacher(
                p.getTeacherId(),
                "Phiếu lương kỳ " + p.getPeriodMonth() + "/" + p.getPeriodYear() + " đang được điều chỉnh",
                "Phiếu lương tháng " + p.getPeriodMonth() + "/" + p.getPeriodYear()
                        + " đã được mở lại để điều chỉnh nên tạm thời không hiển thị."
                        + " Bạn sẽ xem lại được ngay khi kế toán chốt lại.",
                "PAYROLL",
                "Payroll",
                p.getId().longValue(),
                false);
        return toResponse(p);
    }

    /**
     * Mở lại MỌI phiếu đã chốt của một kỳ.
     *
     * <p>Có mặt vì lỗi lịch nghỉ hiếm khi chỉ dính một người: một ngày lễ khai muộn kéo theo cả
     * chục giáo viên. Bắt bấm từng dòng, mỗi dòng nhập lại lý do, là cách chắc chắn để người ta
     * bỏ dở giữa chừng.
     *
     * @return số phiếu đã mở lại
     */
    @Transactional
    public int reopenPeriod(short year, short month, String reason) {
        assertPeriodWithinReopenWindow(year, month);
        List<Payroll> targets = payrollRepo.findByPeriodYearAndPeriodMonthOrderByTeacherId(year, month).stream()
                .filter(p -> "FINALIZED".equals(p.getStatus()))
                .toList();
        if (targets.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Kỳ lương " + month + "/" + year + " không có phiếu nào ở trạng thái đã chốt.");
        }
        for (Payroll p : targets) {
            reopen(p.getId(), reason);
        }
        return targets.size();
    }

    /** Lịch sử chốt/mở lại của một phiếu — mới nhất trước. */
    @Transactional(readOnly = true)
    public List<PayrollChangeLogResponse> logs(Integer id) {
        getOrThrow(id); // 404 sớm thay vì trả mảng rỗng khó hiểu
        Map<Integer, String> nameCache = new HashMap<>();
        List<PayrollChangeLogResponse> out = new ArrayList<>();
        for (PayrollChangeLog l : changeLogRepo.findByPayrollIdOrderByChangedAtDescIdDesc(id)) {
            out.add(PayrollChangeLogResponse.fromEntity(l, actorName(l.getChangedBy(), nameCache)));
        }
        return out;
    }

    /**
     * Cảnh báo trước khi chốt: kỳ này còn dòng Vắng nào rơi vào ngày nghỉ không.
     *
     * <p>Ủy quyền sang {@link HolidayService} vì đó là nơi biết luật ngày nghỉ (phạm vi trường,
     * khoảng ngày chồng nhau). Bảng lương chỉ là bên ĐẶT CÂU HỎI, đúng lúc nó sắp làm một việc
     * một chiều.
     */
    @Transactional(readOnly = true)
    public PayrollHolidayIssueResponse holidayIssues(short year, short month) {
        return holidayService.holidayIssues(year, month);
    }

    private void assertReopenable(Payroll p) {
        if ("DRAFT".equals(p.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Phiếu lương này đang là nháp — không cần mở lại.");
        }
        if (!"FINALIZED".equals(p.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Phiếu lương đã ở trạng thái ĐÃ TRẢ — tiền đã ra khỏi quỹ nên không mở lại được."
                            + " Điều chỉnh chênh lệch vào kỳ lương kế tiếp.");
        }
        assertPeriodWithinReopenWindow(p.getPeriodYear(), p.getPeriodMonth());
    }

    private void assertPeriodWithinReopenWindow(short year, short month) {
        YearMonth target = YearMonth.of(year, month);
        YearMonth oldest = YearMonth.from(BusinessTime.today()).minusMonths(REOPEN_MAX_MONTHS_BACK);
        if (target.isBefore(oldest)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Kỳ lương " + month + "/" + year + " đã quá " + REOPEN_MAX_MONTHS_BACK
                            + " tháng nên không mở lại được (sớm nhất: " + oldest.getMonthValue() + "/"
                            + oldest.getYear() + "). Điều chỉnh chênh lệch vào kỳ lương kế tiếp.");
        }
    }

    private void writeLog(
            Payroll p,
            String action,
            String reason,
            String statusBefore,
            String statusAfter,
            BigDecimal netBefore,
            BigDecimal netAfter) {
        PayrollChangeLog l = new PayrollChangeLog();
        l.setPayrollId(p.getId());
        l.setAction(action);
        l.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        l.setStatusBefore(statusBefore);
        l.setStatusAfter(statusAfter);
        l.setNetAmountBefore(netBefore);
        l.setNetAmountAfter(netAfter);
        l.setChangedBy(SecurityUtils.currentUserId());
        changeLogRepo.save(l);
    }

    private String actorName(Integer userId, Map<Integer, String> cache) {
        if (userId == null) {
            return null;
        }
        return cache.computeIfAbsent(
                userId,
                id -> userRepo.findById(id).map(displayNameResolver::resolve).orElse("(user #" + id + ")"));
    }

    /** Định dạng tiền VND gọn (vd 3.250.000đ). Null → "—". */
    private static String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        return String.format(java.util.Locale.US, "%,d", amount.longValue()).replace(',', '.') + "đ";
    }

    /* ── helpers ── */

    /** Đơn giá/tiết của một buổi chấm công: tra cấp qua buổi→phân công→lớp→khối. */
    private BigDecimal rateForAttendance(Attendance a, Map<Integer, BigDecimal> cache) {
        if (a.getScheduleId() == null) {
            return TH_RATE; // chấm công lẻ không gắn buổi: mặc định đơn giá TH (an toàn thấp)
        }
        int key = a.getScheduleId().intValue();
        BigDecimal cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        BigDecimal rate = TH_RATE;
        Schedule s = scheduleRepo.findById(a.getScheduleId()).orElse(null);
        if (s != null) {
            var asg = assignmentRepo.findById(s.getAssignmentId()).orElse(null);
            // Đơn giá theo KHỐI của lớp dạy ở CHÍNH tiết đó (V16): một phân công nay có thể
            // trải nhiều lớp, mà lớp 5 (TH) và lớp 6 (THCS) khác đơn giá — đọc lớp ở cấp
            // phân công sẽ tính sai tiền.
            Integer classId = asg != null ? asg.getClassId() : null;
            if (s.getSourceSlotId() != null) {
                AssignmentSlot slot = slotRepo.findById(s.getSourceSlotId()).orElse(null);
                if (slot != null && slot.getClassId() != null) {
                    classId = slot.getClassId();
                }
            }
            if (classId != null) {
                SchoolClass c = classRepo.findById(classId).orElse(null);
                Integer grade = c != null ? parseGrade(c.getGradeLevel(), c.getName()) : null;
                BigDecimal byGrade = rateForGrade(grade);
                if (byGrade != null) {
                    rate = byGrade;
                } else if (grade != null) {
                    log.warn(
                            "Lớp id={} có khối {} ngoài phạm vi 1-9 — tạm tính đơn giá TH. Dữ liệu này cần sửa lại.",
                            classId,
                            grade);
                }
            }
        }
        cache.put(key, rate);
        return rate;
    }

    /**
     * Đơn giá 1 tiết theo số khối, hoặc {@code null} nếu khối không thuộc 1–9.
     *
     * <p>Tách hẳn ra thành hàm riêng vì trước đây đây là một biểu thức ba ngôi
     * {@code grade <= 5 ? TH_RATE : THCS_RATE} nằm lọt giữa thân hàm: đọc lướt thì thấy "1–5
     * tiểu học, còn lại THCS" rất hợp lý, nhưng nó nuốt gọn cả khối 10–12 vào đơn giá THCS.
     * Hồi hệ thống còn trường cấp 3 thì đó là tính SAI TIỀN LƯƠNG mà không ai biết, vì không
     * có nhánh nào báo lên.
     *
     * <p>Nay trung tâm chỉ dạy khối 1–9 nên khối ngoài phạm vi là dữ liệu hỏng — trả
     * {@code null} để bên gọi rơi về đơn giá thấp nhất KÈM cảnh báo, thay vì đoán bừa.
     */
    static BigDecimal rateForGrade(Integer grade) {
        if (grade == null || grade < 1 || grade > 9) {
            return null;
        }
        return grade <= 5 ? TH_RATE : THCS_RATE;
    }

    /** Lấy số khối từ "Khối 6" / tên lớp "6A"… (1–9). */
    private static Integer parseGrade(String gradeLevel, String className) {
        Integer g = firstInt(gradeLevel);
        if (g == null) {
            g = firstInt(className);
        }
        return g;
    }

    private static Integer firstInt(String s) {
        if (s == null) {
            return null;
        }
        Matcher m = DIGITS.matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private Payroll getOrThrow(Integer id) {
        return payrollRepo
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy dòng lương id=" + id));
    }

    private PayrollResponse toResponse(Payroll p) {
        String name = teacherRepo
                .findById(p.getTeacherId())
                .map(PayrollService::fullName)
                .orElse("(GV #" + p.getTeacherId() + ")");
        return PayrollResponse.fromEntity(p, name);
    }

    private String teacherName(Integer teacherId, Map<Integer, String> cache) {
        return cache.computeIfAbsent(
                teacherId,
                id -> teacherRepo.findById(id).map(PayrollService::fullName).orElse("(GV #" + id + ")"));
    }

    private static String fullName(Teacher t) {
        return (t.getLastName() + " " + t.getFirstName()).trim();
    }
}
