package com.kdc.tsdms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * File đính kèm của bài giảng (PDF, PPTX, video link ...).
 */
@Entity
@Table(name = "LessonFile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LessonFileId")
    private Integer lessonFileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LessonId", nullable = false)
    private Lesson lesson;

    @Column(name = "FileName", nullable = false, length = 255)
    private String fileName;

    @Column(name = "FileUrl", nullable = false, length = 500)
    private String fileUrl;

    /** pdf / pptx / mp4 / link */
    @Column(name = "FileType", length = 50)
    private String fileType;

    /** Kích thước KB */
    @Column(name = "FileSizeKb")
    private Integer fileSizeKb;

    @Column(name = "IsDeleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "CreatedBy")
    private Integer createdBy;
}
