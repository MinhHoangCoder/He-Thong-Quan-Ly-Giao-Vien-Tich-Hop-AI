package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Hậu quả của một kỳ nghỉ lên lịch dạy ĐÃ SINH TRƯỚC ĐÓ.
 *
 * <p>Generator chỉ bỏ ngày nghỉ tại thời điểm sinh buổi. Khai báo một kỳ nghỉ MỚI vì thế
 * không tự dọn các buổi đã nằm sẵn trong lịch — và những buổi đó rất nguy hiểm: job khép sổ
 * chấm công sẽ ghi VẮNG cho giáo viên vào ngày trường đóng cửa, rồi trừ thẳng vào lương.
 *
 * <p>Vì vậy màn hình phải nói rõ có bao nhiêu buổi bị ảnh hưởng, để người dùng CHỦ ĐỘNG bấm
 * hủy — không tự hủy ngầm sau lưng họ.
 *
 * <p>Dùng cho HAI chỗ, cùng một hình dạng dữ liệu: xem trước lúc THÊM kỳ nghỉ (kỳ chưa có
 * trong DB) và dọn dẹp kỳ nghỉ ĐÃ CÓ. Hai DTO gần giống hệt nhau chỉ khiến người đọc phải
 * đối chiếu xem chúng khác nhau chỗ nào.
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
        int pastSessionCount,
        /**
         * Vài buổi ĐẦU TIÊN trong số sẽ bị hủy — để hộp xác nhận kể được tên người thật thay
         * vì chỉ ném ra một con số. "Ảnh hưởng 214 buổi" không nói lên điều gì, còn "thứ Năm
         * 10/09, cô Lan, THCS Ngô Quyền" thì đọc phát là biết mình có gõ nhầm năm hay không.
         */
        List<Session> samples) {

    /** Một dòng trong danh sách rút gọn. Chỉ những gì đủ để NHẬN RA buổi dạy, không hơn. */
    public record Session(LocalDate date, LocalTime startTime, String teacherName, String schoolName) {}
}
