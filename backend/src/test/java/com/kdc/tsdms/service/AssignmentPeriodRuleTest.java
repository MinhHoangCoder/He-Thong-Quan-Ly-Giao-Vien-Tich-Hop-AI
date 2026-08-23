package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdc.tsdms.exception.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * GIAI ĐOẠN CỦA PHIẾU PHÂN CÔNG: bắt buộc có ngày kết thúc, và không dài quá 12 tháng.
 *
 * <p>Hai lỗ hổng cùng nằm ở ô "Ngày kết thúc".
 *
 * <p><b>Bỏ trống.</b> Bản cũ cho để trống rồi âm thầm chốt thành {@code startDate + 8 tuần} —
 * một con số người xếp lịch không chọn và không nhìn thấy. Màn hình ghi "17/08 → không giới
 * hạn" trong khi buổi dạy cuối cùng nằm ở tuần thứ tám, nên cả học kỳ chạy theo một cái lịch
 * đã hết từ lâu mà không ai biết.
 *
 * <p><b>Gõ nhầm năm.</b> Không có trần thì "31/12/2099" là hợp lệ. Generator trải slot theo
 * từng tuần nên một phiếu như vậy sinh ra hàng trăm nghìn buổi dạy: request treo, bảng
 * Schedule phình lên, và cách duy nhất để dọn là xóa tay dưới database.
 *
 * <p>Test gọi thẳng hàm chốt chặn (không dựng cả service): luật ở đây thuần về ngày tháng,
 * dựng thêm mười mock chỉ làm mờ đi thứ đang được kiểm tra.
 */
class AssignmentPeriodRuleTest {

    private static final LocalDate BAT_DAU = LocalDate.of(2026, 9, 7);

    private static HttpStatus statusKhiGoi(LocalDate start, LocalDate end) {
        Throwable t =
                org.assertj.core.api.Assertions.catchThrowable(() -> AssignmentService.assertPeriodValid(start, end));
        return ((ApiException) t).getStatus();
    }

    @Test
    @DisplayName("Bỏ trống ngày kết thúc thì bị chặn, không tự suy ra 8 tuần nữa")
    void bo_trong_ngay_ket_thuc_thi_chan() {
        assertThatThrownBy(() -> AssignmentService.assertPeriodValid(BAT_DAU, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ngày kết thúc");
        org.assertj.core.api.Assertions.assertThat(statusKhiGoi(BAT_DAU, null)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Ngày kết thúc trước ngày bắt đầu thì bị chặn")
    void ket_thuc_truoc_bat_dau_thi_chan() {
        assertThatThrownBy(() -> AssignmentService.assertPeriodValid(BAT_DAU, BAT_DAU.minusDays(1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("phải sau ngày bắt đầu");
    }

    @Test
    @DisplayName("Gõ nhầm năm (2099) thì bị chặn trước khi sinh ra hàng trăm nghìn buổi dạy")
    void go_nham_nam_thi_chan() {
        assertThatThrownBy(() -> AssignmentService.assertPeriodValid(BAT_DAU, LocalDate.of(2099, 12, 31)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("12 tháng");
    }

    @Test
    @DisplayName("Đúng 12 tháng vẫn hợp lệ — trần là cận TRÊN, không phải cận dưới của lỗi")
    void dung_12_thang_thi_van_hop_le() {
        assertThatCode(() -> AssignmentService.assertPeriodValid(BAT_DAU, BAT_DAU.plusMonths(12)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Quá 12 tháng dù chỉ một ngày cũng bị chặn")
    void hon_12_thang_mot_ngay_thi_chan() {
        assertThatThrownBy(() -> AssignmentService.assertPeriodValid(
                        BAT_DAU, BAT_DAU.plusMonths(12).plusDays(1)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Một học kỳ (09 → 01) là ca dùng thật, phải qua")
    void mot_hoc_ky_thi_qua() {
        assertThatCode(() -> AssignmentService.assertPeriodValid(BAT_DAU, LocalDate.of(2027, 1, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Dạy đúng một ngày cũng hợp lệ")
    void day_dung_mot_ngay_thi_qua() {
        assertThatCode(() -> AssignmentService.assertPeriodValid(BAT_DAU, BAT_DAU))
                .doesNotThrowAnyException();
    }
}
