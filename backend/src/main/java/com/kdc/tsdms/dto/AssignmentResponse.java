package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.Assignment;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO response chi tiết 1 phân công giáo viên — bao gồm thông tin tên của
 * Teacher/School/Subject/Class (để frontend hiển thị trực tiếp mà không cần
 * gọi thêm API tra thông tin), kèm danh sách slot Thứ+Tiết.
 *
 * <p>Pattern: Service load Assignment + các entity liên quan, rồi gọi
 * {@link #fromEntity(Assignment, String, String, String, String, List)} để build response.
 * Giữ đúng pattern {@code fromEntity()} của dự án (vd: SubjectCategoryResponse).
 */
public class AssignmentResponse {

    public Integer id;

    // ── Giáo viên ────────────────────────────────────────────────────────
    public Integer teacherId;
    /** Họ tên đầy đủ: lastName + " " + firstName (đúng convention project). */
    public String teacherName;

    // ── Trường ───────────────────────────────────────────────────────────
    public Integer schoolId;
    public String schoolName;

    // ── Môn học ──────────────────────────────────────────────────────────
    public Integer subjectId;
    public String subjectName;

    // ── Lớp học ──────────────────────────────────────────────────────────
    public Integer classId;
    public String className;

    // ── Giai đoạn phân công ──────────────────────────────────────────────
    public LocalDate startDate;
    public LocalDate endDate;

    /** PENDING | ACTIVE | REJECTED | EXPIRED | COMPLETED | CANCELLED | TERMINATED (kết thúc sớm). */
    public String status;

    // ── Dấu vết hủy (V39) ────────────────────────────────────────────────
    /** Ngày đầu tiên GV không dạy nữa; null = phiếu chưa từng bị hủy. */
    public LocalDate cancelEffectiveDate;

    /** Ngày kết thúc GỐC trước khi bị thu hẹp — để màn hình nói "đáng lẽ đến …". */
    public LocalDate originalEndDate;

    public String cancelReason;

    public Instant cancelledAt;

    // ── Luồng xác nhận của giáo viên (V17) ───────────────────────────────
    /** Hạn giáo viên phải trả lời; null với phiếu cũ tạo trước khi có luồng xác nhận. */
    public LocalDateTime confirmDeadline;

    public LocalDateTime confirmedAt;

    /** TEACHER = giáo viên tự xác nhận | ADMIN = admin ép duyệt thay. */
    public String confirmSource;

    /** Lý do giáo viên từ chối (chỉ có khi status = REJECTED). */
    public String rejectionReason;

    /** Ghi chú admin nhập khi ép duyệt. */
    public String approvalNote;

    /**
     * Danh sách slot Thứ+Tiết của phân công này.
     * Mỗi slot chứa đủ thông tin tiết học (PeriodNumber, StartTime, EndTime)
     * để frontend vẽ timetable mà không cần gọi thêm API.
     */
    public List<AssignmentSlotResponse> slots;

    /**
     * Factory method build response từ entity Assignment + các tên liên quan +
     * danh sách slots đã build sẵn.
     *
     * @param a            entity Assignment
     * @param teacherName  họ tên GV (lastName + " " + firstName)
     * @param schoolName   tên trường
     * @param subjectName  tên môn học
     * @param className    tên lớp (null nếu Assignment không gắn lớp)
     * @param slots        danh sách AssignmentSlotResponse đã build từ AssignmentSlot + Period
     */
    public static AssignmentResponse fromEntity(
            Assignment a,
            String teacherName,
            String schoolName,
            String subjectName,
            String className,
            List<AssignmentSlotResponse> slots) {
        return fromEntity(a, teacherName, schoolName, subjectName, className, slots, a.getStatus());
    }

    /**
     * @param status trạng thái HIỂN THỊ — service truyền vào để phiếu chờ đã quá hạn hiện ngay là
     *     EXPIRED, không phải đợi tác vụ nền ghi lại DB
     */
    public static AssignmentResponse fromEntity(
            Assignment a,
            String teacherName,
            String schoolName,
            String subjectName,
            String className,
            List<AssignmentSlotResponse> slots,
            String status) {

        AssignmentResponse r = new AssignmentResponse();
        r.id = a.getId();
        r.teacherId = a.getTeacherId();
        r.teacherName = teacherName;
        r.schoolId = a.getSchoolId();
        r.schoolName = schoolName;
        r.subjectId = a.getSubjectId();
        r.subjectName = subjectName;
        r.classId = a.getClassId();
        r.className = className;
        r.startDate = a.getStartDate();
        r.endDate = a.getEndDate();
        r.status = status;
        r.confirmDeadline = a.getConfirmDeadline();
        r.confirmedAt = a.getConfirmedAt();
        r.confirmSource = a.getConfirmSource();
        r.rejectionReason = a.getRejectionReason();
        r.approvalNote = a.getApprovalNote();
        r.cancelEffectiveDate = a.getCancelEffectiveDate();
        r.originalEndDate = a.getOriginalEndDate();
        r.cancelReason = a.getCancelReason();
        r.cancelledAt = a.getCancelledAt();
        r.slots = slots;
        return r;
    }
}
