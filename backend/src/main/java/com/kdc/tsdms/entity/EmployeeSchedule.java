package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Lịch làm việc THỰC TẾ của nhân viên theo buổi (bảng EmployeeSchedule). Sinh từ 3 nguồn
 * (Source): FIXED (full-time cố định T2-T6), FROM_REQUEST (từ {@link PartTimeShiftRequest}
 * đã duyệt), MANUAL (HR tự xếp). Song song mô hình {@link Schedule} của lịch dạy GV.
 */
@Entity
@Table(name = "EmployeeSchedule")
@Getter
@Setter
public class EmployeeSchedule extends SoftDeletableEntity {

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

    @Column(name = "StartTime", nullable = false)
    private LocalTime startTime;

    @Column(name = "EndTime", nullable = false)
    private LocalTime endTime;

    /** SCHEDULED | ON_LEAVE | CANCELLED */
    @Column(name = "Status", nullable = false)
    private String status = "SCHEDULED";

    /** FIXED | FROM_REQUEST | MANUAL */
    @Column(name = "Source", nullable = false)
    private String source = "FIXED";

    /** Đăng ký gốc (→ PartTimeShiftRequest) — chỉ có khi Source = FROM_REQUEST. */
    @Column(name = "SourceRequestId")
    private Integer sourceRequestId;

    @Column(name = "Note")
    private String note;
}
