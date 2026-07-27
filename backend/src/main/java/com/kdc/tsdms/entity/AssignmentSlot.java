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
 *
 * <p>Từ V16 slot còn mang {@code ClassId} riêng: grain thật của nghiệp vụ là "1 tiết = 1 lớp"
 * (sáng tiết 1 dạy 1A1, tiết 2 dạy 1A2…), nên lớp phải nằm ở đây chứ không ở Assignment.
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

    /**
     * Lớp dạy ở ô lịch NÀY (V16) — mỗi tiết một lớp, vì cùng một buổi GV có thể dạy
     * tiết 1 lớp 1A1, tiết 2 lớp 1A2. Nullable cho dữ liệu cũ: Service fallback về
     * {@link Assignment#getClassId()} khi slot chưa gán lớp.
     */
    @Column(name = "ClassId")
    private Integer classId;

    /** Phòng mặc định của ô lịch (nullable) — generator copy xuống Schedule, cho override. */
    @Column(name = "RoomId")
    private Integer roomId;
}
