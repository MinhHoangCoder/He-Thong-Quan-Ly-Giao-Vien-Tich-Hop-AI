package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.SchoolClass;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng SchoolClass — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Integer> {

    Optional<SchoolClass> findByIdAndDeletedFalse(Integer id);

    boolean existsBySchoolIdAndNameAndSchoolYearAndDeletedFalse(Integer schoolId, String name, String schoolYear);

    boolean existsBySchoolIdAndNameAndSchoolYearAndDeletedFalseAndIdNot(
            Integer schoolId, String name, String schoolYear, Integer id);

    List<SchoolClass> findBySchoolIdAndDeletedFalseAndStatusOrderByName(Integer schoolId, String status);

    @Query("""
            SELECT sc FROM SchoolClass sc
            WHERE sc.deleted = false
              AND (:schoolId IS NULL OR sc.schoolId = :schoolId)
              AND (:status IS NULL OR sc.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(sc.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(sc.gradeLevel) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(sc.schoolYear) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<SchoolClass> search(
            @Param("keyword") String keyword,
            @Param("schoolId") Integer schoolId,
            @Param("status") String status,
            Pageable pageable);
}
