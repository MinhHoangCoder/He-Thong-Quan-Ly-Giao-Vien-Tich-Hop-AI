package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Bằng cấp & chứng chỉ của giáo viên (bảng Certificate) — 1 GV có nhiều bằng.
 */
@Entity
@Table(name = "Certificate")
@Getter
@Setter
public class Certificate extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    @Column(name = "Name", nullable = false)
    private String name;

    /** Nơi cấp. */
    @Column(name = "Issuer")
    private String issuer;

    @Column(name = "IssueDate")
    private LocalDate issueDate;

    @Column(name = "ExpiryDate")
    private LocalDate expiryDate;

    /** Link file scan đã upload. */
    @Column(name = "FileUrl")
    private String fileUrl;
}
