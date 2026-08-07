package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một ô lịch GV ĐANG BẬN (V16) — dùng cho form phân công khóa sẵn các tiết không chọn được.
 *
 * <p>Khác với danh sách phân công: ở đây mỗi dòng đã kèm KHOẢNG GIỜ THẬT của tiết, nên
 * frontend so trực tiếp giờ với tiết đang định chọn — bắt được cả trùng giờ CHÉO TRƯỜNG
 * (tiết 1 của TH Dư Hàng 07:00–07:35 đè tiết 1 của THCS Vinh Quang 07:00–07:45), thứ mà
 * cách so theo {@code periodId} trước đây không thấy.
 */
public class TeacherBusySlot {

    public Integer assignmentId;

    /** MON | TUE | WED | THU | FRI | SAT | SUN */
    public String dayOfWeek;

    public String dayOfWeekLabel;

    // ── Khung giờ thật của tiết ─────────────────────────────────────────
    public Integer periodId;

    /** Số tiết trong NGÀY (1..n) của trường đó. */
    public Short periodNumber;

    /** Số tiết trong BUỔI (chiều đánh lại từ 1) — dùng để hiển thị lý do bận. */
    public Short indexInSession;

    /** MORNING | AFTERNOON */
    public String sessionType;

    public LocalTime startTime;
    public LocalTime endTime;

    // ── Bận ở đâu (để hiện lý do cho người xếp lịch) ────────────────────
    public Integer schoolId;
    public String schoolName;
    public String className;
    public String subjectName;

    // ── Giai đoạn của phân công chứa ô lịch này (client lọc chồng ngày) ─
    public LocalDate startDate;

    /** null = phân công vô thời hạn. */
    public LocalDate endDate;
}
