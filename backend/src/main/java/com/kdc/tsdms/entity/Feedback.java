package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Phản hồi (bảng Feedback) — người dùng gửi, nhân viên trung tâm xử lý & trả lời. */
@Entity
@Table(name = "Feedback")
@Getter
@Setter
public class Feedback extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    /** Người gửi (→ AppUser). */
    @Column(name = "SenderUserId", nullable = false)
    private Integer senderUserId;

    @Column(name = "Title", nullable = false)
    private String title;

    @Column(name = "Content", nullable = false)
    private String content;

    /** OPEN | IN_PROGRESS | RESOLVED | CLOSED */
    @Column(name = "Status", nullable = false)
    private String status = "OPEN";

    /** Nhân viên xử lý (→ Employee). */
    @Column(name = "HandledByEmployeeId")
    private Integer handledByEmployeeId;

    @Column(name = "Response")
    private String response;
}
