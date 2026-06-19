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

/**
 * Hợp đồng dịch vụ trường ↔ trung tâm (bảng ServiceContract) — NGUỒN DOANH THU.
 *
 * <p>Khác với {@link Contract} (hợp đồng LAO ĐỘNG của giáo viên). Mỗi trường có thể có
 * nhiều hợp đồng theo từng năm học; {@code contractValue} là giá trị HĐ dùng để cộng
 * doanh thu cho dashboard "doanh thu theo năm học".
 */
@Entity
@Table(name = "ServiceContract")
@Getter
@Setter
public class ServiceContract extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "SchoolId", nullable = false)
    private Integer schoolId;

    @Column(name = "BranchId", nullable = false)
    private Integer branchId;

    /** Mã hợp đồng (unique), vd HDDV-2025-001. */
    @Column(name = "ContractCode", nullable = false)
    private String contractCode;

    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;

    @Column(name = "EndDate", nullable = false)
    private LocalDate endDate;

    /** Giá trị hợp đồng = doanh thu ghi nhận. */
    @Column(name = "ContractValue", nullable = false)
    private BigDecimal contractValue = BigDecimal.ZERO;

    /** DRAFT | ACTIVE | EXPIRED | TERMINATED */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
