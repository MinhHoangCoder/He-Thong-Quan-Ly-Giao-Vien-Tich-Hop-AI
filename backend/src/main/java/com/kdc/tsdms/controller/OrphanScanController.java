package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.OrphanScanResponse;
import com.kdc.tsdms.service.OrphanScanService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Rà soát dữ liệu mồ côi — /api/v1/orphan-scan (Flyway V35).
 *
 * <p>Chỉ ADMIN: đây là câu hỏi về SỨC KHỎE của cơ sở dữ liệu, không phải nghiệp vụ hằng ngày.
 * Con số ở đây chỉ có nghĩa với người hiểu mô hình dữ liệu, và hiểu nhầm nó dễ dẫn tới hành
 * động sai (xóa nốt phần con thay vì khôi phục phần cha).
 *
 * <p>Cố ý KHÔNG có endpoint dọn — xem giải thích ở {@link OrphanScanService}.
 */
@RestController
@RequestMapping("/api/v1/orphan-scan")
public class OrphanScanController {

    private final OrphanScanService service;

    public OrphanScanController(OrphanScanService service) {
        this.service = service;
    }

    /**
     * Quét lại ngay và trả kết quả, đồng thời ghi một ảnh chụp vào nhật ký.
     *
     * <p>Là GET dù có ghi nhật ký: cái ghi xuống chỉ là số liệu quan trắc, không phải dữ liệu
     * nghiệp vụ, và bấm hai lần không gây hậu quả gì ngoài hai dòng lịch sử.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public OrphanScanResponse quet() {
        return service.quet();
    }
}
