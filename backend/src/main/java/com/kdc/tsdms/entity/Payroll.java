package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng lương theo tháng (bảng Payroll) — mỗi GV mỗi tháng đúng 1 dòng (UNIQUE
 * TeacherId+PeriodYear+PeriodMonth). Không có soft delete.
 */
@Entity
@Table(name = "Payroll")
@Getter
@Setter
public class Payroll extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    /** Tháng 1..12 (TINYINT). */
    @Column(name = "PeriodMonth", nullable = false)
    private Short periodMonth;

    /** Năm (SMALLINT). */
    @Column(name = "PeriodYear", nullable = false)
    private Short periodYear;

    @Column(name = "BaseSalary", nullable = false)
    private BigDecimal baseSalary = BigDecimal.ZERO;

    /** Số giờ dạy thực tế (nếu trả theo giờ). */
    @Column(name = "TaughtHours")
    private BigDecimal taughtHours;

    @Column(name = "RatePerHour")
    private BigDecimal ratePerHour;

    @Column(name = "Allowance", nullable = false)
    private BigDecimal allowance = BigDecimal.ZERO;

    @Column(name = "Bonus", nullable = false)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(name = "Deduction", nullable = false)
    private BigDecimal deduction = BigDecimal.ZERO;

    /**
     * Cột TÍNH SẴN trong DB (computed PERSISTED):
     * lương cứng + giờ dạy × đơn giá + phụ cấp + thưởng − khấu trừ.
     * DB tự tính → Java CHỈ ĐỌC, không bao giờ ghi.
     */
    @Column(name = "NetAmount", insertable = false, updatable = false)
    private BigDecimal netAmount;

    /** DRAFT | FINALIZED | PAID */
    @Column(name = "Status", nullable = false)
    private String status = "DRAFT";
}
