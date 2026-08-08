package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bảng LỊCH DẠY CHI TIẾT của một lời mời phân công — giáo viên mở từ chuông thông báo.
 *
 * <p>Vì sao không dùng thẳng {@link AssignmentResponse}: bản đó là góc nhìn của ADMIN, có
 * ghi chú duyệt và lý do từ chối (chuyện nội bộ trung tâm). Ở đây chỉ trả đúng thứ giáo
 * viên cần để quyết định nhận hay không.
 *
 * <p>Dữ liệu đọc LIVE từ phân công chứ không phải chuỗi đã lưu trong thông báo — admin sửa
 * phiếu sau khi gửi thì giáo viên mở ra thấy bản mới nhất.
 *
 * @param classNames các lớp KHÁC NHAU của phiếu, theo thứ tự xuất hiện
 * @param slots mẫu lặp tuần (thứ + tiết + lớp), frontend dựng thành lưới thời khóa biểu
 */
public record AssignmentInviteDetail(
        Integer assignmentId,
        String schoolName,
        String subjectName,
        String teacherName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        LocalDateTime confirmDeadline,
        List<String> classNames,
        List<AssignmentSlotResponse> slots) {}
