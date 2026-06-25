package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Phân công (bảng Assignment) — nhân viên TRUNG TÂM gán GV ↔ trường ↔ môn ↔ lớp
 * trong
 * một giai đoạn. Đây là điểm BẮT ĐẦU của luồng điều phối (không có "yêu cầu từ
 * trường");
 * từ một Assignment sinh ra nhiều Schedule (buổi dạy cụ thể).
 */
@Entity
@Table(name = "Assignment")
@Getter
@Setter
public class Assignment extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    @Column(name = "SchoolId", nullable = false)
    private Integer schoolId;

    @Column(name = "SubjectId", nullable = false)
    private Integer subjectId;

    /** Lớp cụ thể (nullable — có thể phân công cấp trường, chưa gắn lớp). */
    @Column(name = "ClassId")
    private Integer classId;

    /** Nhân viên nào phân công (→ Employee). */
    @Column(name = "AssignedByEmployeeId")
    private Integer assignedByEmployeeId;

    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;

    @Column(name = "EndDate")
    private LocalDate endDate;

    /** ACTIVE | COMPLETED | CANCELLED */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
