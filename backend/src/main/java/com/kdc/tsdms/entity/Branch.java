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
 * Chi nhánh (bảng Branch) — gốc tổ chức: Employee/Teacher/School đều gắn về 1
 * chi nhánh.
 */
@Entity
@Table(name = "Branch")
@Gettera
@Setter
public class Branch extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "Name", nullable = false)
    private String name;

    @Column(name = "Address")
    private String address;

    @Column(name = "Phone")
    private String phone;

    /** ACTIVE | INACTIVE */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
