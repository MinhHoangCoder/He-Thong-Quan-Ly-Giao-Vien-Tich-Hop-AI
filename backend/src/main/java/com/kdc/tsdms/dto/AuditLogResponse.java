package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.AuditLog;
import java.time.Instant;

/**
 * Một dòng nhật ký hệ thống.
 *
 * <p>{@code actorName} là TÊN ĐĂNG NHẬP chứ không phải id: người đọc nhật ký muốn biết "ai",
 * và một con số 42 không trả lời được câu đó. Tài khoản đã bị xóa vẫn phải hiện tên — giấu đi
 * thì dòng nhật ký mất luôn phần quan trọng nhất của nó.
 */
public record AuditLogResponse(
        Long id,
        Instant createdAt,
        Integer actorUserId,
        String actorName,
        String action,
        String actionLabel,
        String entity,
        String entityId,
        String oldValue,
        String newValue,
        String ipAddress) {

    public static AuditLogResponse from(AuditLog a, String actorName, String actionLabel) {
        return new AuditLogResponse(
                a.getId(),
                a.getCreatedAt(),
                a.getActorUserId(),
                actorName,
                a.getAction(),
                actionLabel,
                a.getEntity(),
                a.getEntityId(),
                a.getOldValue(),
                a.getNewValue(),
                a.getIpAddress());
    }
}
