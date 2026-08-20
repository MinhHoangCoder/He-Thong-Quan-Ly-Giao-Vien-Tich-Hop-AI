package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Khu điều hành của Bảng điều khiển: việc cần xử lý, lịch dạy trong ngày và phân công gần đây.
 *
 * <p>Khác với khu phân tích, khu này KHÔNG chịu bộ lọc thời gian. Lý do: "hợp đồng sắp hết hạn"
 * hay "buổi đã dạy chưa chấm công" là việc phải làm HÔM NAY — nếu để bộ lọc chi phối thì người
 * dùng lỡ chọn xem số liệu năm ngoái sẽ thấy cảnh báo biến mất và tưởng là đã xử lý xong.
 *
 * @param canhBao danh sách việc cần xử lý, đã xếp theo mức độ khẩn
 * @param lichNhan nhãn của khối lịch, vd "Hôm nay · Thứ Năm 20/08/2026"
 * @param lichLaDuBao true khi hôm nay không có buổi nào và hệ thống hiển thị ngày dạy kế tiếp
 * @param lich các buổi dạy của ngày đang hiển thị
 * @param phanCongGanDay sáu phân công mới lập gần nhất
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
     * @param key mã định danh
     * @param muc mức độ: {@code khan} (đỏ) | {@code luuY} (cam) | {@code tin} (xanh)
     * @param nhan tiêu đề ngắn
     * @param soLuong số bản ghi đang chờ; 0 = mọi thứ đều ổn
     * @param moTa câu giải thích việc cần làm
     * @param route đường dẫn tới trang xử lý
     */
    public record CanhBao(String key, String muc, String nhan, long soLuong, String moTa, String route) {}

    /**
     * Một buổi dạy trên dải thời gian.
     *
     * @param batDau giờ bắt đầu "HH:mm"
     * @param ketThuc giờ kết thúc "HH:mm"
     * @param trangThaiThoiGian {@code daXong} | {@code dangDien} | {@code sapToi}
     */
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
     * @param ngay đã bao gồm NĂM — dữ liệu trải hai năm học nên "07/09" không đủ để phân biệt
     * @param soTiet số ô lịch mỗi tuần của phân công
     * @param tone ok | wait | done | no — quyết định màu của thẻ trạng thái
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
