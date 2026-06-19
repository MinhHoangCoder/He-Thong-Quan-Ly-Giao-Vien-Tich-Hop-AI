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
 * Bài giảng (bảng Lesson) — nhân viên trung tâm soạn/quản lý.
 * Môn học liên kết qua SubjectId FK → bảng Subject (đã có sẵn từ V1/V2).
 * KHÔNG dùng cột Category text nữa. Không thay đổi DB.
 */
@Entity
@Table(name = "Lesson")
@Getter
@Setter
public class Lesson extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LessonId")
    private Integer id;

    /** Môn học (FK → Subject). */
    @Column(name = "SubjectId", nullable = false)
    private Integer subjectId;

    /** GV phụ trách soạn bài (→ Teacher), có thể NULL. */
    @Column(name = "TeacherId")
    private Integer teacherId;

    /** Chi nhánh quản lý bài giảng (→ Branch). */
    @Column(name = "BranchId", nullable = false)
    private Integer branchId;

    @Column(name = "Title", nullable = false)
    private String title;

    /** Mô tả / tóm tắt ngắn. */
    @Column(name = "Description")
    private String description;

    /** Nội dung chi tiết (rich text / markdown) — NVARCHAR(MAX). */
    @Column(name = "Content")
    private String content;

    /** Khối lớp áp dụng, vd: Lớp 6, Lớp 10. */
    @Column(name = "GradeLevel")
    private String gradeLevel;

    /** Thời lượng dạy dự kiến (phút). */
    @Column(name = "Duration")
    private Integer duration;

    /** BASIC | INTERMEDIATE | ADVANCED */
    @Column(name = "DifficultyLevel")
    private String difficultyLevel;

    /** DRAFT | PUBLISHED | ARCHIVED */
    @Column(name = "Status", nullable = false)
    private String status = "DRAFT";
}
