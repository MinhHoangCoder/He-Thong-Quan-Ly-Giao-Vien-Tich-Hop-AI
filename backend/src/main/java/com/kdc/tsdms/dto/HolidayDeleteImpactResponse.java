package com.kdc.tsdms.dto;

/**
 * Những gì một kỳ nghỉ đã để lại — hỏi trước khi xóa nó.
 *
 * <p>Vì sao không CHẶN xóa như Trường/Lớp: kỳ nghỉ gõ nhầm năm (2027 thành 2026) chính là
 * loại kỳ nghỉ để lại nhiều hậu quả nhất, mà cũng là loại cần xóa gấp nhất. Chặn theo dữ liệu
 * con ở đây là tự nhốt mình. Nên: kể đủ rồi để người dùng quyết.
 *
 * @param cancelledSessions số buổi dạy ĐÃ HỦY nằm trong khoảng ngày của kỳ nghỉ (ước lượng —
 *     buổi bị hủy không lưu lại nó bị hủy vì kỳ nghỉ nào)
 * @param leaveAttendances số dòng chấm công đang là NGHỈ PHÉP trong khoảng ngày đó
 * @param futureSessions số buổi CHƯA diễn ra còn nằm trong khoảng — xóa kỳ nghỉ thì chúng
 *     chạy lại bình thường
 */
public record HolidayDeleteImpactResponse(long cancelledSessions, long leaveAttendances, long futureSessions) {}
