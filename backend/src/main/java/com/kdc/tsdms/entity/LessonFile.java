package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * File đính kèm của bài giảng (bảng LessonFile) — 1 {@link Lesson} có nhiều file
 * (PDF, PPTX, link video...). FileUrl chỉ lưu đường dẫn; file vật lý nằm ở
 * storage riêng (chưa làm ở giai đoạn này).
 */
@Entity
@Table(name = "LessonFile")
@Getter
@Setter
public class LessonFile extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "LessonId", nullable = false)
    private Integer lessonId;

    /** Tên file hiển thị cho người dùng. */
    @Column(name = "FileName", nullable = false)
    private String fileName;

    /** Đường dẫn / URL lưu trữ. */
    @Column(name = "FileUrl", nullable = false)
    private String fileUrl;

    /** pdf / pptx / mp4 / link ... */
    @Column(name = "FileType")
    private String fileType;

    /** Kích thước file (KB). */
    @Column(name = "FileSizeKb")
    private Integer fileSizeKb;
}
