package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Payroll;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Payroll — mỗi GV mỗi tháng đúng 1 dòng (UNIQUE Teacher+Year+Month). */
public interface PayrollRepository extends JpaRepository<Payroll, Integer> {

    /** Bảng lương của một kỳ (tháng/năm). */
    List<Payroll> findByPeriodYearAndPeriodMonthOrderByTeacherId(Short periodYear, Short periodMonth);

    /** Dòng lương của một GV trong một kỳ (upsert khi generate). */
    Optional<Payroll> findByTeacherIdAndPeriodYearAndPeriodMonth(
            Integer teacherId, Short periodYear, Short periodMonth);
}
