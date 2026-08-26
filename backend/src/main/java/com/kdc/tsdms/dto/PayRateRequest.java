package com.kdc.tsdms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Khai một mức đơn giá tiết dạy.
 *
 * <p>Không có {@code effectiveTo}: đóng một mức là việc của {@code PayRateService.close()},
 * không phải của form khai giá. Cho sửa hai đầu khoảng cùng lúc là mở đường tạo ra hai mức
 * chồng nhau cho cùng một khối.
 *
 * <p>Khối 1..9 chứ không phải 1..12: V26 đã bỏ hẳn cấp 3 khỏi hệ thống, và chính header của
 * V26 chỉ đích danh "các ràng buộc 1..12 của tầng service" là thứ cần dọn. V38 (bảng đơn giá)
 * ra đời sau nên lỡ chép lại khoảng cũ — một mức giá cho khối 10..12 là dòng dữ liệu không
 * lớp nào khớp được.
 *
 * <p>CHECK ở DB ({@code CK_PayRate_Grade}) vẫn còn {@code BETWEEN 1 AND 12}; siết nó cần một
 * migration mới nên để lại, ở đây chặn ngay cửa vào là đủ để không sinh thêm dòng như vậy.
 */
public record PayRateRequest(
        @NotNull(message = "Thiếu khối bắt đầu") @Min(value = 1, message = "Khối từ 1 đến 9") @Max(value = 9, message = "Khối từ 1 đến 9") Short gradeFrom,

        @NotNull(message = "Thiếu khối kết thúc") @Min(value = 1, message = "Khối từ 1 đến 9") @Max(value = 9, message = "Khối từ 1 đến 9") Short gradeTo,

        @NotNull(message = "Vui lòng nhập đơn giá") @DecimalMin(value = "1", message = "Đơn giá phải lớn hơn 0") BigDecimal amount,

        @NotNull(message = "Vui lòng chọn ngày bắt đầu áp dụng") LocalDate effectiveFrom,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự") String note) {}
