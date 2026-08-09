package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin xử lý một yêu cầu bổ sung chấm công (approve / reject).
 *
 * @param status chỉ dùng khi DUYỆT — ép trạng thái dòng chấm công sinh ra. Bỏ trống thì
 *     service tự suy từ giờ giáo viên khai (muộn quá ngưỡng → LATE, còn lại → PRESENT).
 * @param reviewNote ghi chú của admin. BẮT BUỘC khi từ chối; tùy chọn khi duyệt.
 */
public record AttendanceAmendReviewRequest(
        @Pattern(regexp = "PRESENT|LATE|LEAVE", message = "Trạng thái không hợp lệ") String status,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String reviewNote) {}
