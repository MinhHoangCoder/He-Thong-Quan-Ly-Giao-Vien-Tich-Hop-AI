package com.kdc.tsdms.exception;

import com.kdc.tsdms.dto.ErrorResponse;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Gom mọi lỗi về một định dạng JSON thống nhất. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        HttpStatus status = ex.getStatus();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), ex.getMessage()));
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

    /* ── Lỗi do REQUEST sai, không phải server hỏng ──────────────────────────────
     * Những exception dưới đây vốn tự mang sẵn mã HTTP đúng của mình, nhưng nếu
     * không bắt riêng thì chúng rơi hết vào handleOther(Exception) và ra 500 —
     * client gõ nhầm URL cũng tưởng hệ thống sập, còn log thì đầy stack trace vô
     * nghĩa. Bắt tên cụ thể để trả đúng 404/405/400 và log ở mức WARN.
     */

    /** Đường dẫn không khớp controller nào. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        log.warn("Khong tim thay duong dan: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Không tìm thấy đường dẫn yêu cầu");
    }

    /** Đúng đường dẫn nhưng sai phương thức (vd POST vào endpoint chỉ nhận GET). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        log.warn("Sai phuong thuc HTTP: {}", ex.getMessage());
        return build(
                HttpStatus.METHOD_NOT_ALLOWED, "Phương thức " + ex.getMethod() + " không dùng được cho đường dẫn này");
    }

    /** Body JSON hỏng, sai kiểu, hoặc gửi thiếu body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Body khong doc duoc: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ (JSON sai định dạng hoặc thiếu nội dung)");
    }

    /** Thiếu tham số bắt buộc trên query string. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "Thiếu tham số bắt buộc: " + ex.getParameterName());
    }

    /** Tham số sai kiểu (vd truyền chữ vào chỗ cần ngày/số). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Giá trị không hợp lệ cho tham số: " + ex.getName());
    }

    /**
     * Vi phạm ràng buộc ở tầng DB (trùng khóa, sai khóa ngoại, thiếu cột NOT NULL,
     * chuỗi quá dài...). Đây là lỗi DỮ LIỆU GỬI LÊN, không phải server hỏng — để rơi
     * vào {@link #handleOther(Exception)} thì người dùng chỉ thấy "Lỗi hệ thống" và
     * không biết phải sửa gì.
     *
     * <p>Phân loại theo MÃ LỖI của SQL Server chứ không đọc nội dung message: message
     * gốc có tên bảng/cột/ràng buộc, trả thẳng cho client là lộ cấu trúc nội bộ. Mã lỗi
     * đủ để nói cho người dùng biết thuộc nhóm nào; chi tiết nằm ở log server.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Vi pham rang buoc du lieu o tang DB", ex);

        return switch (sqlErrorCode(ex)) {
            // 2627 = vi phạm UNIQUE/PRIMARY KEY, 2601 = vi phạm unique index
            case 2627, 2601 -> build(HttpStatus.CONFLICT, "Dữ liệu bị trùng với một bản ghi đã có trong hệ thống");
            // 547 = vi phạm FOREIGN KEY hoặc CHECK constraint
            case 547 ->
                build(
                        HttpStatus.BAD_REQUEST,
                        "Dữ liệu không hợp lệ: tham chiếu không tồn tại hoặc sai giá trị cho phép");
            // 515 = chèn NULL vào cột NOT NULL
            case 515 -> build(HttpStatus.BAD_REQUEST, "Thiếu thông tin bắt buộc");
            // 8152 / 2628 = chuỗi dài hơn kích thước cột
            case 8152, 2628 -> build(HttpStatus.BAD_REQUEST, "Một trong các trường nhập vượt quá độ dài cho phép");
            default -> build(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ hoặc vi phạm ràng buộc của hệ thống");
        };
    }

    /** Dò mã lỗi SQL Server trong chuỗi cause (Spring bọc SQLException qua nhiều lớp). */
    private int sqlErrorCode(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sqlEx) {
                return sqlEx.getErrorCode();
            }
        }
        return 0;
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
