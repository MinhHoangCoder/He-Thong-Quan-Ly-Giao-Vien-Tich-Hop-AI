package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.AssignmentLeaveRequest;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một đơn xin nghỉ kèm mô tả đầy đủ BUỔI bị xin nghỉ (giáo viên · trường · lớp · môn · thứ ·
 * tiết · giờ) để hộp thoại duyệt hiện thẳng, không phải gọi thêm API.
 *
 * <p>Vì sao gói cả buổi vào đây: người duyệt phải quyết trong vài giây và câu hỏi của họ luôn
 * là "ai, hôm nào, lớp nào, ai gánh tiết đó" — bắt màn hình đi hỏi tiếp lịch dạy rồi ghép tay
 * thì mỗi lần mở hộp thoại là ba lượt gọi mạng cho một quyết định.
 */
public class LeaveRequestResponse {

    public Integer id;
    public Integer assignmentId;
    public Integer teacherId;
    public String teacherName;
    public String schoolName;
    public String subjectName;

    /** Lớp của chính buổi xin nghỉ (một phiếu trải nhiều lớp từ V16 nên phải lấy theo buổi). */
    public String className;

    /** Ngày của buổi xin nghỉ. */
    public LocalDate leaveDate;

    /** Mô tả buổi để hiện một dòng: "Thứ 2, 08/09/2026 · Sáng · Tiết 3 (07:00–07:45)". */
    public String sessionText;

    /**
     * Buổi dạy tương ứng còn tìm thấy không. false = buổi đã bị xóa/hủy sau khi đơn được gửi —
     * hộp thoại duyệt phải nói rõ thay vì hiện một dòng trống rồi để người duyệt bấm vào khoảng
     * không.
     */
    public boolean sessionFound;

    public String reason;

    /** PENDING | APPROVED | REJECTED. */
    public String status;

    public String decisionNote;
    public Instant decidedAt;

    /** Thời điểm giáo viên gửi đơn — người duyệt cần biết đơn đã nằm chờ bao lâu. */
    public Instant createdAt;

    public static LeaveRequestResponse fromEntity(
            AssignmentLeaveRequest e,
            String teacherName,
            String schoolName,
            String subjectName,
            String className,
            String sessionText) {
        LeaveRequestResponse r = new LeaveRequestResponse();
        r.id = e.getId();
        r.assignmentId = e.getAssignmentId();
        r.teacherId = e.getTeacherId();
        r.teacherName = teacherName;
        r.schoolName = schoolName;
        r.subjectName = subjectName;
        r.className = className;
        r.leaveDate = e.getLeaveDate();
        r.sessionText = sessionText;
        r.sessionFound = sessionText != null;
        r.reason = e.getReason();
        r.status = e.getStatus();
        r.decisionNote = e.getDecisionNote();
        r.decidedAt = e.getDecidedAt();
        r.createdAt = e.getCreatedAt();
        return r;
    }
}
