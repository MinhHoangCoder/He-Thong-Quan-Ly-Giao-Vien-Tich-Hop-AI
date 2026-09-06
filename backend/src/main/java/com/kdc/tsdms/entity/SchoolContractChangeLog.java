package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Nhật ký đổi NGÀY HẾT HẠN HỢP ĐỒNG DỊCH VỤ của một trường (bảng SchoolContractChangeLog, V40).
 *
 * <p>Vì sao cần: {@code School.ContractEndDate} quyết định trường còn nhận lớp mới và phân công
 * mới hay không (xem {@code School.conHopTac}). Nó lại là một ô ngày sửa tự do trên form, nên
 * trường đã hết hạn chỉ cần kéo ngày lên là "sống lại" — không ai ký, không ai biết. V40 chọn
 * cách RĂN ĐE BẰNG DẤU VẾT thay vì khoá ô ngày lại: vẫn sửa được trong 10 giây, nhưng mỗi lần
 * sửa để lại một dòng ở đây kèm lý do.
 *
 * <p>Chỉ GHI THÊM, không sửa và không xoá: một dòng nhật ký sửa được thì không còn là nhật ký.
 * Vì vậy service duy nhất được phép gọi {@code save} với bản ghi MỚI (xem
 * {@code SchoolService.ghiNhatKyDoiHanHopDong}).
 */
@Entity
@Table(name = "SchoolContractChangeLog")
@Getter
@Setter
public class SchoolContractChangeLog {

    /** Kéo dài hạn — hướng đáng ngờ nhất, và là lý do cả bảng này ra đời. */
    public static final String EXTEND = "EXTEND";

    /** Rút ngắn hạn (chấm dứt hợp tác sớm). */
    public static final String SHORTEN = "SHORTEN";

    /** Điền lần đầu: trước đó hồ sơ chưa có ngày hết hạn. */
    public static final String SET = "SET";

    /** Xoá trắng ngày hết hạn — nguy hiểm ngang EXTEND vì hợp đồng thành vô thời hạn. */
    public static final String CLEAR = "CLEAR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "SchoolId", nullable = false)
    private Integer schoolId;

    /** {@code null} = trước đó chưa nhập ngày hết hạn. */
    @Column(name = "OldEndDate")
    private LocalDate oldEndDate;

    /** {@code null} = vừa xoá trắng ngày hết hạn. */
    @Column(name = "NewEndDate")
    private LocalDate newEndDate;

    /** EXTEND | SHORTEN | SET | CLEAR — xem các hằng số ở trên. */
    @Column(name = "ChangeKind", nullable = false)
    private String changeKind;

    @Column(name = "Reason", nullable = false)
    private String reason;

    /** {@code null} khi thao tác đi từ script/job chứ không phải người đăng nhập. */
    @Column(name = "ChangedByUserId")
    private Integer changedByUserId;

    @Column(name = "ChangedAt", nullable = false)
    private Instant changedAt;

    /**
     * Phân loại một lần đổi hạn, hoặc {@code null} nếu hai ngày y hệt nhau (= không có gì để ghi).
     *
     * <p>Đặt ở entity chứ không ở service vì đây là luật đọc dữ liệu của chính bảng này: bên nào
     * ghi vào bảng cũng phải phân loại đúng một kiểu, không thì cột ChangeKind mỗi chỗ một nghĩa
     * và bộ lọc "ai đã kéo dài hạn" đếm hụt.
     *
     * <p>Trả {@code null} thay vì một mã "NO_CHANGE": bấm Lưu mà không đụng tới ngày hết hạn là
     * chuyện xảy ra hàng ngày (sửa số điện thoại, đổi người liên hệ) — ghi lại thì nhật ký đầy
     * dòng vô nghĩa và cái cần tìm chìm nghỉm.
     */
    public static String phanLoai(LocalDate hanCu, LocalDate hanMoi) {
        if (Objects.equals(hanCu, hanMoi)) {
            return null;
        }
        if (hanCu == null) {
            return SET;
        }
        if (hanMoi == null) {
            return CLEAR;
        }
        return hanMoi.isAfter(hanCu) ? EXTEND : SHORTEN;
    }
}
