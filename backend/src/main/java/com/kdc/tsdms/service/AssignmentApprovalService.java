package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.AssignmentBulkResult;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Notification;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.NotificationRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Duyệt phân công: giáo viên XÁC NHẬN / TỪ CHỐI, admin ÉP DUYỆT / NHẮC, và quét phiếu HẾT HẠN.
 *
 * <p>Đây là chỗ DUY NHẤT chuyển phiếu từ "chờ" sang "có hiệu lực". Lịch dạy chỉ chảy sang màn
 * giáo viên / chấm công / lương / thống kê sau khi buổi được đưa lên APPROVED tại đây.
 *
 * <p>Tách khỏi {@link NotificationService} để tránh phụ thuộc vòng: service này gọi
 * NotificationService để phát thông báo, còn NotificationService không biết gì về nó.
 */
@Service
public class AssignmentApprovalService {

    /** Giáo viên tự bấm xác nhận. */
    private static final String SRC_TEACHER = "TEACHER";

    /** Admin ép duyệt thay giáo viên. */
    private static final String SRC_ADMIN = "ADMIN";

    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final ScheduleRepository scheduleRepo;
    private final NotificationRepository notificationRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final SubjectRepository subjectRepo;
    private final SchoolClassRepository classRepo;
    private final PeriodRepository periodRepo;
    private final NotificationService notificationService;
    private final TeacherTimeConflictChecker conflictChecker;
    private final ApplicationContext applicationContext;

    public AssignmentApprovalService(
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            ScheduleRepository scheduleRepo,
            NotificationRepository notificationRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            SubjectRepository subjectRepo,
            SchoolClassRepository classRepo,
            PeriodRepository periodRepo,
            NotificationService notificationService,
            TeacherTimeConflictChecker conflictChecker,
            ApplicationContext applicationContext) {
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.scheduleRepo = scheduleRepo;
        this.notificationRepo = notificationRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.subjectRepo = subjectRepo;
        this.classRepo = classRepo;
        this.periodRepo = periodRepo;
        this.notificationService = notificationService;
        this.conflictChecker = conflictChecker;
        this.applicationContext = applicationContext;
    }

    /* ═══════════════════ GIÁO VIÊN xác nhận / từ chối ═══════════════════ */

    /**
     * Giáo viên XÁC NHẬN lời mời dạy (bấm từ chuông thông báo) → phiếu ACTIVE, toàn bộ buổi lên
     * APPROVED, từ đó lịch mới hiện ở màn giáo viên và được tính công/lương/thống kê.
     */
    @Transactional
    public Assignment confirmByTeacher(Integer assignmentId) {
        Assignment a = pendingOrThrow(assignmentId);
        if (a.isExpiredPending()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Phiếu phân công đã quá hạn xác nhận (" + fmt(a.getConfirmDeadline())
                            + "). Vui lòng liên hệ trung tâm để được gửi lại.");
        }
        approve(a, SRC_TEACHER, null);
        closeOpenInvites(a.getId(), "CONFIRMED");
        notifyAdmin(a, "Giáo viên đã xác nhận lịch dạy", currentTeacherName() + " đã XÁC NHẬN " + describe(a) + ".");
        return a;
    }

    /**
     * Giáo viên TỪ CHỐI (bắt buộc lý do) → phiếu chuyển "Bị từ chối" kèm lý do, mọi buổi chưa
     * duyệt chuyển REJECTED nên khung giờ được nhả ra cho người khác.
     */
    @Transactional
    public Assignment rejectByTeacher(Integer assignmentId, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối");
        }
        Assignment a = pendingOrThrow(assignmentId);
        Integer userId = SecurityUtils.currentUserId();
        a.setStatus(AssignmentStatus.REJECTED);
        a.setRejectionReason(trimmed);
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);
        markSchedules(a.getId(), "REJECTED", trimmed, userId);
        closeOpenInvites(a.getId(), "CANCELLED");
        notifyAdmin(
                a,
                "Giáo viên đã từ chối lịch dạy",
                currentTeacherName() + " đã TỪ CHỐI " + describe(a) + " — Lý do: " + trimmed);
        return a;
    }

    /* ═══════════════════ ADMIN ép duyệt / nhắc ═══════════════════ */

    /**
     * Admin ÉP DUYỆT thay giáo viên (họp trực tiếp rồi, giáo viên không dùng app, hoặc phiếu đã
     * quá hạn). Ghi rõ nguồn duyệt là ADMIN để về sau còn phân biệt lịch nào giáo viên thật sự
     * đồng ý. Không ép duyệt được phiếu giáo viên đã chủ động từ chối.
     */
    @Transactional
    public Assignment forceApprove(Integer assignmentId, String note) {
        Assignment a = liveOrThrow(assignmentId);
        if (AssignmentStatus.REJECTED.equals(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Giáo viên đã từ chối phiếu này — hãy sửa rồi gửi lại (có thể đổi sang giáo viên khác)");
        }
        if (!AssignmentStatus.PENDING.equals(a.getStatus()) && !AssignmentStatus.EXPIRED.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ ép duyệt được phiếu đang chờ xác nhận hoặc đã hết hạn");
        }
        // Phiếu HẾT HẠN đã nhả chỗ, nên trong lúc nó nằm chờ khung giờ có thể đã bị phiếu khác
        // chiếm. Không soát lại ở đây thì ép duyệt sẽ tự tay tạo ra một ca trùng giờ thật.
        for (AssignmentSlot slot : slotRepo.findByAssignmentIdAndDeletedFalse(a.getId())) {
            Period p = periodRepo
                    .findById(slot.getPeriodId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Tiết của phân công không còn tồn tại"));
            conflictChecker.check(
                    a.getTeacherId(), slot.getDayOfWeek(), p, a.getStartDate(), a.getEndDate(), a.getId());
        }
        approve(a, SRC_ADMIN, note);
        closeOpenInvites(a.getId(), "CONFIRMED");
        notificationService.publishToTeacher(
                a.getTeacherId(),
                "Lịch dạy của bạn đã được duyệt",
                "Trung tâm đã duyệt " + describe(a) + " thay bạn"
                        + (note != null && !note.isBlank() ? " — Ghi chú: " + note.trim() : "")
                        + ". Lịch đã có hiệu lực.",
                "ASSIGNMENT",
                "Assignment",
                a.getId().longValue(),
                false);
        return a;
    }

    /** Admin gửi lại lời mời cho phiếu đang chờ (thông báo cũ dễ trôi mất trong chuông). */
    @Transactional
    public Assignment remind(Integer assignmentId) {
        Assignment a = liveOrThrow(assignmentId);
        if (!AssignmentStatus.PENDING.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ nhắc được phiếu đang chờ xác nhận");
        }
        // Đóng lời mời cũ trước rồi phát lời mời mới: tránh chuông có 2 nút Xác nhận cho cùng
        // một phiếu, bấm cái nào cũng được nhưng nhìn rất khó hiểu.
        closeOpenInvites(a.getId(), "CANCELLED");
        publishInvite(a, true);
        return a;
    }

    /* ═══════════════════ HÀNG LOẠT ═══════════════════ */

    /**
     * Chạy một thao tác trên nhiều phiếu, KHÔNG dừng ở phiếu lỗi đầu tiên: chọn 10 phiếu mà 1
     * phiếu đã hết hạn thì 9 phiếu còn lại vẫn phải xong, lỗi gom lại trả về cho người dùng.
     *
     * <p>Mỗi phiếu chạy trong giao dịch RIÊNG ({@code REQUIRES_NEW}) nên một phiếu rollback không
     * kéo theo cả mẻ.
     */
    public AssignmentBulkResult bulk(List<Integer> ids, BulkAction action, String note) {
        AssignmentBulkResult result = new AssignmentBulkResult();
        for (Integer id : ids) {
            try {
                switch (action) {
                    case REMIND -> self().remind(id);
                    case FORCE_APPROVE -> self().forceApprove(id, note);
                }
                result.ok();
            } catch (ApiException e) {
                result.fail(id, e.getMessage());
            }
        }
        return result;
    }

    /** Các thao tác chạy hàng loạt được (hủy hàng loạt do AssignmentService lo vì nó sở hữu cancel). */
    public enum BulkAction {
        REMIND,
        FORCE_APPROVE
    }

    /**
     * Tự tham chiếu qua proxy Spring để @Transactional trên từng phiếu có hiệu lực — gọi thẳng
     * {@code this.remind(...)} sẽ đi tắt qua proxy và mất ranh giới giao dịch.
     */
    private AssignmentApprovalService self() {
        return applicationContext.getBean(AssignmentApprovalService.class);
    }

    /* ═══════════════════ HẾT HẠN ═══════════════════ */

    /**
     * Quét phiếu chờ đã quá hạn → chuyển "Hết hạn" + báo người tạo phiếu. Chạy mỗi giờ.
     *
     * <p>Trạng thái hết hạn còn được tính TẠI CHỖ khi đọc dữ liệu
     * ({@link Assignment#isExpiredPending()}), nên màn hình và luật giữ chỗ vẫn đúng ngay cả khi
     * tác vụ này lỡ nhịp hoặc backend vừa khởi động lại — job chỉ lo phần ghi lại DB + báo tin.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void sweepExpired() {
        List<Assignment> overdue = assignmentRepo.findByStatusAndConfirmDeadlineBeforeAndDeletedFalse(
                AssignmentStatus.PENDING, LocalDateTime.now());
        for (Assignment a : overdue) {
            a.setStatus(AssignmentStatus.EXPIRED);
            a.setUpdatedAt(Instant.now());
            assignmentRepo.save(a);
            closeOpenInvites(a.getId(), "CANCELLED");
            if (a.getCreatedBy() != null) {
                notificationService.publishSystem(
                        a.getCreatedBy(),
                        "Phân công hết hạn xác nhận",
                        teacherName(a.getTeacherId()) + " không phản hồi " + describe(a) + " trước hạn "
                                + fmt(a.getConfirmDeadline()) + ". Phiếu đã hết hiệu lực — hãy sửa và gửi lại"
                                + " hoặc xếp giáo viên khác.",
                        "Assignment",
                        a.getId().longValue());
            }
        }
    }

    /* ═══════════════════ dùng chung ═══════════════════ */

    /**
     * Phát lời mời dạy (có nút Xác nhận/Từ chối) cho giáo viên của phiếu. Dùng chung cho cả lúc
     * tạo phiếu, lúc admin bấm Nhắc và lúc admin sửa rồi gửi lại — để giáo viên luôn nhận đúng
     * một mẫu nội dung, không có bản rút gọn thiếu thông tin.
     */
    public void publishInvite(Assignment a, boolean resend) {
        String title = resend ? "Nhắc lại: bạn có lịch dạy chờ xác nhận" : "Bạn được phân công lịch dạy mới";
        notificationService.publishToTeacher(
                a.getTeacherId(),
                title,
                describe(a) + " · Lịch: " + scheduleSummary(a)
                        + " · Từ " + fmtDate(a.getStartDate()) + " đến " + fmtDate(lastLessonDate(a))
                        + " · HẠN XÁC NHẬN: " + fmt(a.getConfirmDeadline())
                        + ". Quá hạn phiếu sẽ tự hết hiệu lực.",
                "ASSIGNMENT",
                "Assignment",
                a.getId().longValue(),
                true);
    }

    /** "Thứ 2 Tiết 1 lớp 1A1, Thứ 2 Tiết 2 lớp 1A2…" — giáo viên cần biết tiết nào vào lớp nào. */
    private String scheduleSummary(Assignment a) {
        StringBuilder sb = new StringBuilder();
        for (AssignmentSlot slot : slotRepo.findByAssignmentIdAndDeletedFalse(a.getId())) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(dayLabelVi(slot.getDayOfWeek()));
            periodRepo.findById(slot.getPeriodId()).ifPresent(p -> sb.append(" Tiết ")
                    .append(p.getPeriodNumber()));
            Integer classId = slot.getClassId() != null ? slot.getClassId() : a.getClassId();
            if (classId != null) {
                classRepo.findById(classId).ifPresent(c -> sb.append(" lớp ").append(c.getName()));
            }
        }
        return sb.length() == 0 ? "(chưa có tiết)" : sb.toString();
    }

    /** Ngày của buổi CUỐI đã sinh — phiếu không có ngày kết thúc thì vẫn nói được "đến ngày nào". */
    private LocalDate lastLessonDate(Assignment a) {
        if (a.getEndDate() != null) {
            return a.getEndDate();
        }
        return scheduleRepo.findByAssignmentIdAndDeletedFalse(a.getId()).stream()
                .map(s -> s.getStartTime().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(a.getStartDate());
    }

    private static String dayLabelVi(String code) {
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

    /**
     * Đóng mọi lời mời còn treo của một phiếu (khi phiếu đã được quyết hoặc bị thay thế) — để
     * chuông của giáo viên không còn nút bấm cho việc đã xong.
     */
    public void closeOpenInvites(Integer assignmentId, String actionStatus) {
        List<Notification> open = notificationRepo.findByRefEntityAndRefIdAndActionStatus(
                "Assignment", assignmentId.longValue(), "PENDING");
        for (Notification n : open) {
            n.setActionStatus(actionStatus);
            if (!n.isRead()) {
                n.setRead(true);
                n.setReadAt(Instant.now());
            }
            notificationRepo.save(n);
        }
    }

    /** Duyệt phiếu: phiếu ACTIVE + mọi buổi chưa duyệt lên APPROVED (gồm cả buổi đã qua). */
    private void approve(Assignment a, String source, String note) {
        Integer userId = SecurityUtils.currentUserId();
        a.setStatus(AssignmentStatus.ACTIVE);
        a.setConfirmedAt(LocalDateTime.now());
        a.setConfirmedByUserId(userId);
        a.setConfirmSource(source);
        a.setRejectionReason(null);
        if (note != null && !note.isBlank()) {
            a.setApprovalNote(note.trim());
        }
        a.setUpdatedAt(Instant.now());
        a.setUpdatedBy(userId);
        assignmentRepo.save(a);
        markSchedules(a.getId(), "APPROVED", null, userId);
    }

    /**
     * Đổi trạng thái các buổi CHƯA quyết (PENDING) của phiếu. Buổi đã CANCELLED/REJECTED trước
     * đó giữ nguyên — chúng thuộc về một quyết định cũ, không được hồi sinh ngầm.
     */
    private void markSchedules(Integer assignmentId, String status, String reason, Integer userId) {
        boolean approving = "APPROVED".equals(status);
        Instant now = Instant.now();
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(assignmentId)) {
            if (!"PENDING".equals(s.getStatus())) {
                continue;
            }
            s.setUpdatedBy(userId); // trigger TR_Schedule_StatusLog đọc cột này
            s.setStatus(status);
            s.setUpdatedAt(now);
            if (approving) {
                s.setApprovedByUserId(userId);
                s.setApprovedAt(now);
            } else {
                s.setRejectionReason(reason);
            }
            scheduleRepo.save(s);
        }
    }

    /** Phiếu phải đang chờ xác nhận và thuộc về CHÍNH giáo viên đang đăng nhập (chống IDOR). */
    private Assignment pendingOrThrow(Integer assignmentId) {
        Assignment a = liveOrThrow(assignmentId);
        Integer myTeacherId = teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .map(Teacher::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không có hồ sơ giáo viên"));
        if (!myTeacherId.equals(a.getTeacherId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Phân công này không phải của bạn");
        }
        if (!AssignmentStatus.PENDING.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Phiếu phân công này đã được xử lý");
        }
        return a;
    }

    private Assignment liveOrThrow(Integer assignmentId) {
        if (assignmentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu mã phân công");
        }
        return assignmentRepo
                .findByIdAndDeletedFalse(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công #" + assignmentId));
    }

    private void notifyAdmin(Assignment a, String title, String content) {
        if (a.getCreatedBy() != null) {
            notificationService.publish(
                    a.getCreatedBy(),
                    title,
                    content,
                    "ASSIGNMENT",
                    "Assignment",
                    a.getId().longValue());
        }
    }

    /** Mô tả ngắn một phiếu để nhét vào nội dung thông báo. */
    private String describe(Assignment a) {
        String school =
                schoolRepo.findById(a.getSchoolId()).map(School::getName).orElse("trường #" + a.getSchoolId());
        String subject =
                subjectRepo.findById(a.getSubjectId()).map(Subject::getName).orElse("môn #" + a.getSubjectId());
        return "phân công #" + a.getId() + " · " + school + " · " + subject;
    }

    private String teacherName(Integer teacherId) {
        return teacherRepo
                .findById(teacherId)
                .map(t -> (t.getLastName() + " " + t.getFirstName()).trim())
                .orElse("Giáo viên #" + teacherId);
    }

    private String currentTeacherName() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .map(t -> (t.getLastName() + " " + t.getFirstName()).trim())
                .orElse("Giáo viên");
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "—" : t.format(DEADLINE_FMT);
    }

    private static String fmtDate(LocalDate d) {
        return d == null ? "—" : d.format(DATE_FMT);
    }
}
