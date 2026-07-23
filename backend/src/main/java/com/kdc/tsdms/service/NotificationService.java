package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.NotificationListResponse;
import com.kdc.tsdms.dto.NotificationResponse;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.Notification;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.NotificationRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Thông báo — mỗi người dùng chỉ thấy/sửa thông báo của CHÍNH MÌNH
 * (chống IDOR: mọi thao tác ép theo {@link SecurityUtils#currentUserId()}).
 *
 * <p>Ngoài phần đọc (chuông topbar), service này còn:
 * <ul>
 *   <li>{@code publish*} — các service khác (Phân công/Chấm công/Bảng lương) gọi để PHÁT thông báo.</li>
 *   <li>{@code confirmAction}/{@code cancelAction} — giáo viên Xác nhận/Hủy một phân công lịch dạy;
 *       kéo theo cập nhật buổi dạy + báo ngược cho admin đã phân công.</li>
 * </ul>
 */
@Service
public class NotificationService {

    /** Số thông báo tối đa trả về cho bảng chuông. */
    private static final int MAX_ITEMS = 30;

    /** Giới hạn cột DB (NVARCHAR): Title(200) / Content(1000). */
    private static final int TITLE_MAX = 200;

    private static final int CONTENT_MAX = 1000;

    private final NotificationRepository repo;
    private final AssignmentRepository assignmentRepo;
    private final ScheduleRepository scheduleRepo;
    private final TeacherRepository teacherRepo;

    public NotificationService(
            NotificationRepository repo,
            AssignmentRepository assignmentRepo,
            ScheduleRepository scheduleRepo,
            TeacherRepository teacherRepo) {
        this.repo = repo;
        this.assignmentRepo = assignmentRepo;
        this.scheduleRepo = scheduleRepo;
        this.teacherRepo = teacherRepo;
    }

    /* ═══════════════════════ ĐỌC (chuông topbar) ═══════════════════════ */

    @Transactional(readOnly = true)
    public NotificationListResponse list() {
        Integer userId = currentUser();
        List<NotificationResponse> items = repo.findByRecipientUserIdOrderByIdDesc(userId).stream()
                .limit(MAX_ITEMS)
                .map(NotificationResponse::fromEntity)
                .toList();
        return new NotificationListResponse(repo.countByRecipientUserIdAndReadFalse(userId), items);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repo.countByRecipientUserIdAndReadFalse(currentUser());
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification n = ownedOrThrow(id);
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(Instant.now());
            repo.save(n);
        }
        return NotificationResponse.fromEntity(n);
    }

    @Transactional
    public NotificationListResponse markAllRead() {
        Integer userId = currentUser();
        Instant now = Instant.now();
        List<Notification> unread = repo.findByRecipientUserIdAndReadFalse(userId);
        for (Notification n : unread) {
            n.setRead(true);
            n.setReadAt(now);
        }
        repo.saveAll(unread);
        return list();
    }

    /* ═══════════════════════ PHÁT thông báo (cho service khác) ═══════════════════════ */

    /** Gửi thông báo THƯỜNG (không có nút hành động) tới một tài khoản. */
    public void publish(
            Integer recipientUserId, String title, String content, String type, String refEntity, Long refId) {
        publish(recipientUserId, title, content, type, refEntity, refId, false);
    }

    /**
     * Gửi thông báo tới một tài khoản. Bỏ qua nếu người nhận trống hoặc CHÍNH là người thao tác
     * (không tự gửi thông báo cho bản thân). {@code requiresAction=true} → tạo trạng thái PENDING
     * để FE hiển thị nút Xác nhận/Hủy.
     */
    public void publish(
            Integer recipientUserId,
            String title,
            String content,
            String type,
            String refEntity,
            Long refId,
            boolean requiresAction) {
        if (recipientUserId == null) {
            return;
        }
        Integer actor = SecurityUtils.currentUserId();
        if (recipientUserId.equals(actor)) {
            return; // không tự gửi thông báo cho người vừa thao tác
        }
        save(recipientUserId, title, content, type, refEntity, refId, requiresAction);
    }

    /**
     * Gửi thông báo tới GIÁO VIÊN theo teacherId (tự tra tài khoản AppUser tương ứng).
     * No-op nếu giáo viên chưa gắn tài khoản.
     */
    public void publishToTeacher(
            Integer teacherId,
            String title,
            String content,
            String type,
            String refEntity,
            Long refId,
            boolean requiresAction) {
        if (teacherId == null) {
            return;
        }
        Integer appUserId =
                teacherRepo.findById(teacherId).map(Teacher::getAppUserId).orElse(null);
        publish(appUserId, title, content, type, refEntity, refId, requiresAction);
    }

    /* ═══════════════════════ HÀNH ĐỘNG của giáo viên (Xác nhận / Hủy) ═══════════════════════ */

    /** Giáo viên XÁC NHẬN nhận lịch dạy → giữ buổi APPROVED + báo lại cho admin đã phân công. */
    @Transactional
    public NotificationResponse confirmAction(Long id) {
        Notification n = actionableOrThrow(id);
        n.setActionStatus("CONFIRMED");
        markReadNow(n);
        repo.save(n);
        notifyAdminOfDecision(n, true, null);
        return NotificationResponse.fromEntity(n);
    }

    /**
     * Giáo viên TỪ CHỐI lịch dạy (bắt buộc lý do) → hủy phân công + các buổi TƯƠNG LAI chuyển
     * REJECTED (kèm lý do), rồi báo lại cho admin đã phân công.
     */
    @Transactional
    public NotificationResponse cancelAction(Long id, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối");
        }
        Notification n = actionableOrThrow(id);
        declineAssignment(n.getRefId(), trimmed);
        n.setActionStatus("CANCELLED");
        markReadNow(n);
        repo.save(n);
        notifyAdminOfDecision(n, false, trimmed);
        return NotificationResponse.fromEntity(n);
    }

    /** Hủy phân công + đưa các buổi tương lai về REJECTED (giữ nguyên buổi đã dạy). */
    private void declineAssignment(Long refId, String reason) {
        if (refId == null) {
            return;
        }
        Integer assignmentId = refId.intValue();
        Integer userId = SecurityUtils.currentUserId();
        Instant now = Instant.now();
        Assignment a = assignmentRepo.findByIdAndDeletedFalse(assignmentId).orElse(null);
        if (a != null && !"CANCELLED".equals(a.getStatus())) {
            a.setStatus("CANCELLED");
            a.setUpdatedBy(userId);
            a.setUpdatedAt(now);
            assignmentRepo.save(a);
        }
        LocalDateTime nowLocal = LocalDateTime.now();
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(assignmentId)) {
            if (!"REJECTED".equals(s.getStatus())
                    && !"CANCELLED".equals(s.getStatus())
                    && s.getStartTime().isAfter(nowLocal)) {
                s.setUpdatedBy(userId); // trigger TR_Schedule_StatusLog đọc cột này
                s.setStatus("REJECTED");
                s.setRejectionReason(reason);
                s.setUpdatedAt(now);
                scheduleRepo.save(s);
            }
        }
    }

    /** Báo cho admin đã tạo phân công biết giáo viên đã Xác nhận / Từ chối (kèm lý do). */
    private void notifyAdminOfDecision(Notification original, boolean confirmed, String reason) {
        if (original.getRefId() == null) {
            return;
        }
        Assignment a = assignmentRepo.findById(original.getRefId().intValue()).orElse(null);
        if (a == null || a.getCreatedBy() == null) {
            return;
        }
        String teacher = currentTeacherName();
        String title = confirmed ? "Giáo viên đã xác nhận lịch dạy" : "Giáo viên đã từ chối lịch dạy";
        StringBuilder content = new StringBuilder(teacher)
                .append(confirmed ? " đã XÁC NHẬN" : " đã TỪ CHỐI")
                .append(" phân công");
        if (original.getContent() != null && !original.getContent().isBlank()) {
            content.append(": ").append(original.getContent());
        }
        if (!confirmed && reason != null) {
            content.append(" — Lý do: ").append(reason);
        }
        save(a.getCreatedBy(), title, content.toString(), "ASSIGNMENT", "Assignment", original.getRefId(), false);
    }

    /* ═══════════════════════ helpers ═══════════════════════ */

    private Notification save(
            Integer recipientUserId,
            String title,
            String content,
            String type,
            String refEntity,
            Long refId,
            boolean requiresAction) {
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setTitle(clip(title, TITLE_MAX));
        n.setContent(clip(content, CONTENT_MAX));
        n.setType(type);
        n.setRefEntity(refEntity);
        n.setRefId(refId);
        n.setRequiresAction(requiresAction);
        n.setActionStatus(requiresAction ? "PENDING" : null);
        n.setRead(false);
        return repo.save(n);
    }

    private void markReadNow(Notification n) {
        n.setRead(true);
        n.setReadAt(Instant.now());
    }

    /** Thông báo phải thuộc người gọi + đang cần hành động + chưa xử lý. */
    private Notification actionableOrThrow(Long id) {
        Notification n = ownedOrThrow(id);
        if (!n.isRequiresAction()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Thông báo này không cần xác nhận");
        }
        if (!"PENDING".equals(n.getActionStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Thông báo đã được xử lý");
        }
        return n;
    }

    private Notification ownedOrThrow(Long id) {
        Integer userId = currentUser();
        Notification n =
                repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        if (!n.getRecipientUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không thể truy cập thông báo này");
        }
        return n;
    }

    private String currentTeacherName() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .map(t -> (t.getLastName() + " " + t.getFirstName()).trim())
                .orElse("Giáo viên");
    }

    private Integer currentUser() {
        Integer userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return userId;
    }

    private static String clip(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }
}
