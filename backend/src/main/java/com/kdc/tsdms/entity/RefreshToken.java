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

/**
 * Refresh token (bảng RefreshToken). Token thô KHÔNG lưu ở DB — chỉ lưu HASH,
 * nên kể cả lộ DB cũng không dùng lại được token. RevokedAt != null = đã thu hồi (đăng xuất).
 */
@Entity
@Table(name = "RefreshToken")
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "AppUserId", nullable = false)
    private Integer appUserId;

    @Column(name = "TokenHash", nullable = false)
    private String tokenHash;

    @Column(name = "ExpiresAt", nullable = false)
    private Instant expiresAt;

    @Column(name = "RevokedAt")
    private Instant revokedAt;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private Instant createdAt;

    /** Còn hiệu lực = chưa thu hồi và chưa hết hạn. */
    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
