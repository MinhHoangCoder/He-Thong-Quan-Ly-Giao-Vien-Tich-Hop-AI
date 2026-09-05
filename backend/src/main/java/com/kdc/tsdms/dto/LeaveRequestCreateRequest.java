package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Giáo viên gửi ĐƠN XIN NGHỈ một phân công, từ ngày {@code effectiveDate}. */
public record LeaveRequestCreateRequest(
        @NotNull(message = "Chưa chọn phân công") Integer assignmentId,
        @NotNull(message = "Chưa chọn ngày bắt đầu nghỉ") LocalDate effectiveDate,

        @NotBlank(message = "Vui lòng nhập lý do xin nghỉ") @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {}
