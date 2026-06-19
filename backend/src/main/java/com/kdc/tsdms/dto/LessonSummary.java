package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.Lesson;
import java.time.Instant;

/** Một dòng trong danh sách bài giảng — không kèm Content (rich text) để nhẹ. */
public record LessonSummary(
        Integer id,
        Integer subjectId,
        String subjectName,
        String category,
        Integer branchId,
        String title,
        String description,
        String gradeLevel,
        Integer duration,
        String difficultyLevel,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static LessonSummary fromEntity(Lesson l, String subjectName, String category) {
        return new LessonSummary(
                l.getId(),
                l.getSubjectId(),
                subjectName,
                category,
                l.getBranchId(),
                l.getTitle(),
                l.getDescription(),
                l.getGradeLevel(),
                l.getDuration(),
                l.getDifficultyLevel(),
                l.getStatus(),
                l.getCreatedAt(),
                l.getUpdatedAt());
    }
}
