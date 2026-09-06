package com.kdc.tsdms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Số liệu kèm theo một trường, nạp riêng khi người dùng mở dòng chi tiết ở màn Quản lý trường.
 *
 * <p>Tách khỏi {@link SchoolResponse} vì mỗi lần mở chi tiết là 5 câu đếm; nhét vào danh sách thì
 * mỗi lần lật trang phải chạy 5 câu × 10 dòng cho thứ người dùng hiếm khi mở.
 */
public record SchoolDetailResponse(
        Integer id,
        long classCount,
        long teacherCount,
        long studentCount,
        /** Số tiết + số tiết buổi sáng, để biết khung đang là tiểu học hay THCS. */
        int periodCount,
        int morningPeriodCount,
        List<ContractRow> contracts,
        /** Lịch sử đổi ngày hết hạn hợp đồng, MỚI NHẤT TRƯỚC (V40). */
        List<ContractChangeRow> contractChanges) {

    /** Một dòng hợp đồng dịch vụ (bảng ServiceContract) — CHỈ ĐỌC ở màn này. */
    public record ContractRow(
            String contractCode, LocalDate startDate, LocalDate endDate, BigDecimal value, String status) {}

    /**
     * Một lần đổi ngày hết hạn hợp đồng (bảng SchoolContractChangeLog).
     *
     * <p>Đi kèm ở đây thay vì mở một endpoint riêng: modal sửa trường đã phải gọi
     * {@code GET /schools/{id}/summary} để có số lớp/giáo viên, thêm một lượt gọi nữa chỉ để lấy
     * vài dòng nhật ký là thêm một đường mạng có thể hỏng cho cùng một màn hình.
     *
     * @param changedByName tên người sửa; {@code null} khi thao tác không đi từ người đăng nhập
     */
    public record ContractChangeRow(
            Instant changedAt,
            LocalDate oldEndDate,
            LocalDate newEndDate,
            /** EXTEND | SHORTEN | SET | CLEAR — xem {@code SchoolContractChangeLog}. */
            String changeKind,
            String reason,
            String changedByName) {}
}
