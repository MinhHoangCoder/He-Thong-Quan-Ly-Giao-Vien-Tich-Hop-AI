package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Size;

/**
 * Quyết định của admin trên một đơn xin nghỉ.
 *
 * <p>{@code note} là ghi chú khi DUYỆT (tùy chọn) và là LÝ DO khi TỪ CHỐI (bắt buộc — service
 * kiểm, vì cùng một record dùng cho hai đường).
 */
public record LeaveRequestDecisionRequest(
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {}
