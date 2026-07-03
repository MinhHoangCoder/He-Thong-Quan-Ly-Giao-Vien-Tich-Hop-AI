package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * DTO request cho 1 slot (Thứ + Tiết) trong phân công — lồng bên trong
 * {@link AssignmentCreateRequest}.
 *
 * <p>Ví dụ: 1 phân công "GV Nguyễn dạy lớp 6A môn Scratch" có thể có 2 slot:
 * <ul>
 *   <li>slot 1: THU (Thứ 5), periodId = 3 (Tiết 3 của trường đó)</li>
 *   <li>slot 2: MON (Thứ 2), periodId = 1 (Tiết 1)</li>
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
        @NotNull(message = "Vui lòng chọn tiết học") Integer periodId) {}
