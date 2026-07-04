package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Mẫu lặp tuần của một {@link Assignment} (bảng AssignmentSlot) — "GV dạy lớp này vào THỨ
 * mấy, TIẾT mấy" lặp hằng tuần trong giai đoạn của Assignment. Đặt ở bảng CON để một phân
 * công (mức kỳ) ôm được NHIỀU tiết/tuần mà không phá grain của Assignment.
 *
 * <p>Tầng Service trải các slot này thành {@link Schedule} (buổi cụ thể từng tuần). TeacherId
 * lưu kèm = GV của Assignment để dò trùng lịch nhanh qua index (giống Schedule). RoomId là
 * phòng MẶC ĐỊNH của ô lịch — generator copy xuống Schedule, vẫn cho override từng buổi.
 */
@Entity
@Table(name = "AssignmentSlot")
@Getter
@Setter
public class AssignmentSlot extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "AssignmentId", nullable = false)
    private Integer assignmentId;

    /** = GV của Assignment (lưu kèm để dò trùng lịch nhanh). */
    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    /** MON | TUE | WED | THU | FRI | SAT | SUN */
    @Column(name = "DayOfWeek", nullable = false)
    private String dayOfWeek;

    @Column(name = "PeriodId", nullable = false)
    private Integer periodId;

    /** Phòng mặc định của ô lịch (nullable) — generator copy xuống Schedule, cho override. */
    @Column(name = "RoomId")
    private Integer roomId;
}
