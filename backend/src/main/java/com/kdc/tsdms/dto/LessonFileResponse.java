package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.LessonFile;
import java.time.Instant;

/** Thông tin 1 file đính kèm của bài giảng — dùng cho cả danh sách và chi tiết. */
public record LessonFileResponse(
        Integer id,
        Integer lessonId,
        String fileName,
        String fileUrl,
        String fileType,
        Integer fileSizeKb,
        Instant createdAt) {
    public static LessonFileResponse fromEntity(LessonFile f) {
        return new LessonFileResponse(
                f.getId(),
                f.getLessonId(),
                f.getFileName(),
                f.getFileUrl(),
                f.getFileType(),
                f.getFileSizeKb(),
                f.getCreatedAt());
    }
}
