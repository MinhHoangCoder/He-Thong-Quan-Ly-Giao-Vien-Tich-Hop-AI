package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.DashboardAnalyticsResponse;
import com.kdc.tsdms.dto.DashboardAnalyticsResponse.DongPhanTich;
import com.kdc.tsdms.dto.DashboardAnalyticsResponse.LatCat;
import com.kdc.tsdms.dto.DashboardFilter;
import com.kdc.tsdms.dto.DashboardOperationsResponse;
import com.kdc.tsdms.dto.DashboardOperationsResponse.BuoiDay;
import com.kdc.tsdms.dto.DashboardOperationsResponse.CanhBao;
import com.kdc.tsdms.dto.DashboardSummaryResponse;
import com.kdc.tsdms.dto.DashboardSummaryResponse.Kpi;
import com.kdc.tsdms.repository.DashboardQueryRepository;
import com.kdc.tsdms.repository.DashboardQueryRepository.Chieu;
import com.kdc.tsdms.repository.DashboardQueryRepository.ThongKeKy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tổng hợp số liệu cho Bảng điều khiển (admin).
 *
 * <p>Truy vấn nằm hết ở {@link DashboardQueryRepository}; lớp này chỉ đặt nhãn, tính % so với kỳ
 * trước và chọn màu.
 *
 * <p>KHÔNG BỊA SỐ: chỗ chưa có dữ liệu trả null để FE hiện "—", không trả 0. "Chưa đo được" khác
 * "đo rồi và bằng 0" — nhầm hai cái đó trên số liệu lương là chuyện lớn.
 */
@Service
public class DashboardService {

    /** Ngưỡng báo hợp đồng lao động sắp hết hạn. */
    private static final int NGAY_BAO_HET_HAN = 60;

    private static final int TOP_TRUONG = 10;
    private static final int SO_BUOI_TRONG_NGAY = 12;
    private static final int SO_PHAN_CONG_GAN_DAY = 6;

    private static final List<String> BANG_MAU =
            List.of("#f97316", "#0ea5e9", "#8b5cf6", "#22c55e", "#f59e0b", "#ec4899", "#14b8a6", "#ef4444");

    private static final DateTimeFormatter GIO_PHUT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter NGAY_GIO = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<String> TEN_THU =
            List.of("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật");

    private final DashboardQueryRepository repo;

    public DashboardService(DashboardQueryRepository repo) {
        this.repo = repo;
    }

    /* ───────────── Thẻ chỉ số ───────────── */

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(DashboardFilter f) {
        ThongKeKy nay = repo.thongKeKy(f);
        ThongKeKy truoc = repo.thongKeKy(f.kyTruoc());

        long gvHoatDong = repo.demGiaoVienHoatDong(f.branchId());
        long truongHopDong = repo.demTruongDangHopDong(f.branchId());

        List<Kpi> chiSo = new ArrayList<>();

        chiSo.add(new Kpi(
                "buoiDay",
                "schedule",
                "Buổi dạy",
                (double) nay.buoiDuyet(),
                "so",
                (double) truoc.buoiDuyet(),
                phanTramThayDoi(nay.buoiDuyet(), truoc.buoiDuyet()),
                nay.buoiTatCa() == 0
                        ? "Chưa xếp buổi nào"
                        : soThapPhan(nay.buoiDuyet() * 100.0 / nay.buoiTatCa()) + "% đã duyệt",
                "#f97316",
                "/schedule"));

        chiSo.add(new Kpi(
                "gioGiang",
                "clock",
                "Giờ giảng",
                lamTron(nay.gioGiang()),
                "gio",
                lamTron(truoc.gioGiang()),
                phanTramThayDoi(nay.gioGiang(), truoc.gioGiang()),
                nay.gvCoLich() == 0
                        ? "Chưa có giáo viên đứng lớp"
                        : "TB " + soThapPhan(nay.gioGiang() / nay.gvCoLich()) + " giờ/giáo viên",
                "#0ea5e9",
                "/schedule"));

        chiSo.add(new Kpi(
                "chiPhi",
                "payroll",
                "Chi phí lương",
                lamTron(nay.chiPhi()),
                "tien",
                lamTron(truoc.chiPhi()),
                phanTramThayDoi(nay.chiPhi(), truoc.chiPhi()),
                nay.buoiDuyet() == 0 ? "Chưa phát sinh" : tienGon(nay.chiPhi() / nay.buoiDuyet()) + "/buổi",
                "#22c55e",
                "/payroll"));

        // Chưa chấm công buổi nào thì chuyên cần là CHƯA ĐO ĐƯỢC, không phải 0%
        boolean coChamCong = nay.chamCongTong() > 0;
        double chuyenCan = coChamCong ? nay.chamCongCoMat() * 100.0 / nay.chamCongTong() : 0;
        Double chuyenCanTruoc = truoc.chamCongTong() > 0 ? truoc.chamCongCoMat() * 100.0 / truoc.chamCongTong() : null;
        chiSo.add(new Kpi(
                "chuyenCan",
                "check",
                "Tỉ lệ chuyên cần",
                coChamCong ? lamTron(chuyenCan) : null,
                "phanTram",
                chuyenCanTruoc == null ? null : lamTron(chuyenCanTruoc),
                coChamCong && chuyenCanTruoc != null ? lamTron(chuyenCan - chuyenCanTruoc) : null,
                coChamCong
                        ? soThapPhan(nay.chamCongDungGio() * 100.0 / nay.chamCongTong()) + "% đúng giờ"
                        : "Chưa có dữ liệu chấm công",
                "#8b5cf6",
                "/attendance"));

        chiSo.add(new Kpi(
                "giaoVien",
                "teacher",
                "Giáo viên có lịch dạy",
                (double) nay.gvCoLich(),
                "so",
                (double) truoc.gvCoLich(),
                phanTramThayDoi(nay.gvCoLich(), truoc.gvCoLich()),
                gvHoatDong == 0 ? "Chưa có giáo viên" : "Trên " + gvHoatDong + " giáo viên đang làm việc",
                "#2563eb",
                "/dashboard/teacher"));

        chiSo.add(new Kpi(
                "truong",
                "school",
                "Trường đang phục vụ",
                (double) nay.truongCoLich(),
                "so",
                (double) truoc.truongCoLich(),
                phanTramThayDoi(nay.truongCoLich(), truoc.truongCoLich()),
                truongHopDong == 0 ? "Chưa có hợp đồng" : "Trên " + truongHopDong + " trường còn hợp đồng",
                "#f59e0b",
                "/admin/schools"));

        return new DashboardSummaryResponse(
                f.nhan(), f.kyTruoc().nhan(), BusinessTime.now().format(NGAY_GIO), chiSo);
    }

    /* ───────────── Biểu đồ + bảng chi tiết ───────────── */

    @Transactional(readOnly = true)
    public DashboardAnalyticsResponse analytics(DashboardFilter f) {
        return new DashboardAnalyticsResponse(
                repo.theoThang(f),
                repo.nhietDo(f),
                repo.soTietToiDa(),
                toMau(repo.coCauNhomMon(f)),
                toMau(repo.topTruong(f, TOP_TRUONG)),
                phanTich(f, Chieu.GIAO_VIEN),
                phanTich(f, Chieu.TRUONG),
                phanTich(f, Chieu.MON));
    }

    /**
     * Ghép điểm đánh giá vào bảng chi tiết.
     *
     * <p>Điểm đánh giá lấy bằng truy vấn riêng vì nó không gắn với buổi dạy — nối chung trong SQL
     * sẽ nhân bản mỗi buổi lên bằng số lượt đánh giá và làm sai mọi phép cộng khác.
     */
    private List<DongPhanTich> phanTich(DashboardFilter f, Chieu chieu) {
        List<DongPhanTich> dong = repo.phanTich(f, chieu);
        Map<Integer, Double> diem = repo.diemDanhGiaTheo(f, chieu);
        if (diem.isEmpty()) {
            return dong;
        }
        return dong.stream()
                .map(d -> new DongPhanTich(
                        d.id(),
                        d.ten(),
                        d.phu(),
                        d.buoiDay(),
                        d.gioGiang(),
                        d.tyLeDuyet(),
                        d.chuyenCan(),
                        diem.get(d.id()),
                        d.chiPhi()))
                .toList();
    }

    private List<LatCat> toMau(List<LatCat> lat) {
        List<LatCat> ketQua = new ArrayList<>(lat.size());
        for (int i = 0; i < lat.size(); i++) {
            LatCat l = lat.get(i);
            ketQua.add(new LatCat(l.id(), l.nhan(), l.giaTri(), BANG_MAU.get(i % BANG_MAU.size())));
        }
        return ketQua;
    }

    /* ───────────── Việc cần xử lý + lịch ───────────── */

    @Transactional(readOnly = true)
    public DashboardOperationsResponse operations(DashboardFilter f) {
        List<CanhBao> canhBao = new ArrayList<>();

        them(canhBao, "lichChoDuyet", "khan", "Lịch dạy chờ duyệt", repo.demLichChoDuyet(), "buổi", "/schedule");
        them(
                canhBao,
                "hopDongSapHetHan",
                "khan",
                "Hợp đồng giáo viên hết hạn trong " + NGAY_BAO_HET_HAN + " ngày",
                repo.demHopDongSapHetHan(NGAY_BAO_HET_HAN),
                "hợp đồng",
                "/dashboard/teacher");
        them(
                canhBao,
                "hopDongDichVu",
                "khan",
                "Hợp đồng dịch vụ đã quá hạn",
                repo.demHopDongDichVuHetHan(),
                "trường",
                "/admin/schools");
        them(
                canhBao,
                "chuaChamCong",
                "luuY",
                "Buổi đã dạy chưa chấm công (30 ngày)",
                repo.demBuoiChuaChamCong(),
                "buổi",
                "/attendance");
        them(canhBao, "luongChuaChot", "luuY", "Kỳ lương chưa chốt", repo.demKyLuongChuaChot(), "kỳ", "/payroll");
        them(
                canhBao,
                "truongKhongPhatSinh",
                "luuY",
                "Trường còn hợp đồng nhưng không có buổi dạy",
                repo.demTruongKhongPhatSinh(f),
                "trường",
                "/admin/schools");
        them(
                canhBao,
                "giaoVienRanh",
                "tin",
                "Giáo viên chưa có lịch trong kỳ",
                repo.demGiaoVienKhongCoLich(f),
                "giáo viên",
                "/dashboard/teacher");

        LocalDate homNay = BusinessTime.today();
        List<Object[]> lich = repo.lichTrongNgay(homNay, SO_BUOI_TRONG_NGAY);
        boolean laDuBao = false;
        LocalDate ngayHienThi = homNay;

        // Hôm nay trống (hè, ngày nghỉ) thì nhảy sang ngày dạy gần nhất — ô rỗng không phân biệt
        // được với hệ thống hỏng.
        if (lich.isEmpty()) {
            LocalDate keTiep = repo.ngayDayKeTiep(homNay);
            if (keTiep != null) {
                ngayHienThi = keTiep;
                lich = repo.lichTrongNgay(keTiep, SO_BUOI_TRONG_NGAY);
                laDuBao = true;
            }
        }

        return new DashboardOperationsResponse(
                canhBao,
                nhanNgay(ngayHienThi, laDuBao),
                laDuBao,
                lich.stream().map(this::toBuoiDay).toList(),
                repo.phanCongGanDay(SO_PHAN_CONG_GAN_DAY));
    }

    /** Mục không có việc nào vẫn giữ lại, đổi mức thành "on" để FE đẩy xuống cuối. */
    private void them(List<CanhBao> ds, String key, String muc, String nhan, long soLuong, String donVi, String route) {
        ds.add(new CanhBao(key, soLuong == 0 ? "on" : muc, nhan, soLuong, soLuong + " " + donVi, route));
    }

    private String nhanNgay(LocalDate ngay, boolean laDuBao) {
        String moTa = TEN_THU.get(ngay.getDayOfWeek().getValue() - 1) + ", " + ngay.format(NGAY);
        return laDuBao ? "Buổi dạy gần nhất: " + moTa : "Lịch dạy hôm nay: " + moTa;
    }

    private BuoiDay toBuoiDay(Object[] r) {
        LocalDateTime batDau = (LocalDateTime) r[1];
        LocalDateTime ketThuc = (LocalDateTime) r[2];
        LocalDateTime bayGio = BusinessTime.now();
        String trangThai = ketThuc.isBefore(bayGio) ? "daXong" : (batDau.isAfter(bayGio) ? "sapToi" : "dangDien");
        return new BuoiDay(
                (Long) r[0],
                batDau.format(GIO_PHUT),
                ketThuc.format(GIO_PHUT),
                (String) r[3],
                (String) r[4],
                (String) r[5],
                r[6] == null ? "—" : (String) r[6],
                trangThai,
                BANG_MAU.get(batDau.getHour() % BANG_MAU.size()));
    }

    /* ───────────── Xuất CSV ───────────── */

    /**
     * Xuất bảng chi tiết ra CSV cho Excel.
     *
     * <p>Phân cách bằng DẤU CHẤM PHẨY: Excel bản tiếng Việt hiểu dấu phẩy là dấu thập phân, dùng
     * phẩy ngăn cột thì mọi số tiền vỡ sang ô bên cạnh. BOM UTF-8 do controller ghi thêm.
     */
    @Transactional(readOnly = true)
    public String xuatCsv(DashboardFilter f, Chieu chieu) {
        String tenCot =
                switch (chieu) {
                    case GIAO_VIEN -> "Giáo viên";
                    case TRUONG -> "Trường";
                    case MON -> "Môn học";
                };
        StringBuilder sb = new StringBuilder();
        sb.append("Thống kê Bảng điều khiển — ").append(f.nhan()).append('\n');
        sb.append("Kết xuất lúc;").append(BusinessTime.now().format(NGAY_GIO)).append('\n');
        sb.append('\n');
        sb.append(tenCot)
                .append(";Ghi chú;Số buổi dạy;Giờ giảng;Tỉ lệ duyệt (%);Chuyên cần (%);Điểm đánh giá;Chi phí (VND)\n");

        for (DongPhanTich d : phanTich(f, chieu)) {
            sb.append(oCsv(d.ten()))
                    .append(';')
                    .append(oCsv(d.phu()))
                    .append(';')
                    .append(d.buoiDay())
                    .append(';')
                    .append(soCsv(d.gioGiang()))
                    .append(';')
                    .append(soCsv(d.tyLeDuyet()))
                    .append(';')
                    .append(d.chuyenCan() == null ? "" : soCsv(d.chuyenCan()))
                    .append(';')
                    .append(d.diemDanhGia() == null ? "" : soCsv(d.diemDanhGia()))
                    .append(';')
                    .append(Math.round(d.chiPhi()))
                    .append('\n');
        }
        return sb.toString();
    }

    /** Bọc ô CSV theo RFC 4180: nhân đôi dấu nháy kép rồi bao ngoài. */
    private static String oCsv(String s) {
        return s == null ? "" : '"' + s.replace("\"", "\"\"") + '"';
    }

    private static String soCsv(double v) {
        return String.format(Locale.GERMANY, "%.2f", v);
    }

    /* ───────────── Tiện ích ───────────── */

    /**
     * % thay đổi so với kỳ trước; null khi kỳ trước bằng 0.
     *
     * <p>Bản dashboard cũ từng hiện "+1801,6%": tăng từ 1 lên 19 đúng là tăng 1800% nhưng con số
     * đó chỉ nói mẫu số quá nhỏ, đặt cạnh mũi tên xanh thì thành khoe sai sự thật.
     */
    private static Double phanTramThayDoi(double nay, double truoc) {
        return truoc <= 0 ? null : lamTron((nay - truoc) / truoc * 100);
    }

    private static double lamTron(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String soThapPhan(double v) {
        return String.format(Locale.forLanguageTag("vi-VN"), "%,.1f", v);
    }

    /** Tiền rút gọn cho dòng phụ của thẻ: 1234567 → "1,23 tr". */
    private static String tienGon(double v) {
        if (v >= 1_000_000_000) {
            return String.format(Locale.forLanguageTag("vi-VN"), "%,.2f tỉ", v / 1_000_000_000);
        }
        if (v >= 1_000_000) {
            return String.format(Locale.forLanguageTag("vi-VN"), "%,.2f tr", v / 1_000_000);
        }
        return String.format(Locale.forLanguageTag("vi-VN"), "%,.0f đ", v);
    }
}
