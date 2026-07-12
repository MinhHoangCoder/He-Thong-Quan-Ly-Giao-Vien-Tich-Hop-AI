package com.kdc.tsdms.dto;

/** Tổng hợp đánh giá theo 1 giáo viên (điểm TB + phân bố + số lượt). */
public record TeacherEvaluationSummaryResponse(
        Integer teacherId,
        String teacherName,
        long count,
        Double averageScore,
        long score1,
        long score2,
        long score3,
        long score4,
        long score5) {}
