package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Lesson;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Integer> {

    /**
     * Tìm theo id và chưa bị xóa mềm (dùng cho GET / UPDATE / DELETE).
     */
    Optional<Lesson> findByLessonIdAndIsDeletedFalse(Integer lessonId);

    /**
     * Tìm kiếm + lọc có phân trang.
     * Các tham số NULL sẽ được bỏ qua (IS NULL OR ...).
     */
    @Query("""
        SELECT l FROM Lesson l
        LEFT JOIN FETCH l.subject  s
        LEFT JOIN FETCH l.teacher  t
        LEFT JOIN FETCH l.branch   b
        WHERE l.isDeleted = false
          AND (:branchId        IS NULL OR l.branch.id        = :branchId)
          AND (:subjectId       IS NULL OR l.subject.subjectId      = :subjectId)
          AND (:teacherId       IS NULL OR l.teacher.id       = :teacherId)
          AND (:status          IS NULL OR l.status                  = :status)
          AND (:difficultyLevel IS NULL OR l.difficultyLevel         = :difficultyLevel)
          AND (:gradeLevel      IS NULL OR l.gradeLevel              = :gradeLevel)
          AND (:keyword         IS NULL
               OR LOWER(l.title)       LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(l.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Lesson> findWithFilters(
            @Param("branchId") Integer branchId,
            @Param("subjectId") Integer subjectId,
            @Param("teacherId") Integer teacherId,
            @Param("status") String status,
            @Param("difficultyLevel") String difficultyLevel,
            @Param("gradeLevel") String gradeLevel,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * Đếm nhanh số bài giảng theo trạng thái + chi nhánh (dashboard).
     */
    @Query("""
        SELECT l.status, COUNT(l)
        FROM Lesson l
        WHERE l.isDeleted = false
          AND (:branchId IS NULL OR l.branch.id = :branchId)
        GROUP BY l.status
        """)
    java.util.List<Object[]> countByStatusAndBranch(@Param("branchId") Integer branchId);
}
