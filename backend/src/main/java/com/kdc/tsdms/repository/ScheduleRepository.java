package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Schedule;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository bảng Schedule (khóa BIGINT → Long).
 * Lưu ý khi làm feature xếp lịch: kiểm tra TRÙNG LỊCH GV/PHÒNG bằng truy vấn khoảng
 * thời gian (đã có index IX_Schedule_Teacher_Time / IX_Schedule_Room_Time hỗ trợ).
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /** Các buổi sinh từ một phân công. */
    List<Schedule> findByAssignmentIdAndDeletedFalse(Integer assignmentId);

    /** Buổi dạy theo khoảng thời gian + trạng thái — nguồn để sinh chấm công. */
    List<Schedule> findByStartTimeBetweenAndStatusAndDeletedFalseOrderByStartTime(
            LocalDateTime from, LocalDateTime to, String status);

    /** Buổi dạy của MỘT giáo viên trong khoảng — dùng cho lịch dạy đã lọc theo GV. */
    List<Schedule> findByTeacherIdAndStatusAndStartTimeBetweenAndDeletedFalseOrderByStartTime(
            Integer teacherId, String status, LocalDateTime from, LocalDateTime to);

    /** Đếm buổi của phân công (thống kê). */
    long countByAssignmentIdAndDeletedFalse(Integer assignmentId);

    /** Mọi buổi (mọi trạng thái) trong khoảng — nguồn tính chỉ số dashboard (biểu đồ, giờ dạy...). */
    List<Schedule> findByStartTimeBetweenAndDeletedFalse(LocalDateTime from, LocalDateTime to);

    /** Đếm buổi theo trạng thái (vd: PENDING = buổi chờ duyệt). */
    long countByStatusAndDeletedFalse(String status);
}
