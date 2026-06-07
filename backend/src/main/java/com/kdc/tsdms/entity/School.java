package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Hồ sơ trường khách hàng (bảng School) — có thể nối 1-1 với AppUser (tài khoản trường). */
@Entity
@Table(name = "School")
@Getter
@Setter
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SchoolId")
    private Integer id;

    @Column(name = "BranchId", nullable = false)
    private Integer branchId;

    @Column(name = "Name", nullable = false)
    private String name;

    @Column(name = "AppUserId")
    private Integer appUserId;

    @Column(name = "ContactPerson")
    private String contactPerson;

    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "IsDeleted", nullable = false)
    private boolean deleted = false;
}
