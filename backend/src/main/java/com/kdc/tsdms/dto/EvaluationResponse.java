package com.kdc.tsdms.dto;

import java.time.Instant;
import java.util.List;

/**
 * Mọi DTO <b>trả về</b> của module đánh giá — gộp 1 file cho dễ theo dõi luồng.
 *
 * <ul>
 *   <li>{@code EvaluationResponse} — 1 phiếu (list / detail / create / update)</li>
 *   <li>{@link Stats} — KPI tổng quan</li>
 *   <li>{@link Summary} — tổng hợp theo 1 GV</li>
 *   <li>{@link TeacherOption} — option chọn GV (+ cờ đã chấm kỳ)</li>
 *   <li>{@link PeriodMeta} — preset + kỳ gợi ý</li>
 *   <li>{@link DuplicateCheck} — kiểm tra trùng kỳ</li>
 *   <li>{@link Unevaluated} — GV chưa đánh giá trong kỳ</li>
 * </ul>
 *
 * <p>INPUT nằm ở {@link EvaluationRequest}.
 */
public record EvaluationResponse(
        Integer id,
        Integer teacherId,
        String teacherName,
        Integer evaluatorUserId,
        String evaluatorName,
        Integer schoolId,
        String schoolName,
        /** CENTER = trung tâm chấm; SCHOOL = trường chấm. */
        String source,
        Short score,
        String comment,
        String periodNote,
        Instant createdAt,
        Instant updatedAt,
        boolean canEdit,
        boolean canDelete) {

    /** KPI tổng quan (đã scope theo quyền). */
    public record Stats(
            long totalCount,
            Double averageScore,
            long highScoreCount,
            long teacherCountEvaluated,
            long score1,
            long score2,
            long score3,
            long score4,
            long score5) {}

    /** Tổng hợp theo 1 giáo viên. */
    public record Summary(
            Integer teacherId,
            String teacherName,
            long count,
            Double averageScore,
            long score1,
            long score2,
            long score3,
            long score4,
            long score5) {}

    /**
     * Option chọn GV (dropdown / panel chưa chấm).
     *
     * @param evaluatedInPeriod đã có phiếu trong kỳ đang xét
     * @param evalsInPeriod số phiếu trong kỳ đó
     */
    public record TeacherOption(
            Integer id,
            String name,
            String status,
            long totalCount,
            Double averageScore,
            boolean evaluatedInPeriod,
            long evalsInPeriod) {}

    /** Preset kỳ + gợi ý theo tháng. */
    public record PeriodMeta(List<String> presets, String suggested) {}

    /** Kết quả check trùng kỳ (cùng GV + người chấm + kỳ). */
    public record DuplicateCheck(boolean duplicate, long count, String message) {}

    /** Báo cáo GV chưa đánh giá trong một kỳ. */
    public record Unevaluated(
            String periodNote,
            long totalTeachers,
            long evaluatedCount,
            long unevaluatedCount,
            List<TeacherOption> teachers) {}
}
