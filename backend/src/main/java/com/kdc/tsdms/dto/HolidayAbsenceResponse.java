package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Các dòng chấm công VẮNG mà hệ thống tự ghi cho buổi rơi vào một kỳ nghỉ (GET
 * /api/v1/holidays/{id}/absences).
 *
 * <p>Đây là hậu quả ĐÃ XẢY RA của việc khai kỳ nghỉ muộn hơn lúc xếp lịch: buổi "ma" ngày lễ
 * đã qua, job khép sổ không thấy ai check-in nên ghi Vắng. Khác với {@link
 * HolidayImpactResponse} (buổi CHƯA diễn ra, hủy là xong) — những dòng này đã nằm trong hồ sơ
 * chuyên cần của giáo viên và phải sửa từng dòng.
 *
 * <p>Trả về DANH SÁCH CHI TIẾT chứ không chỉ con số: người duyệt phải nhìn được từng ngày,
 * từng giáo viên để bỏ tick dòng nào giáo viên thật sự có dạy bù rồi bỏ buổi.
 */
public record HolidayAbsenceResponse(
        /** Các dòng còn sửa được. */
        List<Row> rows,
        /**
         * Số dòng nằm trong kỳ lương ĐÃ CHỐT nên không sửa được — chỉ báo, không nằm trong
         * {@code rows}. Muốn sửa phải mở lại bảng lương trước (Flyway V32).
         */
        int lockedCount,
        /** Các kỳ lương đang khóa, dạng "8/2026" — để màn hình chỉ đúng chỗ cần mở lại. */
        List<String> lockedPeriods) {

    /** Một dòng chấm công Vắng chờ chuyển thành Nghỉ phép. */
    public record Row(
            Long attendanceId,
            Integer teacherId,
            String teacherName,
            LocalDate workDate,
            Long scheduleId,
            /** Ghi chú hệ thống để lại lúc ghi Vắng — cho người duyệt đối chiếu. */
            String note) {}
}
