package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Thông báo trong app (bảng Notification) — vd: lịch được duyệt, có phân công mới. */
@Entity
@Table(name = "Notification")
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id; // BIGINT

    /** Người nhận (→ AppUser). */
    @Column(name = "RecipientUserId", nullable = false)
    private Integer recipientUserId;

    @Column(name = "Title", nullable = false)
    private String title;

    @Column(name = "Content")
    private String content;

    /** SCHEDULE | ASSIGNMENT | SYSTEM... */
    @Column(name = "Type")
    private String type;

    /** Tên bảng liên quan + id bản ghi — để FE bấm vào điều hướng tới đúng chỗ. */
    @Column(name = "RefEntity")
    private String refEntity;

    @Column(name = "RefId")
    private Long refId;

    @Column(name = "IsRead", nullable = false)
    private boolean read = false;

    /** true = thông báo cần người nhận bấm Xác nhận/Hủy (vd: phân công lịch dạy). */
    @Column(name = "RequiresAction", nullable = false)
    private boolean requiresAction = false;

    /** Trạng thái hành động: PENDING | CONFIRMED | CANCELLED (null nếu không cần hành động). */
    @Column(name = "ActionStatus")
    private String actionStatus;

    @Column(name = "ReadAt")
    private Instant readAt;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private Instant createdAt;
}
