package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.TeacherEvaluation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng TeacherEvaluation — list/filter qua Specification + vài query thống kê. */
public interface TeacherEvaluationRepository
        extends JpaRepository<TeacherEvaluation, Integer>, JpaSpecificationExecutor<TeacherEvaluation> {

    Optional<TeacherEvaluation> findByIdAndDeletedFalse(Integer id);

    List<TeacherEvaluation> findByTeacherIdAndDeletedFalseOrderByCreatedAtDesc(Integer teacherId);

    /** Dùng check trùng kỳ (normalize period ở tầng service). */
    List<TeacherEvaluation> findByTeacherIdAndEvaluatorUserIdAndDeletedFalse(
            Integer teacherId, Integer evaluatorUserId);

    /** Load đánh giá của nhiều GV (dùng cho dropdown / chưa chấm kỳ). */
    List<TeacherEvaluation> findByTeacherIdInAndDeletedFalse(java.util.Collection<Integer> teacherIds);

    @Query("SELECT e.score, COUNT(e) FROM TeacherEvaluation e WHERE e.deleted = false "
            + "AND (:teacherId IS NULL OR e.teacherId = :teacherId) "
            + "AND (:schoolId IS NULL OR e.schoolId = :schoolId) "
            + "AND (:centerOnly = false OR e.schoolId IS NULL) "
            + "AND (:schoolOnly = false OR e.schoolId IS NOT NULL) "
            + "AND e.score IS NOT NULL "
            + "GROUP BY e.score")
    List<Object[]> countByScoreGrouped(
            @Param("teacherId") Integer teacherId,
            @Param("schoolId") Integer schoolId,
            @Param("centerOnly") boolean centerOnly,
            @Param("schoolOnly") boolean schoolOnly);

    @Query("SELECT COUNT(DISTINCT e.teacherId) FROM TeacherEvaluation e WHERE e.deleted = false "
            + "AND (:teacherId IS NULL OR e.teacherId = :teacherId) "
            + "AND (:schoolId IS NULL OR e.schoolId = :schoolId) "
            + "AND (:centerOnly = false OR e.schoolId IS NULL) "
            + "AND (:schoolOnly = false OR e.schoolId IS NOT NULL)")
    long countDistinctTeachers(
            @Param("teacherId") Integer teacherId,
            @Param("schoolId") Integer schoolId,
            @Param("centerOnly") boolean centerOnly,
            @Param("schoolOnly") boolean schoolOnly);

    @Query("SELECT COUNT(e) FROM TeacherEvaluation e WHERE e.deleted = false "
            + "AND e.score >= 4 "
            + "AND (:teacherId IS NULL OR e.teacherId = :teacherId) "
            + "AND (:schoolId IS NULL OR e.schoolId = :schoolId) "
            + "AND (:centerOnly = false OR e.schoolId IS NULL) "
            + "AND (:schoolOnly = false OR e.schoolId IS NOT NULL)")
    long countHighScores(
            @Param("teacherId") Integer teacherId,
            @Param("schoolId") Integer schoolId,
            @Param("centerOnly") boolean centerOnly,
            @Param("schoolOnly") boolean schoolOnly);
}
