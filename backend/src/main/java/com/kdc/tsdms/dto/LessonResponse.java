package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.Lesson;
import java.time.Instant;
import java.util.List;

/** Chi tiết đầy đủ 1 bài giảng kèm danh sách file đính kèm. */
public record LessonResponse(
        Integer id,
        Integer subjectId,
        String subjectName,
        String category,
        Integer teacherId,
        Integer branchId,
        String title,
        String description,
        String content,
        String gradeLevel,
        Integer duration,
        String difficultyLevel,
        String status,
        List<LessonFileResponse> files,
        Instant createdAt,
        Instant updatedAt) {

    public static LessonResponse fromEntity(
            Lesson l, String subjectName, String category, List<LessonFileResponse> files) {
        return new LessonResponse(
                l.getId(),
                l.getSubjectId(),
                subjectName,
                category,
                l.getTeacherId(),
                l.getBranchId(),
                l.getTitle(),
                l.getDescription(),
                l.getContent(),
                l.getGradeLevel(),
                l.getDuration(),
                l.getDifficultyLevel(),
                l.getStatus(),
                files,
                l.getCreatedAt(),
                l.getUpdatedAt());
    }
}
