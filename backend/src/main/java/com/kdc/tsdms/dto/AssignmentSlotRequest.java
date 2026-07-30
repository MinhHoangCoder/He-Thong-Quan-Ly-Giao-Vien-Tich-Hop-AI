package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * DTO request cho 1 slot (Thứ + Tiết + Lớp) trong phân công — lồng bên trong
 * {@link AssignmentCreateRequest}.
 *
 * <p>Ví dụ: 1 phân công "GV Nguyễn dạy môn Kĩ năng số tại TH Dư Hàng" sáng Thứ 2:
 * <ul>
 *   <li>slot 1: MON, periodId = 6 (Tiết 1), classId = 23 (lớp 1A1)</li>
 *   <li>slot 2: MON, periodId = 7 (Tiết 2), classId = 24 (lớp 1A2)</li>
 *   <li>slot 3: MON, periodId = 8 (Tiết 3), classId = 25 (lớp 1A3)</li>
 * </ul>
 */
public record AssignmentSlotRequest(

        /**
         * Thứ trong tuần áp dụng cho slot này.
         * Giá trị hợp lệ: MON | TUE | WED | THU | FRI | SAT | SUN
         * (đồng bộ với CK_AssignmentSlot_DayOfWeek trong DB).
         */
        @NotBlank(message = "Vui lòng chọn thứ trong tuần") @Pattern(
                regexp = "MON|TUE|WED|THU|FRI|SAT|SUN",
                message = "Thứ không hợp lệ — chỉ chấp nhận MON/TUE/WED/THU/FRI/SAT/SUN")
        String dayOfWeek,

        /**
         * Id tiết học (→ Period) — PHẢI là tiết thuộc đúng trường của Assignment cha.
         * Service validate lại bằng PeriodRepository.findByIdAndSchoolIdAndDeletedFalse.
         */
        @NotNull(message = "Vui lòng chọn tiết học") Integer periodId,

        /**
         * Lớp dạy Ở TIẾT NÀY (→ SchoolClass), phải thuộc đúng trường của Assignment cha.
         * Nullable để tương thích ngược: bỏ trống thì Service lấy {@code classId} ở cấp
         * phân công làm lớp mặc định (và báo lỗi nếu cả hai đều trống).
         */
        Integer classId) {}
