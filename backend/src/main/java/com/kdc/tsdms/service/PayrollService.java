package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.PayrollChangeLogResponse;
import com.kdc.tsdms.dto.PayrollHolidayIssueResponse;
import com.kdc.tsdms.dto.PayrollResponse;
import com.kdc.tsdms.dto.PayrollUpdateRequest;
import com.kdc.tsdms.entity.Contract;
import com.kdc.tsdms.entity.PayRate;
import com.kdc.tsdms.entity.Payroll;
import com.kdc.tsdms.entity.PayrollChangeLog;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.PayRateRepository;
import com.kdc.tsdms.repository.PayrollChangeLogRepository;
import com.kdc.tsdms.repository.PayrollRepository;
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
 * Nghiệp vụ Bảng lương (Payroll) — tính theo TIẾT (buổi dạy).
 *
 * <p>Mỗi dòng chấm công PRESENT/LATE = 1 tiết. Đơn giá tra theo thứ tự: đơn giá riêng ghi
 * trong hợp đồng của giáo viên → barem chung theo khối và theo ngày dạy (bảng {@code PayRate},
 * Flyway V38). Khối lấy từ lớp của Ô THỜI KHÓA BIỂU sinh ra buổi, không phải lớp cấp phiếu —
 * từ V16 một phiếu trải nhiều lớp, mà lớp 5 và lớp 6 khác giá.
 *
 * <p>Lương cứng đọc từ {@code Contract.BaseSalary} và chỉ áp cho giáo viên CƠ HỮU; thỉnh
 * giảng chỉ ăn tiền tiết.
 *
 * <p>TÊN CỘT GÂY NHẦM: {@code TaughtHours} lưu SỐ TIẾT, {@code RatePerHour} lưu ĐƠN GIÁ/TIẾT.
 * Hai cột này bị dùng lại chứ không đổi tên vì {@code NetAmount} là cột COMPUTED của SQL
 * Server dựng trên chính chúng — đổi tên phải drop rồi tạo lại cột computed, đánh đổi không
 * xứng với việc chỉ để tên đọc xuôi hơn. Giao diện đã hiện đúng nhãn "Số tiết".
 *
 * <p>{@code NetAmount} = lương cứng + TaughtHours×RatePerHour + phụ cấp + thưởng − khấu trừ.
 */
@Service
public class PayrollService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PayrollService.class);

    /** Trạng thái phiếu lương được phép cho GV tự xem (đã chốt/đã trả) — KHÔNG lộ bản nháp. */
    private static final Set<String> TEACHER_VISIBLE_STATUS = Set.of("FINALIZED", "PAID");

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
    private final PayRateRepository payRateRepo;
    private final ContractRepository contractRepo;
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
            PayRateRepository payRateRepo,
            ContractRepository contractRepo,
            PayrollChangeLogRepository changeLogRepo,
            AppUserRepository userRepo,
            DisplayNameResolver displayNameResolver,
            HolidayService holidayService,
            EntityManager em,
            NotificationService notificationService) {
        this.payrollRepo = payrollRepo;
        this.attendanceRepo = attendanceRepo;
        this.teacherRepo = teacherRepo;
        this.payRateRepo = payRateRepo;
        this.contractRepo = contractRepo;
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
        Map<Integer, Long> lateByTeacher = lateCountsOf(year, month);
        return payrollRepo.findByPeriodYearAndPeriodMonthOrderByTeacherId(year, month).stream()
                .map(p -> {
                    PayrollResponse r = PayrollResponse.fromEntity(p, teacherName(p.getTeacherId(), cache));
                    r.lateCount = lateByTeacher.getOrDefault(p.getTeacherId(), 0L);
                    return r;
                })
                .toList();
    }

    /** teacherId → số buổi đi muộn trong kỳ. Một câu SQL cho cả bảng lương. */
    private Map<Integer, Long> lateCountsOf(short year, short month) {
        LocalDate from = LocalDate.of(year, month, 1);
        Map<Integer, Long> out = new HashMap<>();
        for (Object[] r : attendanceRepo.countLateByTeacher(from, from.withDayOfMonth(from.lengthOfMonth()))) {
            out.put(((Number) r[0]).intValue(), ((Number) r[1]).longValue());
        }
        return out;
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
     * đơn giá của từng buổi. Chỉ ghi đè các dòng NHÁP (DRAFT).
     *
     * <p>Đơn giá lấy theo thứ tự: {@code Attendance.RateAmount} — mức ĐÃ ĐÓNG BĂNG vào buổi
     * lúc chấm công (V40) → hết đường đó (dòng cũ trước V40 để NULL) mới tra như cũ: đơn giá
     * riêng trong hợp đồng của giáo viên → barem chung theo khối và theo NGÀY DẠY ({@code
     * PayRate}) → không tra ra thì bỏ qua và ghi cảnh báo.
     *
     * <p>Vì sao ưu tiên số đã đóng băng: bảng {@code PayRate} vẫn sửa được, mà sửa một dòng giá
     * cũ là mọi phiếu lương từng tính theo dòng đó đổi số. Buổi đã chấm mang theo giá của chính
     * nó thì tính lại bao nhiêu lần cũng ra đúng số đã trả. Nhánh tra bảng giữ nguyên chứ không
     * bỏ: dữ liệu có trước V40 không có gì để đọc, bỏ đi là những kỳ đó về 0đ.
     *
     * <p>Tra theo ngày dạy chứ không theo hôm nay: tính lại tháng 7 sau khi tăng giá từ 1/9
     * phải ra đúng số của tháng 7.
     *
     * <p>Lương cứng đọc từ hợp đồng và CHỈ áp cho giáo viên cơ hữu — thỉnh giảng chỉ ăn tiền
     * tiết. Trước V38 cột này luôn bằng 0 trừ khi kế toán gõ tay.
     */
    @Transactional
    public List<PayrollResponse> generate(short year, short month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        // MỘT câu SQL cho cả kỳ, đã kèm khối lớp của từng buổi. Bản cũ hỏi DB bốn lần cho
        // mỗi dòng chấm công (~3.000 câu/tháng) vì cache đánh theo scheduleId không bao giờ
        // trúng — mỗi dòng chấm công có một scheduleId riêng.
        List<Object[]> rows = attendanceRepo.findPayableWithGrade(from, to);

        // Gom theo GV: số tiết · số buổi muộn · tổng tiền.
        Map<Integer, int[]> countsByTeacher = new LinkedHashMap<>(); // [0] = tiết, [1] = buổi muộn
        Map<Integer, BigDecimal> payByTeacher = new LinkedHashMap<>();

        List<PayRate> rateTable = payRateRepo.findAllByOrderByEffectiveFromDescGradeFromAsc();
        Map<Integer, Contract> contracts = contractsOf(rows);

        for (Object[] r : rows) {
            Integer teacherId = ((Number) r[0]).intValue();
            LocalDate workDate = toLocalDate(r[1]);
            boolean late = "LATE".equals(r[2]);
            Integer grade = parseGrade((String) r[3], (String) r[4]);

            // Mức đóng băng lúc chấm công thắng mọi thứ khác — xem javadoc của hàm này.
            BigDecimal rate = toAmount(r[5]);
            if (rate == null) {
                rate = resolveRate(contracts.get(teacherId), grade, workDate, rateTable);
            }
            if (rate == null) {
                log.warn(
                        "Không tra được đơn giá cho GV id={} ngày {} (khối {}) — bỏ qua tiết này."
                                + " Kiểm tra bảng PayRate và khối của lớp.",
                        teacherId,
                        workDate,
                        grade);
                continue;
            }
            int[] counts = countsByTeacher.computeIfAbsent(teacherId, k -> new int[2]);
            counts[0]++;
            if (late) {
                counts[1]++;
            }
            payByTeacher.merge(teacherId, rate, BigDecimal::add);
        }

        Integer userId = SecurityUtils.currentUserId();
        for (Map.Entry<Integer, int[]> e : countsByTeacher.entrySet()) {
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
            p.setBaseSalary(baseSalaryOf(teacherId, contracts.get(teacherId)));
            p.setUpdatedBy(userId);
            p.setUpdatedAt(Instant.now());
            payrollRepo.save(p);
        }

        // Dòng nháp của GV KHÔNG còn tiết nào trong kỳ → reset 0, tránh giữ số cũ đã sai.
        for (Payroll p : payrollRepo.findByPeriodYearAndPeriodMonthOrderByTeacherId(year, month)) {
            if ("DRAFT".equals(p.getStatus())
                    && !countsByTeacher.containsKey(p.getTeacherId())
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

    /* ──────────────── XÁC NHẬN ĐÃ TRẢ (V38) ──────────────── */

    /**
     * Đánh dấu một phiếu ĐÃ CHỐT thành ĐÃ TRẢ.
     *
     * <p>Trước V38 trạng thái {@code PAID} là trạng thái CHẾT: có trong ràng buộc của bảng, có
     * trong danh sách giáo viên được xem, và {@link #assertReopenable} từ chối mở lại phiếu
     * PAID — nhưng không có đường code nào đặt được nó. Kế toán chi tiền xong không có nút nào
     * để ghi nhận, nên "đã chốt" và "đã trả" trên hệ thống là một.
     *
     * <p>Chỉ đi được từ FINALIZED. Từ DRAFT thẳng sang PAID là bỏ qua bước chốt sổ, mà bước
     * chốt mới là chỗ có cảnh báo ngày nghỉ và khóa chấm công.
     */
    @Transactional
    public PayrollResponse pay(Integer id) {
        Payroll p = getOrThrow(id);
        if ("PAID".equals(p.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Phiếu lương này đã được đánh dấu ĐÃ TRẢ.");
        }
        if (!"FINALIZED".equals(p.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Chỉ đánh dấu đã trả cho phiếu ĐÃ CHỐT. Vui lòng chốt phiếu trước.");
        }
        p.setStatus("PAID");
        p.setUpdatedBy(SecurityUtils.currentUserId());
        p.setUpdatedAt(Instant.now());
        payrollRepo.saveAndFlush(p);
        em.refresh(p);
        writeLog(p, "PAY", null, "FINALIZED", "PAID", p.getNetAmount(), p.getNetAmount());

        notificationService.publishToTeacher(
                p.getTeacherId(),
                "Đã chi lương kỳ " + p.getPeriodMonth() + "/" + p.getPeriodYear(),
                "Lương tháng " + p.getPeriodMonth() + "/" + p.getPeriodYear() + " đã được chi: "
                        + formatVnd(p.getNetAmount()) + ".",
                "PAYROLL",
                "Payroll",
                p.getId().longValue(),
                false);
        return toResponse(p);
    }

    /**
     * Đánh dấu ĐÃ TRẢ cho MỌI phiếu đã chốt của một kỳ — kế toán chi lương theo đợt chứ không
     * theo từng người.
     *
     * @return số phiếu đã đánh dấu
     */
    @Transactional
    public int payPeriod(short year, short month) {
        List<Payroll> targets = payrollRepo.findByPeriodYearAndPeriodMonthOrderByTeacherId(year, month).stream()
                .filter(p -> "FINALIZED".equals(p.getStatus()))
                .toList();
        if (targets.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Kỳ lương " + month + "/" + year + " không có phiếu nào ở trạng thái đã chốt.");
        }
        for (Payroll p : targets) {
            pay(p.getId());
        }
        return targets.size();
    }

    /* ──────────────── MỞ LẠI KỲ LƯƠNG ĐÃ CHỐT (V32) ──────────────── */

    /**
     * Đưa một phiếu lương ĐÃ CHỐT về lại nháp để sửa (V32).
     *
     * <p>Cần có vì chốt lương khóa luôn chấm công của kỳ ({@code
     * AttendanceService.assertPeriodOpen}) — trước V32, dòng VẮNG ghi nhầm cho buổi rơi vào ngày
     * lễ là lỗi không thể sửa.
     *
     * <p>CHỈ mở phiếu FINALIZED, KHÔNG mở phiếu PAID: tiền đã chi thì sửa số trên hệ thống chỉ
     * làm lệch sổ sách.
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
     * Mở lại MỌI phiếu đã chốt của một kỳ. Lỗi ngày lễ khai muộn thường dính cả chục giáo viên
     * cùng lúc nên cần thao tác hàng loạt.
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

    /**
     * Đơn giá một tiết, theo thứ tự ưu tiên: hợp đồng riêng → barem chung.
     *
     * <p>Trả {@code null} khi không tra ra mức nào (khối rỗng, khối ngoài barem, hoặc ngày dạy
     * nằm ngoài mọi khoảng hiệu lực). Cố ý KHÔNG lấy một mức mặc định: đoán một con số ở đây
     * là ghi tiền sai vào phiếu lương mà không ai biết. Bên gọi ghi cảnh báo và bỏ qua tiết đó,
     * để số tiết trên phiếu lệch đi và người dùng nhìn thấy có gì đó không ổn.
     *
     * @param contract hợp đồng đang hiệu lực của giáo viên, có thể null
     * @param grade khối lớp của buổi dạy, có thể null nếu dữ liệu lớp hỏng
     * @param workDate ngày dạy — mức giá tra theo ngày này, KHÔNG theo hôm nay
     */
    static BigDecimal resolveRate(Contract contract, Integer grade, LocalDate workDate, List<PayRate> rateTable) {
        if (contract != null && contract.getRatePerPeriod() != null) {
            return contract.getRatePerPeriod();
        }
        if (grade == null || workDate == null) {
            return null;
        }
        // Bảng giá đã sắp EffectiveFrom giảm dần nên mức đầu tiên khớp cũng là mức mới nhất
        // còn hiệu lực vào ngày đó.
        for (PayRate r : rateTable) {
            if (r.coversGrade(grade) && r.coversDate(workDate)) {
                return r.getAmount();
            }
        }
        return null;
    }

    /**
     * Lương cứng của một kỳ.
     *
     * <p>CHỈ giáo viên cơ hữu mới có: thỉnh giảng ăn theo tiết, cộng thêm lương cứng cho họ là
     * trả tiền cho những tháng họ không dạy buổi nào. Không có hợp đồng thì trả 0 chứ không
     * chặn — hồ sơ thiếu hợp đồng là việc của HR, không nên làm kế toán không chốt được lương.
     */
    private BigDecimal baseSalaryOf(Integer teacherId, Contract contract) {
        if (contract == null || contract.getBaseSalary() == null) {
            return BigDecimal.ZERO;
        }
        boolean coHuu = teacherRepo
                .findById(teacherId)
                .map(t -> "CO_HUU".equals(t.getEmploymentType()))
                .orElse(false);
        return coHuu ? contract.getBaseSalary() : BigDecimal.ZERO;
    }

    /** Hợp đồng của mọi giáo viên xuất hiện trong kết quả, nạp một lượt. */
    private Map<Integer, Contract> contractsOf(List<Object[]> rows) {
        Set<Integer> ids =
                rows.stream().map(r -> ((Number) r[0]).intValue()).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Contract> out = new HashMap<>();
        for (Contract c : contractRepo.findByTeacherIdInAndDeletedFalse(ids)) {
            out.put(c.getTeacherId(), c);
        }
        return out;
    }

    /** Cột DATE của SQL Server về qua JDBC dưới dạng java.sql.Date. */
    private static LocalDate toLocalDate(Object v) {
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return v instanceof LocalDate d ? d : null;
    }

    /** Cột DECIMAL(18,2) của SQL Server; NULL = dòng chấm công chưa đóng băng đơn giá. */
    private static BigDecimal toAmount(Object v) {
        if (v instanceof BigDecimal d) {
            return d;
        }
        return v instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null;
    }

    /**
     * Lấy số khối từ "Khối 6" / tên lớp "6A"… (1–9).
     *
     * <p>Để mức gói (package-private) chứ không private: {@code AttendanceService} phải bóc khối
     * đúng cùng một luật lúc đóng băng đơn giá, mà chép lại luật là hai bản dần dần lệch nhau.
     */
    static Integer parseGrade(String gradeLevel, String className) {
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
