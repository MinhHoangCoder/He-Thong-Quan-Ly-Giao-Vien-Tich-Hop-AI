package com.kdc.tsdms.dto;

import java.util.List;

/** Việc cần xử lý, buổi dạy sắp tới và phân công gần đây. */
public record DashboardOperationsResponse(
        List<CanhBao> canhBao, List<BuoiDay> lichSapToi, List<DongPhanCong> phanCongGanDay) {

    /**
     * Một việc cần xử lý.
     *
     * @param muc khan (đỏ) | luuY (cam) | tin (xanh) | on (không có việc nào)
     */
    public record CanhBao(String key, String muc, String nhan, long soLuong, String moTa, String route) {}

    /**
     * Một buổi dạy trong bảng "Buổi dạy 7 ngày tới".
     *
     * @param ngay dd/MM — bảng trải 7 ngày nên chỉ có giờ thì không biết là hôm nào
     * @param nhomNgay nhãn nhóm, vd "Hôm nay · Thứ Sáu 21/08"; các buổi cùng ngày dùng chung
     * @param trangThaiThoiGian daXong | dangDien | sapToi
     */
    public record BuoiDay(
            Long id,
            String ngay,
            String nhomNgay,
            String batDau,
            String ketThuc,
            String giaoVien,
            String mon,
            String truong,
            String trangThaiThoiGian) {}

    /**
     * Một dòng bảng "Phân công gần đây".
     *
     * @param ngay có kèm NĂM — dữ liệu trải hai năm học nên "07/09" không đủ phân biệt
     * @param tone ok | wait | done | no
     */
    public record DongPhanCong(
            Integer id, String giaoVien, String truong, String mon, String ngay, String nhanTrangThai, String tone) {}
}
