package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.SubjectCategory;
import java.time.Instant;

public record SubjectCategoryResponse(
        Integer id,
        String code,
        String name,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt,
        /** Số môn học đang dùng nhóm này (ACTIVE, chưa xóa mềm). */
        long subjectCount) {
    public static SubjectCategoryResponse fromEntity(SubjectCategory sc, long subjectCount) {
        return new SubjectCategoryResponse(
                sc.getId(),
                sc.getCode(),
                sc.getName(),
                sc.getDescription(),
                sc.getStatus(),
                sc.getCreatedAt(),
                sc.getUpdatedAt(),
                subjectCount);
    }
}
