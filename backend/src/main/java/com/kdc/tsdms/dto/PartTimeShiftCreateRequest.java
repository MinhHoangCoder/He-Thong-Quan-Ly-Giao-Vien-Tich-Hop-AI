package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO request nhân viên PART_TIME đăng ký 1 ca làm việc
 * (POST /api/v1/employee-schedules/shift-requests).
 *
 * <p>Quy tắc nghiệp vụ (Service kiểm tra):
 * <ul>
 *   <li>Cửa sổ đăng ký chỉ mở từ Thứ 4 đến Thứ 7 12:00 trưa —
 *       ngoài giờ này Service ném ApiException 400.</li>
 *   <li>WorkDate phải rơi vào Thứ 2–6 của TUẦN KẾ TIẾP —
 *       không cho đăng ký ngày trong tuần hiện tại hoặc quá khứ.</li>
 *   <li>Mỗi NV chỉ được đăng ký 1 ca / 1 ngày (trừ đơn đã REJECTED —
 *       được phép đăng ký lại).</li>
 * </ul>
 */
public record PartTimeShiftCreateRequest(

        /**
         * Ngày làm việc muốn đăng ký (phải là T2–T6 tuần kế tiếp).
         * Frontend hiển thị lịch 5 ngày của tuần kế tiếp để người dùng tick,
         * rồi gửi từng ngày thành 1 request riêng (hoặc gửi batch nếu mở rộng sau).
         */
        @NotNull(message = "Vui lòng chọn ngày làm việc") LocalDate workDate,

        /**
         * Ca đăng ký:
         * MORNING   = sáng 08:00–11:00
         * AFTERNOON = chiều 14:00–17:00
         */
        @NotBlank(message = "Vui lòng chọn ca làm việc") @Pattern(regexp = "MORNING|AFTERNOON", message = "Ca không hợp lệ — chỉ chấp nhận MORNING hoặc AFTERNOON") String shiftType,

        /**
         * Ghi chú tự do (tùy chọn) — nhân viên có thể ghi thêm lưu ý cho HR.
         * Ví dụ: "10h có việc xin về sớm", "Có thể đến từ 8h30".
         */
        @Size(max = 255, message = "Ghi chú không được quá 255 ký tự") String note) {}
