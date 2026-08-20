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
 * NGÀY NGHỈ / KỲ NGHỈ (bảng Holiday, Flyway V29) — những ngày KHÔNG sinh buổi dạy.
 *
 * <p>Lưu theo KHOẢNG {@code [fromDate, toDate]}: nghỉ hè là một dòng thay vì sáu chục dòng
 * rời rạc; ngày lễ đơn thì hai cột bằng nhau. Các khoảng được phép chồng nhau — một ngày là
 * ngày nghỉ khi có BẤT KỲ dòng nào phủ nó.
 *
 * <p>{@code schoolId} null = áp dụng toàn hệ thống (lễ quốc gia, nghỉ hè chung); có giá trị
 * = chỉ riêng trường đó nghỉ. Vì thế lúc lọc phải so theo trường của TỪNG TIẾT
 * ({@link AssignmentSlot#getSchoolId()}), không phải trường cấp phiếu: từ V27 một phiếu có
 * thể trải nhiều trường.
 */
@Entity
@Table(name = "Holiday")
@Getter
@Setter
public class Holiday extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    /** Ngày đầu kỳ nghỉ (bao gồm chính ngày này). */
    @Column(name = "FromDate", nullable = false)
    private LocalDate fromDate;

    /** Ngày cuối kỳ nghỉ (bao gồm chính ngày này); nghỉ 1 ngày thì bằng {@code fromDate}. */
    @Column(name = "ToDate", nullable = false)
    private LocalDate toDate;

    @Column(name = "Name", nullable = false)
    private String name;

    /** NATIONAL (lễ theo luật) | BREAK (kỳ nghỉ của học sinh) | CENTER (trung tâm nghỉ riêng). */
    @Column(name = "Kind", nullable = false)
    private String kind;

    /** Trường áp dụng riêng, hoặc null = toàn hệ thống. */
    @Column(name = "SchoolId")
    private Integer schoolId;

    @Column(name = "Note")
    private String note;
}
