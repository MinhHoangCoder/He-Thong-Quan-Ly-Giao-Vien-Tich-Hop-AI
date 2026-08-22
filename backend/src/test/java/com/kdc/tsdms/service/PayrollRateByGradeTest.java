package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdc.tsdms.entity.Contract;
import com.kdc.tsdms.entity.PayRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tra đơn giá 1 tiết — KHÔNG cần DB.
 *
 * <p>Chỗ này từng là một biểu thức ba ngôi nằm lọt giữa thân hàm:
 * {@code grade <= 5 ? TH_RATE : THCS_RATE}. Đọc lướt thì rất hợp lý — "1–5 tiểu học, còn lại
 * THCS" — nhưng hồi hệ thống còn trường cấp 3 thì khối 10, 11, 12 rơi hết vào vế "còn lại" và
 * được tính theo giá THCS. Sai TIỀN LƯƠNG, mà không có exception nào, không log nào, không test
 * nào.
 *
 * <p>Từ V37 hai mức giá nằm trong bảng {@code PayRate} và có HIỆU LỰC THEO NGÀY, nên bộ test
 * này khóa lại bốn thứ:
 *
 * <ul>
 *   <li>đúng ranh giới khối 5/6 — lệch một khối là sai tiền cả lớp trong cả tháng;
 *   <li>khối ngoài barem KHÔNG được lặng lẽ nhận một đơn giá nào (lỗi cũ);
 *   <li>tra theo NGÀY DẠY chứ không theo hôm nay — tính lại tháng cũ phải ra giá tháng cũ;
 *   <li>đơn giá riêng trong hợp đồng thắng barem chung.
 * </ul>
 */
class PayrollRateByGradeTest {

    private static final BigDecimal TH = new BigDecimal("115000");
    private static final BigDecimal THCS = new BigDecimal("125000");
    private static final BigDecimal TH_MOI = new BigDecimal("130000");

    private static final LocalDate NGAY_TANG_GIA = LocalDate.of(2026, 9, 1);
    private static final LocalDate TRUOC_TANG_GIA = LocalDate.of(2026, 7, 15);
    private static final LocalDate SAU_TANG_GIA = LocalDate.of(2026, 10, 15);

    /** Bảng giá đúng dáng thật: mức TH đã bị đóng khi tăng giá, mức THCS còn nguyên. */
    private static List<PayRate> bangGia() {
        return List.of(
                rate(1, 5, TH_MOI, NGAY_TANG_GIA, null),
                rate(1, 5, TH, LocalDate.of(2020, 1, 1), NGAY_TANG_GIA.minusDays(1)),
                rate(6, 9, THCS, LocalDate.of(2020, 1, 1), null));
    }

    private static PayRate rate(int from, int to, BigDecimal amount, LocalDate hieuLucTu, LocalDate hieuLucDen) {
        PayRate r = new PayRate();
        r.setGradeFrom((short) from);
        r.setGradeTo((short) to);
        r.setAmount(amount);
        r.setEffectiveFrom(hieuLucTu);
        r.setEffectiveTo(hieuLucDen);
        return r;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void khoiTieuHoc_dungDonGiaTH(int grade) {
        assertThat(PayrollService.resolveRate(null, grade, TRUOC_TANG_GIA, bangGia()))
                .isEqualByComparingTo(TH);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7, 8, 9})
    void khoiThcs_dungDonGiaThcs(int grade) {
        assertThat(PayrollService.resolveRate(null, grade, TRUOC_TANG_GIA, bangGia()))
                .isEqualByComparingTo(THCS);
    }

    @Test
    void ranhGioi5va6_khongLechMotKhoi() {
        assertThat(PayrollService.resolveRate(null, 5, TRUOC_TANG_GIA, bangGia()))
                .isEqualByComparingTo(TH);
        assertThat(PayrollService.resolveRate(null, 6, TRUOC_TANG_GIA, bangGia()))
                .isEqualByComparingTo(THCS);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11, 12})
    void khoiCap3_khongConDuocTinhTheoGiaThcs(int grade) {
        // Đây chính là lỗi cũ: 10/11/12 từng lọt vào vế "còn lại" của biểu thức ba ngôi.
        assertThat(PayrollService.resolveRate(null, grade, TRUOC_TANG_GIA, bangGia()))
                .as("khối %d không có trong barem nên không được gán đơn giá nào", grade)
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 13, 99})
    void khoiVoNghia_traVeNull(int grade) {
        assertThat(PayrollService.resolveRate(null, grade, TRUOC_TANG_GIA, bangGia()))
                .isNull();
    }

    @Test
    void khongCoThongTinKhoi_traVeNull() {
        assertThat(PayrollService.resolveRate(null, null, TRUOC_TANG_GIA, bangGia()))
                .isNull();
    }

    @Test
    void tangGiaTuThangChin_thangBayVanTinhTheoGiaCu() {
        // Lý do bảng PayRate phải có khoảng hiệu lực thay vì một cột giá: bảng lương tính lại
        // được bất cứ lúc nào. Nếu tra theo giá hiện hành thì bấm "Tính lại" tháng 7 sau khi
        // tăng giá sẽ ra số khác với số đã trả cho giáo viên, mà không ai đụng vào chấm công.
        assertThat(PayrollService.resolveRate(null, 3, TRUOC_TANG_GIA, bangGia()))
                .as("buổi dạy tháng 7 phải tính theo giá cũ")
                .isEqualByComparingTo(TH);
        assertThat(PayrollService.resolveRate(null, 3, SAU_TANG_GIA, bangGia()))
                .as("buổi dạy tháng 10 tính theo giá mới")
                .isEqualByComparingTo(TH_MOI);
    }

    @Test
    void ngayDay_dungMocTangGia_thiAnGiaMoi() {
        // Khoảng hiệu lực tính CẢ ngày đầu: mức mới có hiệu lực "từ 1/9" nghĩa là buổi dạy
        // ngày 1/9 đã ăn giá mới, còn 31/8 vẫn giá cũ.
        assertThat(PayrollService.resolveRate(null, 3, NGAY_TANG_GIA, bangGia()))
                .isEqualByComparingTo(TH_MOI);
        assertThat(PayrollService.resolveRate(null, 3, NGAY_TANG_GIA.minusDays(1), bangGia()))
                .isEqualByComparingTo(TH);
    }

    @Test
    void hopDongCoDonGiaRieng_thiThangBaremChung() {
        Contract hd = new Contract();
        hd.setRatePerPeriod(new BigDecimal("200000"));

        assertThat(PayrollService.resolveRate(hd, 3, TRUOC_TANG_GIA, bangGia()))
                .as("giáo viên thương lượng riêng thì barem chung không có tiếng nói")
                .isEqualByComparingTo(new BigDecimal("200000"));
    }

    @Test
    void hopDongKhongKhaiDonGiaRieng_thiVeBaremChung() {
        Contract hd = new Contract(); // ratePerPeriod = null

        assertThat(PayrollService.resolveRate(hd, 7, TRUOC_TANG_GIA, bangGia())).isEqualByComparingTo(THCS);
    }

    @Test
    void ngayDayNamNgoaiMoiKhoangHieuLuc_traVeNull() {
        // Không đoán một mức mặc định: đoán ở đây là ghi tiền sai vào phiếu lương mà không ai
        // biết. Trả null để bên gọi ghi cảnh báo và bỏ qua tiết đó.
        assertThat(PayrollService.resolveRate(null, 3, LocalDate.of(2019, 5, 1), bangGia()))
                .isNull();
    }
}
