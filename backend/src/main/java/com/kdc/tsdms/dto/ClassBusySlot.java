package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một ô lịch của LỚP đang bị chiếm (V27) — lưới xếp phân công dùng để tô xám sẵn những ô
 * "lớp này đã có giáo viên khác dạy".
 *
 * <p>Song sinh với {@link TeacherBusySlot}: cái kia trả lời "giáo viên có rảnh không", cái này
 * trả lời "lớp có trống không". Thiếu vế thứ hai thì form vẫn cho người dùng chọn thoải mái rồi
 * mới nhận 409 từ backend lúc bấm Lưu — người xếp lịch mất công dựng cả thời khóa biểu mới biết
 * mình chọn sai từ ô đầu tiên.
 */
public class ClassBusySlot {

    /** Lớp đang bị chiếm (→ SchoolClass). */
    public Integer classId;

    /** MON | TUE | WED | THU | FRI | SAT | SUN */
    public String dayOfWeek;

    // ── Khung giờ thật của tiết ─────────────────────────────────────────
    public Integer periodId;

    public Short periodNumber;

    public LocalTime startTime;
    public LocalTime endTime;

    // ── Ai đang chiếm (để hiện lý do cho người xếp lịch) ────────────────
    public Integer teacherId;
    public String teacherName;
    public String subjectName;

    /** Phiếu chứa ô lịch này — form bỏ qua chính phiếu đang sửa. */
    public Integer assignmentId;

    /** true = phiếu còn CHỜ giáo viên xác nhận (vẫn giữ chỗ, nhưng hết hạn thì nhả). */
    public boolean pending;

    // ── Giai đoạn của phân công chứa ô lịch này (client lọc chồng ngày) ─
    public LocalDate startDate;

    /** null = phân công vô thời hạn. */
    public LocalDate endDate;
}
