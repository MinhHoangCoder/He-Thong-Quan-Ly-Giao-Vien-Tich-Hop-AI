package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Lớp học thuộc trường (bảng SchoolClass) — nối với Student qua ClassEnrollment. */
@Entity
@Table(name = "SchoolClass")
@Getter
@Setter
public class SchoolClass extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ClassId")
    private Integer id;

    @Column(name = "SchoolId", nullable = false)
    private Integer schoolId;

    @Column(name = "Name", nullable = false)
    private String name;

    /** Khối, vd: Khối 6. */
    @Column(name = "GradeLevel")
    private String gradeLevel;

    /** Năm học, vd: 2025-2026. */
    @Column(name = "SchoolYear", nullable = false)
    private String schoolYear;

    /** ACTIVE | INACTIVE */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
