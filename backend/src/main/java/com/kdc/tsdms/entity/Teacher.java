package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Hồ sơ giáo viên (bảng Teacher) — nối 1-1 với AppUser qua AppUserId. */
@Entity
@Table(name = "Teacher")
@Getter
@Setter
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TeacherId")
    private Integer id;

    @Column(name = "AppUserId", nullable = false)
    private Integer appUserId;

    @Column(name = "BranchId", nullable = false)
    private Integer branchId;

    @Column(name = "FullName", nullable = false)
    private String fullName;

    /** FULL_TIME | PART_TIME | CONTRACT (nullable). */
    @Column(name = "EmploymentType")
    private String employmentType;

    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "IsDeleted", nullable = false)
    private boolean deleted = false;
}
