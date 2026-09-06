package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một buổi dạy trên LỊCH DẠY — đã ghép sẵn tên GV/trường/lớp/môn/tiết để frontend vẽ
 * lịch tháng/tuần mà không cần gọi thêm API. Chỉ đọc (read-only view).
 */
public class ScheduleEventResponse {

    public Long id;
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;

    /** MON..SUN — tiện cho lưới thời khóa biểu tuần. */
    public String dayOfWeek;

    public Integer teacherId;
    public String teacherName;
    public Integer schoolId;
    public String schoolName;
    public Integer classId;
    public String className;
    public Integer subjectId;
    public String subjectName;
    public Integer periodId;

    /** Số tiết trong NGÀY (1..n) của trường đó. */
    public Short periodNumber;

    /** Số tiết trong BUỔI (chiều đánh lại từ 1) — nhãn "Chiều · Tiết 5" lấy từ đây. */
    public Short indexInSession;

    /** MORNING | AFTERNOON */
    public String sessionType;

    /** PENDING | APPROVED | REJECTED | CANCELLED */
    public String status;

    /**
     * Nhãn tiếng Việt của trạng thái, TÍNH Ở SERVICE và không lưu trong DB.
     *
     * <p>Ba buổi cùng mang chữ CANCELLED có ba nghĩa khác nhau — "Nghỉ có phép", "Nghỉ lễ",
     * "Đã hủy" — phân biệt bằng {@code Schedule.CancelKind} (V40). Ghép nhãn ở đây chứ không
     * để frontend tự suy: luật ghép là nghiệp vụ (dòng nào kế toán được trừ lương), mà nghiệp
     * vụ nằm rải ở hai màn hình thì hai màn hình sẽ lệch nhau.
     */
    public String statusLabel;
}
