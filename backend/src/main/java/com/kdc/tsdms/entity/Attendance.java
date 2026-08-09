package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Chấm công (bảng Attendance) — GV có dạy buổi đó không, giờ vào/ra. Chống gian
 * lận:
 * GV tự check-in (CheckInMethod=SELF) + trường xác nhận (ConfirmedByUserId).
 * Bảng log: không có soft delete / UpdatedAt.
 */
@Entity
@Table(name = "Attendance")
@Getter
@Setter
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id; // BIGINT

    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    /** Buổi dạy tương ứng — BẮT BUỘC từ V25 (bỏ khái niệm chấm công không gắn buổi). */
    @Column(name = "ScheduleId", nullable = false)
    private Long scheduleId;

    @Column(name = "WorkDate", nullable = false)
    private LocalDate workDate;

    /** Giờ vào — TIME(0) → LocalTime. */
    @Column(name = "CheckIn")
    private LocalTime checkIn;

    @Column(name = "CheckOut")
    private LocalTime checkOut;

    /** PRESENT | ABSENT | LATE | LEAVE */
    @Column(name = "Status", nullable = false)
    private String status = "PRESENT";

    /** Ai/cái gì ghi nhận: SELF | EMPLOYEE | SCHOOL | DEVICE. */
    @Column(name = "CheckInMethod")
    private String checkInMethod;

    /** Người xác nhận (vd: tài khoản trường) → AppUser. */
    @Column(name = "ConfirmedByUserId")
    private Integer confirmedByUserId;

    @Column(name = "ConfirmedAt")
    private Instant confirmedAt;

    @Column(name = "Note")
    private String note;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "CreatedBy")
    private Integer createdBy;

    /** Giờ ra do HỆ THỐNG chốt (giáo viên quên bấm) — kế toán soát lại được (V24). */
    @Column(name = "AutoCheckOut", nullable = false)
    private boolean autoCheckOut;

    /**
     * Ai sửa lần cuối — trigger TR_Attendance_ChangeLog đọc để ghi nhật ký. Job chạy nền
     * để null (log hiểu là hệ thống tự làm).
     */
    @Column(name = "UpdatedAt")
    private Instant updatedAt;

    @Column(name = "UpdatedBy")
    private Integer updatedBy;

    /**
     * Vì sao dòng này bị người ngoài động vào (V25). Nguồn công chính thức là giáo viên tự
     * chấm; admin sửa tay hoặc duyệt yêu cầu bổ sung đều phải để lại lời giải thích ở đây.
     * Tách khỏi {@link #note} vì Note là ghi chú của chính giáo viên lúc check-in.
     */
    @Column(name = "AdjustReason")
    private String adjustReason;
}
