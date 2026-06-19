package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Hợp đồng của giáo viên (bảng Contract) — 1 GV có thể có nhiều hợp đồng theo thời gian. */
@Entity
@Table(name = "Contract")
@Getter
@Setter
public class Contract extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    /** Số hợp đồng (unique). */
    @Column(name = "ContractNo", nullable = false)
    private String contractNo;

    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;

    /** NULL = hợp đồng vô thời hạn. */
    @Column(name = "EndDate")
    private LocalDate endDate;

    @Column(name = "BaseSalary")
    private BigDecimal baseSalary;

    @Column(name = "Allowance")
    private BigDecimal allowance;

    /** ACTIVE | EXPIRED | TERMINATED */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "FileUrl")
    private String fileUrl;
}
