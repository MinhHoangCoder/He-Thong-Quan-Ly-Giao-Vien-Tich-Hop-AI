package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Lesson;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng Lesson. */
public interface LessonRepository extends JpaRepository<Lesson, Integer> {

    Optional<Lesson> findByIdAndDeletedFalse(Integer id);

    /**
     * Tìm kiếm bài giảng có phân trang + lọc — lọc theo SubjectId (FK → Subject),
     * GradeLevel (LIKE), Status, và keyword tiêu đề.
     *
     *  - subjectId  : NULL = tất cả môn
     *  - gradeLevel : NULL/blank = tất cả khối
     *  - status     : DRAFT | PUBLISHED | ARCHIVED, NULL/blank = tất cả
     *  - keyword    : tìm theo tiêu đề, không phân biệt hoa thường
     */
    @Query("""
            SELECT l FROM Lesson l
            WHERE l.deleted = false
              AND (:subjectId  IS NULL OR l.subjectId = :subjectId)
              AND (:gradeLevel IS NULL OR :gradeLevel = ''
                   OR LOWER(l.gradeLevel) LIKE LOWER(CONCAT('%', :gradeLevel, '%')))
              AND (:status     IS NULL OR :status = '' OR l.status = :status)
              AND (:keyword    IS NULL OR :keyword = ''
                   OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Lesson> search(
            @Param("subjectId") Integer subjectId,
            @Param("gradeLevel") String gradeLevel,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
