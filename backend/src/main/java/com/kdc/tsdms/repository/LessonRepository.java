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

    /** Dùng để chặn xóa Subject đang được bài giảng nào đó tham chiếu. */
    long countBySubjectIdAndDeletedFalse(Integer subjectId);

    /**
     * Tìm kiếm bài giảng có phân trang + lọc — lọc theo Category (TÊN nhóm từ
     * SubjectCategory, qua bảng Subject), GradeLevel (LIKE), Status, và keyword
     * tiêu đề.
     *
     * - category : NULL/blank = tất cả danh mục; lọc qua subquery: môn thuộc nhóm
     * có tên = :category
     * - gradeLevel : NULL/blank = tất cả khối
     * - status : DRAFT | PUBLISHED | ARCHIVED, NULL/blank = tất cả
     * - keyword : tìm theo tiêu đề HOẶC tên môn học, không phân biệt hoa thường
     *             (khớp tên môn để link "Bài giảng" từ Lịch dạy lọc đúng theo môn của buổi).
     */
    @Query("""
               SELECT l FROM Lesson l
               WHERE l.deleted = false
                 AND (:category   IS NULL OR :category = '' OR l.subjectId IN
                      (SELECT s.id FROM Subject s WHERE s.category.name = :category AND s.deleted = false))
                 AND (:gradeLevel IS NULL OR :gradeLevel = ''
                      OR LOWER(l.gradeLevel) LIKE LOWER(CONCAT('%', :gradeLevel, '%')))
                 AND (:status     IS NULL OR :status = '' OR l.status = :status)
                 AND (:keyword    IS NULL OR :keyword = ''
                      OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR l.subjectId IN
                      (SELECT s2.id FROM Subject s2 WHERE s2.deleted = false
                       AND LOWER(s2.name) LIKE LOWER(CONCAT('%', :keyword, '%'))))
               """)
    Page<Lesson> search(
            @Param("category") String category,
            @Param("gradeLevel") String gradeLevel,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
