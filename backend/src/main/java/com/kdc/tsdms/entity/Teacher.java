package com.kdc.tsdms.entity;

import java.time.LocalDate;

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
public class Teacher extends SoftDeletableEntity{

        @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")  // Sửa từ TeacherId thành Id
    private Integer id;
    
    @Column(name = "AppUserId", nullable = false)
    private Integer appUserId;
    
    @Column(name = "BranchId", nullable = false)
    private Integer branchId;
    
    @Column(name = "FirstName", nullable = false, length = 100)
    private String firstName;
    
    @Column(name = "LastName", nullable = false, length = 100)
    private String lastName;
    // thông tin cá nhân==================================================
    
    @Column(name = "DateOfBirth")
    private LocalDate dateOfBirth;
    
    @Column(name = "Gender")
    private Boolean gender;  
    
    @Column(name = "IdCardNo", length = 20)
    private String idCardNo;  // CCCD unique in db
    
    @Column(name = "Phone", length = 15)
    private String phone;
    
    @Column(name = "Address", length = 255)
    private String address;
    
    @Column(name = "HireDate")
    private LocalDate hireDate;
    // thông tin cviec =================================================
    
    @Column(name = "EmploymentType")
    private String employmentType;
    
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
    
    @Column(name = "IsDeleted", nullable = false)
    private boolean deleted = false;
    
    // Getter cho fullName (tổng hợp từ FirstName + LastName = ghép tên)
    public String getFullName() {
        return (this.lastName != null ? this.lastName : "") + 
               " " + 
               (this.firstName != null ? this.firstName : "");
    }
}
