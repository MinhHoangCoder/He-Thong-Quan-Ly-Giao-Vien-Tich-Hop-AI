package com.kdc.tsdms.dto;

import java.time.Instant;
import java.util.List;

/**
 * Mọi DTO <b>trả về</b> của module đánh giá — gộp 1 file cho dễ theo dõi luồng.
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
     * Option chọn GV — kèm trường đang/đã dạy + chi nhánh để phân biệt khi nhiều người.
     *
     * @param schoolNames tên các trường đã phân công (từ Assignment)
     * @param schoolsLabel chuỗi gộp hiển thị, vd "THCS A · TH B"
     */
    public record TeacherOption(
            Integer id,
            String name,
            String status,
            String phone,
            Integer branchId,
            String branchName,
            List<Integer> schoolIds,
            List<String> schoolNames,
            String schoolsLabel,
            long totalCount,
            Double averageScore,
            boolean evaluatedInPeriod,
            long evalsInPeriod) {}

    /** Trang kết quả tìm GV (phân trang server). */
    public record TeacherPage(List<TeacherOption> content, int page, int size, long totalElements, int totalPages) {}

    /** Dropdown lọc: trường / chi nhánh. */
    public record FilterMeta(List<IdName> schools, List<IdName> branches, boolean schoolScoped) {}

    public record IdName(Integer id, String name) {}

    /** Preset kỳ + gợi ý theo tháng. */
    public record PeriodMeta(List<String> presets, String suggested) {}

    /** Kết quả check trùng kỳ. */
    public record DuplicateCheck(boolean duplicate, long count, String message) {}

    /** Báo cáo GV chưa đánh giá trong một kỳ. */
    public record Unevaluated(
            String periodNote,
            long totalTeachers,
            long evaluatedCount,
            long unevaluatedCount,
            List<TeacherOption> teachers) {}
}
