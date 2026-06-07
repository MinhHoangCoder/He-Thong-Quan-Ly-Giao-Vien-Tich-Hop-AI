package com.kdc.tsdms.exception;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có kèm mã HTTP, để controller advice trả về đúng status + message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
