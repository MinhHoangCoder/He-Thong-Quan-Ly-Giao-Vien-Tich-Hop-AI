package com.kdc.tsdms.dto;

/**
 * Một dòng bảng "Thống kê chi tiết"; cùng hình dạng cho cả ba chiều (giáo viên / trường / môn).
 *
 * <p>Tách khỏi {@link DashboardAnalyticsResponse} vì mỗi chiều nay là MỘT lượt gọi riêng: giao
 * diện chỉ hiện một tab tại một thời điểm, gom sẵn cả ba chiều là làm thừa hai phần ba công việc
 * ở mọi lần mở trang.
 *
 * @param chuyenCan % có mặt; null = chưa có dữ liệu chấm công
 * @param diemDanhGia điểm trung bình 1–5; null = chưa được đánh giá
 */
public record DashboardBreakdownRow(
        Integer id,
        String ten,
        String phu,
        long buoiDay,
        double gioGiang,
        double tyLeDuyet,
        Double chuyenCan,
        Double diemDanhGia,
        double chiPhi) {}
