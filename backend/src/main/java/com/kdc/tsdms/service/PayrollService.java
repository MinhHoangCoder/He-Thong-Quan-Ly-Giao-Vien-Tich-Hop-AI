package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.PayrollResponse;
import com.kdc.tsdms.dto.PayrollUpdateRequest;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Payroll;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
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
 * (khối 6–9) {@value #THCS_RATE_STR}đ/tiết. Cấp suy ra từ buổi → phân công → lớp → khối
 * ({@code Attendance.scheduleId → Schedule → Assignment → SchoolClass.gradeLevel}).
 *
 * <p>Vì mỗi GV chỉ dạy 1 cấp (quy ước dữ liệu) nên toàn bộ tiết của một GV cùng một đơn
 * giá → lưu {@code TaughtHours} = SỐ TIẾT, {@code RatePerHour} = ĐƠN GIÁ/TIẾT. Cột computed
 * {@code NetAmount} của DB (= base + TaughtHours×RatePerHour + phụ cấp + thưởng − khấu trừ)
 * cho ra đúng tiền lương theo tiết mà không cần đổi schema.
 */
@Service
public class PayrollService {

    static final String TH_RATE_STR = "115000";
    static final String THCS_RATE_STR = "125000";

    /** Trạng thái phiếu lương được phép cho GV tự xem (đã chốt/đã trả) — KHÔNG lộ bản nháp. */
    private static final Set<String> TEACHER_VISIBLE_STATUS = Set.of("FINALIZED", "PAID");

    /** Đơn giá 1 tiết theo cấp học. */
    private static final BigDecimal TH_RATE = new BigDecimal(TH_RATE_STR); // Tiểu học (khối 1–5)

    private static final BigDecimal THCS_RATE = new BigDecimal(THCS_RATE_STR); // THCS (khối 6–9)

    private static final Pattern DIGITS = Pattern.compile("(\\d{1,2})");

    private final PayrollRepository payrollRepo;
    private final AttendanceRepository attendanceRepo;
    private final TeacherRepository teacherRepo;
    private final ScheduleRepository scheduleRepo;
    private final AssignmentRepository assignmentRepo;
    private final SchoolClassRepository classRepo;
    private final EntityManager em;

    public PayrollService(
            PayrollRepository payrollRepo,
            AttendanceRepository attendanceRepo,
            TeacherRepository teacherRepo,
            ScheduleRepository scheduleRepo,
            AssignmentRepository assignmentRepo,
            SchoolClassRepository classRepo,
            EntityManager em) {
        this.payrollRepo = payrollRepo;
        this.attendanceRepo = attendanceRepo;
        this.teacherRepo = teacherRepo;
        this.scheduleRepo = scheduleRepo;
        this.assignmentRepo = assignmentRepo;
        this.classRepo = classRepo;
        this.em = em;
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
        return toResponse(p);
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
            if (asg != null && asg.getClassId() != null) {
                SchoolClass c = classRepo.findById(asg.getClassId()).orElse(null);
                Integer grade = c != null ? parseGrade(c.getGradeLevel(), c.getName()) : null;
                if (grade != null) {
                    rate = grade <= 5 ? TH_RATE : THCS_RATE;
                }
            }
        }
        cache.put(key, rate);
        return rate;
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
