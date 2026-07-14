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
    public Short periodNumber;

    /** MORNING | AFTERNOON */
    public String sessionType;

    /** PENDING | APPROVED | REJECTED | CANCELLED */
    public String status;
}
