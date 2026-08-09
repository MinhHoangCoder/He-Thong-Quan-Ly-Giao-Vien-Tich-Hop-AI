package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.AttendanceAmendCreateRequest;
import com.kdc.tsdms.dto.AttendanceAmendResponse;
import com.kdc.tsdms.dto.AttendanceAmendReviewRequest;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.AttendanceAmendRequest;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceAmendRequestRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * YÊU CẦU BỔ SUNG CHẤM CÔNG — đường duy nhất để một buổi đã lỡ được ghi công.
 *
 * <p>Vì sao phải có: từ V25 nguồn công chính thức là giáo viên tự check-in, mà cửa sổ
 * check-in đóng khi hết tiết. Giáo viên quên bấm thì buổi đó thành Vắng và mất tiền, trong
 * khi họ có dạy thật. Không thể cho tự sửa (thành ra tự khai công), cũng không nên bắt gọi
 * điện cho kế toán (không để lại vết gì) — nên đi qua một yêu cầu có người duyệt.
 *
 * <p>Ba hàng rào chống lạm dụng:
 *
 * <ul>
 *   <li>chỉ xin được cho buổi ĐÃ KẾT THÚC của CHÍNH MÌNH
 *   <li>quá {@value #MAX_AMEND_DAYS} ngày thì hết hạn — không lật lại sổ cũ
 *   <li>kỳ lương đã chốt thì không gửi/duyệt được, kể cả còn trong hạn
 * </ul>
 */
@Service
public class AttendanceAmendService {

    /** Hạn gửi yêu cầu, tính từ ngày buổi dạy diễn ra. */
    private static final int MAX_AMEND_DAYS = 7;

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final AttendanceAmendRequestRepository amendRepo;
    private final AttendanceRepository attendanceRepo;
    private final ScheduleRepository scheduleRepo;
    private final TeacherRepository teacherRepo;
    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final AppUserRepository userRepo;
    private final DisplayNameResolver displayNameResolver;
    private final AttendanceService attendanceService;
    private final NotificationService notificationService;

    public AttendanceAmendService(
            AttendanceAmendRequestRepository amendRepo,
            AttendanceRepository attendanceRepo,
            ScheduleRepository scheduleRepo,
            TeacherRepository teacherRepo,
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            AppUserRepository userRepo,
            DisplayNameResolver displayNameResolver,
            AttendanceService attendanceService,
            NotificationService notificationService) {
        this.amendRepo = amendRepo;
        this.attendanceRepo = attendanceRepo;
        this.scheduleRepo = scheduleRepo;
        this.teacherRepo = teacherRepo;
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
        this.displayNameResolver = displayNameResolver;
        this.attendanceService = attendanceService;
        this.notificationService = notificationService;
    }

    /* ══════════════ GIÁO VIÊN GỬI ══════════════ */

    /** Giáo viên xin bổ sung công cho một buổi đã lỡ. */
    @Transactional
    public AttendanceAmendResponse create(AttendanceAmendCreateRequest req) {
        Integer teacherId = currentTeacherId();
        Schedule s = requireMyEndedSession(teacherId, req.scheduleId());

        if (!req.proposedCheckOut().isAfter(req.proposedCheckIn())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Giờ ra phải sau giờ vào");
        }

        LocalDate workDate = s.getStartTime().toLocalDate();
        if (BusinessTime.today().isAfter(workDate.plusDays(MAX_AMEND_DAYS))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Đã quá hạn xin bổ sung (" + MAX_AMEND_DAYS + " ngày kể từ ngày dạy " + workDate + ")");
        }
        if (attendanceService.isPeriodLocked(teacherId, workDate)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Bảng lương tháng " + workDate.getMonthValue() + "/" + workDate.getYear()
                            + " đã chốt — không bổ sung được chấm công của kỳ này.");
        }

        // Đã có công rồi thì không có gì để xin: chặn ở đây cho thông điệp rõ ràng, thay vì
        // để admin duyệt xong mới phát hiện dòng đó vốn đã Có mặt.
        Attendance existing =
                attendanceRepo.findFirstByScheduleIdOrderByIdAsc(s.getId()).orElse(null);
        if (existing != null && ("PRESENT".equals(existing.getStatus()) || "LATE".equals(existing.getStatus()))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Buổi này đã được ghi nhận \"" + AttendanceService.statusLabel(existing.getStatus())
                            + "\" — không cần xin bổ sung");
        }
        amendRepo.findFirstByScheduleIdAndStatus(s.getId(), PENDING).ifPresent(r -> {
            throw new ApiException(HttpStatus.CONFLICT, "Bạn đã gửi yêu cầu cho buổi này, đang chờ duyệt");
        });

        AttendanceAmendRequest r = new AttendanceAmendRequest();
        r.setTeacherId(teacherId);
        r.setScheduleId(s.getId());
        r.setReason(req.reason().trim());
        r.setProposedCheckIn(req.proposedCheckIn());
        r.setProposedCheckOut(req.proposedCheckOut());
        r.setStatus(PENDING);
        try {
            // flush ngay để UX_AttAmend_PendingSchedule bắt hai request gửi cùng lúc.
            amendRepo.saveAndFlush(r);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "Yêu cầu cho buổi này vừa được gửi — tải lại để xem");
        }

        String teacherName = teacherName(teacherId);
        notificationService.publishToPermission(
                "ATTENDANCE_MANAGE",
                "Yêu cầu bổ sung chấm công",
                teacherName + " xin bổ sung công buổi ngày " + workDate + " ("
                        + s.getStartTime().toLocalTime() + "–" + s.getEndTime().toLocalTime() + "). Lý do: "
                        + r.getReason(),
                "ATTENDANCE",
                "AttendanceAmendRequest",
                r.getId());
        return toResponse(r, teacherName, null);
    }

    /* ══════════════ ADMIN DUYỆT ══════════════ */

    /** Duyệt: ghi công cho buổi theo giờ giáo viên khai. */
    @Transactional
    public AttendanceAmendResponse approve(Long id, AttendanceAmendReviewRequest req) {
        AttendanceAmendRequest r = pendingOrThrow(id);
        Schedule s = scheduleRepo
                .findById(r.getScheduleId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Buổi dạy của yêu cầu này không còn"));

        Attendance a = attendanceService.applyApprovedAmend(
                s,
                r.getProposedCheckIn(),
                r.getProposedCheckOut(),
                req.status(),
                "Bổ sung theo yêu cầu: " + r.getReason());

        markReviewed(r, APPROVED, req.reviewNote());

        notificationService.publishToTeacher(
                r.getTeacherId(),
                "Yêu cầu bổ sung chấm công được duyệt",
                "Buổi ngày " + a.getWorkDate() + " đã được ghi nhận \"" + AttendanceService.statusLabel(a.getStatus())
                        + "\" (" + a.getCheckIn() + "–" + a.getCheckOut() + ").",
                "ATTENDANCE",
                "Attendance",
                a.getId(),
                false);
        return toResponse(r, teacherName(r.getTeacherId()), reviewerName(r.getReviewedByUserId()));
    }

    /** Từ chối: dòng chấm công giữ nguyên, giáo viên phải biết vì sao nên bắt buộc ghi lý do. */
    @Transactional
    public AttendanceAmendResponse reject(Long id, AttendanceAmendReviewRequest req) {
        AttendanceAmendRequest r = pendingOrThrow(id);
        if (req == null || req.reviewNote() == null || req.reviewNote().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối");
        }
        markReviewed(r, REJECTED, req.reviewNote());

        LocalDate workDate = scheduleRepo
                .findById(r.getScheduleId())
                .map(s -> s.getStartTime().toLocalDate())
                .orElse(null);
        notificationService.publishToTeacher(
                r.getTeacherId(),
                "Yêu cầu bổ sung chấm công bị từ chối",
                "Buổi ngày " + workDate + " không được ghi công. Lý do: " + r.getReviewNote(),
                "ATTENDANCE",
                "AttendanceAmendRequest",
                r.getId(),
                false);
        return toResponse(r, teacherName(r.getTeacherId()), reviewerName(r.getReviewedByUserId()));
    }

    /* ══════════════ ĐỌC ══════════════ */

    /** Hộp yêu cầu của admin — lọc theo trạng thái, bỏ trống thì lấy tất. */
    @Transactional(readOnly = true)
    public List<AttendanceAmendResponse> list(String status) {
        List<AttendanceAmendRequest> items = status == null || status.isBlank()
                ? amendRepo.findByOrderByIdDesc()
                : amendRepo.findByStatusOrderByIdDesc(status.toUpperCase());
        return enrichAll(items);
    }

    /** Yêu cầu của CHÍNH giáo viên đang đăng nhập (không nhận teacherId — chống IDOR). */
    @Transactional(readOnly = true)
    public List<AttendanceAmendResponse> listMine() {
        return enrichAll(amendRepo.findByTeacherIdOrderByIdDesc(currentTeacherId()));
    }

    /* ══════════════ helpers ══════════════ */

    /** Buổi dạy phải: của chính GV, đã duyệt, chưa xóa, và ĐÃ KẾT THÚC. */
    private Schedule requireMyEndedSession(Integer teacherId, Long scheduleId) {
        Schedule s = scheduleRepo
                .findById(scheduleId)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy buổi dạy"));
        if (!teacherId.equals(s.getTeacherId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Buổi dạy không thuộc về bạn");
        }
        if (!"APPROVED".equals(s.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Buổi dạy chưa được duyệt");
        }
        if (BusinessTime.now().isBefore(s.getEndTime())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Buổi dạy chưa kết thúc — hãy dùng nút Check in/out như bình thường");
        }
        return s;
    }

    private AttendanceAmendRequest pendingOrThrow(Long id) {
        AttendanceAmendRequest r = amendRepo
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu id=" + id));
        if (!PENDING.equals(r.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Yêu cầu này đã được xử lý");
        }
        return r;
    }

    private void markReviewed(AttendanceAmendRequest r, String status, String note) {
        r.setStatus(status);
        r.setReviewedByUserId(SecurityUtils.currentUserId());
        r.setReviewedAt(Instant.now());
        r.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        amendRepo.save(r);
    }

    private Integer currentTeacherId() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .map(Teacher::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không có hồ sơ giáo viên"));
    }

    private String teacherName(Integer teacherId) {
        return teacherRepo
                .findById(teacherId)
                .map(t -> (t.getLastName() + " " + t.getFirstName()).trim())
                .orElse("(GV #" + teacherId + ")");
    }

    private String reviewerName(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userRepo.findById(userId).map(displayNameResolver::resolve).orElse("Người dùng #" + userId);
    }

    private List<AttendanceAmendResponse> enrichAll(List<AttendanceAmendRequest> items) {
        Map<Integer, String> teacherNames = new HashMap<>();
        Map<Integer, String> reviewerNames = new HashMap<>();
        return items.stream()
                .map(r -> toResponse(
                        r,
                        teacherNames.computeIfAbsent(r.getTeacherId(), this::teacherName),
                        r.getReviewedByUserId() == null
                                ? null
                                : reviewerNames.computeIfAbsent(r.getReviewedByUserId(), this::reviewerName)))
                .toList();
    }

    /** Ghép thông tin buổi dạy vào DTO — admin duyệt cần thấy trường/lớp/môn ngay trên dòng. */
    private AttendanceAmendResponse toResponse(AttendanceAmendRequest r, String teacherName, String reviewerName) {
        AttendanceAmendResponse d = AttendanceAmendResponse.fromEntity(r, teacherName, reviewerName);
        Schedule s = scheduleRepo.findById(r.getScheduleId()).orElse(null);
        if (s == null) {
            return d;
        }
        d.workDate = s.getStartTime().toLocalDate();
        d.sessionStart = s.getStartTime();
        d.sessionEnd = s.getEndTime();
        Assignment asg = assignmentRepo.findById(s.getAssignmentId()).orElse(null);
        if (asg == null) {
            return d;
        }
        d.schoolName =
                schoolRepo.findById(asg.getSchoolId()).map(School::getName).orElse(null);
        d.subjectName =
                subjectRepo.findById(asg.getSubjectId()).map(Subject::getName).orElse(null);
        // Lớp lấy từ ô lịch gốc của buổi (V16 — mỗi tiết một lớp), fallback lớp cấp phân công.
        Integer classId = asg.getClassId();
        if (s.getSourceSlotId() != null) {
            AssignmentSlot slot = slotRepo.findById(s.getSourceSlotId()).orElse(null);
            if (slot != null && slot.getClassId() != null) {
                classId = slot.getClassId();
            }
        }
        if (classId != null) {
            d.className = classRepo.findById(classId).map(SchoolClass::getName).orElse(null);
        }
        return d;
    }
}
