package com.kdc.tsdms.dto;

import java.time.Instant;

/** Chi tiết / dòng danh sách đánh giá giáo viên (đã join tên để FE khỏi N+1). */
public record EvaluationResponse(
        Integer id,
        Integer teacherId,
        String teacherName,
        Integer evaluatorUserId,
        String evaluatorName,
        Integer schoolId,
        String schoolName,
        /** CENTER = trung tâm chấm; SCHOOL = trường chấm. */
        String source,
        Short score,
        String comment,
        String periodNote,
        Instant createdAt,
        Instant updatedAt,
        /** FE ẩn/hiện nút Sửa theo ownership. */
        boolean canEdit,
        /** FE ẩn/hiện nút Xóa theo ownership. */
        boolean canDelete) {}
