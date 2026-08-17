package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.StandardFrameResult;
import com.kdc.tsdms.service.PeriodService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Khung tiết học — /api/v1/periods.
 *
 * <p>Chỉ mở đúng một lối: áp bộ khung CHUẨN cho trường chưa có khung nào. Không làm CRUD tiết
 * đầy đủ ở đây — sửa tay từng tiết của một trường đang chạy sẽ làm lệch giờ các buổi dạy đã sinh
 * ra theo khung cũ, việc đó cần một luồng riêng có sinh lại lịch.
 */
@RestController
@RequestMapping("/api/v1/periods")
public class PeriodController {

    private final PeriodService service;

    public PeriodController(PeriodService service) {
        this.service = service;
    }

    /**
     * Áp khung tiết chuẩn theo cấp học cho một trường.
     *
     * <p>Quyền: người xếp phân công — họ mới là người đụng phải trường thiếu khung tiết và cần
     * gỡ tại chỗ.
     */
    @PostMapping("/standard/{schoolId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public StandardFrameResult applyStandard(@PathVariable Integer schoolId) {
        return service.applyStandardFrame(schoolId);
    }
}
