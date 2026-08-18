package com.kdc.tsdms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO sửa một phiếu phân công CHƯA được xác nhận (PUT /api/v1/assignments/{id}).
 *
 * <p>Sửa xong phiếu quay về Chờ xác nhận và giáo viên nhận lại lời mời từ đầu — không được
 * lặng lẽ đổi lịch dưới chân người đã đồng ý, nên endpoint chỉ nhận phiếu ở trạng thái
 * Chờ xác nhận / Bị từ chối / Hết hạn.
 *
 * <p>ĐỔI ĐƯỢC giáo viên (phiếu bị từ chối thì xếp cho người khác; giáo viên cũ vẫn chọn lại
 * được phòng khi họ bấm nhầm nút Từ chối) và ĐỔI ĐƯỢC TRƯỜNG của từng tiết — từ V27 trường
 * nằm ở {@code slots[].schoolId} chứ không còn ở cấp phiếu, nên sửa trường không còn là "một
 * phiếu khác" nữa. KHÔNG đổi được MÔN: môn vẫn ở cấp phiếu.
 */
public record AssignmentUpdateRequest(

        /** Giáo viên nhận phiếu sau khi sửa (có thể khác người cũ). */
        @NotNull(message = "Vui lòng chọn giáo viên") Integer teacherId,

        @NotNull(message = "Vui lòng nhập ngày bắt đầu") LocalDate startDate,

        /** Bỏ trống = sinh lịch mặc định 8 tuần kể từ ngày bắt đầu. */
        LocalDate endDate,

        /** Các tiết Thứ+Tiết+Lớp — thay THẾ toàn bộ danh sách cũ. */
        @NotEmpty(message = "Vui lòng chọn ít nhất 1 tiết dạy") @Size(max = 63, message = "Số tiết dạy trong một phân công vượt mức cho phép") @Valid List<AssignmentSlotRequest> slots) {}
