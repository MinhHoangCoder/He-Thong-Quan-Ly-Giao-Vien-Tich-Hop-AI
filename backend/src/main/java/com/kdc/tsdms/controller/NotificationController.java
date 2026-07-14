package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.NotificationListResponse;
import com.kdc.tsdms.dto.NotificationResponse;
import com.kdc.tsdms.service.NotificationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Thông báo — /api/v1/notifications. Mọi endpoint chỉ cần đăng nhập;
 * Service tự giới hạn theo người dùng hiện tại.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Danh sách thông báo + số chưa đọc. */
    @GetMapping
    public NotificationListResponse list() {
        return service.list();
    }

    /** Chỉ số chưa đọc (poll nhẹ cho badge). */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unreadCount", service.unreadCount());
    }

    /** Đánh dấu 1 thông báo đã đọc. */
    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return service.markRead(id);
    }

    /** Đánh dấu tất cả đã đọc. */
    @PostMapping("/read-all")
    public NotificationListResponse markAllRead() {
        return service.markAllRead();
    }
}
