package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.AssignmentLeaveRequest;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một đơn xin nghỉ kèm tên GV / trường / môn để màn hình hiện thẳng, không phải gọi thêm API.
 */
public class LeaveRequestResponse {

    public Integer id;
    public Integer assignmentId;
    public Integer teacherId;
    public String teacherName;
    public String schoolName;
    public String subjectName;
    public LocalDate effectiveDate;
    public String reason;

    /** PENDING | APPROVED | REJECTED. */
    public String status;

    public String decisionNote;
    public Instant decidedAt;
    public Instant createdAt;

    public static LeaveRequestResponse fromEntity(
            AssignmentLeaveRequest e, String teacherName, String schoolName, String subjectName) {
        LeaveRequestResponse r = new LeaveRequestResponse();
        r.id = e.getId();
        r.assignmentId = e.getAssignmentId();
        r.teacherId = e.getTeacherId();
        r.teacherName = teacherName;
        r.schoolName = schoolName;
        r.subjectName = subjectName;
        r.effectiveDate = e.getEffectiveDate();
        r.reason = e.getReason();
        r.status = e.getStatus();
        r.decisionNote = e.getDecisionNote();
        r.decidedAt = e.getDecidedAt();
        r.createdAt = e.getCreatedAt();
        return r;
    }
}
