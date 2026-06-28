package com.kdc.tsdms.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Certificate")
@Getter
@Setter
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;
 
    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;           // FK → Teacher
 
    @Column(name = "Name", nullable = false, length = 200)
    private String name;                 // Tên chứng chỉ: Tin học | English | STEM-AI | Kỹ năng sống
 
    @Column(name = "Issuer", length = 200)
    private String issuer;               // Nơi cấp
 


}
