package com.kdc.tsdms.dto;

import com.kdc.tsdms.common.BusinessTime;
import java.time.LocalDate;
import java.time.Month;

/**
 * Bộ lọc áp cho toàn Bảng điều khiển. Gom về một chỗ để mọi thẻ số, biểu đồ và bảng trên màn
 * hình luôn nói về cùng một kỳ.
 *
 * @param from ngày đầu kỳ (tính cả ngày này)
 * @param to ngày cuối kỳ (tính cả ngày này)
 * @param branchId lọc theo chi nhánh; null = tất cả
 * @param schoolId lọc theo trường; null = tất cả
 * @param categoryId lọc theo nhóm môn; null = tất cả
 */
public record DashboardFilter(LocalDate from, LocalDate to, Integer branchId, Integer schoolId, Integer categoryId) {

    /** Tháng bắt đầu năm học ở Việt Nam. */
    private static final Month THANG_KHAI_GIANG = Month.SEPTEMBER;

    public DashboardFilter {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Khoảng thời gian không được để trống.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Ngày cuối kỳ phải sau ngày đầu kỳ.");
        }
    }

    /**
     * Kỳ mặc định: NĂM HỌC hiện hành (01/9 → 31/8). Không lấy "tháng này" vì mở dashboard vào
     * tháng hè sẽ ra màn hình toàn số 0.
     */
    public static DashboardFilter namHocHienHanh() {
        LocalDate homNay = BusinessTime.today();
        int namBatDau = homNay.getMonthValue() >= THANG_KHAI_GIANG.getValue() ? homNay.getYear() : homNay.getYear() - 1;
        return new DashboardFilter(
                LocalDate.of(namBatDau, THANG_KHAI_GIANG, 1),
                LocalDate.of(namBatDau + 1, THANG_KHAI_GIANG, 1).minusDays(1),
                null,
                null,
                null);
    }

    /** Nhãn hiển thị của kỳ, vd "Năm học 2025–2026" hoặc "01/09/2025 – 31/12/2025". */
    public String nhan() {
        LocalDate namHocTu = LocalDate.of(from.getYear(), THANG_KHAI_GIANG, 1);
        if (from.equals(namHocTu) && to.equals(namHocTu.plusYears(1).minusDays(1))) {
            return "Năm học " + from.getYear() + "–" + (from.getYear() + 1);
        }
        return "%02d/%02d/%d – %02d/%02d/%d"
                .formatted(
                        from.getDayOfMonth(),
                        from.getMonthValue(),
                        from.getYear(),
                        to.getDayOfMonth(),
                        to.getMonthValue(),
                        to.getYear());
    }

    /** Số ngày của kỳ (tính cả hai đầu). */
    public long soNgay() {
        return java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
    }

    /**
     * Kỳ liền trước, dài BẰNG ĐÚNG kỳ hiện tại. Đem một quý so với một tháng rồi kết luận
     * "giảm 66%" là con số vô nghĩa nhưng nhìn vẫn rất thuyết phục.
     */
    public DashboardFilter kyTruoc() {
        long ngay = soNgay();
        return new DashboardFilter(from.minusDays(ngay), from.minusDays(1), branchId, schoolId, categoryId);
    }
}
