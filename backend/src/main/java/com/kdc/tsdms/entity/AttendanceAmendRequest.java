package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Yêu cầu BỔ SUNG chấm công (bảng AttendanceAmendRequest) — giáo viên lỡ buổi dạy, xin
 * admin ghi nhận công cho buổi đó.
 *
 * <p>Vì sao là bảng riêng chứ không phải một trạng thái của {@link Attendance}: dòng chấm
 * công là đầu vào tính tiền, thêm trạng thái "chờ duyệt" vào đó thì mọi truy vấn lương đều
 * phải nhớ loại trừ nó — quên một chỗ là trả tiền cho việc chưa ai xác nhận.
 *
 * <p>Giáo viên tự khai giờ vào/ra; duyệt xong service ghi nguyên giờ đó sang dòng chấm công.
 */
@Entity
@Table(name = "AttendanceAmendRequest")
@Getter
@Setter
public class AttendanceAmendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    /** Buổi dạy bị lỡ — bắt buộc, vì mọi luật kiểm tra thời gian đều đọc từ đây. */
    @Column(name = "ScheduleId", nullable = false)
    private Long scheduleId;

    /** Giáo viên giải trình vì sao quên chấm. */
    @Column(name = "Reason", nullable = false)
    private String reason;

    @Column(name = "ProposedCheckIn", nullable = false)
    private LocalTime proposedCheckIn;

    @Column(name = "ProposedCheckOut", nullable = false)
    private LocalTime proposedCheckOut;

    /** PENDING | APPROVED | REJECTED */
    @Column(name = "Status", nullable = false)
    private String status = "PENDING";

    /** Admin đã xử lý (→ AppUser). */
    @Column(name = "ReviewedByUserId")
    private Integer reviewedByUserId;

    @Column(name = "ReviewedAt")
    private Instant reviewedAt;

    /** Lý do từ chối — bắt buộc khi REJECTED, để giáo viên biết đường sửa mà gửi lại. */
    @Column(name = "ReviewNote")
    private String reviewNote;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private Instant createdAt;
}
