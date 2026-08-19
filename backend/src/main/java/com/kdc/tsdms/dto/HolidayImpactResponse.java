package com.kdc.tsdms.dto;

import java.time.LocalDate;

/**
 * Hậu quả của một kỳ nghỉ lên lịch dạy ĐÃ SINH TRƯỚC ĐÓ.
 *
 * <p>Generator chỉ bỏ ngày nghỉ tại thời điểm sinh buổi. Khai báo một kỳ nghỉ MỚI vì thế
 * không tự dọn các buổi đã nằm sẵn trong lịch — và những buổi đó rất nguy hiểm: job khép sổ
 * chấm công sẽ ghi VẮNG cho giáo viên vào ngày trường đóng cửa, rồi trừ thẳng vào lương.
 *
 * <p>Vì vậy màn hình phải nói rõ có bao nhiêu buổi bị ảnh hưởng, để người dùng CHỦ ĐỘNG bấm
 * hủy — không tự hủy ngầm sau lưng họ.
 */
public record HolidayImpactResponse(
        /** Số buổi dạy CHƯA diễn ra đang rơi vào kỳ nghỉ này. */
        int sessionCount,
        /** Số giáo viên bị ảnh hưởng. */
        int teacherCount,
        LocalDate firstDate,
        LocalDate lastDate,
        /**
         * Số buổi rơi vào kỳ nghỉ nhưng ĐÃ diễn ra — chỉ để báo cho biết, KHÔNG hủy: chúng có
         * thể đã gắn chấm công và đã vào bảng lương, hủy đi là làm sai số tiền đã trả.
         */
        int pastSessionCount) {}
