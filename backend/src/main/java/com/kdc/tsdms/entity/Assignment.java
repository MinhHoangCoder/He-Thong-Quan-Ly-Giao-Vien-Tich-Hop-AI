package com.kdc.tsdms.entity;

import com.kdc.tsdms.common.BusinessTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Phân công (bảng Assignment) — nhân viên TRUNG TÂM gán GV ↔ trường ↔ môn ↔ lớp
 * trong
 * một giai đoạn. Đây là điểm BẮT ĐẦU của luồng điều phối (không có "yêu cầu từ
 * trường");
 * từ một Assignment sinh ra nhiều Schedule (buổi dạy cụ thể).
 */
@Entity
@Table(name = "Assignment")
@Getter
@Setter
public class Assignment extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    @Column(name = "SchoolId", nullable = false)
    private Integer schoolId;

    @Column(name = "SubjectId", nullable = false)
    private Integer subjectId;

    /** Lớp cụ thể (nullable — có thể phân công cấp trường, chưa gắn lớp). */
    @Column(name = "ClassId")
    private Integer classId;

    /** Nhân viên nào phân công (→ Employee). */
    @Column(name = "AssignedByEmployeeId")
    private Integer assignedByEmployeeId;

    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;

    @Column(name = "EndDate")
    private LocalDate endDate;

    /** PENDING | ACTIVE | REJECTED | EXPIRED | COMPLETED | CANCELLED (xem AssignmentStatus). */
    @Column(name = "Status", nullable = false)
    private String status = AssignmentStatus.PENDING;

    /**
     * Hạn giáo viên phải trả lời (V17). Quá hạn mà chưa xác nhận → phiếu hết hiệu lực.
     * LocalDateTime cho khớp {@link Schedule#getStartTime()} — cùng quy ước "giờ treo tường".
     */
    @Column(name = "ConfirmDeadline")
    private LocalDateTime confirmDeadline;

    @Column(name = "ConfirmedAt")
    private LocalDateTime confirmedAt;

    /** Tài khoản bấm duyệt (→ AppUser): giáo viên tự xác nhận hoặc admin ép duyệt. */
    @Column(name = "ConfirmedByUserId")
    private Integer confirmedByUserId;

    /** TEACHER = GV tự xác nhận | ADMIN = admin ép duyệt thay. */
    @Column(name = "ConfirmSource")
    private String confirmSource;

    /** Lý do giáo viên từ chối (hiện trên dòng phân công để admin xếp lại người khác). */
    @Column(name = "RejectionReason")
    private String rejectionReason;

    /** Ghi chú tùy chọn khi admin ép duyệt. */
    @Column(name = "ApprovalNote")
    private String approvalNote;

    /* ── Dấu vết HỦY có ngày hiệu lực (V39) ── */

    /**
     * Ngày ĐẦU TIÊN giáo viên không dạy nữa. Buổi trước ngày này giữ nguyên (bằng chứng chấm
     * công/lương), buổi từ ngày này trở đi bị hủy. null = phiếu chưa từng bị hủy.
     */
    @Column(name = "CancelEffectiveDate")
    private LocalDate cancelEffectiveDate;

    /**
     * {@link #endDate} TRƯỚC khi bị thu hẹp về {@code cancelEffectiveDate - 1}.
     *
     * <p>Phải thu hẹp EndDate vì chính cặp StartDate/EndDate là thứ luật chống trùng lịch dùng
     * để nói "khung Thứ+Tiết này đã có người" — để nguyên mốc cũ thì hủy xong khung giờ vẫn bị
     * coi là đang bị chiếm và admin không xếp được giáo viên thay vào lớp đó. Cột này giữ mốc
     * gốc để "Bỏ hủy" biết trả EndDate về đâu.
     */
    @Column(name = "OriginalEndDate")
    private LocalDate originalEndDate;

    /** Lý do hủy — bắt buộc nhập, hiện nguyên văn trong thông báo gửi giáo viên. */
    @Column(name = "CancelReason")
    private String cancelReason;

    @Column(name = "CancelledAt")
    private Instant cancelledAt;

    /** Tài khoản bấm hủy (→ AppUser). */
    @Column(name = "CancelledByUserId")
    private Integer cancelledByUserId;

    /**
     * Phiếu đang chờ mà ĐÃ quá hạn — tính tại chỗ, không đợi tác vụ nền chạy. Nhờ vậy dù
     * backend vừa khởi động lại hay job lỡ nhịp thì màn hình và luật giữ chỗ vẫn đúng.
     */
    public boolean isExpiredPending() {
        return AssignmentStatus.PENDING.equals(status)
                && confirmDeadline != null
                && confirmDeadline.isBefore(BusinessTime.now());
    }

    /**
     * Phiếu có CHIẾM khung giờ của giáo viên không. Phiếu chờ xác nhận vẫn giữ chỗ (quyết
     * định nghiệp vụ) để không gửi hai lời mời đè giờ cho cùng một người rồi cả hai cùng
     * được xác nhận — nhưng chờ QUÁ HẠN thì nhả chỗ ra.
     */
    public boolean holdsTimeSlot() {
        return AssignmentStatus.ACTIVE.equals(status)
                || (AssignmentStatus.PENDING.equals(status) && !isExpiredPending());
    }

    /**
     * Phiếu bị KẾT THÚC SỚM: còn buổi phải dạy tới trước ngày hiệu lực nên trạng thái dưới DB
     * vẫn là ACTIVE, nhưng đã có mốc dừng. Màn hình hiện nhãn riêng cho loại này thay vì "Đang
     * dạy" — người xếp lịch phải thấy ngay là lớp sắp trống người.
     */
    public boolean isTerminated() {
        return cancelEffectiveDate != null && AssignmentStatus.ACTIVE.equals(status);
    }
}
