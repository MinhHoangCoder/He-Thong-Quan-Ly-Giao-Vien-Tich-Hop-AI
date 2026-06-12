package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Quyền chi tiết (bảng Permission), vd: SCHEDULE_APPROVE — gán cho vai trò qua RolePermission. */
@Entity
@Table(name = "Permission")
@Getter
@Setter
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PermissionId")
    private Integer id;

    /** Mã quyền (unique), vd: SCHEDULE_APPROVE. */
    @Column(name = "Code", nullable = false)
    private String code;

    @Column(name = "Description")
    private String description;
}
