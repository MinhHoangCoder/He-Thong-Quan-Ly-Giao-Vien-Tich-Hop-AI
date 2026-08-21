package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Dữ liệu cho khu BIỂU ĐỒ của Bảng điều khiển.
 *
 * <p>Bảng "Thống kê chi tiết" đã tách sang {@link DashboardBreakdownRow} và endpoint riêng: gom
 * cả ba chiều ở đây khiến một lượt gọi phải chạy 7 truy vấn, trong đó 5 truy vấn dựng hai tab
 * người dùng không nhìn tới.
 *
 * @param theoThang chuỗi thời gian theo tháng (biểu đồ cột + đường)
 * @param coCauNhomMon cơ cấu buổi dạy theo nhóm môn (biểu đồ tròn)
 */
public record DashboardAnalyticsResponse(List<DiemThang> theoThang, List<LatCat> coCauNhomMon) {

    /** Một tháng trên biểu đồ. */
    public record DiemThang(String nhan, long buoiDay, double gioGiang, double chiPhi) {}

    /** Một lát của biểu đồ tròn. */
    public record LatCat(Integer id, String nhan, long giaTri, String mau) {}
}
