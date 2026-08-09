package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.NotificationListResponse;
import com.kdc.tsdms.dto.NotificationResponse;
import com.kdc.tsdms.entity.Notification;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.NotificationRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.repository.UserRoleRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 *   <li>{@code actionableAssignmentId} — kiểm tra một thông báo "cần hành động" hợp lệ và cho biết
 *       nó trỏ tới phân công nào; phần đổi trạng thái phân công thuộc về
 *       {@link AssignmentApprovalService}.</li>
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
    private final TeacherRepository teacherRepo;
    private final UserRoleRepository userRoleRepo;

    public NotificationService(
            NotificationRepository repo, TeacherRepository teacherRepo, UserRoleRepository userRoleRepo) {
        this.repo = repo;
        this.teacherRepo = teacherRepo;
        this.userRoleRepo = userRoleRepo;
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
     * Thông báo do HỆ THỐNG phát (tác vụ nền, không có người thao tác). Khác {@link #publish}:
     * không có luật "không tự gửi cho bản thân" vì không tồn tại người thao tác để so sánh —
     * bỏ qua bước đó thì admin mới nhận được tin phiếu của chính mình đã hết hạn.
     */
    public void publishSystem(Integer recipientUserId, String title, String content, String refEntity, Long refId) {
        if (recipientUserId == null) {
            return;
        }
        save(recipientUserId, title, content, "ASSIGNMENT", refEntity, refId, false);
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

    /**
     * Gửi thông báo tới MỌI NGƯỜI PHỤ TRÁCH một mảng việc, xác định qua QUYỀN (vd
     * {@code ATTENDANCE_MANAGE}) thay vì liệt kê tên role — đổi phân quyền thì danh sách
     * người nhận tự đổi theo, không phải sửa code.
     *
     * <p>Gộp thêm role ADMIN vì V3 cố tình không seed RolePermission cho ADMIN (nó đi tắt
     * ở {@code @PreAuthorize}); tra theo quyền thôi là admin không bao giờ nhận được gì.
     *
     * <p>Người vừa thao tác bị {@link #publish} loại ra — admin tự duyệt yêu cầu của mình
     * thì không cần tin nhắn báo lại chính việc mình vừa làm.
     */
    public void publishToPermission(
            String permissionCode, String title, String content, String type, String refEntity, Long refId) {
        Set<Integer> recipients = new LinkedHashSet<>(userRoleRepo.findAppUserIdsByPermissionCode(permissionCode));
        recipients.addAll(userRoleRepo.findAppUserIdsByRoleName("ADMIN"));
        for (Integer userId : recipients) {
            publish(userId, title, content, type, refEntity, refId, false);
        }
    }

    /* ═══════════════════════ HÀNH ĐỘNG của giáo viên (Xác nhận / Hủy) ═══════════════════════ */

    /**
     * Kiểm tra thông báo thuộc người gọi + còn đang chờ hành động, rồi trả về id PHÂN CÔNG mà
     * nó trỏ tới. Việc đổi trạng thái phân công nằm ở {@link AssignmentApprovalService} — service
     * này cố tình không biết gì về nghiệp vụ phân công để hai bên không phụ thuộc vòng nhau.
     */
    @Transactional(readOnly = true)
    public Integer actionableAssignmentId(Long notificationId) {
        Notification n = actionableOrThrow(notificationId);
        return assignmentIdOrThrow(n);
    }

    /**
     * Phân công mà một thông báo trỏ tới, CHỈ cần thông báo thuộc người gọi — dùng cho thao tác
     * ĐỌC (mở bảng lịch dạy chi tiết). Không đòi trạng thái PENDING như {@code
     * actionableAssignmentId}: nếu tab khác vừa xác nhận xong thì việc xem lại bảng vẫn phải mở
     * được, không nên nổ 409.
     */
    @Transactional(readOnly = true)
    public Integer ownedAssignmentId(Long notificationId) {
        return assignmentIdOrThrow(ownedOrThrow(notificationId));
    }

    private static Integer assignmentIdOrThrow(Notification n) {
        if (!"Assignment".equals(n.getRefEntity()) || n.getRefId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Thông báo này không gắn với phân công nào");
        }
        return n.getRefId().intValue();
    }

    /** Đọc lại một thông báo của chính người gọi (trả về client sau khi đã xử lý hành động). */
    @Transactional(readOnly = true)
    public NotificationResponse detail(Long id) {
        return NotificationResponse.fromEntity(ownedOrThrow(id));
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
