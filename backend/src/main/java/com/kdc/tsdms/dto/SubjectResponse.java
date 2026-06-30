package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.Subject;
import java.time.Instant;

public record SubjectResponse(
        Integer id,
        String code,
        String name,
        Integer categoryId,
        String categoryName,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt,
        /** Số bài giảng (chưa xóa mềm) đang dùng môn này — chặn xóa nếu > 0. */
        long lessonCount) {
    public static SubjectResponse fromEntity(Subject s, long lessonCount) {
        return new SubjectResponse(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getCategory() != null ? s.getCategory().getId() : null,
                s.getCategory() != null ? s.getCategory().getName() : null,
                s.getDescription(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                lessonCount);
    }
}
