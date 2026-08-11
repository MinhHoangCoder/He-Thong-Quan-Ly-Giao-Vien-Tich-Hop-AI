package com.kdc.tsdms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cấu trúc lỗi trả về thống nhất.
 *
 * @param code mã nghiệp vụ để frontend XỬ LÝ KHÁC NHAU theo loại lỗi, không phải để hiển thị.
 *     Gần như mọi lỗi đều bỏ trống (chỉ hiện {@code message}); chỉ đặt mã cho những lỗi cần
 *     giao diện phản ứng riêng — ví dụ cảnh báo có thể bỏ qua thì mở hộp xác nhận thay vì
 *     báo đỏ. Bỏ khỏi JSON khi null để không làm rối các phản hồi lỗi thông thường.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code) {

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
