package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit + XÓA MỀM (IsDeleted/DeletedAt/DeletedBy). Bản ghi "xóa" chỉ gắn cờ, vẫn nằm
 * trong DB để giữ lịch sử; mọi truy vấn nghiệp vụ phải lọc {@code deleted = false}
 * (đặt tên method repository dạng {@code ...AndDeletedFalse}).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class SoftDeletableEntity extends AuditableEntity {

    @Column(name = "IsDeleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "DeletedAt")
    private Instant deletedAt;

    @Column(name = "DeletedBy")
    private Integer deletedBy;
}
