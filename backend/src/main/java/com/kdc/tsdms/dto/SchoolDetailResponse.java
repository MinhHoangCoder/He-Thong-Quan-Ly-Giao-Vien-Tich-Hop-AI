package com.kdc.tsdms.dto;

import java.math.BigDecimal;
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
        List<ContractRow> contracts) {

    /** Một dòng hợp đồng dịch vụ (bảng ServiceContract) — CHỈ ĐỌC ở màn này. */
    public record ContractRow(
            String contractCode, LocalDate startDate, LocalDate endDate, BigDecimal value, String status) {}
}
