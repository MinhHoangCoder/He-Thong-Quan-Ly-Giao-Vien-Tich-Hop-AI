package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một cặp ô lịch ĐANG TRÙNG GIỜ của cùng một giáo viên (V16) — kết quả quét
 * {@code GET /assignments/conflicts}.
 *
 * <p>Cần quét lại dữ liệu cũ vì trước V16 luật chống trùng so theo {@code periodId}: hai
 * trường khác nhau có periodId khác nhau nên GV vẫn được xếp dạy hai nơi cùng một khung
 * giờ. Những cặp lọt lưới đó nằm im trong DB, chỉ lộ ra khi quét theo giờ thật.
 */
public class AssignmentConflict {

    public Integer teacherId;
    public String teacherName;

    /** MON | TUE | … — hai ô lịch trùng nhau luôn cùng một Thứ. */
    public String dayOfWeek;

    public String dayOfWeekLabel;

    /** Phần giai đoạn hai phân công cùng chạy (null ở {@code overlapEnd} = vô thời hạn). */
    public LocalDate overlapStart;

    public LocalDate overlapEnd;

    /** Khoảng giờ thật sự chồng lên nhau trong ngày, vd 07:00–07:35. */
    public LocalTime overlapFrom;

    public LocalTime overlapTo;

    public Side first;
    public Side second;

    /** Một phía của cặp trùng: ô lịch nào, thuộc phân công/trường/lớp nào. */
    public static class Side {
        public Integer assignmentId;
        public Integer slotId;
        public Integer schoolId;
        public String schoolName;
        public String className;
        public String subjectName;
        public Short periodNumber;
        public String sessionType;
        public LocalTime startTime;
        public LocalTime endTime;
    }
}
