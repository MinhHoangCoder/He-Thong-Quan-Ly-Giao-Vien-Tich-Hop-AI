package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Khung TIẾT HỌC theo TỪNG TRƯỜNG (bảng Period) — mỗi trường có số tiết & giờ giấc riêng
 * (gắn {@code SchoolId}). Là dữ liệu VẬN HÀNH: trường sửa được qua admin (UPDATE/xóa mềm)
 * nên có đủ soft-delete + audit. {@link AssignmentSlot} và {@link Schedule} trỏ tới qua
 * PeriodId (thêm ở Flyway V9); dữ liệu tiết seed theo từng trường ở seed demo.
 */
@Entity
@Table(name = "Period")
@Getter
@Setter
public class Period extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    /** Trường sở hữu khung tiết này (→ School). */
    @Column(name = "SchoolId", nullable = false)
    private Integer schoolId;

    /** Số tiết trong ngày của trường (1..n) — unique trong phạm vi 1 trường. */
    @Column(name = "PeriodNumber", nullable = false)
    private Short periodNumber;

    /** MORNING | AFTERNOON */
    @Column(name = "SessionType", nullable = false)
    private String sessionType;

    /** Giờ bắt đầu tiết — khung cố định mọi ngày, không kèm ngày → LocalTime. */
    @Column(name = "StartTime", nullable = false)
    private LocalTime startTime;

    @Column(name = "EndTime", nullable = false)
    private LocalTime endTime;
}
