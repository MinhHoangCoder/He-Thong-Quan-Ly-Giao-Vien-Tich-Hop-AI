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
 * Tổng hợp số liệu cho Bảng điều khiển của quản trị viên.
 *
 * <p>Lớp này KHÔNG chứa truy vấn — mọi phép cộng đếm nằm ở {@link DashboardQueryRepository} dưới
 * dạng SQL. Việc ở đây là nặn số liệu thô thành thứ đọc được: đặt nhãn, so sánh với kỳ trước,
 * chọn màu, và quan trọng nhất là quyết định khi nào một con số KHÔNG đáng hiển thị.
 *
 * <p>NGUYÊN TẮC XUYÊN SUỐT — không bịa số. Chưa có dữ liệu thì trả null để giao diện hiện "—" kèm
 * lời giải thích, tuyệt đối không trả 0. Một dấu gạch ngang nói "chưa có dữ liệu"; một số 0 nói
 * "đã đo và kết quả bằng không" — hai điều hoàn toàn khác nhau, và trên bảng lương thì lẫn lộn
 * hai điều đó là chuyện lớn.
 */
@Service
public class DashboardService {

    /** Ngưỡng báo động hợp đồng lao động sắp hết hạn. */
    private static final int NGAY_BAO_HET_HAN = 60;

    /** Số trường hiển thị trên biểu đồ xếp hạng. */
    private static final int TOP_TRUONG = 10;

    private static final int SO_BUOI_TRONG_NGAY = 12;
    private static final int SO_PHAN_CONG_GAN_DAY = 6;

    /**
     * Bảng màu của biểu đồ. Sáu màu tách bạch cả về sắc lẫn độ sáng, nên vẫn phân biệt được khi in
     * đen trắng hoặc với người khó phân biệt màu đỏ–lục.
     */
    private static final List<String> BANG_MAU =
            List.of("#f97316", "#0ea5e9", "#8b5cf6", "#22c55e", "#f59e0b", "#ec4899", "#14b8a6", "#ef4444");

    private static final DateTimeFormatter GIO_PHUT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter NGAY_GIO = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<String> TEN_THU =
            List.of("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật");

    private final DashboardQueryRepository repo;

    public DashboardService(DashboardQueryRepository repo) {
        this.repo = repo;
    }

    /* ═════════════════════════ 1. THẺ CHỈ SỐ ═════════════════════════ */

    /**
     * Sáu chỉ số chủ chốt, mỗi chỉ số kèm đối chiếu với kỳ liền trước cùng độ dài.
     *
     * <p>Chọn đúng sáu chỉ số này vì chúng trả lời sáu câu hỏi mà người điều hành một trung tâm
     * gia sư thực sự hỏi mỗi sáng: dạy được bao nhiêu, tốn bao nhiêu công, mất bao nhiêu tiền,
     * giáo viên có đi dạy đầy đủ không, còn ai đang rảnh, và khách hàng nào đang được phục vụ.
     */
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
                        ? "Chưa có buổi nào được xếp"
                        : soThapPhan(nay.buoiDuyet() * 100.0 / nay.buoiTatCa()) + "% số buổi đã xếp được duyệt",
                "Đếm bản ghi lịch dạy ở trạng thái ĐÃ DUYỆT, chưa xoá, có giờ bắt đầu nằm trong kỳ.",
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
                        ? "Chưa có giáo viên nào đứng lớp"
                        : "Trung bình " + soThapPhan(nay.gioGiang() / nay.gvCoLich()) + " giờ mỗi giáo viên",
                "Cộng thời lượng (giờ kết thúc − giờ bắt đầu) của mọi buổi đã duyệt trong kỳ.",
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
                nay.buoiDuyet() == 0
                        ? "Chưa phát sinh buổi dạy nào"
                        : tienGon(nay.chiPhi() / nay.buoiDuyet()) + " mỗi buổi dạy",
                "Trung tâm trả lương theo tiết và mỗi buổi có mặt là một tiết, nên chi phí một "
                        + "buổi = lương thực nhận của tháng ÷ số tiết đã dạy trong tháng đó, rồi "
                        + "cộng lại theo bộ lọc. Phân bổ như vậy thì chi phí mới lọc được theo "
                        + "trường và theo môn. Tháng chưa lập bảng lương tính bằng 0.",
                "#22c55e",
                "/payroll"));

        // Chưa chấm công buổi nào thì tỉ lệ chuyên cần KHÔNG phải 0% — nó là chưa đo được.
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
                        ? soThapPhan(nay.chamCongDungGio() * 100.0 / nay.chamCongTong()) + "% vào lớp đúng giờ"
                        : "Kỳ này chưa có dữ liệu chấm công",
                "Số buổi giáo viên có mặt (đúng giờ hoặc đi muộn) chia cho tổng số buổi đã chấm công. "
                        + "Nghỉ phép và vắng đều tính là không có mặt.",
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
                gvHoatDong == 0
                        ? "Chưa có giáo viên đang làm việc"
                        : "Trên " + gvHoatDong + " giáo viên đang làm việc · khai thác "
                                + soThapPhan(nay.gvCoLich() * 100.0 / gvHoatDong) + "%",
                "Số giáo viên có ít nhất một buổi đã duyệt trong kỳ. Mẫu số là giáo viên "
                        + "trạng thái ĐANG LÀM VIỆC, không tính người đã nghỉ hay bị tạm dừng.",
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
                truongHopDong == 0
                        ? "Chưa có hợp đồng dịch vụ nào còn hiệu lực"
                        : "Trên " + truongHopDong + " trường còn hợp đồng dịch vụ",
                "Số trường có ít nhất một buổi đã duyệt trong kỳ. Mẫu số là trường còn hợp đồng "
                        + "dịch vụ hiệu lực — không lấy toàn bộ danh bạ trường.",
                "#f59e0b",
                "/admin/schools"));

        return new DashboardSummaryResponse(
                f.nhan(), f.kyTruoc().nhan(), BusinessTime.now().format(NGAY_GIO), chiSo);
    }

    /* ═════════════════════════ 2. KHU PHÂN TÍCH ═════════════════════════ */

    /** Bốn biểu đồ và bảng phân tích ba tab, tất cả cùng chịu một bộ lọc. */
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
     * Ghép điểm đánh giá vào bảng phân tích.
     *
     * <p>Điểm đánh giá đến từ một truy vấn riêng vì nó không gắn với buổi dạy; nối chung trong SQL
     * sẽ nhân bản mỗi buổi lên bằng số lượt đánh giá và thổi phồng mọi phép cộng khác.
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

    /** Gán màu theo thứ tự đã sắp xếp, để lát lớn nhất luôn nhận màu nhấn của hệ thống. */
    private List<LatCat> toMau(List<LatCat> lat) {
        List<LatCat> ketQua = new ArrayList<>(lat.size());
        for (int i = 0; i < lat.size(); i++) {
            LatCat l = lat.get(i);
            ketQua.add(new LatCat(l.id(), l.nhan(), l.giaTri(), BANG_MAU.get(i % BANG_MAU.size())));
        }
        return ketQua;
    }

    /* ═════════════════════════ 3. KHU ĐIỀU HÀNH ═════════════════════════ */

    /**
     * Việc cần xử lý, lịch dạy trong ngày và phân công gần đây.
     *
     * <p>Bộ lọc chỉ dùng cho hai cảnh báo phụ thuộc kỳ (giáo viên rảnh, trường không phát sinh);
     * phần còn lại luôn nói về hiện tại — xem báo cáo năm ngoái không làm hợp đồng hết hạn tuần
     * này biến mất.
     */
    @Transactional(readOnly = true)
    public DashboardOperationsResponse operations(DashboardFilter f) {
        List<CanhBao> canhBao = new ArrayList<>();

        themCanhBao(
                canhBao,
                "lichChoDuyet",
                "khan",
                "Lịch dạy chờ duyệt",
                repo.demLichChoDuyet(),
                "buổi chưa được duyệt — giáo viên chưa thể lên lớp.",
                "/schedule");

        themCanhBao(
                canhBao,
                "hopDongSapHetHan",
                "khan",
                "Hợp đồng giáo viên sắp hết hạn",
                repo.demHopDongSapHetHan(NGAY_BAO_HET_HAN),
                "hợp đồng hết hạn trong " + NGAY_BAO_HET_HAN + " ngày tới — cần ký gia hạn.",
                "/dashboard/teacher");

        themCanhBao(
                canhBao,
                "hopDongDichVu",
                "khan",
                "Hợp đồng dịch vụ đã quá hạn",
                repo.demHopDongDichVuHetHan(),
                "trường đã hết hạn hợp đồng mà chưa gia hạn.",
                "/admin/schools");

        themCanhBao(
                canhBao,
                "chuaChamCong",
                "luuY",
                "Buổi đã dạy chưa chấm công",
                repo.demBuoiChuaChamCong(),
                "buổi trong 30 ngày qua chưa có dữ liệu chấm công — thiếu đầu vào tính lương.",
                "/attendance");

        themCanhBao(
                canhBao,
                "luongChuaChot",
                "luuY",
                "Kỳ lương chưa chốt",
                repo.demKyLuongChuaChot(),
                "kỳ lương còn ở trạng thái nháp, chưa thể chi trả.",
                "/payroll");

        themCanhBao(
                canhBao,
                "truongKhongPhatSinh",
                "luuY",
                "Trường không phát sinh buổi dạy",
                repo.demTruongKhongPhatSinh(f),
                "trường còn hợp đồng nhưng không có buổi nào trong kỳ — nguy cơ mất khách hàng.",
                "/admin/schools");

        themCanhBao(
                canhBao,
                "giaoVienRanh",
                "tin",
                "Giáo viên chưa có lịch",
                repo.demGiaoVienKhongCoLich(f),
                "giáo viên đang làm việc nhưng không có buổi nào trong kỳ — còn năng lực nhận thêm lớp.",
                "/dashboard/teacher");

        // Việc khẩn lên trước; trong cùng mức thì việc nhiều lên trước.
        canhBao.sort((x, y) -> {
            int uuTien = Integer.compare(thuTuMuc(x.muc()), thuTuMuc(y.muc()));
            return uuTien != 0 ? uuTien : Long.compare(y.soLuong(), x.soLuong());
        });

        LocalDate homNay = BusinessTime.today();
        List<Object[]> lich = repo.lichTrongNgay(homNay, SO_BUOI_TRONG_NGAY);
        boolean laDuBao = false;
        LocalDate ngayHienThi = homNay;

        // Hôm nay trống thì chỉ sang ngày dạy gần nhất thay vì để một ô rỗng. Ba tháng hè và mọi
        // ngày nghỉ lễ đều rơi vào tình huống này; ô rỗng không phân biệt được với hệ thống hỏng.
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

    private static int thuTuMuc(String muc) {
        return switch (muc) {
            case "khan" -> 0;
            case "luuY" -> 1;
            default -> 2;
        };
    }

    /** Cảnh báo không có việc nào thì vẫn giữ lại — người dùng cần thấy "mục này đang ổn". */
    private void themCanhBao(
            List<CanhBao> ds, String key, String muc, String nhan, long soLuong, String moTa, String route) {
        ds.add(new CanhBao(key, soLuong == 0 ? "on" : muc, nhan, soLuong, soLuong + " " + moTa, route));
    }

    private String nhanNgay(LocalDate ngay, boolean laDuBao) {
        String thu = TEN_THU.get(ngay.getDayOfWeek().getValue() - 1);
        String moTa = thu + ", " + ngay.format(NGAY);
        return laDuBao ? "Buổi dạy gần nhất · " + moTa : "Hôm nay · " + moTa;
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
                BANG_MAU.get((int) (batDau.getHour() % BANG_MAU.size())));
    }

    /* ═════════════════════════ 4. XUẤT BÁO CÁO ═════════════════════════ */

    /**
     * Xuất bảng phân tích ra CSV mở được bằng Excel.
     *
     * <p>Hai chi tiết nhỏ quyết định file có dùng được hay không:
     *
     * <ul>
     *   <li>DẤU PHÂN CÁCH LÀ CHẤM PHẨY. Excel bản tiếng Việt đọc dấu phẩy là dấu thập phân, dùng
     *       phẩy làm phân cách cột thì mọi số tiền sẽ vỡ sang ô bên cạnh.
     *   <li>BOM UTF-8 do tầng controller ghi thêm — thiếu nó thì Excel đoán bảng mã theo vùng và
     *       toàn bộ tên tiếng Việt thành ký tự lạ.
     * </ul>
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
        sb.append("Báo cáo thống kê Bảng điều khiển — ").append(f.nhan()).append('\n');
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

    /** Bọc ô CSV: nhân đôi dấu nháy kép rồi bao ngoài, đúng chuẩn RFC 4180. */
    private static String oCsv(String s) {
        if (s == null) {
            return "";
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    /** Số trong CSV dùng dấu PHẨY thập phân để Excel tiếng Việt nhận ra ngay là số. */
    private static String soCsv(double v) {
        return String.format(Locale.GERMANY, "%.2f", v);
    }

    /* ═════════════════════════ TIỆN ÍCH ═════════════════════════ */

    /**
     * Phần trăm thay đổi so với kỳ trước.
     *
     * <p>Trả null khi kỳ trước bằng 0 — đó là chỗ bản dashboard cũ từng hiện "+1801,6%". Tăng từ
     * 1 lên 19 đúng là tăng 1800%, nhưng con số ấy nói về việc mẫu số quá nhỏ chứ không nói gì về
     * hoạt động của trung tâm, và đặt cạnh một mũi tên xanh thì nó thành lời khoe sai sự thật.
     */
    private static Double phanTramThayDoi(double nay, double truoc) {
        if (truoc <= 0) {
            return null;
        }
        return lamTron((nay - truoc) / truoc * 100);
    }

    private static double lamTron(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String soThapPhan(double v) {
        return String.format(Locale.forLanguageTag("vi-VN"), "%,.1f", v);
    }

    /** Rút gọn tiền cho dòng phụ của thẻ: 1.234.567 → "1,23 tr ₫". */
    private static String tienGon(double v) {
        if (v >= 1_000_000_000) {
            return String.format(Locale.forLanguageTag("vi-VN"), "%,.2f tỉ ₫", v / 1_000_000_000);
        }
        if (v >= 1_000_000) {
            return String.format(Locale.forLanguageTag("vi-VN"), "%,.2f tr ₫", v / 1_000_000);
        }
        return String.format(Locale.forLanguageTag("vi-VN"), "%,.0f ₫", v);
    }
}
