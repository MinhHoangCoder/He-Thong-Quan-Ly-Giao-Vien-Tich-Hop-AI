package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Attendance;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng Attendance (khóa BIGINT → Long). */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** Bảng chấm công theo khoảng ngày (mới nhất trước). */
    List<Attendance> findByWorkDateBetweenOrderByWorkDateDescIdDesc(LocalDate from, LocalDate to);

    /** Lọc thêm theo giáo viên. */
    List<Attendance> findByTeacherIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
            Integer teacherId, LocalDate from, LocalDate to);

    /** Tránh sinh trùng khi generate chấm công từ lịch dạy. */
    boolean existsByScheduleId(Long scheduleId);

    /** Dòng chấm công của một buổi dạy (lấy dòng đầu — bảng không có unique constraint). */
    java.util.Optional<Attendance> findFirstByScheduleIdOrderByIdAsc(Long scheduleId);

    /**
     * Các dòng VẮNG do JOB NỀN tự ghi trong khoảng ngày ({@code checkInMethod = 'SYSTEM'}).
     *
     * <p>Lọc theo nguồn chứ không lấy mọi dòng Vắng là CỐ Ý: dòng kế toán ghi tay là một phán
     * quyết có người chịu trách nhiệm (giáo viên vẫn phải dạy bù hôm đó mà bỏ), còn dòng
     * SYSTEM chỉ là hệ quả máy móc của việc "hết buổi không ai check-in". Chỉ dòng thứ hai mới
     * có thể là nạn nhân của buổi "ma" ngày lễ.
     */
    @Query("""
            SELECT a FROM Attendance a
            WHERE a.status = 'ABSENT'
              AND a.checkInMethod = 'SYSTEM'
              AND a.workDate BETWEEN :from AND :to
            ORDER BY a.workDate ASC, a.teacherId ASC
            """)
    List<Attendance> findSystemAbsencesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
