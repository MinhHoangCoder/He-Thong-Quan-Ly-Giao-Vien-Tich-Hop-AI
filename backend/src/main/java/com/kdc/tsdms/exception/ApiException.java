package com.kdc.tsdms.exception;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có kèm mã HTTP, để controller advice trả về đúng status + message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Mã nghiệp vụ cho frontend xử lý riêng; {@code null} với hầu hết lỗi (chỉ hiện message).
     * Dùng khi giao diện cần phản ứng khác nhau theo loại lỗi — ví dụ cảnh báo có thể bỏ qua
     * thì mở hộp xác nhận thay vì báo đỏ.
     */
    private final String code;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
