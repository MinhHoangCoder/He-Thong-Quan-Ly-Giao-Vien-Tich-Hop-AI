package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Môn học (bảng Subject) — danh mục môn: STEM, Công dân số... */
@Entity
@Table(name = "Subject")
@Getter
@Setter
public class Subject extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    /** Mã môn (unique), vd: STEM01, CDS01. */
    @Column(name = "Code", nullable = false)
    private String code;

    @Column(name = "Name", nullable = false)
    private String name;

    /** Nhóm môn: STEM | CONG_DAN_SO | ... */
    @Column(name = "Category")
    private String category;

    @Column(name = "Description")
    private String description;

    /** ACTIVE | DISABLED */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
