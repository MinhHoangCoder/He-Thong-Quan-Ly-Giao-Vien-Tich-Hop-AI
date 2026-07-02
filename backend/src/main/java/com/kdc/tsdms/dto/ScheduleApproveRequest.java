package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO request Admin duyệt hoặc từ chối 1 buổi dạy (PATCH /api/v1/schedules/{id}/status).
 *
 * <p>Quy tắc nghiệp vụ (Service kiểm tra):
 * <ul>
 *   <li>action = APPROVED → Schedule.status đổi PENDING → APPROVED,
 *       trigger ghi ScheduleStatusLog, gửi Notification cho GV.</li>
 *   <li>action = REJECTED → phải có rejectionReason, Schedule.status đổi PENDING → REJECTED,
 *       gửi Notification kèm lý do cho GV.</li>
 *   <li>Chỉ Admin / Employee có quyền SCHEDULE_APPROVE mới được gọi endpoint này.</li>
 * </ul>
 */
public record ScheduleApproveRequest(

        /**
         * Hành động duyệt: APPROVED hoặc REJECTED.
         * Không cho phép đổi trực tiếp sang CANCELLED qua endpoint này —
         * hủy lịch đã duyệt dùng endpoint DELETE riêng.
         */
        @NotBlank(message = "Vui lòng chọn hành động duyệt")
        @Pattern(
                regexp = "APPROVED|REJECTED",
                message = "Hành động không hợp lệ — chỉ chấp nhận APPROVED hoặc REJECTED")
        String action,

        /**
         * Lý do từ chối — bắt buộc khi action = REJECTED, tùy chọn khi APPROVED.
         * Service validate: nếu action = REJECTED mà rejectionReason trống → ném ApiException.
         */
        String rejectionReason

) {}