package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.util.List;

import com.kdc.tsdms.entity.Assignment;

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

    /** ACTIVE | COMPLETED | CANCELLED */
    public String status;

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

        AssignmentResponse r = new AssignmentResponse();
        r.id          = a.getId();
        r.teacherId   = a.getTeacherId();
        r.teacherName = teacherName;
        r.schoolId    = a.getSchoolId();
        r.schoolName  = schoolName;
        r.subjectId   = a.getSubjectId();
        r.subjectName = subjectName;
        r.classId     = a.getClassId();
        r.className   = className;
        r.startDate   = a.getStartDate();
        r.endDate     = a.getEndDate();
        r.status      = a.getStatus();
        r.slots       = slots;
        return r;
    }
}