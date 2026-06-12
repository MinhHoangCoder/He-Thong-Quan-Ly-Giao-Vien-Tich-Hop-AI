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
 * Tài khoản đăng nhập (bảng AppUser) — điểm xác thực chung cho cả 4 actor.
 * Map thẳng vào cột PascalCase của SQL Server nhờ @Column.
 */
@Entity
@Table(name = "AppUser")
@Getter
@Setter
public class AppUser extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // SQL Server IDENTITY
    @Column(name = "AppUserId")
    private Integer id;

    @Column(name = "Username", nullable = false)
    private String username;

    /** Mật khẩu đã băm BCrypt — KHÔNG bao giờ lưu mật khẩu thô. */
    @Column(name = "PasswordHash", nullable = false)
    private String passwordHash;

    @Column(name = "Email", nullable = false)
    private String email;

    @Column(name = "FullName", nullable = false)
    private String fullName;

    @Column(name = "Phone")
    private String phone;

    /** ACTIVE | INACTIVE | LOCKED */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "LastLoginAt")
    private Instant lastLoginAt;
}
