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
 * Nhóm môn học (bảng SubjectCategory) — danh mục tra cứu: Tin học, Tiếng Anh,
 * STEM - AI, Kĩ năng sống. Thay cho cột text Subject.Category cũ (chuẩn hóa ở
 * Flyway V8): mỗi {@link Subject} trỏ tới một nhóm qua FK CategoryId.
 */
@Entity
@Table(name = "SubjectCategory")
@Getter
@Setter
public class SubjectCategory extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    /** Mã nhóm (unique), khớp Enum Java: TIN_HOC | TIENG_ANH | STEM_AI | KY_NANG_SONG. */
    @Column(name = "Code", nullable = false)
    private String code;

    /** Tên hiển thị: Tin học | Tiếng Anh | STEM - AI | Kĩ năng sống. */
    @Column(name = "Name", nullable = false)
    private String name;

    @Column(name = "Description")
    private String description;

    /** ACTIVE | DISABLED */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
