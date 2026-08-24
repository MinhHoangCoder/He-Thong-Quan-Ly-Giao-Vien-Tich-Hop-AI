package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.LessonFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng LessonFile. */
public interface LessonFileRepository extends JpaRepository<LessonFile, Integer> {

    @Query("""
            SELECT f FROM LessonFile f
            WHERE f.deleted = false AND f.lessonId = :lessonId
            ORDER BY f.createdAt ASC
            """)
    List<LessonFile> findByLessonId(@Param("lessonId") Integer lessonId);

    Optional<LessonFile> findByIdAndDeletedFalse(Integer id);

    /** File còn sống của một bài giảng — dùng để cascade xóa mềm theo bài giảng. */
    List<LessonFile> findByLessonIdAndDeletedFalse(Integer lessonId);

    /**
     * File đã xóa mềm của một bài giảng. Luồng khôi phục lọc tiếp theo {@code deletedAt} để
     * chỉ trả lại đúng những file BIẾN MẤT THEO bài giảng, không đụng tới file mà người dùng
     * đã cố ý xóa riêng trước đó (xem {@code LessonService.restore}).
     */
    List<LessonFile> findByLessonIdAndDeletedTrue(Integer lessonId);
}
