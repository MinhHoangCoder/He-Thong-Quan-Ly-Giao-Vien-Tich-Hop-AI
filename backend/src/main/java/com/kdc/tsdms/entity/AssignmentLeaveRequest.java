package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * ĐƠN XIN NGHỈ DẠY do giáo viên gửi (bảng AssignmentLeaveRequest, V39) — "tôi xin nghỉ phân
 * công này TỪ ngày…, vì…".
 *
 * <p>Đơn tự nó không hủy gì cả: admin duyệt thì service gọi đúng luồng hủy có ngày hiệu lực
 * ({@code AssignmentService.cancel}), nên hủy tay và duyệt đơn chạy qua cùng một đoạn mã và
 * không thể lệch nhau.
 *
 * <p>Bảng riêng chứ không thêm cột vào {@link Assignment}: một phiếu có thể bị xin nghỉ, bị
 * từ chối, rồi xin lại — nhét vào phiếu là lần sau ghi đè lần trước, mất đúng phần lịch sử
 * cần để đối chiếu.
 */
@Entity
@Table(name = "AssignmentLeaveRequest")
@Getter
@Setter
public class AssignmentLeaveRequest extends AuditableEntity {

    /** Chờ admin xử lý. */
    public static final String PENDING = "PENDING";

    /** Đã duyệt — phân công đã bị hủy từ {@link #effectiveDate}. */
    public static final String APPROVED = "APPROVED";

    /** Admin từ chối (kèm {@link #decisionNote}); phân công giữ nguyên. */
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "AssignmentId", nullable = false)
    private Integer assignmentId;

    /** = GV của phân công (lưu kèm để lọc "đơn của tôi" không phải join). */
    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    /** Xin nghỉ TỪ ngày này (ngày đầu tiên không dạy nữa). */
    @Column(name = "EffectiveDate", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "Reason", nullable = false)
    private String reason;

    /** PENDING | APPROVED | REJECTED. */
    @Column(name = "Status", nullable = false)
    private String status = PENDING;

    /** Ghi chú của admin khi duyệt, hoặc LÝ DO TỪ CHỐI (bắt buộc khi từ chối). */
    @Column(name = "DecisionNote")
    private String decisionNote;

    @Column(name = "DecidedByUserId")
    private Integer decidedByUserId;

    @Column(name = "DecidedAt")
    private Instant decidedAt;
}
