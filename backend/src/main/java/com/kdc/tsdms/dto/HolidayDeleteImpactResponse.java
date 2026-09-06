package com.kdc.tsdms.dto;

/**
 * Những gì một kỳ nghỉ đã để lại — hỏi trước khi xóa nó.
 *
 * <p>Vì sao không CHẶN xóa như Trường/Lớp: kỳ nghỉ gõ nhầm năm (2027 thành 2026) chính là
 * loại kỳ nghỉ để lại nhiều hậu quả nhất, mà cũng là loại cần xóa gấp nhất. Chặn theo dữ liệu
 * con ở đây là tự nhốt mình. Nên: kể đủ rồi để người dùng quyết.
 *
 * @param restorableSessions số buổi mang đúng {@code HolidayId} của kỳ nghỉ này — xóa kỳ nghỉ
 *     là trả chúng về APPROVED. Đếm theo HolidayId (V40) chứ không quét theo khoảng ngày như
 *     bản cũ: buổi admin hủy tay trong cùng khoảng KHÔNG được tính vào đây, vì chúng cũng sẽ
 *     không được trả lại.
 * @param leaveAttendances số dòng chấm công đang là NGHỈ PHÉP trong khoảng ngày đó — thứ xóa
 *     kỳ nghỉ KHÔNG hoàn lại được, phải sửa tay ở màn Chấm công
 * @param futureSessions số buổi CHƯA diễn ra còn nằm trong khoảng mà kỳ nghỉ chưa đụng tới —
 *     xóa kỳ nghỉ thì chúng chạy bình thường như cũ
 */
public record HolidayDeleteImpactResponse(long restorableSessions, long leaveAttendances, long futureSessions) {}
