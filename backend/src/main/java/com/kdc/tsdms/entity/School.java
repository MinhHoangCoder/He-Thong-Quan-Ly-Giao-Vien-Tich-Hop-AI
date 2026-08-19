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

/** Trường khách hàng (bảng School) — có thể nối 1-1 với AppUser (tài khoản trường, tùy chọn). */
@Entity
@Table(name = "School")
@Getter
@Setter
public class School extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "BranchId", nullable = false)
    private Integer branchId;

    @Column(name = "Name", nullable = false)
    private String name;

    @Column(name = "Address")
    private String address;

    @Column(name = "Phone")
    private String phone;

    @Column(name = "Email")
    private String email;

    @Column(name = "ContactPerson")
    private String contactPerson;

    @Column(name = "ContractStartDate")
    private LocalDate contractStartDate;

    //  Ngày hết hạn hợp đồng dịch vụ
    @Column(name = "ContractEndDate")
    private LocalDate contractEndDate;

    /** ACTIVE | INACTIVE | EXPIRED */
    @Column(name = "Status", nullable = false)
    private String status = "ACTIVE";
}
