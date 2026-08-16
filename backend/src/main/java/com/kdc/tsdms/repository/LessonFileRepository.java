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
     * Xóa vĩnh viễn mọi file đính kèm của các bài giảng truyền vào — dùng khi
     * hard-delete môn học (cascade xóa hẳn bài giảng con), phải dọn LessonFile
     * trước vì LessonFile.LessonId có khóa ngoại trỏ tới Lesson.
     */
    void deleteByLessonIdIn(List<Integer> lessonIds);
}
