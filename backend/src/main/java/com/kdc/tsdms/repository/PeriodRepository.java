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

    /** Số tiết của một BUỔI (MORNING|AFTERNOON) — dùng quy đổi "tiết thứ mấy trong buổi". */
    int countBySchoolIdAndSessionTypeAndDeletedFalse(Integer schoolId, String sessionType);

    /** Tiết còn sống theo id — dùng cho luồng xóa có kiểm soát ({@code PeriodService.delete}). */
    Optional<Period> findByIdAndDeletedFalse(Integer id);
}
