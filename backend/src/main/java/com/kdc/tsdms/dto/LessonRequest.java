package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Body tạo mới / sửa bài giảng (JSON). */
public record LessonRequest(
        @NotNull(message = "Thiếu môn học (subjectId)") Integer subjectId,

        Integer teacherId,

        @NotNull(message = "Thiếu chi nhánh (branchId)") Integer branchId,

        @NotBlank(message = "Tiêu đề không được để trống") String title,

        String description,

        String content,

        /** Khối lớp — text tự do, vd: "Lớp 4", "Lớp 6", "Lớp 10". */
        String gradeLevel,

        @Positive(message = "Thời lượng phải lớn hơn 0") Integer duration,

        @Pattern(regexp = "BASIC|INTERMEDIATE|ADVANCED|", message = "Độ khó phải là BASIC, INTERMEDIATE hoặc ADVANCED") String difficultyLevel,

        @NotBlank(message = "Thiếu trạng thái") @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED", message = "Trạng thái phải là DRAFT, PUBLISHED hoặc ARCHIVED") String status) {}
