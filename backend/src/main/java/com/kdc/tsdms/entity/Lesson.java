package com.kdc.tsdms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Bài giảng — do NHÂN VIÊN trung tâm quản lý (tạo / sửa / xóa mềm).
 * Giáo viên và trường chỉ có quyền XEM.
 */
@Entity
@Table(name = "Lesson")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LessonId")
    private Integer lessonId;

    // ── Quan hệ ──────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubjectId", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TeacherId")
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BranchId", nullable = false)
    private Branch branch;

    // ── Nội dung ─────────────────────────────────────────────────────
    @Column(name = "Title", nullable = false, length = 300)
    private String title;

    @Column(name = "Description", length = 2000)
    private String description;

    @Lob
    @Column(name = "Content", columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "GradeLevel", length = 50)
    private String gradeLevel;

    /** Thời lượng tính bằng phút */
    @Column(name = "Duration")
    private Integer duration;

    /**
     * Mức độ khó: BASIC | INTERMEDIATE | ADVANCED
     */
    @Column(name = "DifficultyLevel", length = 20)
    private String difficultyLevel;

    /**
     * Trạng thái: DRAFT | PUBLISHED | ARCHIVED
     */
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    // ── Soft delete ───────────────────────────────────────────────────
    @Column(name = "IsDeleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "DeletedAt")
    private Instant deletedAt;

    @Column(name = "DeletedBy")
    private Integer deletedBy;

    // ── Audit ─────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "CreatedBy")
    private Integer createdBy;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private Instant updatedAt;

    @Column(name = "UpdatedBy")
    private Integer updatedBy;

    // ── Quan hệ 1-N với LessonFile ───────────────────────────────────
    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<LessonFile> files = new ArrayList<>();
}
