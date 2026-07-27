package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Thao tác hàng loạt trên nhiều phiếu phân công (nhắc / ép duyệt / hủy).
 *
 * <p>Đầu kỳ trung tâm phân công cho cả chục giáo viên cùng lúc; bấm từng dòng để nhắc hay
 * ép duyệt là quá chậm.
 */
public record AssignmentBulkRequest(

        /** Danh sách phiếu cần thao tác. */
        @NotEmpty(message = "Chưa chọn phiếu phân công nào") List<Integer> ids,

        /** Ghi chú khi ép duyệt (tùy chọn) — không dùng cho nhắc/hủy. */
        String note) {}
