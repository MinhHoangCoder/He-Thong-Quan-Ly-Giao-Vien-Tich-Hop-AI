package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Sáu chỉ số chủ chốt ở đầu Bảng điều khiển, kèm so sánh với kỳ liền trước.
 *
 * @param ky nhãn kỳ đang xem, vd "Năm học 2025–2026"
 * @param kyTruoc nhãn kỳ đối chiếu
 * @param tinhDenLuc thời điểm chốt số liệu (giờ Việt Nam), để người xem biết số liệu mới tới đâu
 * @param chiSo danh sách thẻ chỉ số
 */
public record DashboardSummaryResponse(String ky, String kyTruoc, String tinhDenLuc, List<Kpi> chiSo) {

    /**
     * Một thẻ chỉ số.
     *
     * <p>Trả về GIÁ TRỊ THÔ chứ không phải chuỗi đã định dạng, kèm mã {@code dinhDang} để phía
     * giao diện tự hiển thị. Làm vậy để cùng một con số có thể vừa lên thẻ, vừa xuống file CSV
     * mà không phải bóc tách lại chuỗi "1.234.567 ₫".
     *
     * @param key mã định danh, giao diện dùng để chọn đường dẫn khi bấm vào thẻ
     * @param nhan nhãn hiển thị
     * @param giaTri giá trị thô của kỳ này; null = chưa đo được (giao diện hiện "—", không hiện 0)
     * @param dinhDang so | gio | tien | phanTram
     * @param giaTriKyTruoc giá trị cùng chỉ số ở kỳ trước; null = kỳ trước không có dữ liệu
     * @param thayDoi phần trăm thay đổi so với kỳ trước; null khi mẫu số bằng 0 (xem
     *     {@code DashboardService#phanTramThayDoi})
     * @param phu dòng phụ đã định dạng sẵn, vd "94,2% buổi đã được duyệt"
     * @param giaiThich công thức tính, hiện ra khi rê chuột — để người xem không phải đoán con số
     *     ở đâu ra
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
            String giaiThich,
            String mau,
            String route) {}
}
