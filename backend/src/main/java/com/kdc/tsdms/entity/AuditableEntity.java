package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Bộ 4 cột audit chuẩn (CreatedAt/CreatedBy/UpdatedAt/UpdatedBy) dùng chung cho
 * các bảng
 * có đủ bộ này. {@code @MappedSuperclass} = KHÔNG phải bảng riêng; các cột được
 * "dán" vào
 * bảng của entity con.
 *
 * <p>
 * CreatedAt do DB tự điền (DEFAULT SYSUTCDATETIME()) → chỉ đọc, không cho
 * Hibernate ghi.
 * UpdatedAt/UpdatedBy do tầng service tự set khi sửa (dự án chưa bật JPA
 * Auditing).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "CreatedBy")
    private Integer createdBy;

    @Column(name = "UpdatedAt")
    private Instant updatedAt;

    @Column(name = "UpdatedBy")
    private Integer updatedBy;
}
