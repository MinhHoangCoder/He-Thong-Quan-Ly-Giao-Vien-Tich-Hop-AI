package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AssignmentSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentSlotRepository extends JpaRepository<AssignmentSlot, Integer> {

    /** Thời khóa biểu (các tiết/tuần) của một phân công. */
    List<AssignmentSlot> findByAssignmentIdAndDeletedFalse(Integer assignmentId);

    /** Mọi slot của phân công (kể cả đã xóa mềm) — cho khôi phục / hiển thị thùng rác. */
    List<AssignmentSlot> findByAssignmentId(Integer assignmentId);

    /** Phục vụ dò trùng lịch GV: cùng GV + thứ + tiết (Service còn so chồng khoảng kỳ). */
    List<AssignmentSlot> findByTeacherIdAndDayOfWeekAndPeriodIdAndDeletedFalse(
            Integer teacherId, String dayOfWeek, Integer periodId);

    /** Xóa CỨNG mọi slot của phân công — dùng khi xóa vĩnh viễn. */
    @Modifying
    @Query("DELETE FROM AssignmentSlot s WHERE s.assignmentId = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") Integer assignmentId);
}
