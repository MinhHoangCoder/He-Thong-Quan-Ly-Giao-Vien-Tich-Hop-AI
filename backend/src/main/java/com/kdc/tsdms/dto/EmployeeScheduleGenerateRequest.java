package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * DTO request HR generate lịch làm việc CỐ ĐỊNH cho nhân viên FULL_TIME
 * (POST /api/v1/employee-schedules/generate).
 *
 * <p>Khi HR gọi endpoint này, Service sẽ:
 * <ol>
 *   <li>Kiểm tra nhân viên có EmploymentType = FULL_TIME không.</li>
 *   <li>Duyệt từng ngày T2–T6 trong khoảng [startDate, endDate].</li>
 *   <li>Với mỗi ngày, tạo 2 dòng EmployeeSchedule (MORNING 08:00–11:00 +
 *       AFTERNOON 14:00–17:00), Source = FIXED.</li>
 *   <li>Bỏ qua ngày đã có lịch rồi (dùng existsByEmployeeIdAndWorkDateAndShiftType)
 *       — tránh lỗi unique constraint khi generate lại.</li>
 * </ol>
 *
 * <p>HR thường gọi 1 lần mỗi học kỳ, truyền vào khoảng thời gian hợp đồng
 * của nhân viên (startDate = hireDate, endDate = contractEndDate).
 */
public record EmployeeScheduleGenerateRequest(

        /** Nhân viên cần generate lịch (→ Employee, phải có EmploymentType = FULL_TIME). */
        @NotNull(message = "Vui lòng chọn nhân viên") Integer employeeId,

        /** Ngày bắt đầu generate — thường là đầu học kỳ hoặc ngày vào làm. */
        @NotNull(message = "Vui lòng nhập ngày bắt đầu") LocalDate startDate,

        /**
         * Ngày kết thúc generate — thường là cuối học kỳ hoặc ngày hết hợp đồng.
         * Service validate: endDate >= startDate và khoảng cách không quá 1 năm
         * (tránh generate quá nhiều dòng gây chậm).
         */
        @NotNull(message = "Vui lòng nhập ngày kết thúc") LocalDate endDate) {}
