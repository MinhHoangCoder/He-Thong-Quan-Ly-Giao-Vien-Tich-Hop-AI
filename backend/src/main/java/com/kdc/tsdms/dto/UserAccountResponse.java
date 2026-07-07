package com.kdc.tsdms.dto;

import java.time.Instant;
import java.util.List;

/** Một dòng trong bảng "Quản lý tài khoản" (GET /api/v1/admin/users). */
public record UserAccountResponse(
        Integer id,
        String username,
        String email,
        String fullName,
        String status,
        Instant lastLoginAt,
        List<String> roles) {}
