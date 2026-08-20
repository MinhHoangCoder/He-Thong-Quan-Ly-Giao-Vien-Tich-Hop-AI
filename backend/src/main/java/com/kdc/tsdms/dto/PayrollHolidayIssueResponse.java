package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Cảnh báo trước khi chốt lương: kỳ này còn dòng VẮNG rơi vào ngày nghỉ (GET
 * /api/v1/payroll/holiday-issues).
 *
 * <p>Chốt lương là hành động một chiều — sau đó chấm công của kỳ bị khóa. Chốt khi còn Vắng
 * giả nghĩa là khóa luôn lỗi vào trong, và (trước V32) không còn đường sửa. Màn hình phải nói
 * ra điều đó TRƯỚC khi người dùng bấm, không phải sau.
 *
 * <p>Kèm luôn {@code holidayId} để bấm một cái là sang thẳng kỳ nghỉ gây lỗi — phát hiện mà
 * không chỉ được đường sửa thì cảnh báo chỉ làm người ta bực.
 */
public record PayrollHolidayIssueResponse(
        /** Tổng số dòng Vắng rơi vào ngày nghỉ trong kỳ. */
        int absenceCount,
        int teacherCount,
        /** Các kỳ nghỉ liên quan, để màn hình liệt kê và trỏ link. */
        List<HolidayRef> holidays) {

    public record HolidayRef(Integer holidayId, String name, int absenceCount) {}
}
