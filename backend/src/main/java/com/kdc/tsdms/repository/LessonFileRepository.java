package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.LessonFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonFileRepository extends JpaRepository<LessonFile, Integer> {

    List<LessonFile> findByLesson_LessonIdAndIsDeletedFalse(Integer lessonId);

    /** Xóa mềm toàn bộ file của 1 bài giảng (khi bài giảng bị xóa) */
    List<LessonFile> findByLesson_LessonId(Integer lessonId);
}
