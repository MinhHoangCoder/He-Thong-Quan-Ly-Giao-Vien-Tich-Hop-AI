package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.ExcelWriter;
import com.kdc.tsdms.dto.PayrollChangeLogResponse;
import com.kdc.tsdms.dto.PayrollHealthResponse;
import com.kdc.tsdms.dto.PayrollHolidayIssueResponse;
import com.kdc.tsdms.dto.PayrollReopenRequest;
import com.kdc.tsdms.dto.PayrollResponse;
import com.kdc.tsdms.dto.PayrollUpdateRequest;
import com.kdc.tsdms.service.PayrollHealthService;
import com.kdc.tsdms.service.PayrollService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Bảng lương — /api/v1/payroll.
 *
 * <p>GET : PAYROLL_VIEW. generate/PUT/finalize : PAYROLL_MANAGE. ADMIN đi tắt.
 *
 * <p>reopen : PAYROLL_REOPEN — quyền RIÊNG, cố ý KHÔNG gộp vào PAYROLL_MANAGE (Flyway V32).
 * Người chốt sổ và người mở khóa sổ là cùng một người thì việc chốt mất hết ý nghĩa kiểm soát.
 */
@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {

    private static final String CAN_VIEW = "hasRole('ADMIN') or hasAuthority('PAYROLL_VIEW')";
    private static final String CAN_MANAGE = "hasRole('ADMIN') or hasAuthority('PAYROLL_MANAGE')";
    private static final String CAN_REOPEN = "hasRole('ADMIN') or hasAuthority('PAYROLL_REOPEN')";
    private static final String CAN_PAY = "hasRole('ADMIN') or hasAuthority('PAYROLL_PAY')";

    private final PayrollService service;
    private final PayrollHealthService healthService;

    public PayrollController(PayrollService service, PayrollHealthService healthService) {
        this.service = service;
        this.healthService = healthService;
    }

    /** Bảng lương một kỳ (mặc định tháng hiện tại). */
    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public List<PayrollResponse> list(
            @RequestParam(required = false) Short year, @RequestParam(required = false) Short month) {
        LocalDate today = LocalDate.now();
        short y = year != null ? year : (short) today.getYear();
        short m = month != null ? month : (short) today.getMonthValue();
        return service.list(y, m);
    }

    /**
     * Phiếu lương của CHÍNH giáo viên đang đăng nhập (read-only, mặc định năm hiện tại).
     * KHÔNG nhận teacherId — backend tự lấy từ token (chống IDOR); chỉ trả phiếu đã chốt/đã trả.
     * month = null → cả năm (mới nhất trước); month cụ thể → đúng 1 phiếu tháng đó.
     */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public List<PayrollResponse> listMine(
            @RequestParam(required = false) Short year, @RequestParam(required = false) Short month) {
        short y = year != null ? year : (short) LocalDate.now().getYear();
        return service.listMine(y, month);
    }

    /** Sinh/tính lại lương từ chấm công theo TIẾT (đơn giá tự tra theo cấp của lớp). */
    @PostMapping("/generate")
    @PreAuthorize(CAN_MANAGE)
    public List<PayrollResponse> generate(@RequestParam Short year, @RequestParam Short month) {
        return service.generate(year, month);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public PayrollResponse update(@PathVariable Integer id, @Valid @RequestBody PayrollUpdateRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize(CAN_MANAGE)
    public PayrollResponse finalizePayroll(@PathVariable Integer id) {
        return service.finalizePayroll(id);
    }

    /**
     * Đánh dấu một phiếu ĐÃ CHỐT thành ĐÃ TRẢ (V38).
     *
     * <p>Quyền RIÊNG {@code PAYROLL_PAY}, không gộp vào PAYROLL_MANAGE: "tính lại lương" và
     * "xác nhận tiền đã ra khỏi quỹ" là hai trách nhiệm khác nhau. Cùng lý lẽ với
     * PAYROLL_REOPEN.
     */
    @PostMapping("/{id}/pay")
    @PreAuthorize(CAN_PAY)
    public PayrollResponse pay(@PathVariable Integer id) {
        return service.pay(id);
    }

    /** Đánh dấu ĐÃ TRẢ cho mọi phiếu đã chốt của một kỳ — kế toán chi lương theo đợt. */
    @PostMapping("/pay-period")
    @PreAuthorize(CAN_PAY)
    public Map<String, Integer> payPeriod(@RequestParam Short year, @RequestParam Short month) {
        return Map.of("paid", service.payPeriod(year, month));
    }

    /**
     * Cảnh báo trước khi chốt: kỳ này còn dòng Vắng nào rơi vào ngày nghỉ.
     *
     * <p>Chỉ PAYROLL_VIEW vì đây là câu hỏi đọc — và người xem bảng lương cần thấy cảnh báo
     * ngay cả khi họ không phải người bấm nút chốt.
     */
    @GetMapping("/holiday-issues")
    @PreAuthorize(CAN_VIEW)
    public PayrollHolidayIssueResponse holidayIssues(@RequestParam Short year, @RequestParam Short month) {
        return service.holidayIssues(year, month);
    }

    /**
     * KIỂM TRA SỨC KHỎE DỮ LIỆU của một kỳ trước khi chốt.
     *
     * <p>Gộp bảy phép đếm trả lời cùng một câu hỏi: "còn bao nhiêu dòng sẽ làm số tiền sai mà
     * không ai biết". Không phép nào trong đó tự báo lỗi — bảng lương vẫn sinh ra bình thường,
     * chỉ là sai — nên phải chủ động đi đếm chứ không đợi màn hình đỏ lên.
     *
     * <p>Chỉ PAYROLL_VIEW: đây là câu hỏi đọc, và người xem bảng lương cần thấy cảnh báo ngay
     * cả khi họ không phải người bấm nút chốt.
     */
    @GetMapping("/health")
    @PreAuthorize(CAN_VIEW)
    public PayrollHealthResponse health(@RequestParam Short year, @RequestParam Short month) {
        return healthService.check(year, month);
    }

    /**
     * Xuất bảng lương của một kỳ ra .xlsx.
     *
     * <p>Lấy dữ liệu TỪ SERVICE chứ không từ trang đang xem: màn hình phân trang 10 dòng, xuất
     * từ đó ra thì được một cái file trông có vẻ đúng nhưng thiếu 99% dữ liệu.
     */
    @GetMapping("/export")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ByteArrayResource> export(@RequestParam Short year, @RequestParam Short month) {
        List<PayrollResponse> rows = service.list(year, month);
        return ExcelWriter.xuat(
                "bang-luong_" + month + "-" + year,
                List.of(
                        "Giáo viên",
                        "Kỳ",
                        "Số tiết",
                        "Đơn giá/tiết",
                        "Lương cứng",
                        "Phụ cấp",
                        "Thưởng",
                        "Khấu trừ",
                        "Thực nhận",
                        "Trạng thái"),
                rows,
                p -> new Object[] {
                    p.teacherName,
                    p.periodMonth + "/" + p.periodYear,
                    p.taughtHours,
                    p.ratePerHour,
                    p.baseSalary,
                    p.allowance,
                    p.bonus,
                    p.deduction,
                    p.netAmount,
                    nhanTrangThai(p.status)
                });
    }

    /** Nhãn tiếng Việt cho file xuất ra — người nhận file không đọc mã trạng thái. */
    private static String nhanTrangThai(String ma) {
        return switch (ma == null ? "" : ma) {
            case "DRAFT" -> "Nháp";
            case "FINALIZED" -> "Đã chốt";
            case "PAID" -> "Đã trả";
            default -> ma;
        };
    }

    /** Lịch sử chốt/mở lại của một phiếu lương. */
    @GetMapping("/{id}/logs")
    @PreAuthorize(CAN_VIEW)
    public List<PayrollChangeLogResponse> logs(@PathVariable Integer id) {
        return service.logs(id);
    }

    /** Mở lại một phiếu lương đã chốt (về nháp) để sửa được chấm công của kỳ. */
    @PostMapping("/{id}/reopen")
    @PreAuthorize(CAN_REOPEN)
    public PayrollResponse reopen(@PathVariable Integer id, @Valid @RequestBody PayrollReopenRequest req) {
        return service.reopen(id, req.reason());
    }

    /** Mở lại MỌI phiếu đã chốt của một kỳ — lỗi lịch nghỉ hiếm khi chỉ dính một người. */
    @PostMapping("/reopen-period")
    @PreAuthorize(CAN_REOPEN)
    public Map<String, Integer> reopenPeriod(
            @RequestParam Short year, @RequestParam Short month, @Valid @RequestBody PayrollReopenRequest req) {
        return Map.of("reopened", service.reopenPeriod(year, month, req.reason()));
    }
}
