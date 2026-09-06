package com.kdc.tsdms.dto;

import java.time.LocalDate;

/**
 * Một BUỔI DẠY SẮP TỚI của giáo viên — nguồn cho ô chọn "xin nghỉ buổi nào".
 *
 * <p>Vì sao cho chọn từ danh sách buổi có thật thay vì gõ ngày tự do: đơn phải trỏ đúng một
 * buổi trong lịch thì duyệt xong mới có cái để tắt. Cho gõ ngày thì phần lớn đơn rơi vào ngày
 * giáo viên vốn không có tiết ở phân công ấy, và người gửi chỉ biết mình gõ sai sau khi bấm Gửi.
 *
 * <p>{@code key} = "assignmentId|ngày" — một khoá gộp để ô {@code <select>} có một giá trị duy
 * nhất, vì bản thân đơn được nhận diện bằng cặp đó chứ không phải bằng id buổi dạy.
 */
public record LeaveRequestSessionOption(
        String key,
        Integer assignmentId,
        LocalDate date,
        /** "Thứ 2, 08/09/2026 · Sáng · Tiết 3 (07:00–07:45)". */
        String sessionText,
        String schoolName,
        String className,
        String subjectName,
        /**
         * Phân công của buổi này đã có đơn đang chờ chưa. Index lọc
         * {@code UX_AssignmentLeaveRequest_Pending} chỉ cho MỘT đơn chờ trên mỗi phân công, nên ô
         * chọn khoá sẵn các buổi đó kèm lý do thay vì để người dùng bấm Gửi rồi mới nhận lỗi 409.
         */
        boolean pending) {}
