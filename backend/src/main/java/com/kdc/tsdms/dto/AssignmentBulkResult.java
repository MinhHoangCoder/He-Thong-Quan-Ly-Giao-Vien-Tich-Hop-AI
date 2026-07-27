package com.kdc.tsdms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả một thao tác hàng loạt: làm được bao nhiêu phiếu, phiếu nào lỗi vì sao.
 *
 * <p>Cố tình KHÔNG dừng ở phiếu lỗi đầu tiên — chọn 10 phiếu mà 1 phiếu đã hết hạn thì 9 phiếu
 * còn lại vẫn phải chạy, người dùng đọc danh sách lỗi rồi xử lý riêng phiếu đó.
 */
public class AssignmentBulkResult {

    /** Số phiếu thao tác thành công. */
    public int succeeded;

    /** Mô tả lỗi từng phiếu không làm được, dạng "#12: lý do". */
    public List<String> errors = new ArrayList<>();

    public void ok() {
        succeeded++;
    }

    public void fail(Integer id, String message) {
        errors.add("#" + id + ": " + message);
    }
}
