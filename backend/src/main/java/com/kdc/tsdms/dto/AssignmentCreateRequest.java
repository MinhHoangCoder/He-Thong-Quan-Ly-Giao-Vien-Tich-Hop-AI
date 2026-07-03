package com.kdc.tsdms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO request tạo phân công giáo viên (POST /api/v1/assignments).
 *
 * <p>Luồng 7 bước trên UI tương ứng với các trường trong record này:
 * <ol>
 *   <li>Chọn môn     → {@code subjectId}</li>
 *   <li>Chọn trường  → {@code schoolId}</li>
 *   <li>Chọn khối    → frontend lọc, không cần trường riêng</li>
 *   <li>Chọn lớp     → {@code classId}</li>
 *   <li>Chọn thứ     → nằm trong {@code slots[].dayOfWeek}</li>
 *   <li>Chọn tiết    → nằm trong {@code slots[].periodId}</li>
 *   <li>Chọn GV      → {@code teacherId}</li>
 * </ol>
 *
 * <p>Sau khi tạo thành công, Service sẽ:
 * <ol>
 *   <li>Lưu 1 bản ghi Assignment.</li>
 *   <li>Lưu N bản ghi AssignmentSlot (1 slot / 1 Thứ+Tiết).</li>
 *   <li>Sinh tất cả Schedule (1 buổi / tuần / slot) trong khoảng StartDate → EndDate
 *       với status = PENDING.</li>
 *   <li>Gửi Notification PENDING cho Admin duyệt.</li>
 * </ol>
 */
public record AssignmentCreateRequest(

        /** Giáo viên được phân công (→ Teacher). */
        @NotNull(message = "Vui lòng chọn giáo viên") Integer teacherId,

        /** Trường sẽ dạy (→ School). */
        @NotNull(message = "Vui lòng chọn trường") Integer schoolId,

        /** Môn học (→ Subject). */
        @NotNull(message = "Vui lòng chọn môn học") Integer subjectId,

        /**
         * Lớp cụ thể (→ SchoolClass) — BẮT BUỘC trong nghiệp vụ phân công GV
         * (Service validate, không thể null khi gọi endpoint này).
         */
        @NotNull(message = "Vui lòng chọn lớp học") Integer classId,

        /** Ngày bắt đầu giai đoạn phân công. */
        @NotNull(message = "Vui lòng nhập ngày bắt đầu") LocalDate startDate,

        /**
         * Ngày kết thúc giai đoạn — nullable (phân công vô thời hạn).
         * Nếu null, Service sinh Schedule tối đa đến cuối năm học hiện tại.
         */
        LocalDate endDate,

        /**
         * Danh sách slot Thứ+Tiết — tối thiểu 1, tối đa 9 (vì 1 ngày có 9 tiết,
         * không thể dạy nhiều hơn tổng số tiết/ngày).
         * Mỗi slot phải là 1 tổ hợp DayOfWeek+PeriodId không trùng nhau.
         */
        @NotEmpty(message = "Vui lòng chọn ít nhất 1 tiết dạy") @Size(max = 9, message = "Không thể chọn quá 9 tiết dạy cho 1 phân công") @Valid // kích hoạt validate đệ quy bên trong AssignmentSlotRequest
        List<AssignmentSlotRequest> slots) {}
