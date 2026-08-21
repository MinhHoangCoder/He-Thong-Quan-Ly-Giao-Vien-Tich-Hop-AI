package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Các chỉ số tổng hợp ở đầu Bảng điều khiển, kèm so sánh với kỳ liền trước.
 *
 * @param ky nhãn kỳ đang xem, vd "Năm học 2025–2026"
 * @param kyTruoc nhãn kỳ đối chiếu
 * @param tinhDenLuc thời điểm chốt số liệu (giờ Việt Nam)
 */
public record DashboardSummaryResponse(String ky, String kyTruoc, String tinhDenLuc, List<Kpi> chiSo) {

    /**
     * Một thẻ chỉ số. Trả GIÁ TRỊ THÔ kèm mã định dạng để FE tự hiển thị — cùng con số đó còn
     * dùng cho file CSV nên không định dạng sẵn thành chuỗi.
     *
     * @param giaTri null = chưa đo được (FE hiện "—"), khác hẳn với 0
     * @param dinhDang so | gio | tien | phanTram
     * @param thayDoi % thay đổi so với kỳ trước; null khi kỳ trước không có dữ liệu
     * @param phu dòng phụ đã định dạng sẵn
     */
    public record Kpi(
            String key,
            String icon,
            String nhan,
            Double giaTri,
            String dinhDang,
            Double giaTriKyTruoc,
            Double thayDoi,
            String phu,
            String mau,
            String route) {}
}
