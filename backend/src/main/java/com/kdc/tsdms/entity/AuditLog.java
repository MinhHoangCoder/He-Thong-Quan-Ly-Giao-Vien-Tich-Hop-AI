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

/** Nhật ký hệ thống (bảng AuditLog) — ai làm gì (tạo/sửa/xóa/đăng nhập) trên toàn hệ thống. */
@Entity
@Table(name = "AuditLog")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id; // BIGINT

    /** Người thao tác (nullable — hành động hệ thống thì để trống). */
    @Column(name = "ActorUserId")
    private Integer actorUserId;

    /** CREATE | UPDATE | DELETE | LOGIN... */
    @Column(name = "Action", nullable = false)
    private String action;

    /** Tên bảng bị tác động. */
    @Column(name = "Entity")
    private String entity;

    /** Id bản ghi bị tác động (VARCHAR để chứa được cả khóa kép). */
    @Column(name = "EntityId")
    private String entityId;

    /** Giá trị trước/sau dạng JSON (NVARCHAR(MAX)). */
    @Column(name = "OldValue")
    private String oldValue;

    @Column(name = "NewValue")
    private String newValue;

    @Column(name = "IpAddress")
    private String ipAddress;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private Instant createdAt;
}
