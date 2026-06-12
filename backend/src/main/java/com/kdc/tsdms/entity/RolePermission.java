package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Bảng nối vai trò ⇄ quyền (nhiều-nhiều). Khóa kép (RoleId, PermissionId). */
@Entity
@Table(name = "RolePermission")
@IdClass(RolePermissionId.class)
@Getter
@Setter
public class RolePermission {

    @Id
    @Column(name = "RoleId")
    private Integer roleId;

    @Id
    @Column(name = "PermissionId")
    private Integer permissionId;

    public RolePermission() {}

    public RolePermission(Integer roleId, Integer permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }
}
