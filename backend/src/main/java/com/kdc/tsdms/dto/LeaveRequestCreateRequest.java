package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Giáo viên gửi ĐƠN XIN NGHỈ một BUỔI dạy: buổi ngày {@code leaveDate} của phân công
 * {@code assignmentId}.
 *
 * <p>Gửi kèm {@code assignmentId} chứ không gửi id buổi dạy: bảng đơn (V39) không có cột
 * ScheduleId, nên khoá tra buổi phải là cặp (phân công, ngày) — xem
 * {@code AssignmentLeaveRequest.leaveDate}.
 */
public record LeaveRequestCreateRequest(
        @NotNull(message = "Chưa chọn phân công") Integer assignmentId,
        @NotNull(message = "Chưa chọn buổi xin nghỉ") LocalDate leaveDate,

        @NotBlank(message = "Vui lòng nhập lý do xin nghỉ") @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {}
