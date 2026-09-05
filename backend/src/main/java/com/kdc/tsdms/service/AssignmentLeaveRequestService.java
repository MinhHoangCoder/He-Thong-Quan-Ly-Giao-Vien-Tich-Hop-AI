package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.LeaveRequestCreateRequest;
import com.kdc.tsdms.dto.LeaveRequestResponse;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentLeaveRequest;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentLeaveRequestRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ĐƠN XIN NGHỈ DẠY (V39): giáo viên gửi — admin duyệt hoặc từ chối.
 *
 * <p>Đơn KHÔNG tự hủy phân công. Được duyệt thì service gọi thẳng
 * {@link AssignmentService#cancel(Integer, LocalDate, String)} — cùng một đoạn mã với nút Hủy của
 * admin, nên hai đường không thể lệch nhau về cách xử lý buổi đã dạy, ngày kết thúc và thông báo.
 *
 * <p>Bản "gọn" theo đúng phạm vi đã chốt: không có màn quản lý đơn riêng, admin quyết ngay trên
 * chuông thông báo (thông báo phát ra có {@code requiresAction = true}).
 */
@Service
public class AssignmentLeaveRequestService {

    private static final DateTimeFormatter NGAY_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AssignmentLeaveRequestRepository repo;
    private final AssignmentRepository assignmentRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final SubjectRepository subjectRepo;
    private final AssignmentApprovalService approvalService;
    private final NotificationService notificationService;
    private final ApplicationContext applicationContext;

    public AssignmentLeaveRequestService(
            AssignmentLeaveRequestRepository repo,
            AssignmentRepository assignmentRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            SubjectRepository subjectRepo,
            AssignmentApprovalService approvalService,
            NotificationService notificationService,
            ApplicationContext applicationContext) {
        this.repo = repo;
        this.assignmentRepo = assignmentRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.subjectRepo = subjectRepo;
        this.approvalService = approvalService;
        this.notificationService = notificationService;
        this.applicationContext = applicationContext;
    }

    /* ═══════════════════════ GIÁO VIÊN ═══════════════════════ */

    /** Giáo viên gửi đơn xin nghỉ một phân công của CHÍNH MÌNH, kể từ ngày {@code effectiveDate}. */
    @Transactional
    public LeaveRequestResponse create(LeaveRequestCreateRequest req) {
        Teacher me = currentTeacher();
        Assignment a = assignmentRepo
                .findByIdAndDeletedFalse(req.assignmentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công."));
        if (!me.getId().equals(a.getTeacherId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Phân công này không thuộc về thầy/cô.");
        }
        if (!AssignmentStatus.ACTIVE.equals(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chỉ xin nghỉ được phân công ĐANG DẠY. Phân công chưa xác nhận thì thầy/cô từ chối"
                            + " thẳng ở lời mời trong chuông thông báo.");
        }
        if (a.isTerminated()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Phân công này đã có ngày kết thúc sớm ("
                            + a.getCancelEffectiveDate().format(NGAY_VN)
                            + "). Vui lòng liên hệ trung tâm nếu cần đổi ngày.");
        }
        LocalDate homNay = BusinessTime.today();
        if (req.effectiveDate().isBefore(homNay)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu nghỉ không được nằm trong quá khứ.");
        }
        if (a.getEndDate() != null && req.effectiveDate().isAfter(a.getEndDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Phân công này đã kết thúc ngày " + a.getEndDate().format(NGAY_VN) + " nên không cần xin nghỉ từ "
                            + req.effectiveDate().format(NGAY_VN) + ".");
        }
        // Chặn ở tầng service để người dùng nhận câu tiếng Việt, thay vì để index
        // UX_AssignmentLeaveRequest_Pending bung ra lỗi SQL thô.
        repo.findFirstByAssignmentIdAndStatus(a.getId(), AssignmentLeaveRequest.PENDING)
                .ifPresent(cu -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "Thầy/cô đã gửi đơn xin nghỉ phân công này (từ ngày "
                                    + cu.getEffectiveDate().format(NGAY_VN) + ") và đơn đang chờ trung tâm xử lý.");
                });

        AssignmentLeaveRequest don = new AssignmentLeaveRequest();
        don.setAssignmentId(a.getId());
        don.setTeacherId(me.getId());
        don.setEffectiveDate(req.effectiveDate());
        don.setReason(req.reason().trim());
        don.setStatus(AssignmentLeaveRequest.PENDING);
        don.setCreatedBy(SecurityUtils.currentUserId());
        repo.save(don);

        approvalService.notifyAdminsLeaveRequested(a, don.getId(), don.getEffectiveDate(), don.getReason());
        return toResponse(don, a);
    }

    /** "Đơn của tôi" — màn giáo viên, mới nhất lên đầu. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> mine() {
        return repo.findByTeacherIdOrderByIdDesc(currentTeacher().getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /* ═══════════════════════ ADMIN ═══════════════════════ */

    /** Hàng đợi đơn đang chờ xử lý. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> pending() {
        return repo.findByStatusOrderByIdDesc(AssignmentLeaveRequest.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * DUYỆT đơn → hủy phân công kể từ ngày trong đơn.
     *
     * <p>Gọi {@code AssignmentService.cancel} qua {@link ApplicationContext} chứ không tiêm thẳng:
     * AssignmentService đã tiêm AssignmentApprovalService, mà service này cũng tiêm
     * AssignmentApprovalService — tiêm thẳng theo chiều còn lại là một vòng phụ thuộc lúc khởi động.
     */
    @Transactional
    public LeaveRequestResponse approve(Integer id, String note) {
        AssignmentLeaveRequest don = pendingOrThrow(id);
        Assignment a = liveAssignment(don);
        String lyDo = "Giáo viên xin nghỉ: " + don.getReason();
        applicationContext.getBean(AssignmentService.class).cancel(don.getAssignmentId(), don.getEffectiveDate(), lyDo);
        decide(don, AssignmentLeaveRequest.APPROVED, note);
        // Thông báo hủy do cancel() phát đã nói "vì sao"; tin này trả lời đúng câu giáo viên đang
        // chờ: ĐƠN CỦA TÔI được duyệt hay không.
        approvalService.notifyTeacherLeaveDecision(a, don.getEffectiveDate(), true, note);
        return toResponse(don, a);
    }

    /** TỪ CHỐI đơn — bắt buộc nêu lý do, vì giáo viên phải biết mình vẫn phải đi dạy và vì sao. */
    @Transactional
    public LeaveRequestResponse reject(Integer id, String note) {
        if (note == null || note.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối đơn.");
        }
        AssignmentLeaveRequest don = pendingOrThrow(id);
        Assignment a = liveAssignment(don);
        decide(don, AssignmentLeaveRequest.REJECTED, note);
        approvalService.notifyTeacherLeaveDecision(a, don.getEffectiveDate(), false, note);
        return toResponse(don, a);
    }

    /* ═══════════════════════ helpers ═══════════════════════ */

    private void decide(AssignmentLeaveRequest don, String status, String note) {
        Integer userId = SecurityUtils.currentUserId();
        don.setStatus(status);
        don.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        don.setDecidedByUserId(userId);
        don.setDecidedAt(Instant.now());
        don.setUpdatedAt(Instant.now());
        don.setUpdatedBy(userId);
        repo.save(don);
        // Đơn được phát cho MỌI người có quyền quản lý phân công → tắt nút ở chuông của tất cả,
        // không riêng người vừa bấm.
        notificationService.closePendingActions(
                "AssignmentLeaveRequest",
                don.getId().longValue(),
                AssignmentLeaveRequest.APPROVED.equals(status) ? "CONFIRMED" : "CANCELLED");
    }

    private AssignmentLeaveRequest pendingOrThrow(Integer id) {
        AssignmentLeaveRequest don =
                repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn."));
        if (!AssignmentLeaveRequest.PENDING.equals(don.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Đơn này đã được xử lý.");
        }
        return don;
    }

    private Assignment liveAssignment(AssignmentLeaveRequest don) {
        return assignmentRepo
                .findByIdAndDeletedFalse(don.getAssignmentId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "Phân công của đơn này đã bị xóa — đơn không còn hiệu lực."));
    }

    private Teacher currentTeacher() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN, "Tài khoản này chưa được liên kết với hồ sơ giáo viên."));
    }

    private LeaveRequestResponse toResponse(AssignmentLeaveRequest don) {
        return toResponse(don, assignmentRepo.findById(don.getAssignmentId()).orElse(null));
    }

    private LeaveRequestResponse toResponse(AssignmentLeaveRequest don, Assignment a) {
        String teacherName = teacherRepo
                .findById(don.getTeacherId())
                .map(t -> (t.getLastName() + " " + t.getFirstName()).trim())
                .orElse("Giáo viên #" + don.getTeacherId());
        String schoolName = a == null
                ? null
                : schoolRepo.findById(a.getSchoolId()).map(School::getName).orElse(null);
        String subjectName = a == null
                ? null
                : subjectRepo.findById(a.getSubjectId()).map(Subject::getName).orElse(null);
        return LeaveRequestResponse.fromEntity(don, teacherName, schoolName, subjectName);
    }
}
