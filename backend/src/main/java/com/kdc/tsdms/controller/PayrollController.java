package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.PayrollResponse;
import com.kdc.tsdms.dto.PayrollUpdateRequest;
import com.kdc.tsdms.service.PayrollService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Bảng lương — /api/v1/payroll.
 *
 * <p>GET : PAYROLL_VIEW. generate/PUT/finalize : PAYROLL_MANAGE. ADMIN đi tắt.
 */
@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {

    private final PayrollService service;

    public PayrollController(PayrollService service) {
        this.service = service;
    }

    /** Bảng lương một kỳ (mặc định tháng hiện tại). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PAYROLL_VIEW')")
    public List<PayrollResponse> list(
            @RequestParam(required = false) Short year, @RequestParam(required = false) Short month) {
        LocalDate today = LocalDate.now();
        short y = year != null ? year : (short) today.getYear();
        short m = month != null ? month : (short) today.getMonthValue();
        return service.list(y, m);
    }

    /** Sinh/tính lại lương từ chấm công theo TIẾT (đơn giá tự tra theo cấp của lớp). */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PAYROLL_MANAGE')")
    public List<PayrollResponse> generate(@RequestParam Short year, @RequestParam Short month) {
        return service.generate(year, month);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PAYROLL_MANAGE')")
    public PayrollResponse update(@PathVariable Integer id, @Valid @RequestBody PayrollUpdateRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PAYROLL_MANAGE')")
    public PayrollResponse finalizePayroll(@PathVariable Integer id) {
        return service.finalizePayroll(id);
    }
}
