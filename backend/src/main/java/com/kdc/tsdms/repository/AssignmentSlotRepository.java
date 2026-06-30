package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AssignmentSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentSlotRepository extends JpaRepository<AssignmentSlot, Integer> {

    /** Thời khóa biểu (các tiết/tuần) của một phân công. */
    List<AssignmentSlot> findByAssignmentIdAndDeletedFalse(Integer assignmentId);

    /** Phục vụ dò trùng lịch GV: cùng GV + thứ + tiết (Service còn so chồng khoảng kỳ). */
    List<AssignmentSlot> findByTeacherIdAndDayOfWeekAndPeriodIdAndDeletedFalse(
            Integer teacherId, String dayOfWeek, Integer periodId);
}
