package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.Paging;
import com.kdc.tsdms.dto.AuditLogResponse;
import com.kdc.tsdms.service.AuditQueryService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Nhật ký hệ thống — /api/v1/audit-logs.
 *
 * <p>Chỉ ADMIN, và cố ý CHỈ CÓ ĐỌC: nhật ký mà sửa hay xóa được thì không còn là nhật ký. Không
 * có POST/PUT/DELETE nào ở đây, kể cả cho admin — dòng nhật ký chỉ do các service ghi vào khi
 * thao tác nguy hiểm xảy ra.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditQueryService service;

    public AuditLogController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLogResponse> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) Integer actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.search(action, entity, actorId, from, to, Paging.of(page, size));
    }

    /** Danh sách loại thao tác + bảng đã từng ghi — đổ vào ô lọc. */
    @GetMapping("/filter-options")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> filterOptions() {
        return service.filterOptions();
    }
}
