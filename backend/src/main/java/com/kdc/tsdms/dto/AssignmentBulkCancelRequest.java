package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * HỦY hàng loạt: nhiều phiếu, CÙNG một ngày hiệu lực và CÙNG một lý do.
 *
 * <p>Tách khỏi {@link AssignmentBulkRequest} (dùng cho nhắc / ép duyệt) vì hủy có thêm hai thứ bắt
 * buộc riêng: ngày hiệu lực và lý do gửi cho giáo viên.
 */
public record AssignmentBulkCancelRequest(
        @NotEmpty(message = "Chưa chọn phiếu phân công nào") List<Integer> ids,

        /** Ngày đầu tiên không dạy nữa; bỏ trống = hủy từ hôm nay. */
        LocalDate effectiveDate,

        @NotBlank(message = "Vui lòng nhập lý do hủy") @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự") String reason) {}
