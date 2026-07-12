package com.kdc.tsdms.dto;

/** KPI tổng quan module đánh giá (đã scope theo quyền người xem). */
public record EvaluationStatsResponse(
        long totalCount,
        /** null nếu chưa có đánh giá nào có điểm. */
        Double averageScore,
        /** Số lượt điểm ≥ 4. */
        long highScoreCount,
        /** Số GV đã được đánh giá ít nhất 1 lần (trong phạm vi filter). */
        long teacherCountEvaluated,
        long score1,
        long score2,
        long score3,
        long score4,
        long score5) {}
