package com.kdc.tsdms.dto;

import java.math.BigDecimal;

/**
 * Ba thẻ tổng quan trên trang Chấm công, tính trên TOÀN BỘ kết quả lọc.
 *
 * <p>Tách khỏi danh sách vì danh sách nay trả theo trang — cộng dồn ở trình duyệt chỉ ra
 * tổng của 10 dòng đang hiện.
 *
 * @param totalRows tổng số dòng chấm công khớp bộ lọc
 * @param presentRows số dòng Có mặt hoặc Đi muộn (buổi thực sự được tính công)
 * @param totalHours tổng số giờ dạy, cộng từ giờ vào/ra
 */
public record AttendanceSummaryResponse(long totalRows, long presentRows, BigDecimal totalHours) {}
