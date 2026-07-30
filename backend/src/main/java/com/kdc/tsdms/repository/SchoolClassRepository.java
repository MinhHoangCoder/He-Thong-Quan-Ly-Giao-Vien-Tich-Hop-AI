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

    Optional<SchoolClass> findByIdAndDeletedTrue(Integer id);

    List<SchoolClass> findByDeletedTrueOrderByDeletedAtDesc();

    boolean existsBySchoolIdAndNameAndSchoolYearAndDeletedFalse(Integer schoolId, String name, String schoolYear);

    boolean existsBySchoolIdAndNameAndSchoolYearAndDeletedFalseAndIdNot(
            Integer schoolId, String name, String schoolYear, Integer id);

    List<SchoolClass> findBySchoolIdAndDeletedFalseAndStatusOrderByName(Integer schoolId, String status);

    /**
     * Keyword do service escape sẵn (escapeLike) với ký tự thoát '!': %, _, [ của
     * SQL Server LIKE là wildcard — không escape thì gõ '%' sẽ khớp tất cả.
     */
    @Query("""
            SELECT sc FROM SchoolClass sc
            WHERE sc.deleted = false
              AND (:schoolId IS NULL OR sc.schoolId = :schoolId)
              AND (:status IS NULL OR sc.status = :status)
              AND (:gradeLevel IS NULL OR sc.gradeLevel = :gradeLevel)
              AND (:keyword IS NULL
                   OR LOWER(sc.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
                   OR LOWER(sc.gradeLevel) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
                   OR LOWER(sc.schoolYear) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!')
            """)
    Page<SchoolClass> search(
            @Param("keyword") String keyword,
            @Param("schoolId") Integer schoolId,
            @Param("status") String status,
            @Param("gradeLevel") String gradeLevel,
            Pageable pageable);

    /** Dropdown lọc: các khối đang tồn tại (chưa xóa mềm). */
    @Query("""
            SELECT DISTINCT sc.gradeLevel FROM SchoolClass sc
            WHERE sc.deleted = false
              AND sc.gradeLevel IS NOT NULL
              AND sc.gradeLevel <> ''
            ORDER BY sc.gradeLevel
            """)
    List<String> findDistinctGradeLevels();
}
