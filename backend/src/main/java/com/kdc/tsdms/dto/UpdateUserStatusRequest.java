package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body cho PATCH /api/v1/admin/users/{id}/status. Chỉ nhận ACTIVE | LOCKED
 * (INACTIVE để dành cho luồng nghỉ việc — thành viên làm nhân sự xử lý sau).
 */
public record UpdateUserStatusRequest(
        @NotBlank(message = "Thiếu trạng thái") String status) {}
