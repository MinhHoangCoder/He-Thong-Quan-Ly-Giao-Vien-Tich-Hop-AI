package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Đánh giá giáo viên (bảng TeacherEvaluation) — trường hoặc nhân viên chấm điểm/nhận xét. */
@Entity
@Table(name = "TeacherEvaluation")
@Getter
@Setter
public class TeacherEvaluation extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EvaluationId")
    private Integer id;

    /** GV được đánh giá. */
    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    /** Người đánh giá (→ AppUser). */
    @Column(name = "EvaluatorUserId", nullable = false)
    private Integer evaluatorUserId;

    /** Trường đánh giá (nullable — nhân viên trung tâm đánh giá thì để trống). */
    @Column(name = "SchoolId")
    private Integer schoolId;

    /** Điểm 1..5 (TINYINT) — đầu vào cho AI gợi ý GV. */
    @Column(name = "Score")
    private Short score;

    @Column(name = "Comment")
    private String comment;

    /** Kỳ đánh giá (ghi chú), vd: HK1 2025-2026. */
    @Column(name = "PeriodNote")
    private String periodNote;
}
