package com.kdc.tsdms.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdc.tsdms.common.BusinessTime;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Bộ lọc kỳ của Bảng điều khiển — KHÔNG cần DB.
 *
 * <p>Hai luật ở đây quyết định con số trên màn hình có nghĩa hay không:
 *
 * <ul>
 *   <li>KỲ MẶC ĐỊNH là NĂM HỌC (01/9 → 31/8), không phải tháng dương lịch. Lấy "tháng này" thì
 *       mở dashboard vào tháng hè sẽ ra một màn hình toàn số 0 trong khi năm học vừa rồi có cả
 *       chục nghìn buổi dạy.
 *   <li>KỲ ĐỐI CHIẾU phải dài BẰNG ĐÚNG kỳ đang xem. Đem một quý so với một tháng rồi kết luận
 *       "giảm 66%" là con số vô nghĩa nhưng nhìn vẫn rất thuyết phục.
 * </ul>
 */
class DashboardFilterTest {

    private static DashboardFilter ky(String from, String to) {
        return new DashboardFilter(LocalDate.parse(from), LocalDate.parse(to), null, null, null);
    }

    /* ───────── Kỳ đối chiếu ───────── */

    @ParameterizedTest(name = "{0}..{1} → kỳ trước {2}..{3}")
    @CsvSource({
        // tháng 31 ngày
        "2026-03-01, 2026-03-31, 2026-01-29, 2026-02-28",
        // trọn năm học 365 ngày
        "2025-09-01, 2026-08-31, 2024-09-01, 2025-08-31",
        // đúng một ngày
        "2026-05-20, 2026-05-20, 2026-05-19, 2026-05-19",
    })
    void kyTruocDaiBangDungKyHienTai(String from, String to, String truocFrom, String truocTo) {
        DashboardFilter truoc = ky(from, to).kyTruoc();

        assertThat(truoc.from()).isEqualTo(LocalDate.parse(truocFrom));
        assertThat(truoc.to()).isEqualTo(LocalDate.parse(truocTo));
        assertThat(truoc.soNgay()).isEqualTo(ky(from, to).soNgay());
    }

    @Test
    void kyTruocKetThucDungHomTruocNgayDauKy_khongChongLan() {
        DashboardFilter nay = ky("2026-03-01", "2026-03-31");

        // Hở một ngày là mất số liệu; chồng một ngày là đếm hai lần cùng một buổi dạy.
        assertThat(nay.kyTruoc().to()).isEqualTo(nay.from().minusDays(1));
    }

    @Test
    void kyTruocGiuNguyenPhamViLoc() {
        DashboardFilter nay =
                new DashboardFilter(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"), 1, 7, 3);

        DashboardFilter truoc = nay.kyTruoc();

        // Đổi phạm vi giữa hai kỳ là so sánh hai tập dữ liệu khác nhau.
        assertThat(truoc.branchId()).isEqualTo(1);
        assertThat(truoc.schoolId()).isEqualTo(7);
        assertThat(truoc.categoryId()).isEqualTo(3);
    }

    /* ───────── Số ngày ───────── */

    @Test
    void soNgayTinhCaHaiDau() {
        assertThat(ky("2026-05-20", "2026-05-20").soNgay()).isEqualTo(1);
        assertThat(ky("2026-05-01", "2026-05-31").soNgay()).isEqualTo(31);
    }

    /* ───────── Kỳ mặc định = năm học ───────── */

    @Test
    void namHocMacDinhBatDauThang9vaKetThucCuoiThang8() {
        DashboardFilter f = DashboardFilter.namHocHienHanh();

        assertThat(f.from().getMonthValue()).isEqualTo(9);
        assertThat(f.from().getDayOfMonth()).isEqualTo(1);
        assertThat(f.to()).isEqualTo(f.from().plusYears(1).minusDays(1));
    }

    @Test
    void homNayLuonNamTrongKyMacDinh() {
        DashboardFilter f = DashboardFilter.namHocHienHanh();
        LocalDate homNay = BusinessTime.today();

        // Bản cũ mặc định "N tháng gần nhất" tính từ tháng hiện tại, nên mở vào tháng hè là ra
        // màn hình rỗng. Bất biến này chặn việc quay lại kiểu đó.
        assertThat(homNay).isBetween(f.from(), f.to());
    }

    @Test
    void namHocKhongDeLotNgayNao_kyTruocNoiLienKyNay() {
        DashboardFilter nay = DashboardFilter.namHocHienHanh();

        assertThat(nay.kyTruoc().to().plusDays(1)).isEqualTo(nay.from());
    }

    /* ───────── Nhãn hiển thị ───────── */

    @Test
    void tronMotNamHocThiHienNhanNamHoc() {
        assertThat(ky("2025-09-01", "2026-08-31").nhan()).isEqualTo("Năm học 2025–2026");
    }

    @Test
    void khoangLeThiHienHaiMocNgay() {
        assertThat(ky("2025-09-01", "2025-12-31").nhan()).isEqualTo("01/09/2025 – 31/12/2025");
    }

    /* ───────── Ràng buộc đầu vào ───────── */

    @Test
    void ngayCuoiTruocNgayDau_biTuChoi() {
        assertThatThrownBy(() -> ky("2026-03-31", "2026-03-01")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thieuMocThoiGian_biTuChoi() {
        assertThatThrownBy(() -> new DashboardFilter(null, LocalDate.parse("2026-03-01"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardFilter(LocalDate.parse("2026-03-01"), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
