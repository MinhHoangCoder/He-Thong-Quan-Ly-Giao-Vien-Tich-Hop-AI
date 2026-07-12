package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Attendance;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Attendance (khóa BIGINT → Long). */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** Bảng chấm công theo khoảng ngày (mới nhất trước). */
    List<Attendance> findByWorkDateBetweenOrderByWorkDateDescIdDesc(LocalDate from, LocalDate to);

    /** Lọc thêm theo giáo viên. */
    List<Attendance> findByTeacherIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
            Integer teacherId, LocalDate from, LocalDate to);

    /** Tránh sinh trùng khi generate chấm công từ lịch dạy. */
    boolean existsByScheduleId(Long scheduleId);
}
