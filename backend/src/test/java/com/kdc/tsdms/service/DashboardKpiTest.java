package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.DashboardFilter;
import com.kdc.tsdms.dto.DashboardSummaryResponse;
import com.kdc.tsdms.dto.DashboardSummaryResponse.Kpi;
import com.kdc.tsdms.repository.DashboardQueryRepository;
import com.kdc.tsdms.repository.DashboardQueryRepository.ThongKeKy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Cách lắp sáu thẻ chỉ số của Bảng điều khiển — KHÔNG cần DB (repository bị mock).
 *
 * <p>Khoá lại hai luật mà bản dashboard cũ đã vi phạm, cả hai đều hiện ra màn hình dưới dạng
 * một con số trông rất bình thường:
 *
 * <ul>
 *   <li>CHƯA ĐO ĐƯỢC KHÁC VỚI BẰNG KHÔNG. Kỳ chưa có dòng chấm công nào thì tỉ lệ chuyên cần
 *       phải trả {@code null} để giao diện hiện "—". Trả 0 là khẳng định "đã đo và không ai đi
 *       dạy" — sai hoàn toàn về nghĩa.
 *   <li>KỲ TRƯỚC BẰNG 0 THÌ KHÔNG CÓ PHẦN TRĂM. Tăng từ 1 lên 19 đúng là tăng 1800%, nhưng con
 *       số đó chỉ nói mẫu số quá nhỏ; đặt cạnh mũi tên xanh nó thành lời khoe sai sự thật. Bản
 *       cũ từng hiện "+1801,6%".
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardKpiTest {

    @Mock
    private DashboardQueryRepository repo;

    @InjectMocks
    private DashboardService service;

    private static final DashboardFilter KY =
            new DashboardFilter(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"), null, null, null);

    /** Số liệu một kỳ; đủ tham số để đọc ra ý nghĩa từng cột ngay tại chỗ gọi. */
    private static ThongKeKy soLieu(
            long buoiDuyet,
            long buoiTatCa,
            double gioGiang,
            double chiPhi,
            long gvCoLich,
            long truongCoLich,
            long ccTong,
            long ccCoMat,
            long ccDungGio) {
        return new ThongKeKy(
                buoiDuyet, buoiTatCa, gioGiang, chiPhi, gvCoLich, truongCoLich, ccTong, ccCoMat, ccDungGio);
    }

    /** Kỳ này và kỳ trước trả về theo đúng thứ tự service gọi. */
    private DashboardSummaryResponse dung(ThongKeKy nay, ThongKeKy truoc) {
        when(repo.thongKeKy(any())).thenReturn(nay, truoc);
        when(repo.demGiaoVienHoatDong(any())).thenReturn(90L);
        when(repo.demTruongDangHopDong(any())).thenReturn(18L);
        return service.summary(KY);
    }

    private static Kpi lay(DashboardSummaryResponse r, String key) {
        return r.chiSo().stream()
                .filter(k -> k.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có chỉ số " + key));
    }

    /* ───────── Chưa đo được ≠ bằng không ───────── */

    @Test
    void chuaChamCongBuoiNao_tiLeChuyenCanLaNullChuKhongPhaiZero() {
        DashboardSummaryResponse r =
                dung(soLieu(100, 110, 70, 12_000_000, 8, 3, 0, 0, 0), soLieu(90, 95, 63, 10_000_000, 8, 3, 0, 0, 0));

        Kpi cc = lay(r, "chuyenCan");
        assertThat(cc.giaTri()).isNull();
        assertThat(cc.phu()).isEqualTo("Chưa có dữ liệu chấm công");
    }

    @Test
    void coChamCong_tinhDungTiLeCoMatVaDungGio() {
        // 80 có mặt / 100 đã chấm = 80%; 75 đúng giờ = 75%
        DashboardSummaryResponse r = dung(
                soLieu(100, 100, 70, 12_000_000, 8, 3, 100, 80, 75),
                soLieu(100, 100, 70, 12_000_000, 8, 3, 100, 80, 75));

        Kpi cc = lay(r, "chuyenCan");
        assertThat(cc.giaTri()).isEqualTo(80.0);
        assertThat(cc.phu()).contains("75,0%");
    }

    /* ───────── Kỳ trước bằng 0 thì không có phần trăm ───────── */

    @Test
    void kyTruocKhongCoDuLieu_khongHienPhanTramThayDoi() {
        DashboardSummaryResponse r =
                dung(soLieu(19, 20, 13, 2_000_000, 5, 2, 19, 18, 17), soLieu(0, 0, 0, 0, 0, 0, 0, 0, 0));

        // Đây chính là chỗ bản cũ hiện "+1801,6%"
        assertThat(lay(r, "buoiDay").thayDoi()).isNull();
        assertThat(lay(r, "gioGiang").thayDoi()).isNull();
        assertThat(lay(r, "chiPhi").thayDoi()).isNull();
    }

    @Test
    void kyTruocCoDuLieu_tinhDungPhanTram() {
        DashboardSummaryResponse r = dung(
                soLieu(150, 150, 100, 15_000_000, 9, 4, 150, 150, 150),
                soLieu(100, 100, 80, 10_000_000, 8, 3, 100, 100, 100));

        assertThat(lay(r, "buoiDay").thayDoi()).isEqualTo(50.0); // 100 → 150
        assertThat(lay(r, "chiPhi").thayDoi()).isEqualTo(50.0); // 10tr → 15tr
        assertThat(lay(r, "buoiDay").giaTriKyTruoc()).isEqualTo(100.0);
    }

    @Test
    void giamThiPhanTramAm() {
        DashboardSummaryResponse r = dung(
                soLieu(50, 50, 35, 5_000_000, 4, 2, 50, 50, 50), soLieu(100, 100, 70, 10_000_000, 8, 3, 100, 100, 100));

        assertThat(lay(r, "buoiDay").thayDoi()).isEqualTo(-50.0);
    }

    /* ───────── Nội dung thẻ ───────── */

    @Test
    void duSauChiSoTheoDungThuTu() {
        DashboardSummaryResponse r = dung(
                soLieu(100, 110, 70, 12_000_000, 8, 3, 100, 95, 89),
                soLieu(100, 110, 70, 12_000_000, 8, 3, 100, 95, 89));

        assertThat(r.chiSo())
                .extracting(Kpi::key)
                .containsExactly("buoiDay", "gioGiang", "chiPhi", "chuyenCan", "giaoVien", "truong");
    }

    @Test
    void theBuoiDayHienTiLeDaDuyet() {
        // 100 duyệt / 110 đã xếp = 90,9%
        DashboardSummaryResponse r = dung(
                soLieu(100, 110, 70, 12_000_000, 8, 3, 100, 95, 89),
                soLieu(100, 110, 70, 12_000_000, 8, 3, 100, 95, 89));

        assertThat(lay(r, "buoiDay").giaTri()).isEqualTo(100.0);
        assertThat(lay(r, "buoiDay").phu()).contains("90,9%").contains("đã duyệt");
    }

    @Test
    void theChiPhiHienChiPhiMoiBuoi() {
        // 12.500.000đ / 100 buổi = 125.000đ mỗi buổi
        DashboardSummaryResponse r = dung(
                soLieu(100, 100, 70, 12_500_000, 8, 3, 100, 100, 100),
                soLieu(100, 100, 70, 12_500_000, 8, 3, 100, 100, 100));

        assertThat(lay(r, "chiPhi").phu()).contains("125.000").contains("/buổi");
    }

    @Test
    void khongCoBuoiDayNao_khongChiaChoKhongOTheChiPhi() {
        DashboardSummaryResponse r = dung(soLieu(0, 0, 0, 0, 0, 0, 0, 0, 0), soLieu(0, 0, 0, 0, 0, 0, 0, 0, 0));

        assertThat(lay(r, "chiPhi").phu()).isEqualTo("Chưa phát sinh");
        assertThat(lay(r, "gioGiang").phu()).isEqualTo("Chưa có giáo viên đứng lớp");
        assertThat(lay(r, "buoiDay").phu()).isEqualTo("Chưa xếp buổi nào");
    }

    @Test
    void theGiaoVienVaTruongHienMauSoLayTuHopDong() {
        DashboardSummaryResponse r = dung(
                soLieu(100, 100, 70, 12_000_000, 82, 18, 100, 95, 89),
                soLieu(100, 100, 70, 12_000_000, 82, 18, 100, 95, 89));

        assertThat(lay(r, "giaoVien").giaTri()).isEqualTo(82.0);
        assertThat(lay(r, "giaoVien").phu()).contains("90 giáo viên đang làm việc");
        assertThat(lay(r, "truong").giaTri()).isEqualTo(18.0);
        assertThat(lay(r, "truong").phu()).contains("18 trường còn hợp đồng");
    }

    @Test
    void moiTheDeuCoDuongDanDeBamSang() {
        DashboardSummaryResponse r = dung(
                soLieu(100, 100, 70, 12_000_000, 8, 3, 100, 95, 89),
                soLieu(100, 100, 70, 12_000_000, 8, 3, 100, 95, 89));

        assertThat(r.chiSo()).allSatisfy(k -> {
            assertThat(k.route()).isNotBlank();
            assertThat(k.icon()).isNotBlank();
            assertThat(k.dinhDang()).isIn("so", "gio", "tien", "phanTram");
        });
    }

    @Test
    void nhanKyVaKyDoiChieuDuocGhiRo() {
        DashboardSummaryResponse r = dung(
                soLieu(100, 100, 70, 12_000_000, 8, 3, 100, 95, 89),
                soLieu(100, 100, 70, 12_000_000, 8, 3, 100, 95, 89));

        assertThat(r.ky()).isEqualTo("01/03/2026 – 31/03/2026");
        assertThat(r.kyTruoc()).isEqualTo("29/01/2026 – 28/02/2026");
        assertThat(r.tinhDenLuc()).isNotBlank();
    }

    /* ───────── Chỉ số phải đọc từ ĐÚNG kỳ ───────── */

    @Test
    void giaTriLayTuKyNay_giaTriKyTruocLayTuKyTruoc() {
        List<ThongKeKy> hai = List.of(
                soLieu(200, 200, 140, 20_000_000, 10, 5, 200, 200, 200),
                soLieu(100, 100, 70, 10_000_000, 8, 3, 100, 100, 100));

        DashboardSummaryResponse r = dung(hai.get(0), hai.get(1));

        // Đảo hai kỳ cho nhau là mọi mũi tên tăng/giảm chỉ ngược chiều mà không ai phát hiện.
        assertThat(lay(r, "buoiDay").giaTri()).isEqualTo(200.0);
        assertThat(lay(r, "buoiDay").giaTriKyTruoc()).isEqualTo(100.0);
    }
}
