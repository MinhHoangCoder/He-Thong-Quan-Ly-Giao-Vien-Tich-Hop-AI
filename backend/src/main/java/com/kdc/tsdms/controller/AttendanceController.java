package com.kdc.tsdms.controller;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller MẪU cho module Chấm công — minh họa cơ chế phân quyền RBAC theo permission.
 *
 * <p>Quy ước nhóm (xem docs/dev-notes/2026-06-14-backend-rbac-permission-matrix.md):
 * mỗi endpoint nghiệp vụ gắn {@code @PreAuthorize("hasRole('ADMIN') or hasAuthority('<MÃ>')")}.
 * ADMIN đi tắt; còn lại phải có đúng mã quyền (perms) trong token mới qua được, sai -> 403.
 *
 * <p>Test nhanh: tài khoản {@code ketoan}/{@code daotao} gọi /ping -> 200; {@code nhansu}
 * (không có ATTENDANCE_VIEW) -> 403. Chỉ {@code ketoan} gọi POST được (ATTENDANCE_MANAGE).
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    /** Xem chấm công — cần quyền ATTENDANCE_VIEW (Kế toán, Đào tạo, Giáo viên¹). */
    @GetMapping("/ping")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_VIEW')")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "message", "Bạn có quyền xem chấm công");
    }

    /** Ghi chấm công — cần quyền ATTENDANCE_MANAGE (chỉ Kế toán). */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public Map<String, String> mark() {
        return Map.of("status", "ok", "message", "Đã ghi nhận chấm công (mẫu)");
    }
}
