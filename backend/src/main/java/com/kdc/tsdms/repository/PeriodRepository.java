package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Period;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodRepository extends JpaRepository<Period, Integer> {

    /** Khung tiết của một trường (đã sắp theo số tiết) — dựng thời khóa biểu. */
    List<Period> findBySchoolIdAndDeletedFalseOrderByPeriodNumber(Integer schoolId);

    /** Tra một tiết cụ thể của trường. */
    Optional<Period> findBySchoolIdAndPeriodNumberAndDeletedFalse(Integer schoolId, Short periodNumber);
}
