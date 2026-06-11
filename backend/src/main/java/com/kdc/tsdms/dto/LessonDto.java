package com.kdc.tsdms.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import lombok.*;

/* =====================================================================
DTO package cho module Bài giảng (Lesson)
Chứa: LessonRequest, LessonResponse, LessonFileDto, LessonSummaryDto
===================================================================== */

public class LessonDto {

    // ─────────────────────────────────────────────────────────────────
    // REQUEST — tạo mới / cập nhật bài giảng
    // ─────────────────────────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LessonRequest {

        @NotNull(message = "SubjectId không được trống") private Integer subjectId;

        private Integer teacherId; // GV phụ trách, tuỳ chọn

        @NotNull(message = "BranchId không được trống") private Integer branchId;

        @NotBlank(message = "Tiêu đề không được trống") @Size(max = 300, message = "Tiêu đề tối đa 300 ký tự") private String title;

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự") private String description;

        private String content; // nội dung rich-text / markdown

        @Size(max = 50) private String gradeLevel;

        @Min(value = 1, message = "Thời lượng phải lớn hơn 0") private Integer duration; // phút

        @Pattern(
                regexp = "BASIC|INTERMEDIATE|ADVANCED",
                message = "difficultyLevel phải là BASIC, INTERMEDIATE hoặc ADVANCED")
        private String difficultyLevel;

        @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED", message = "status phải là DRAFT, PUBLISHED hoặc ARCHIVED") private String status; // mặc định DRAFT nếu null

        /** Danh sách file đính kèm (tuỳ chọn khi tạo) */
        private List<LessonFileRequest> files;
    }

    // ─────────────────────────────────────────────────────────────────
    // REQUEST — file đính kèm
    // ─────────────────────────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LessonFileRequest {

        @NotBlank(message = "Tên file không được trống") @Size(max = 255) private String fileName;

        @NotBlank(message = "URL file không được trống") @Size(max = 500) private String fileUrl;

        @Size(max = 50) private String fileType; // pdf / pptx / mp4 / link

        private Integer fileSizeKb;
    }

    // ─────────────────────────────────────────────────────────────────
    // RESPONSE — trả về đầy đủ thông tin bài giảng
    // ─────────────────────────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LessonResponse {

        private Integer lessonId;

        // Môn học
        private Integer subjectId;
        private String subjectCode;
        private String subjectName;

        // Giáo viên phụ trách
        private Integer teacherId;
        private String teacherName;

        // Chi nhánh
        private Integer branchId;
        private String branchName;

        private String title;
        private String description;
        private String content;
        private String gradeLevel;
        private Integer duration;
        private String difficultyLevel;
        private String status;

        private Instant createdAt;
        private Integer createdBy;
        private Instant updatedAt;
        private Integer updatedBy;

        /** Danh sách file đính kèm */
        private List<LessonFileDto> files;
    }

    // ─────────────────────────────────────────────────────────────────
    // RESPONSE — thông tin file đính kèm
    // ─────────────────────────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LessonFileDto {
        private Integer lessonFileId;
        private String fileName;
        private String fileUrl;
        private String fileType;
        private Integer fileSizeKb;
        private Instant createdAt;
    }

    // ─────────────────────────────────────────────────────────────────
    // RESPONSE — tóm tắt (dùng cho danh sách, không kèm content dài)
    // ─────────────────────────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LessonSummaryDto {
        private Integer lessonId;
        private String title;
        private String subjectName;
        private String teacherName;
        private String branchName;
        private String gradeLevel;
        private Integer duration;
        private String difficultyLevel;
        private String status;
        private int fileCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ─────────────────────────────────────────────────────────────────
    // REQUEST — đổi trạng thái (publish / archive / revert to draft)
    // ─────────────────────────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeStatusRequest {
        @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") private String status;
    }

    // ─────────────────────────────────────────────────────────────────
    // REQUEST — bộ lọc tìm kiếm (dùng @ModelAttribute ở controller)
    // ─────────────────────────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LessonFilter {
        private Integer branchId;
        private Integer subjectId;
        private Integer teacherId;
        private String status; // DRAFT / PUBLISHED / ARCHIVED / null = tất cả
        private String difficultyLevel;
        private String gradeLevel;
        private String keyword; // tìm trong title / description
        private int page = 0;
        private int size = 10;
        private String sortBy = "createdAt";
        private String sortDir = "desc";
    }
}
