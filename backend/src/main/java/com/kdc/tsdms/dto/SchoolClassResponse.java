package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.SchoolClass;
import java.time.Instant;

/** DTO trả về lớp học kèm tên trường (để hiển thị bảng). */
public record SchoolClassResponse(
        Integer id,
        Integer schoolId,
        String schoolName,
        String name,
        String gradeLevel,
        String schoolYear,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static SchoolClassResponse fromEntity(SchoolClass sc, String schoolName) {
        return new SchoolClassResponse(
                sc.getId(),
                sc.getSchoolId(),
                schoolName,
                sc.getName(),
                sc.getGradeLevel(),
                sc.getSchoolYear(),
                sc.getStatus(),
                sc.getCreatedAt(),
                sc.getUpdatedAt());
    }
}
