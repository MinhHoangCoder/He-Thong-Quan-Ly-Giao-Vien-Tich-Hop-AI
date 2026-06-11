package com.kdc.tsdms.mapper;

import com.kdc.tsdms.dto.LessonDto.*;
import com.kdc.tsdms.entity.*;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi giữa Lesson entity ↔ các DTO.
 * Không dùng MapStruct để giữ code đơn giản và dễ debug trong VS Code.
 */
@Component
public class LessonMapper {

    // ── Entity → Response (đầy đủ) ───────────────────────────────────
    public LessonResponse toResponse(Lesson lesson) {
        if (lesson == null) return null;

        LessonResponse.LessonResponseBuilder builder = LessonResponse.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .description(lesson.getDescription())
                .content(lesson.getContent())
                .gradeLevel(lesson.getGradeLevel())
                .duration(lesson.getDuration())
                .difficultyLevel(lesson.getDifficultyLevel())
                .status(lesson.getStatus())
                .createdAt(lesson.getCreatedAt())
                .createdBy(lesson.getCreatedBy())
                .updatedAt(lesson.getUpdatedAt())
                .updatedBy(lesson.getUpdatedBy());

        if (lesson.getSubject() != null) {
            builder.subjectId(lesson.getSubject().getSubjectId())
                    .subjectCode(lesson.getSubject().getCode())
                    .subjectName(lesson.getSubject().getName());
        }
        if (lesson.getTeacher() != null) {
            builder.teacherId(lesson.getTeacher().getId())
                    .teacherName(lesson.getTeacher().getFullName());
        }
        if (lesson.getBranch() != null) {
            builder.branchId(lesson.getBranch().getId())
                    .branchName(lesson.getBranch().getName());
        }

        // Files (chỉ lấy chưa xóa mềm)
        if (lesson.getFiles() != null) {
            List<LessonFileDto> fileDtos = lesson.getFiles().stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                    .map(this::toFileDto)
                    .collect(Collectors.toList());
            builder.files(fileDtos);
        }

        return builder.build();
    }

    // ── Entity → Summary (danh sách, không kèm content) ──────────────
    public LessonSummaryDto toSummary(Lesson lesson) {
        if (lesson == null) return null;

        long activeFileCount = 0;
        if (lesson.getFiles() != null) {
            activeFileCount = lesson.getFiles().stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                    .count();
        }

        return LessonSummaryDto.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .subjectName(lesson.getSubject() != null ? lesson.getSubject().getName() : null)
                .teacherName(lesson.getTeacher() != null ? lesson.getTeacher().getFullName() : null)
                .branchName(lesson.getBranch() != null ? lesson.getBranch().getName() : null)
                .gradeLevel(lesson.getGradeLevel())
                .duration(lesson.getDuration())
                .difficultyLevel(lesson.getDifficultyLevel())
                .status(lesson.getStatus())
                .fileCount((int) activeFileCount)
                .createdAt(lesson.getCreatedAt())
                .updatedAt(lesson.getUpdatedAt())
                .build();
    }

    // ── LessonFile Entity → DTO ───────────────────────────────────────
    public LessonFileDto toFileDto(LessonFile file) {
        if (file == null) return null;
        return LessonFileDto.builder()
                .lessonFileId(file.getLessonFileId())
                .fileName(file.getFileName())
                .fileUrl(file.getFileUrl())
                .fileType(file.getFileType())
                .fileSizeKb(file.getFileSizeKb())
                .createdAt(file.getCreatedAt())
                .build();
    }

    // ── Request → Entity mới ─────────────────────────────────────────
    public Lesson toEntity(LessonRequest req) {
        return Lesson.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .content(req.getContent())
                .gradeLevel(req.getGradeLevel())
                .duration(req.getDuration())
                .difficultyLevel(req.getDifficultyLevel())
                .status(req.getStatus() != null ? req.getStatus() : "DRAFT")
                .build();
    }

    // ── Cập nhật entity từ request (bỏ null = không đổi) ─────────────
    public void updateEntity(Lesson lesson, LessonRequest req) {
        if (req.getTitle() != null) lesson.setTitle(req.getTitle());
        if (req.getDescription() != null) lesson.setDescription(req.getDescription());
        if (req.getContent() != null) lesson.setContent(req.getContent());
        if (req.getGradeLevel() != null) lesson.setGradeLevel(req.getGradeLevel());
        if (req.getDuration() != null) lesson.setDuration(req.getDuration());
        if (req.getDifficultyLevel() != null) lesson.setDifficultyLevel(req.getDifficultyLevel());
        if (req.getStatus() != null) lesson.setStatus(req.getStatus());
    }
}
