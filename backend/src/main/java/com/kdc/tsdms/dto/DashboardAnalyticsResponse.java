package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Dữ liệu cho khu biểu đồ và bảng thống kê chi tiết của Bảng điều khiển.
 *
 * @param theoThang chuỗi thời gian theo tháng (biểu đồ cột + đường)
 * @param coCauNhomMon cơ cấu buổi dạy theo nhóm môn (biểu đồ tròn)
 */
public record DashboardAnalyticsResponse(
        List<DiemThang> theoThang,
        List<LatCat> coCauNhomMon,
        List<DongPhanTich> theoGiaoVien,
        List<DongPhanTich> theoTruong,
        List<DongPhanTich> theoMon) {

    /** Một tháng trên biểu đồ. */
    public record DiemThang(String nhan, long buoiDay, double gioGiang, double chiPhi) {}

    /** Một lát của biểu đồ tròn. */
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
