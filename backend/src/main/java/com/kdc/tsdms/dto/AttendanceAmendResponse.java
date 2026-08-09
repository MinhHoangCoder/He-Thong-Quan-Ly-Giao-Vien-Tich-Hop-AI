package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.AttendanceAmendRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO một yêu cầu bổ sung chấm công — kèm sẵn tên giáo viên và thông tin buổi dạy
 * (trường/lớp/môn/giờ) để admin duyệt được ngay mà không phải mở thêm màn hình nào.
 */
public class AttendanceAmendResponse {

    public Long id;
    public Integer teacherId;
    public String teacherName;
    public Long scheduleId;
    public String reason;
    public LocalTime proposedCheckIn;
    public LocalTime proposedCheckOut;

    /** PENDING | APPROVED | REJECTED */
    public String status;

    public String reviewedByName;
    public Instant reviewedAt;
    public String reviewNote;
    public Instant createdAt;

    /* ── Thông tin buổi dạy (ghép từ Schedule → Assignment), null nếu buổi đã bị xóa. ── */
    public LocalDate workDate;
    public LocalDateTime sessionStart;
    public LocalDateTime sessionEnd;
    public String schoolName;
    public String className;
    public String subjectName;

    public static AttendanceAmendResponse fromEntity(
            AttendanceAmendRequest r, String teacherName, String reviewedByName) {
        AttendanceAmendResponse d = new AttendanceAmendResponse();
        d.id = r.getId();
        d.teacherId = r.getTeacherId();
        d.teacherName = teacherName;
        d.scheduleId = r.getScheduleId();
        d.reason = r.getReason();
        d.proposedCheckIn = r.getProposedCheckIn();
        d.proposedCheckOut = r.getProposedCheckOut();
        d.status = r.getStatus();
        d.reviewedByName = reviewedByName;
        d.reviewedAt = r.getReviewedAt();
        d.reviewNote = r.getReviewNote();
        d.createdAt = r.getCreatedAt();
        return d;
    }
}
