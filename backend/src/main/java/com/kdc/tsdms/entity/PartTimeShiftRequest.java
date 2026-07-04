package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Đăng ký ca làm của nhân viên PART-TIME (bảng PartTimeShiftRequest) — NV tự đăng ký buổi
 * muốn làm, HR duyệt/từ chối. Đây là "ý định"; khi APPROVED tầng Service sinh ra
 * {@link EmployeeSchedule} tương ứng. Quy trình nội bộ NV↔HR (không phải yêu cầu từ bên ngoài).
 */
@Entity
@Table(name = "PartTimeShiftRequest")
@Getter
@Setter
public class PartTimeShiftRequest extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "EmployeeId", nullable = false)
    private Integer employeeId;

    @Column(name = "WorkDate", nullable = false)
    private LocalDate workDate;

    /** MORNING | AFTERNOON */
    @Column(name = "ShiftType", nullable = false)
    private String shiftType;

    @Column(name = "Note")
    private String note;

    /** PENDING | APPROVED | REJECTED */
    @Column(name = "Status", nullable = false)
    private String status = "PENDING";

    /** HR đã duyệt/từ chối (→ Employee). */
    @Column(name = "ReviewedByEmployeeId")
    private Integer reviewedByEmployeeId;

    @Column(name = "ReviewedAt")
    private Instant reviewedAt;

    @Column(name = "RejectionReason")
    private String rejectionReason;
}
