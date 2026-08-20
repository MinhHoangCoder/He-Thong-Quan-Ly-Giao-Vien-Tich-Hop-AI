package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Dữ liệu cho khu biểu đồ và bảng thống kê chi tiết của Bảng điều khiển.
 *
 * @param theoThang chuỗi thời gian theo tháng (biểu đồ cột + đường)
 * @param nhietDo mật độ buổi dạy theo (thứ × tiết)
 * @param soTietToiDa số tiết lớn nhất trong ngày, dùng dựng trục cho bản đồ nhiệt
 * @param coCauNhomMon cơ cấu buổi dạy theo nhóm môn (biểu đồ tròn)
 * @param topTruong xếp hạng trường theo số buổi dạy
 */
public record DashboardAnalyticsResponse(
        List<DiemThang> theoThang,
        List<ODoNhiet> nhietDo,
        int soTietToiDa,
        List<LatCat> coCauNhomMon,
        List<LatCat> topTruong,
        List<DongPhanTich> theoGiaoVien,
        List<DongPhanTich> theoTruong,
        List<DongPhanTich> theoMon) {

    /** Một tháng trên biểu đồ. */
    public record DiemThang(String nhan, long buoiDay, double gioGiang, double chiPhi) {}

    /** Một ô bản đồ nhiệt. thu: 1 = Thứ Hai … 7 = Chủ Nhật. */
    public record ODoNhiet(int thu, int tiet, long soBuoi) {}

    /** Một lát của biểu đồ tròn hoặc một thanh của biểu đồ xếp hạng. */
    public record LatCat(Integer id, String nhan, long giaTri, String mau) {}

    /**
     * Một dòng bảng thống kê chi tiết; cùng hình dạng cho cả ba tab.
     *
     * @param chuyenCan % có mặt; null = chưa có dữ liệu chấm công
     * @param diemDanhGia điểm trung bình 1–5; null = chưa được đánh giá
     */
    public record DongPhanTich(
            Integer id,
            String ten,
            String phu,
            long buoiDay,
            double gioGiang,
            double tyLeDuyet,
            Double chuyenCan,
            Double diemDanhGia,
            double chiPhi) {}
}
