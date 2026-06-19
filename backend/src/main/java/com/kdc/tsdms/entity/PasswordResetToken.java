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

/** Token đặt lại mật khẩu (bảng PasswordResetToken). Lưu HASH, dùng 1 lần, hết hạn ngắn. */
@Entity
@Table(name = "PasswordResetToken")
@Getter
@Setter
public class PasswordResetToken {

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

    @Column(name = "UsedAt")
    private Instant usedAt;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private Instant createdAt;

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}
