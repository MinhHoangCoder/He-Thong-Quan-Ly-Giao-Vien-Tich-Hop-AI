package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body khi HỦY một phân công (V39).
 *
 * <p>{@code effectiveDate} = ngày ĐẦU TIÊN giáo viên không dạy nữa; bỏ trống = hủy từ hôm nay.
 * Buổi trước ngày này giữ nguyên làm bằng chứng chấm công/lương.
 *
 * <p>Lý do BẮT BUỘC: nó được gửi thẳng vào thông báo của giáo viên. Hủy lịch của người khác mà
 * không nói vì sao thì họ chỉ thấy buổi dạy biến mất — đúng cái lỗi bản cũ mắc phải.
 */
public record AssignmentCancelRequest(
        LocalDate effectiveDate,

        @NotBlank(message = "Vui lòng nhập lý do hủy") @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự") String reason) {}
