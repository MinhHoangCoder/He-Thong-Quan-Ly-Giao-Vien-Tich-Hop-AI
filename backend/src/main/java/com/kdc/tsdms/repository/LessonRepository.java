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
     * Tìm kiếm bài giảng có phân trang + lọc — lọc theo Category (từ bảng Subject),
     * GradeLevel (LIKE), Status, và keyword tiêu đề.
     *
     * - category : NULL/blank = tất cả danh mục; lọc qua subquery vào bảng Subject
     * - gradeLevel : NULL/blank = tất cả khối
     * - status : DRAFT | PUBLISHED | ARCHIVED, NULL/blank = tất cả
     * - keyword : tìm theo tiêu đề, không phân biệt hoa thường
     */
    @Query("""
                        SELECT l FROM Lesson l
                        WHERE l.deleted = false
                          AND (:category   IS NULL OR :category = '' OR l.subjectId IN
                               (SELECT s.id FROM Subject s WHERE s.category = :category AND s.deleted = false))
                          AND (:gradeLevel IS NULL OR :gradeLevel = ''
                               OR LOWER(l.gradeLevel) LIKE LOWER(CONCAT('%', :gradeLevel, '%')))
                          AND (:status     IS NULL OR :status = '' OR l.status = :status)
                          AND (:keyword    IS NULL OR :keyword = ''
                               OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
                        """)
    Page<Lesson> search(
            @Param("category") String category,
            @Param("gradeLevel") String gradeLevel,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
