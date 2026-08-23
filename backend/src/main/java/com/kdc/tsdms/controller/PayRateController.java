package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.PayRateRequest;
import com.kdc.tsdms.entity.PayRate;
import com.kdc.tsdms.service.PayRateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Bảng đơn giá tiết dạy — /api/v1/pay-rates (Flyway V38).
 *
 * <p>Đọc mở cho PAYROLL_VIEW: người xem bảng lương cần đối chiếu được con số trên phiếu với
 * barem, chứ không phải hỏi lại kế toán. Ghi cần PAYRATE_MANAGE — đổi giá là quyết định tiền
 * bạc, không phải thao tác vận hành hằng ngày.
 *
 * <p>Cố ý KHÔNG có PUT: sửa đè một mức đã áp dụng sẽ làm mọi kỳ lương cũ tính lại ra số khác
 * với số đã trả. Đổi giá = thêm mức mới, service tự đóng mức cũ.
 */
@RestController
@RequestMapping("/api/v1/pay-rates")
public class PayRateController {

    private static final String CAN_VIEW = "hasRole('ADMIN') or hasAuthority('PAYROLL_VIEW')";
    private static final String CAN_MANAGE = "hasRole('ADMIN') or hasAuthority('PAYRATE_MANAGE')";

    private final PayRateService service;

    public PayRateController(PayRateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public List<PayRate> list() {
        return service.list();
    }

    /** Khai mức giá mới; mức cũ cùng khoảng khối tự được đóng ở ngày liền trước. */
    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<PayRate> create(@Valid @RequestBody PayRateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    /** Xóa một mức CHƯA có hiệu lực (gõ nhầm). Mức đã áp dụng thì không xóa được. */
    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
