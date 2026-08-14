package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Đơn giá 1 tiết suy từ KHỐI của lớp — KHÔNG cần DB.
 *
 * <p>Chỗ này từng là một biểu thức ba ngôi nằm lọt giữa thân hàm:
 * {@code grade <= 5 ? TH_RATE : THCS_RATE}. Đọc lướt thì rất hợp lý — "1–5 tiểu học, còn lại
 * THCS" — nhưng hồi hệ thống còn trường cấp 3 thì khối 10, 11, 12 rơi hết vào vế "còn lại" và
 * được tính 125.000đ/tiết theo giá THCS. Sai TIỀN LƯƠNG, mà không có exception nào, không có
 * log nào, không có test nào.
 *
 * <p>Nay cấp 3 đã bị bỏ khỏi hệ thống (V26 + validate ở SchoolClassService). Bộ test này khóa
 * lại cả hai vế: đúng ranh giới 5/6, và khối ngoài 1–9 KHÔNG được lặng lẽ nhận một đơn giá nào.
 */
class PayrollRateByGradeTest {

    private static final BigDecimal TH = new BigDecimal(PayrollService.TH_RATE_STR);
    private static final BigDecimal THCS = new BigDecimal(PayrollService.THCS_RATE_STR);

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void khoiTieuHoc_dungDonGiaTH(int grade) {
        assertThat(PayrollService.rateForGrade(grade)).isEqualByComparingTo(TH);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7, 8, 9})
    void khoiThcs_dungDonGiaThcs(int grade) {
        assertThat(PayrollService.rateForGrade(grade)).isEqualByComparingTo(THCS);
    }

    @Test
    void ranhGioi5va6_khongLechMotKhoi() {
        // Lớp 5 và lớp 6 chỉ cách nhau một khối nhưng khác đơn giá — sai một bước là sai tiền
        // của cả một lớp trong cả tháng.
        assertThat(PayrollService.rateForGrade(5)).isEqualByComparingTo(TH);
        assertThat(PayrollService.rateForGrade(6)).isEqualByComparingTo(THCS);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11, 12})
    void khoiCap3_khongConDuocTinhTheoGiaThcs(int grade) {
        // Đây chính là lỗi cũ: 10/11/12 từng lọt vào vế "còn lại" của biểu thức ba ngôi.
        assertThat(PayrollService.rateForGrade(grade))
                .as("khối %d không thuộc 1-9 nên không được gán đơn giá nào", grade)
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 13, 99})
    void khoiVoNghia_traVeNull(int grade) {
        assertThat(PayrollService.rateForGrade(grade)).isNull();
    }

    @Test
    void khongCoThongTinKhoi_traVeNull() {
        assertThat(PayrollService.rateForGrade(null)).isNull();
    }
}
