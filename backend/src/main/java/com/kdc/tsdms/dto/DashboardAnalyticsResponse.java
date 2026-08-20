package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Toàn bộ dữ liệu cho khu phân tích của Bảng điều khiển: bốn biểu đồ và một bảng ba tab.
 *
 * <p>Gộp chung một lần trả về thay vì mỗi biểu đồ một lời gọi, vì cả năm khối đều đọc từ CÙNG
 * một bộ lọc — tách ra thì mỗi lần đổi bộ lọc sẽ bắn năm request và các khối cập nhật lệch nhau
 * vài trăm mili giây, nhìn rất giật.
 *
 * @param theoThang chuỗi thời gian tháng — vẽ biểu đồ cột (khối lượng) chồng đường (chi phí)
 * @param nhietDo mật độ buổi dạy theo (thứ × tiết)
 * @param soTietToiDa số tiết lớn nhất trong ngày của toàn hệ thống, để dựng trục cho bản đồ nhiệt
 * @param coCauNhomMon cơ cấu buổi dạy theo nhóm môn — biểu đồ tròn khuyết
 * @param topTruong xếp hạng trường theo số buổi dạy — biểu đồ thanh ngang
 * @param theoGiaoVien bảng phân tích, tab "Theo giáo viên"
 * @param theoTruong bảng phân tích, tab "Theo trường"
 * @param theoMon bảng phân tích, tab "Theo môn"
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

    /**
     * Một tháng trên biểu đồ xu hướng.
     *
     * @param nhan nhãn trục X, vd "T9/2025"
     * @param buoiDay số buổi đã duyệt
     * @param gioGiang tổng giờ giảng
     * @param chiPhi chi phí lương phân bổ cho các buổi đó
     */
    public record DiemThang(String nhan, long buoiDay, double gioGiang, double chiPhi) {}

    /**
     * Một ô của bản đồ nhiệt.
     *
     * @param thu 1 = Thứ Hai … 7 = Chủ Nhật
     * @param tiet số thứ tự tiết trong ngày
     * @param soBuoi số buổi dạy rơi vào ô này
     */
    public record ODoNhiet(int thu, int tiet, long soBuoi) {}

    /** Một lát của biểu đồ tròn hoặc một thanh của biểu đồ xếp hạng. */
    public record LatCat(Integer id, String nhan, long giaTri, String mau) {}

    /**
     * Một dòng của bảng phân tích sâu. Cùng một hình dạng cho cả ba tab để giao diện chỉ cần
     * viết một component bảng duy nhất.
     *
     * @param ten tên giáo viên / trường / môn
     * @param phu dòng phụ: môn chính của GV, phường của trường, nhóm của môn…
     * @param buoiDay số buổi đã duyệt
     * @param gioGiang tổng giờ giảng
     * @param tyLeDuyet phần trăm buổi được duyệt trên tổng số buổi đã xếp
     * @param chuyenCan phần trăm có mặt (PRESENT + LATE); null = chưa có dữ liệu chấm công
     * @param diemDanhGia điểm đánh giá trung bình 1–5; null = chưa được đánh giá
     * @param chiPhi chi phí lương phân bổ
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
