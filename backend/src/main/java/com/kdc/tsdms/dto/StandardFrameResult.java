package com.kdc.tsdms.dto;

/**
 * Kết quả áp khung tiết chuẩn cho một trường.
 *
 * @param level "Tiểu học" | "THCS" — cấp học đã suy ra, trả về để giao diện nói rõ hệ thống vừa
 *     áp bộ nào (người dùng cần biết để phát hiện nếu nó đoán sai cấp)
 * @param created số tiết đã tạo
 * @param periodMinutes độ dài một tiết của bộ vừa áp
 */
public record StandardFrameResult(Integer schoolId, String schoolName, String level, int created, int periodMinutes) {}
