package com.kdc.tsdms.exception;

import com.kdc.tsdms.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Gom mọi lỗi về một định dạng JSON thống nhất. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    /** Lỗi validate @Valid -> gom các message field thành 1 chuỗi. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Dữ liệu không hợp lệ");
        return build(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
    }

    /**
     * Lỗi không lường trước: KHÔNG trả ex.getMessage() cho client — message của
     * SQLException/Hibernate lộ tên bảng, câu query, cấu trúc nội bộ (information
     * disclosure). Log đầy đủ ở server để debug, client chỉ nhận thông báo chung.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex) {
        log.error("Lỗi không xử lý được", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống, vui lòng thử lại sau");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
