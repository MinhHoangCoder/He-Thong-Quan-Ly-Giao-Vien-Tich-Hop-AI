package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.Notification;
import java.time.Instant;

/** Một thông báo trả về cho FE (bảng chuông thông báo trên topbar). */
public record NotificationResponse(
        Long id,
        String title,
        String content,
        String type,
        String refEntity,
        Long refId,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse fromEntity(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getType(),
                n.getRefEntity(),
                n.getRefId(),
                n.isRead(),
                n.getCreatedAt());
    }
}
