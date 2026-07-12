package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.NotificationListResponse;
import com.kdc.tsdms.dto.NotificationResponse;
import com.kdc.tsdms.entity.Notification;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.NotificationRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Thông báo — mỗi người dùng chỉ thấy/sửa thông báo của CHÍNH MÌNH
 * (chống IDOR: mọi thao tác ép theo {@link SecurityUtils#currentUserId()}).
 */
@Service
public class NotificationService {

    /** Số thông báo tối đa trả về cho bảng chuông. */
    private static final int MAX_ITEMS = 30;

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

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
        Integer userId = currentUser();
        Notification n =
                repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        if (!n.getRecipientUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không thể truy cập thông báo này");
        }
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

    private Integer currentUser() {
        Integer userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return userId;
    }
}
