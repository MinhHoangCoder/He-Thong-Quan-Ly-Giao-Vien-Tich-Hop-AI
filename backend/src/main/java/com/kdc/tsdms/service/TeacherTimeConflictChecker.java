package com.kdc.tsdms.service;

import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Luật chống một giáo viên bị xếp dạy hai nơi cùng khung giờ.
 *
 * <p>So theo GIỜ THẬT của tiết chứ không theo {@code periodId}: mỗi trường có bộ khung tiết
 * riêng nên "tiết 1" của hai trường là hai id khác nhau mà giờ vẫn đè lên nhau.
 *
 * <p>Tách riêng thành component vì có HAI đường vào cần cùng một luật: lúc tạo/sửa phiếu
 * ({@link AssignmentService}) và lúc admin ép duyệt một phiếu đã hết hạn
 * ({@link AssignmentApprovalService}) — phiếu hết hạn đã nhả chỗ nên trong lúc nó nằm chờ,
 * khung giờ có thể đã bị người khác chiếm.
 */
@Component
public class TeacherTimeConflictChecker {

    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final PeriodRepository periodRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final TeacherRepository teacherRepo;

    public TeacherTimeConflictChecker(
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            PeriodRepository periodRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            TeacherRepository teacherRepo) {
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.periodRepo = periodRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.teacherRepo = teacherRepo;
    }

    /**
     * Ném 409 nếu giáo viên đã có ô lịch đè giờ với {@code period} vào {@code dayOfWeek} trong
     * giai đoạn [startDate, endDate].
     *
     * @param ignoreAssignmentId bỏ qua ô lịch của chính phiếu này (khi sửa / duyệt lại chính nó)
     */
    public void check(
            Integer teacherId,
            String dayOfWeek,
            Period period,
            LocalDate startDate,
            LocalDate endDate,
            Integer ignoreAssignmentId) {
        for (AssignmentSlot existing : slotRepo.findByTeacherIdAndDayOfWeekAndDeletedFalse(teacherId, dayOfWeek)) {
            if (ignoreAssignmentId != null && ignoreAssignmentId.equals(existing.getAssignmentId())) {
                continue;
            }
            Assignment other = assignmentRepo
                    .findByIdAndDeletedFalse(existing.getAssignmentId())
                    .orElse(null);
            // Phiếu CHỜ XÁC NHẬN cũng giữ chỗ (chỉ nhả ra khi quá hạn) — xem holdsTimeSlot().
            if (other == null || !other.holdsTimeSlot()) {
                continue;
            }
            if (!datesOverlap(startDate, endDate, other.getStartDate(), other.getEndDate())) {
                continue;
            }
            Period otherPeriod = periodRepo.findById(existing.getPeriodId()).orElse(null);
            if (!timeOverlaps(period, otherPeriod)) {
                continue;
            }
            throw new ApiException(HttpStatus.CONFLICT, message(dayOfWeek, period, otherPeriod, existing, other));
        }
    }

    /**
     * Ném 409 nếu LỚP đã có giáo viên KHÁC dạy đè giờ với {@code period} vào {@code dayOfWeek}.
     *
     * <p>Chiều ngược của {@link #check}: luật kia hỏi "giáo viên này có bận không", luật này hỏi
     * "lớp này có ai đang dạy chưa". Thiếu nó thì xếp ba giáo viên vào cùng một lớp, cùng một
     * tiết là hợp lệ — mà lớp chỉ có một, trong khi cả ba đều sinh buổi dạy và đều được tính
     * công. Cùng một tiết học trả tiền ba lần.
     *
     * <p>Chỉ so giữa các giáo viên KHÁC nhau: một giáo viên có nhiều ô lịch cho cùng lớp trong
     * một thứ là chuyện bình thường (dạy liền tiết), và trường hợp chính họ tự đè giờ đã có
     * {@link #check} lo.
     *
     * @param classId lớp của ô lịch; {@code null} (dữ liệu cũ chưa gắn lớp) thì bỏ qua
     */
    public void checkClass(
            Integer classId,
            Integer teacherId,
            String dayOfWeek,
            Period period,
            LocalDate startDate,
            LocalDate endDate,
            Integer ignoreAssignmentId) {
        if (classId == null) {
            return;
        }
        for (AssignmentSlot existing : slotRepo.findByClassIdAndDayOfWeekAndDeletedFalse(classId, dayOfWeek)) {
            if (ignoreAssignmentId != null && ignoreAssignmentId.equals(existing.getAssignmentId())) {
                continue;
            }
            if (teacherId != null && teacherId.equals(existing.getTeacherId())) {
                continue;
            }
            Assignment other = assignmentRepo
                    .findByIdAndDeletedFalse(existing.getAssignmentId())
                    .orElse(null);
            if (other == null || !other.holdsTimeSlot()) {
                continue;
            }
            if (!datesOverlap(startDate, endDate, other.getStartDate(), other.getEndDate())) {
                continue;
            }
            Period otherPeriod = periodRepo.findById(existing.getPeriodId()).orElse(null);
            if (!timeOverlaps(period, otherPeriod)) {
                continue;
            }
            throw new ApiException(
                    HttpStatus.CONFLICT, classMessage(classId, dayOfWeek, period, otherPeriod, existing, other));
        }
    }

    private String classMessage(
            Integer classId,
            String dayOfWeek,
            Period period,
            Period otherPeriod,
            AssignmentSlot existing,
            Assignment other) {
        String className = classRepo.findById(classId).map(SchoolClass::getName).orElse("đã chọn");
        String teacherName = teacherRepo
                .findById(existing.getTeacherId())
                .map(t -> (t.getLastName() + " " + t.getFirstName()).trim())
                .orElse("một giáo viên khác");
        return "Không thể tạo phân công — lớp " + className + " đã được bố trí cho " + teacherName
                + " trong khung giờ " + dayLabelVi(dayOfWeek) + ", " + timeRange(otherPeriod)
                + " (tiết " + otherPeriod.getPeriodNumber() + ")" + pendingNote(other) + ".";
    }

    /** Phiếu chưa được xác nhận vẫn giữ chỗ — nói rõ để người xếp lịch biết vì sao bị chặn. */
    private static String pendingNote(Assignment other) {
        return AssignmentStatus.PENDING.equals(other.getStatus()) ? ", theo lịch đang chờ giáo viên xác nhận" : "";
    }

    private String message(
            String dayOfWeek, Period period, Period otherPeriod, AssignmentSlot existing, Assignment other) {
        String schoolName =
                schoolRepo.findById(other.getSchoolId()).map(School::getName).orElse("một trường khác");
        String className = existing.getClassId() == null
                ? null
                : classRepo
                        .findById(existing.getClassId())
                        .map(SchoolClass::getName)
                        .orElse(null);
        return "Không thể tạo phân công — giáo viên đã có lịch dạy trùng khung giờ "
                + dayLabelVi(dayOfWeek) + ", " + timeRange(period) + " (tiết " + period.getPeriodNumber()
                + "): tiết " + otherPeriod.getPeriodNumber() + " (" + timeRange(otherPeriod) + ") tại "
                + schoolName + (className != null ? ", lớp " + className : "") + pendingNote(other) + ".";
    }

    /** Hai khung tiết có giao nhau về thời gian không (null = không xác định được → bỏ qua). */
    public static boolean timeOverlaps(Period a, Period b) {
        if (a == null || b == null || a.getStartTime() == null || b.getStartTime() == null) {
            return false;
        }
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }

    /** Hai giai đoạn có chồng nhau không (endDate null = vô thời hạn). */
    public static boolean datesOverlap(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return !(aEnd != null && aEnd.isBefore(bStart) || bEnd != null && bEnd.isBefore(aStart));
    }

    public static String timeRange(Period p) {
        return p == null ? "" : p.getStartTime() + "–" + p.getEndTime();
    }

    static String dayLabelVi(String code) {
        return switch (code) {
            case "MON" -> "Thứ 2";
            case "TUE" -> "Thứ 3";
            case "WED" -> "Thứ 4";
            case "THU" -> "Thứ 5";
            case "FRI" -> "Thứ 6";
            case "SAT" -> "Thứ 7";
            case "SUN" -> "Chủ nhật";
            default -> code;
        };
    }
}
