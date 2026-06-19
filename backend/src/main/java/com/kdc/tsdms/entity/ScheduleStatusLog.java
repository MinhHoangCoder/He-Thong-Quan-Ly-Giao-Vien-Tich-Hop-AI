package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Nhật ký đổi trạng thái lịch (bảng ScheduleStatusLog). Bản ghi do TRIGGER
 * TR_Schedule_StatusLog trong DB tự INSERT — phía Java chủ yếu ĐỌC để hiển thị lịch sử.
 */
@Entity
@Table(name = "ScheduleStatusLog")
@Getter
@Setter
public class ScheduleStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LogId")
    private Long id;

    @Column(name = "ScheduleId", nullable = false)
    private Long scheduleId;

    @Column(name = "OldStatus")
    private String oldStatus;

    @Column(name = "NewStatus", nullable = false)
    private String newStatus;

    @Column(name = "ChangedByUserId")
    private Integer changedByUserId;

    @Column(name = "ChangedAt", insertable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "Note")
    private String note;
}
