package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.EmployeeSchedule;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Integer> {

    /** Lịch làm của một NV trong khoảng ngày (màn hình Timetable). */
    List<EmployeeSchedule> findByEmployeeIdAndWorkDateBetweenAndDeletedFalse(
            Integer employeeId, LocalDate from, LocalDate to);

    /**
     * Ca làm TƯƠNG LAI còn hiệu lực (SCHEDULED) của một nhân viên — chặn xóa người còn ca
     * phải làm. ON_LEAVE/CANCELLED không tính: không còn nghĩa vụ đứng ca.
     */
    long countByEmployeeIdAndWorkDateGreaterThanEqualAndStatusAndDeletedFalse(
            Integer employeeId, java.time.LocalDate from, String status);
}
