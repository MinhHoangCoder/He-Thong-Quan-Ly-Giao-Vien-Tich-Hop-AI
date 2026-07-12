package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.DashboardResponse;
import com.kdc.tsdms.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Bảng điều khiển (admin) — /api/v1/dashboard.
 *
 * <p>Chỉ nhân sự trung tâm (ADMIN / EMPLOYEE) xem số liệu tổng hợp toàn hệ thống.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** Tổng hợp số liệu; {@code months} = số tháng vẽ biểu đồ (mặc định 8). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public DashboardResponse summary(@RequestParam(defaultValue = "8") int months) {
        return service.summary(months);
    }
}
