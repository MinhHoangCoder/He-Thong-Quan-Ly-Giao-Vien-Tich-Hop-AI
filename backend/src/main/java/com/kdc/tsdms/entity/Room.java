package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Phòng học thuộc trường (bảng Room) — chỉ phòng AVAILABLE mới được xếp lịch. */
@Entity
@Table(name = "Room")
@Getter
@Setter
public class Room extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoomId")
    private Integer id;

    @Column(name = "SchoolId", nullable = false)
    private Integer schoolId;

    /** Tên phòng (unique trong 1 trường), vd: A101. */
    @Column(name = "Name", nullable = false)
    private String name;

    @Column(name = "Building")
    private String building;

    /** Tầng — VARCHAR trong DB (có thể là "G", "Trệt"...), không phải số. */
    @Column(name = "Floor")
    private String floor;

    /** LAB | CLASSROOM | HALL (nullable). */
    @Column(name = "Type")
    private String type;

    @Column(name = "Capacity")
    private Integer capacity;

    /** AVAILABLE | MAINTENANCE | DISABLED */
    @Column(name = "Status", nullable = false)
    private String status = "AVAILABLE";
}
