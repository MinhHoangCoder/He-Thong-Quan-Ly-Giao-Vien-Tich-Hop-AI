package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * ĐƠN GIÁ MỘT TIẾT DẠY theo khối lớp, có hiệu lực theo thời gian (bảng PayRate, Flyway V38).
 *
 * <p>Trước V38 hai mức giá nằm thẳng trong {@code PayrollService} dưới dạng hằng số Java:
 * đổi giá phải sửa code và deploy lại.
 *
 * <p>Vì sao phải có KHOẢNG HIỆU LỰC chứ không chỉ một cột giá: bảng lương tính lại được bất
 * cứ lúc nào ({@code generate()} ghi đè dòng nháp). Nếu chỉ giữ giá hiện hành thì bấm "Tính
 * lại" tháng 7 sau khi tăng giá từ 1/9 sẽ trả tháng 7 theo giá tháng 9 — số tiền đổi mà
 * không ai đụng vào chấm công.
 *
 * <p>Tăng giá = đóng dòng cũ ({@code effectiveTo}) rồi thêm dòng mới. KHÔNG sửa đè dòng cũ:
 * sửa đè là xóa lịch sử giá, và mọi phiếu lương cũ tính lại sẽ lệch với số đã trả.
 */
@Entity
@Table(name = "PayRate")
@Getter
@Setter
public class PayRate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    /** Khối nhỏ nhất áp mức giá này (bao gồm chính khối này). */
    @Column(name = "GradeFrom", nullable = false)
    private Short gradeFrom;

    /** Khối lớn nhất áp mức giá này (bao gồm chính khối này). */
    @Column(name = "GradeTo", nullable = false)
    private Short gradeTo;

    /** Tiền một tiết, đồng. */
    @Column(name = "Amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "EffectiveFrom", nullable = false)
    private LocalDate effectiveFrom;

    /** {@code null} = còn hiệu lực tới nay. */
    @Column(name = "EffectiveTo")
    private LocalDate effectiveTo;

    @Column(name = "Note")
    private String note;

    /** Khối {@code grade} có nằm trong mức này không. */
    public boolean coversGrade(int grade) {
        return grade >= gradeFrom && grade <= gradeTo;
    }

    /** Mức này có hiệu lực vào ngày {@code day} không. */
    public boolean coversDate(LocalDate day) {
        return !day.isBefore(effectiveFrom) && (effectiveTo == null || !day.isAfter(effectiveTo));
    }
}
