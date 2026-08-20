package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body chuyển các dòng Vắng giả của một kỳ nghỉ sang Nghỉ phép (POST
 * /api/v1/holidays/{id}/fix-absences).
 *
 * <p>Nhận DANH SÁCH ID chứ không phải cờ "làm tất": màn hình cho bỏ tick từng dòng, và một
 * request "sửa hết những gì đang vướng" sẽ âm thầm nuốt luôn dòng vừa xuất hiện giữa lúc
 * người dùng đang xem — không phải thứ họ bấm đồng ý.
 */
public record HolidayFixAbsencesRequest(
        @NotEmpty(message = "Chưa chọn dòng chấm công nào") List<Long> attendanceIds,

        @NotBlank(message = "Vui lòng nhập lý do điều chỉnh") @Size(max = 255, message = "Lý do tối đa 255 ký tự") String reason) {}
