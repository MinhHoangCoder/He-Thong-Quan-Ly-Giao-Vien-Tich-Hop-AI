package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Hồ sơ giáo viên (bảng Teacher) — nối 1-1 với AppUser qua AppUserId. */
@Entity
@Table(name = "Teacher")
@Getter
@Setter
public class Teacher extends SoftDeletableEntity {

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

    @Column(name = "DateOfBirth")
    private LocalDate dateOfBirth;

    /** true = Nam, false = Nữ (cột BIT nullable → dùng Boolean, không dùng boolean). */
    @Column(name = "Gender")
    private Boolean gender;

    /** Số CCCD (unique trên bản ghi chưa xóa mềm). */
    @Column(name = "IdCardNo")
    private String idCardNo;

    @Column(name = "Phone")
    private String phone;

    @Column(name = "Address")
    private String address;

    @Column(name = "HireDate")
    private LocalDate hireDate;

    /** FULL_TIME | PART_TIME | CONTRACT (nullable). */
    @Column(name = "EmploymentType")
    private String employmentType;

    /** ACTIVE | RETIRED | SUSPENDED */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
