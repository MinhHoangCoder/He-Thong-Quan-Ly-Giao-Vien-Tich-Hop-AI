package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;

/** Trường khách hàng (bảng School). */
@Entity
@Table(name = "School")
@Getter
@Setter
public class School extends SoftDeletableEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String EXPIRED = "EXPIRED";

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
    private String status = ACTIVE;

    /**
     * Trạng thái THẬT tại ngày {@code today}: cột Status, nhưng ACTIVE mà hợp đồng đã qua hạn thì
     * tính là EXPIRED.
     *
     * <p>Suy lúc đọc thay vì chạy tác vụ nền ghi lại cột Status — cùng cách {@code
     * Assignment.isExpiredPending()} đang làm. Tác vụ nền chỉ đúng sau lần chạy gần nhất; máy demo
     * tắt qua đêm là cả bảng sai trạng thái mà không ai biết.
     *
     * <p>INACTIVE do người dùng đặt tay thì GIỮ NGUYÊN, không đổi thành EXPIRED: hai thứ đó khác
     * nhau về nghiệp vụ (một bên ngừng hợp tác, một bên hết hạn hợp đồng) và người đặt tay biết rõ
     * hơn ngày tháng trong hồ sơ.
     */
    public String effectiveStatus(LocalDate today) {
        if (ACTIVE.equals(status) && contractEndDate != null && contractEndDate.isBefore(today)) {
            return EXPIRED;
        }
        return status;
    }

    /**
     * Còn hợp tác không — điều kiện để nhận LỚP MỚI và PHÂN CÔNG MỚI.
     *
     * <p>Trường hết hạn/ngừng hợp tác vẫn nằm trong hệ thống để tra cứu lịch sử, nhưng không được
     * xếp thêm việc vào.
     */
    public boolean conHopTac(LocalDate today) {
        return ACTIVE.equals(effectiveStatus(today));
    }

    /**
     * Số ngày còn lại của hợp đồng ({@code null} nếu chưa nhập ngày hết hạn). Âm = đã quá hạn.
     * Dùng cho cảnh báo "sắp hết hạn" ở màn Quản lý trường.
     */
    public Long soNgayConLai(LocalDate today) {
        return contractEndDate == null ? null : ChronoUnit.DAYS.between(today, contractEndDate);
    }
}
