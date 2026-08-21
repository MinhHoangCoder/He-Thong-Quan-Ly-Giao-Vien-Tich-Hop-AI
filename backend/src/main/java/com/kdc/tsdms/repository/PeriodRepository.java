package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Period;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeriodRepository extends JpaRepository<Period, Integer> {

    /** Khung tiết của một trường (đã sắp theo số tiết) — dựng thời khóa biểu. */
    List<Period> findBySchoolIdAndDeletedFalseOrderByPeriodNumber(Integer schoolId);

    /** Tra một tiết cụ thể của trường. */
    Optional<Period> findBySchoolIdAndPeriodNumberAndDeletedFalse(Integer schoolId, Short periodNumber);

    /** Số tiết của một BUỔI (MORNING|AFTERNOON) — dùng quy đổi "tiết thứ mấy trong buổi". */
    int countBySchoolIdAndSessionTypeAndDeletedFalse(Integer schoolId, String sessionType);

    /** Tiết còn sống theo id — dùng cho luồng xóa có kiểm soát ({@code PeriodService.delete}). */
    Optional<Period> findByIdAndDeletedFalse(Integer id);

    /** Số tiết của MỘT LOẠT trường trong một câu — dựng cột "Khung tiết" ở màn Quản lý trường mà
     * không phải đếm lẻ từng trường. */
    @Query("SELECT p.schoolId, COUNT(p) FROM Period p WHERE p.deleted = false"
            + " AND p.schoolId IN :schoolIds GROUP BY p.schoolId")
    List<Object[]> demTietTheoTruong(@Param("schoolIds") Collection<Integer> schoolIds);

    /** Xóa CỨNG khung tiết của một trường — chỉ gọi khi xóa vĩnh viễn trường khỏi thùng rác. */
    @Modifying
    @Query("DELETE FROM Period p WHERE p.schoolId = :schoolId")
    int xoaCungTheoTruong(@Param("schoolId") Integer schoolId);
}
