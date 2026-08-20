package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Việc cần xử lý, lịch dạy trong ngày và phân công gần đây.
 *
 * @param lichLaDuBao true khi hôm nay không có buổi nào và đang hiển thị ngày dạy kế tiếp
 */
public record DashboardOperationsResponse(
        List<CanhBao> canhBao,
        String lichNhan,
        boolean lichLaDuBao,
        List<BuoiDay> lich,
        List<DongPhanCong> phanCongGanDay) {

    /**
     * Một việc cần xử lý.
     *
     * @param muc khan (đỏ) | luuY (cam) | tin (xanh) | on (không có việc nào)
     */
    public record CanhBao(String key, String muc, String nhan, long soLuong, String moTa, String route) {}

    /** Một buổi dạy. trangThaiThoiGian: daXong | dangDien | sapToi. */
    public record BuoiDay(
            Long id,
            String batDau,
            String ketThuc,
            String giaoVien,
            String mon,
            String truong,
            String phong,
            String trangThaiThoiGian,
            String mau) {}

    /**
     * Một dòng bảng "Phân công gần đây".
     *
     * @param ngay có kèm NĂM — dữ liệu trải hai năm học nên "07/09" không đủ phân biệt
     * @param tone ok | wait | done | no
     */
    public record DongPhanCong(
            Integer id,
            String giaoVien,
            String truong,
            String mon,
            String ngay,
            int soTiet,
            String nhanTrangThai,
            String tone) {}
}
