package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * NHẬT KÝ VÒNG ĐỜI PHIẾU LƯƠNG (bảng PayrollChangeLog, Flyway V32) — mỗi lần chốt hoặc mở lại
 * là một dòng, không bao giờ sửa/xóa.
 *
 * <p>Vì sao là bảng riêng chứ không phải mấy cột {@code ReopenedAt/By} trên chính {@link
 * Payroll}: cột chỉ giữ được LẦN MỚI NHẤT. Phiếu mở ra chốt lại hai lần là mất dấu lần đầu —
 * đúng thứ cần nhất khi có tranh chấp tiền lương.
 *
 * <p>Ghi cả {@code FINALIZE} lẫn {@code REOPEN} để đọc log ra được mạch "phiếu này đã qua tay
 * ai, mấy lần, vì sao", chứ không phải một dòng mở lại đứng trơ trọi.
 */
@Entity
@Table(name = "PayrollChangeLog")
@Getter
@Setter
public class PayrollChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "PayrollId", nullable = false)
    private Integer payrollId;

    /** FINALIZE | REOPEN. */
    @Column(name = "Action", nullable = false)
    private String action;

    /** Bắt buộc với REOPEN, để trống với FINALIZE. */
    @Column(name = "Reason")
    private String reason;

    @Column(name = "StatusBefore")
    private String statusBefore;

    @Column(name = "StatusAfter")
    private String statusAfter;

    /** Số tiền trước/sau — đọc log là biết ngay lần đó có làm đổi tiền không. */
    @Column(name = "NetAmountBefore")
    private BigDecimal netAmountBefore;

    @Column(name = "NetAmountAfter")
    private BigDecimal netAmountAfter;

    @Column(name = "ChangedBy")
    private Integer changedBy;

    @Column(name = "ChangedAt", insertable = false, updatable = false)
    private Instant changedAt;
}
