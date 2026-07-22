package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.DashboardResponse;
import com.kdc.tsdms.dto.DashboardResponse.AssignmentRow;
import com.kdc.tsdms.dto.DashboardResponse.ChartData;
import com.kdc.tsdms.dto.DashboardResponse.ChartSeries;
import com.kdc.tsdms.dto.DashboardResponse.ScheduleRow;
import com.kdc.tsdms.dto.DashboardResponse.SideStat;
import com.kdc.tsdms.dto.DashboardResponse.StatCard;
import com.kdc.tsdms.dto.DashboardResponse.TopTeacher;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Payroll;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.SubjectCategory;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.entity.TeacherEvaluation;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.PayrollRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherEvaluationRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tổng hợp số liệu cho Bảng điều khiển (admin) — TÍNH TRỰC TIẾP từ DB.
 *
 * <p>Nguyên tắc: chỉ đọc dữ liệu thật; chỗ nào chưa có dữ liệu thì trả 0 hoặc null
 * (FE hiển thị "—") thay vì bịa số. "Buổi dạy" = Schedule ở trạng thái APPROVED.
 */
@Service
public class DashboardService {

    /** Số tháng vẽ sparkline ở các thẻ chỉ số phụ. */
    private static final int SPARK_MONTHS = 7;

    private static final String APPROVED = "APPROVED";

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HOUR_MIN = DateTimeFormatter.ofPattern("HH:mm");

    /** Bảng màu xoay vòng cho các nhóm môn / phần tử biểu đồ. */
    private static final List<String> PALETTE =
            List.of("#f97316", "#0ea5e9", "#2563eb", "#f59e0b", "#22c55e", "#8b5cf6");

    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final AssignmentRepository assignmentRepo;
    private final ScheduleRepository scheduleRepo;
    private final SubjectRepository subjectRepo;
    private final AttendanceRepository attendanceRepo;
    private final TeacherEvaluationRepository evaluationRepo;
    private final PayrollRepository payrollRepo;

    public DashboardService(
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            AssignmentRepository assignmentRepo,
            ScheduleRepository scheduleRepo,
            SubjectRepository subjectRepo,
            AttendanceRepository attendanceRepo,
            TeacherEvaluationRepository evaluationRepo,
            PayrollRepository payrollRepo) {
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.assignmentRepo = assignmentRepo;
        this.scheduleRepo = scheduleRepo;
        this.subjectRepo = subjectRepo;
        this.attendanceRepo = attendanceRepo;
        this.evaluationRepo = evaluationRepo;
        this.payrollRepo = payrollRepo;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary(int months) {
        int chartMonths = Math.max(1, Math.min(12, months));

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);
        LocalDateTime weekStart =
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusWeeks(1);
        LocalDateTime lastWeekStart = weekStart.minusWeeks(1);
        LocalDateTime chartStart = monthStart.minusMonths(chartMonths - 1);
        LocalDateTime sparkStart = monthStart.minusMonths(SPARK_MONTHS - 1);

        // Một truy vấn duy nhất phủ mọi khoảng cần dùng → lọc lại trong bộ nhớ.
        LocalDateTime from = min(chartStart, min(sparkStart, min(lastWeekStart, lastMonthStart)));
        LocalDateTime to = max(monthEnd, max(weekEnd, tomorrowStart));
        List<Schedule> schedules = scheduleRepo.findByStartTimeBetweenAndDeletedFalse(from, to);

        // Bảng tra cứu tên/nhóm môn (nạp 1 lần, tránh N+1).
        Map<Integer, Assignment> assignmentById =
                assignmentRepo.findAll().stream().collect(Collectors.toMap(Assignment::getId, a -> a, (a, b) -> a));
        Map<Integer, Subject> subjectById =
                subjectRepo.findAll().stream().collect(Collectors.toMap(Subject::getId, s -> s, (a, b) -> a));
        Map<Integer, School> schoolById =
                schoolRepo.findAll().stream().collect(Collectors.toMap(School::getId, s -> s, (a, b) -> a));
        Map<Integer, Teacher> teacherById =
                teacherRepo.findAll().stream().collect(Collectors.toMap(Teacher::getId, t -> t, (a, b) -> a));

        List<Attendance> attendance =
                attendanceRepo.findByWorkDateBetweenOrderByWorkDateDescIdDesc(from.toLocalDate(), to.toLocalDate());
        List<TeacherEvaluation> evaluations = evaluationRepo.findByDeletedFalse();
        List<Payroll> payrolls = payrollRepo.findAll();

        return new DashboardResponse(
                buildStats(schedules, teacherById, weekStart, weekEnd, lastWeekStart),
                buildChart(schedules, assignmentById, subjectById, monthStart, chartMonths),
                buildSideStats(schedules, attendance, evaluations, payrolls, monthStart, lastMonthStart),
                buildRecentAssignments(assignmentById, schoolById, subjectById, teacherById, today),
                buildToday(schedules, assignmentById, subjectById, schoolById, teacherById, todayStart, tomorrowStart),
                buildTopTeachers(schedules, assignmentById, subjectById, teacherById, monthStart, monthEnd));
    }

    /* ─────────────────────────── STAT CARDS ─────────────────────────── */

    private List<StatCard> buildStats(
            List<Schedule> schedules,
            Map<Integer, Teacher> teacherById,
            LocalDateTime weekStart,
            LocalDateTime weekEnd,
            LocalDateTime lastWeekStart) {
        long activeTeachers = teacherById.values().stream()
                .filter(t -> !t.isDeleted() && "ACTIVE".equals(t.getStatus()))
                .count();
        long clientSchools =
                schoolRepo.findAll().stream().filter(s -> !s.isDeleted()).count();
        long runningAssignments = assignmentRepo.findByDeletedFalseOrderByIdDesc().stream()
                .filter(a -> "ACTIVE".equals(a.getStatus()))
                .count();

        long lessonsThisWeek = countApproved(schedules, weekStart, weekEnd);
        long lessonsLastWeek = countApproved(schedules, lastWeekStart, weekStart);

        return List.of(
                new StatCard(
                        "teachers",
                        "teacher",
                        "Giáo viên đang dạy",
                        activeTeachers,
                        "so với tháng trước",
                        null,
                        "#f97316"),
                new StatCard(
                        "schools", "school", "Trường khách hàng", clientSchools, "so với tháng trước", null, "#2563eb"),
                new StatCard(
                        "assignments",
                        "assignment",
                        "Phân công đang chạy",
                        runningAssignments,
                        "so với tháng trước",
                        null,
                        "#f59e0b"),
                new StatCard(
                        "lessons",
                        "schedule",
                        "Buổi dạy tuần này",
                        lessonsThisWeek,
                        "so với tuần trước",
                        percentChange(lessonsThisWeek, lessonsLastWeek),
                        "#0ea5e9"));
    }

    /* ─────────────────────────── CHART ─────────────────────────── */

    private ChartData buildChart(
            List<Schedule> schedules,
            Map<Integer, Assignment> assignmentById,
            Map<Integer, Subject> subjectById,
            LocalDateTime monthStart,
            int chartMonths) {
        // Nhãn tháng T1..Tn theo thứ tự thời gian.
        List<String> labels = new ArrayList<>();
        List<LocalDateTime> bucketStarts = new ArrayList<>();
        for (int i = chartMonths - 1; i >= 0; i--) {
            LocalDateTime b = monthStart.minusMonths(i);
            bucketStarts.add(b);
            labels.add("T" + b.getMonthValue());
        }

        // category -> mảng đếm theo tháng.
        Map<String, long[]> byCategory = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            if (!APPROVED.equals(s.getStatus())) {
                continue;
            }
            int idx = monthIndex(s.getStartTime(), bucketStarts);
            if (idx < 0) {
                continue;
            }
            String category = categoryOf(s, assignmentById, subjectById);
            byCategory.computeIfAbsent(category, k -> new long[chartMonths])[idx]++;
        }

        // Chỉ giữ nhóm có ít nhất 1 buổi; xếp theo tổng giảm dần cho biểu đồ gọn.
        List<ChartSeries> series = new ArrayList<>();
        List<Map.Entry<String, long[]>> entries = new ArrayList<>(byCategory.entrySet());
        entries.sort(Comparator.comparingLong((Map.Entry<String, long[]> e) -> sum(e.getValue()))
                .reversed());
        int c = 0;
        for (Map.Entry<String, long[]> e : entries) {
            if (sum(e.getValue()) == 0) {
                continue;
            }
            List<Long> data = new ArrayList<>();
            for (long v : e.getValue()) {
                data.add(v);
            }
            series.add(new ChartSeries(e.getKey(), PALETTE.get(c % PALETTE.size()), data));
            c++;
        }
        return new ChartData(labels, series);
    }

    /* ─────────────────────────── SIDE STATS ─────────────────────────── */

    private List<SideStat> buildSideStats(
            List<Schedule> schedules,
            List<Attendance> attendance,
            List<TeacherEvaluation> evaluations,
            List<Payroll> payrolls,
            LocalDateTime monthStart,
            LocalDateTime lastMonthStart) {
        // 1) Số TIẾT dạy tháng này (mỗi buổi APPROVED = 1 tiết). Bỏ tính theo giờ.
        long tietThisMonth = countApproved(schedules, monthStart, monthStart.plusMonths(1));
        long tietLastMonth = countApproved(schedules, lastMonthStart, monthStart);
        List<Long> tietSpark = monthlySeries(monthStart, (from, toEx) -> countApproved(schedules, from, toEx));
        SideStat tiet = new SideStat(
                "hours",
                "Số tiết dạy tháng này",
                formatInt(tietThisMonth),
                percentChange(tietThisMonth, tietLastMonth),
                tietSpark,
                "#f97316");

        // 2) Tỉ lệ chấm công đúng giờ (PRESENT / (PRESENT+LATE)) trong tháng.
        double rateThis = onTimeRate(
                attendance, monthStart.toLocalDate(), monthStart.plusMonths(1).toLocalDate());
        double rateLast = onTimeRate(attendance, lastMonthStart.toLocalDate(), monthStart.toLocalDate());
        boolean hasAttendance = countAttendance(
                        attendance,
                        monthStart.toLocalDate(),
                        monthStart.plusMonths(1).toLocalDate())
                > 0;
        List<Long> rateSpark = monthlySeries(
                monthStart, (from, toEx) -> (long) countPresent(attendance, from.toLocalDate(), toEx.toLocalDate()));
        SideStat onTime = new SideStat(
                "ontime",
                "Tỉ lệ chấm công đúng giờ",
                hasAttendance ? Math.round(rateThis) + "%" : "—",
                hasAttendance && rateLast > 0 ? round1(rateThis - rateLast) : null,
                rateSpark,
                "#2563eb");

        // 3) Điểm đánh giá trung bình.
        double avg = evaluations.stream()
                .filter(e -> e.getScore() != null)
                .mapToInt(e -> e.getScore().intValue())
                .average()
                .orElse(0);
        boolean hasRating = evaluations.stream().anyMatch(e -> e.getScore() != null);
        List<Long> ratingSpark = monthlySeries(monthStart, (from, toEx) -> evaluations.stream()
                .filter(e -> e.getCreatedAt() != null)
                .filter(e -> {
                    LocalDateTime at = LocalDateTime.ofInstant(e.getCreatedAt(), java.time.ZoneOffset.UTC);
                    return !at.isBefore(from) && at.isBefore(toEx);
                })
                .count());
        SideStat rating = new SideStat(
                "rating",
                "Điểm đánh giá trung bình",
                hasRating ? round1(avg) + "/5" : "—",
                null,
                ratingSpark,
                "#0ea5e9");

        // 4) Chi phí lương tháng này = tổng NetAmount các dòng Payroll của kỳ hiện tại
        //    (NetAmount do DB tính sẵn). Chưa "Tạo bảng lương" cho tháng này → hiển thị "—".
        short curYear = (short) monthStart.getYear();
        short curMonth = (short) monthStart.getMonthValue();

        BigDecimal payThis = sumNet(payrolls, curYear, curMonth);
        boolean hasPayroll = payrolls.stream()
                .filter(p -> p.getPeriodYear() != null && p.getPeriodMonth() != null)
                .anyMatch(p -> p.getPeriodYear().shortValue() == curYear
                        && p.getPeriodMonth().shortValue() == curMonth);

        // Sparkline: tổng lương từng tháng trong SPARK_MONTHS tháng gần nhất (cũ → mới).
        List<Long> paySpark = new ArrayList<>();
        for (int i = SPARK_MONTHS - 1; i >= 0; i--) {
            LocalDateTime b = monthStart.minusMonths(i);
            paySpark.add(sumNet(payrolls, (short) b.getYear(), (short) b.getMonthValue())
                    .longValue());
        }

        // Không hiển thị % thay đổi cho thẻ chi phí lương: tháng trước thường có
        // mẫu số rất nhỏ nên % biến động bị "phóng đại" (vd 1801.6%), gây hiểu nhầm.
        SideStat payroll = new SideStat(
                "payroll",
                "Chi phí lương tháng này",
                hasPayroll ? formatMoney(payThis) : "—",
                null,
                paySpark,
                "#22c55e");

        return List.of(tiet, onTime, rating, payroll);
    }

    /** Tổng NetAmount (lương thực nhận, DB tính) của mọi dòng Payroll thuộc kỳ year/month. */
    private static BigDecimal sumNet(List<Payroll> payrolls, short year, short month) {
        return payrolls.stream()
                .filter(p -> p.getPeriodYear() != null && p.getPeriodMonth() != null)
                .filter(p -> p.getPeriodYear().shortValue() == year
                        && p.getPeriodMonth().shortValue() == month)
                .map(Payroll::getNetAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /* ─────────────────────────── RECENT ASSIGNMENTS ─────────────────────────── */

    private List<AssignmentRow> buildRecentAssignments(
            Map<Integer, Assignment> assignmentById,
            Map<Integer, School> schoolById,
            Map<Integer, Subject> subjectById,
            Map<Integer, Teacher> teacherById,
            LocalDate today) {
        return assignmentRepo.findByDeletedFalseOrderByIdDesc().stream()
                .limit(6)
                .map(a -> {
                    String[] status = assignmentStatus(a, today);
                    return new AssignmentRow(
                            a.getId(),
                            teacherName(teacherById.get(a.getTeacherId())),
                            name(schoolById.get(a.getSchoolId()), School::getName, "Trường"),
                            name(subjectById.get(a.getSubjectId()), Subject::getName, "Môn"),
                            a.getStartDate() == null ? "" : a.getStartDate().format(DAY_MONTH),
                            status[1],
                            status[0]);
                })
                .toList();
    }

    /* ─────────────────────────── TODAY SCHEDULE ─────────────────────────── */

    private List<ScheduleRow> buildToday(
            List<Schedule> schedules,
            Map<Integer, Assignment> assignmentById,
            Map<Integer, Subject> subjectById,
            Map<Integer, School> schoolById,
            Map<Integer, Teacher> teacherById,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart) {
        return schedules.stream()
                .filter(s -> APPROVED.equals(s.getStatus()))
                .filter(s -> !s.getStartTime().isBefore(todayStart)
                        && s.getStartTime().isBefore(tomorrowStart))
                .sorted(Comparator.comparing(Schedule::getStartTime))
                .limit(6)
                .map(s -> {
                    Assignment a = assignmentById.get(s.getAssignmentId());
                    Subject subject = a == null ? null : subjectById.get(a.getSubjectId());
                    School school = a == null ? null : schoolById.get(a.getSchoolId());
                    return new ScheduleRow(
                            s.getId(),
                            s.getStartTime().format(HOUR_MIN),
                            teacherName(teacherById.get(s.getTeacherId())),
                            subject == null ? "Buổi dạy" : subject.getName(),
                            school == null ? "" : school.getName(),
                            categoryColor(subject));
                })
                .toList();
    }

    /* ─────────────────────────── TOP TEACHERS ─────────────────────────── */

    private List<TopTeacher> buildTopTeachers(
            List<Schedule> schedules,
            Map<Integer, Assignment> assignmentById,
            Map<Integer, Subject> subjectById,
            Map<Integer, Teacher> teacherById,
            LocalDateTime monthStart,
            LocalDateTime monthEnd) {
        // Đếm SỐ TIẾT mỗi GV trong tháng (mỗi buổi APPROVED = 1 tiết) thay cho cộng giờ.
        Map<Integer, Long> tietByTeacher = new LinkedHashMap<>();
        Map<Integer, Map<String, Long>> subjectByTeacher = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            if (!APPROVED.equals(s.getStatus())
                    || s.getStartTime().isBefore(monthStart)
                    || !s.getStartTime().isBefore(monthEnd)) {
                continue;
            }
            tietByTeacher.merge(s.getTeacherId(), 1L, Long::sum);
            Assignment a = assignmentById.get(s.getAssignmentId());
            Subject subject = a == null ? null : subjectById.get(a.getSubjectId());
            if (subject != null) {
                subjectByTeacher
                        .computeIfAbsent(s.getTeacherId(), k -> new LinkedHashMap<>())
                        .merge(subject.getName(), 1L, Long::sum);
            }
        }
        return tietByTeacher.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(4)
                .map(e -> {
                    Teacher t = teacherById.get(e.getKey());
                    String subject = subjectByTeacher.getOrDefault(e.getKey(), Map.of()).entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("—");
                    return new TopTeacher(e.getKey(), teacherName(t), subject, e.getValue(), initials(t));
                })
                .toList();
    }

    /* ─────────────────────────── HELPERS ─────────────────────────── */

    private long countApproved(List<Schedule> schedules, LocalDateTime from, LocalDateTime toEx) {
        return schedules.stream()
                .filter(s -> APPROVED.equals(s.getStatus()))
                .filter(s ->
                        !s.getStartTime().isBefore(from) && s.getStartTime().isBefore(toEx))
                .count();
    }

    private int monthIndex(LocalDateTime when, List<LocalDateTime> bucketStarts) {
        for (int i = bucketStarts.size() - 1; i >= 0; i--) {
            if (!when.isBefore(bucketStarts.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Sinh mảng số liệu SPARK_MONTHS tháng gần nhất (cũ → mới) từ một hàm đếm theo khoảng. */
    private List<Long> monthlySeries(
            LocalDateTime monthStart, java.util.function.BiFunction<LocalDateTime, LocalDateTime, Long> counter) {
        List<Long> out = new ArrayList<>();
        for (int i = SPARK_MONTHS - 1; i >= 0; i--) {
            LocalDateTime b = monthStart.minusMonths(i);
            out.add(counter.apply(b, b.plusMonths(1)));
        }
        return out;
    }

    private String categoryOf(Schedule s, Map<Integer, Assignment> assignmentById, Map<Integer, Subject> subjectById) {
        Assignment a = assignmentById.get(s.getAssignmentId());
        if (a == null) {
            return "Khác";
        }
        Subject subject = subjectById.get(a.getSubjectId());
        if (subject == null || subject.getCategory() == null) {
            return "Khác";
        }
        return subject.getCategory().getName();
    }

    private String categoryColor(Subject subject) {
        if (subject == null || subject.getCategory() == null) {
            return PALETTE.get(0);
        }
        SubjectCategory cat = subject.getCategory();
        int idx = Math.floorMod(cat.getId() == null ? 0 : cat.getId(), PALETTE.size());
        return PALETTE.get(idx);
    }

    private long countAttendance(List<Attendance> att, LocalDate from, LocalDate toEx) {
        return att.stream()
                .filter(a -> !a.getWorkDate().isBefore(from) && a.getWorkDate().isBefore(toEx))
                .count();
    }

    private long countPresent(List<Attendance> att, LocalDate from, LocalDate toEx) {
        return att.stream()
                .filter(a -> !a.getWorkDate().isBefore(from) && a.getWorkDate().isBefore(toEx))
                .filter(a -> "PRESENT".equals(a.getStatus()))
                .count();
    }

    private double onTimeRate(List<Attendance> att, LocalDate from, LocalDate toEx) {
        long present = 0;
        long counted = 0;
        for (Attendance a : att) {
            if (a.getWorkDate().isBefore(from) || !a.getWorkDate().isBefore(toEx)) {
                continue;
            }
            if ("PRESENT".equals(a.getStatus())) {
                present++;
                counted++;
            } else if ("LATE".equals(a.getStatus())) {
                counted++;
            }
        }
        return counted == 0 ? 0 : (present * 100.0) / counted;
    }

    /** Trả [tone, label]: ok=Đang dạy, wait=Sắp bắt đầu, no=Đã hủy, done=Hoàn thành. */
    private String[] assignmentStatus(Assignment a, LocalDate today) {
        if ("CANCELLED".equals(a.getStatus())) {
            return new String[] {"no", "Đã hủy"};
        }
        if ("COMPLETED".equals(a.getStatus())) {
            return new String[] {"done", "Hoàn thành"};
        }
        if (a.getStartDate() != null && a.getStartDate().isAfter(today)) {
            return new String[] {"wait", "Sắp bắt đầu"};
        }
        return new String[] {"ok", "Đang dạy"};
    }

    private String teacherName(Teacher t) {
        if (t == null) {
            return "(GV)";
        }
        return (safe(t.getLastName()) + " " + safe(t.getFirstName())).trim();
    }

    private String initials(Teacher t) {
        if (t == null) {
            return "GV";
        }
        String a = firstChar(t.getLastName());
        String b = firstChar(t.getFirstName());
        String s = (a + b).trim();
        return s.isEmpty() ? "GV" : s.toUpperCase(Locale.ROOT);
    }

    private static String firstChar(String s) {
        return s == null || s.isBlank() ? "" : s.trim().substring(0, 1);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static <T> String name(T obj, Function<T, String> getter, String fallback) {
        return obj == null ? "(" + fallback + ")" : getter.apply(obj);
    }

    /** % thay đổi so với kỳ trước; null nếu kỳ trước = 0 (không so sánh được). */
    private static Double percentChange(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return round1((current - previous) * 100.0 / previous);
    }

    private static Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static long sum(long[] arr) {
        long total = 0;
        for (long v : arr) {
            total += v;
        }
        return total;
    }

    /** Định dạng số nguyên có phân tách hàng nghìn bằng dấu chấm (kiểu VN): 4820 → 4.820. */
    private static String formatInt(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.');
    }

    /**
     * Định dạng tiền VND gọn cho thẻ nhỏ trên dashboard: ≥1 tỷ → "1,2 tỷ ₫",
     * ≥1 triệu → "12,3 tr ₫", còn lại → số đầy đủ "123.000 ₫".
     */
    private static String formatMoney(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return "0 ₫";
        }
        double v = amount.doubleValue();
        if (v >= 1_000_000_000d) {
            return trimDec(v / 1_000_000_000d) + " tỷ ₫";
        }
        if (v >= 1_000_000d) {
            return trimDec(v / 1_000_000d) + " tr ₫";
        }
        return formatInt(amount.longValue()) + " ₫";
    }

    /** Làm tròn 1 chữ số thập phân, dùng dấu phẩy kiểu VN và bỏ ",0" thừa: 5,0 → "5"; 12,34 → "12,3". */
    private static String trimDec(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        if (r == Math.floor(r)) {
            return String.valueOf((long) r);
        }
        return String.format(Locale.US, "%.1f", r).replace('.', ',');
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
