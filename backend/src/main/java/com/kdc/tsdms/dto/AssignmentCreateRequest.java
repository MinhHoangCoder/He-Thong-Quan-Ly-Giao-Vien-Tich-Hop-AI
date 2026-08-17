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
 *   <li>Chọn thứ     → nằm trong {@code slots[].dayOfWeek}</li>
 *   <li>Chọn tiết    → nằm trong {@code slots[].periodId}</li>
 *   <li>Chọn lớp     → nằm trong {@code slots[].classId} (MỖI TIẾT một lớp, V16)</li>
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

        /**
         * Trường CHÍNH của phiếu (→ School) — từ V27 trường thật nằm ở từng tiết
         * ({@link AssignmentSlotRequest#schoolId()}); trường này chỉ còn là giá trị mặc định cho
         * slot không ghi trường (client cũ) và là nhãn đại diện lưu ở {@code Assignment.SchoolId}.
         * Service ghi đè bằng trường của TIẾT ĐẦU TIÊN.
         */
        @NotNull(message = "Vui lòng chọn trường") Integer schoolId,

        /** Môn học (→ Subject). */
        @NotNull(message = "Vui lòng chọn môn học") Integer subjectId,

        /**
         * Lớp MẶC ĐỊNH của phân công (→ SchoolClass) — từ V16 lớp thật nằm ở từng slot
         * ({@link AssignmentSlotRequest#classId()}), trường này chỉ còn là giá trị dự phòng
         * cho slot không ghi lớp (và cho client cũ chỉ biết gửi 1 lớp). Nullable: Service
         * chấp nhận null miễn là MỌI slot đều có lớp.
         */
        Integer classId,

        /** Ngày bắt đầu giai đoạn phân công. */
        @NotNull(message = "Vui lòng nhập ngày bắt đầu") LocalDate startDate,

        /**
         * Ngày kết thúc giai đoạn — nullable trên REQUEST, nhưng KHÔNG bao giờ được lưu null:
         * bỏ trống thì Service chốt luôn thành {@code startDate + 8 tuần} (đúng bằng khoảng đã
         * sinh Schedule) rồi mới ghi xuống. Lưu null sẽ khiến luật chống trùng coi giai đoạn là
         * vô hạn và giữ khung giờ của giáo viên mãi mãi dù không còn buổi dạy nào.
         */
        LocalDate endDate,

        /**
         * Danh sách slot Thứ+Tiết — tối thiểu 1. KHÔNG giới hạn ở 9 nữa: một phân công có
         * thể trải nhiều Thứ (T2–CN) nên tổng số tiết/tuần vượt 9 là hợp lệ. Chỉ giữ một
         * trần an toàn 63 = 9 tiết × 7 ngày (số tổ hợp Thứ+Tiết tối đa) để chặn payload rác.
         * Mỗi slot phải là 1 tổ hợp DayOfWeek+PeriodId không trùng nhau.
         */
        @NotEmpty(message = "Vui lòng chọn ít nhất 1 tiết dạy") @Size(max = 63, message = "Số tiết dạy trong một phân công vượt mức cho phép") @Valid // kích hoạt validate đệ quy bên trong AssignmentSlotRequest
        List<AssignmentSlotRequest> slots) {}
