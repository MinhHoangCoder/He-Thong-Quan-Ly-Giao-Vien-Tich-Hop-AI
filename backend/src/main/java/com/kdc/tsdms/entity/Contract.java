package com.kdc.tsdms.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Contract")
@Getter
@Setter
public class Contract extends SoftDeletableEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;
 
    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;           // FK → Teacher
 
    @Column(name = "ContractNo", nullable = false, unique = true, length = 50)
    private String contractNo;           // Số hợp đồng (duy nhất)
 
    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;
 
    @Column(name = "EndDate")
    private LocalDate endDate;           // null = vô thời hạn
 
    @Column(name = "BaseSalary", precision = 18, scale = 2)
    private BigDecimal baseSalary;
 
    @Column(name = "Allowance", precision = 18, scale = 2)
    private BigDecimal allowance;
 
    /** ACTIVE | EXPIRED | TERMINATED */
    @Column(name = "Status", nullable = false, length = 20)
    private String status = "ACTIVE";
 
    @Column(name = "FileUrl", length = 500)
    private String fileUrl;
}
