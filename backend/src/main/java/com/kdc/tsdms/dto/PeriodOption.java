package com.kdc.tsdms.dto;

import java.time.LocalTime;

/**
 * Tiết học của một trường — phục vụ dropdown chọn tiết khi phân công.
 *
 * @param periodNumber số tiết trong NGÀY (1..n) — khóa nghiệp vụ, dùng khi đối chiếu dữ liệu
 * @param indexInSession số tiết trong BUỔI (chiều đánh lại từ 1) — dùng để HIỂN THỊ
 */
public record PeriodOption(
        Integer id,
        Short periodNumber,
        Short indexInSession,
        String sessionType,
        LocalTime startTime,
        LocalTime endTime,
        String label) {}
