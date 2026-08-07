package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import lombok.Getter;

/**
 * NHẬT KÝ thay đổi chấm công (V24) — chỉ ĐỌC từ phía ứng dụng.
 *
 * <p>Dòng log do trigger {@code TR_Attendance_ChangeLog} sinh, không phải service ghi: đặt ở
 * DB thì không đường ghi nào lọt sổ, kể cả sửa tay bằng SQL. Vì vậy entity này không có
 * setter và không bao giờ được save.
 *
 * <p>{@code changedByUserId} null = do hệ thống (job khép sổ) làm, không phải người.
 */
@Entity
@Table(name = "AttendanceChangeLog")
@Getter
public class AttendanceChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "AttendanceId", nullable = false)
    private Long attendanceId;

    /** CREATE | UPDATE | AUTO_CHECKOUT | AUTO_ABSENT */
    @Column(name = "Action", nullable = false)
    private String action;

    @Column(name = "OldStatus")
    private String oldStatus;

    @Column(name = "NewStatus")
    private String newStatus;

    @Column(name = "OldCheckIn")
    private LocalTime oldCheckIn;

    @Column(name = "NewCheckIn")
    private LocalTime newCheckIn;

    @Column(name = "OldCheckOut")
    private LocalTime oldCheckOut;

    @Column(name = "NewCheckOut")
    private LocalTime newCheckOut;

    @Column(name = "ChangedByUserId")
    private Integer changedByUserId;

    @Column(name = "ChangedAt", nullable = false)
    private Instant changedAt;
}
