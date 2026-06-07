package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Bảng nối tài khoản ⇄ vai trò (nhiều-nhiều). Khóa kép (AppUserId, RoleId). */
@Entity
@Table(name = "UserRole")
@IdClass(UserRoleId.class)
@Getter
@Setter
public class UserRole {

    @Id
    @Column(name = "AppUserId")
    private Integer appUserId;

    @Id
    @Column(name = "RoleId")
    private Integer roleId;

    @Column(name = "AssignedAt", insertable = false, updatable = false)
    private Instant assignedAt;

    public UserRole() {}

    public UserRole(Integer appUserId, Integer roleId) {
        this.appUserId = appUserId;
        this.roleId = roleId;
    }
}
