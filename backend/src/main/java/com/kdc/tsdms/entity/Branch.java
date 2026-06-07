package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Chi nhánh (bảng Branch). Tối thiểu cho việc kiểm tra branch tồn tại khi tạo GV/trường. */
@Entity
@Table(name = "Branch")
@Getter
@Setter
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BranchId")
    private Integer id;

    @Column(name = "Name", nullable = false)
    private String name;
}
