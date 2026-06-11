package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Nhân viên trung tâm (bảng Employee) — nối 1-1 với AppUser, thuộc 1 chi nhánh. */
@Entity
@Table(name = "Employee")
@Getter
@Setter
public class Employee extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EmployeeId")
    private Integer id;

    @Column(name = "AppUserId", nullable = false)
    private Integer appUserId;

    @Column(name = "BranchId", nullable = false)
    private Integer branchId;

    /** Chức vụ, vd: Điều phối viên. */
    @Column(name = "Position")
    private String position;

    /** ACTIVE | INACTIVE */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
