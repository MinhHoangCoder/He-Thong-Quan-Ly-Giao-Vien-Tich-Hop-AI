package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Các thẻ chỉ số ở đầu Bảng điều khiển.
 *
 * @param tinhDenLuc thời điểm chốt số liệu (giờ Việt Nam), dạng "HH:mm dd/MM/yyyy"
 */
public record DashboardSummaryResponse(String tinhDenLuc, List<Kpi> chiSo) {

    /**
     * Một thẻ chỉ số. Trả GIÁ TRỊ THÔ kèm mã định dạng để FE tự hiển thị — cùng con số đó còn
     * dùng cho file CSV nên không định dạng sẵn thành chuỗi.
     *
     * @param giaTri null = chưa đo được (FE hiện "—"), khác hẳn với 0
     * @param dinhDang so | tien | phanTram
     * @param thayDoi % thay đổi so với kỳ trước; null = KHÔNG hiện mũi tên. Chỉ số không phụ
     *     thuộc kỳ (tổng giáo viên, tổng trường) luôn để null vì đem chúng so giữa hai kỳ thì
     *     lúc nào cũng ra 0% — một con số đúng về số học nhưng vô nghĩa và nhìn như tính hỏng.
     * @param phu dòng phụ đã định dạng sẵn
     */
    public record Kpi(
            String key,
            String icon,
            String nhan,
            Double giaTri,
            String dinhDang,
            Double thayDoi,
            String phu,
            String mau,
            String route) {}
}
