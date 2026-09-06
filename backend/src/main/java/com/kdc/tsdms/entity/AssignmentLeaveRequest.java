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
 * ĐƠN XIN NGHỈ DẠY do giáo viên gửi (bảng AssignmentLeaveRequest, V39) — "hôm ấy tôi xin
 * nghỉ BUỔI này, vì…".
 *
 * <p>Bảng riêng chứ không thêm cột vào {@link Assignment}: một phiếu có thể bị xin nghỉ, bị
 * từ chối, rồi xin lại — nhét vào phiếu là lần sau ghi đè lần trước, mất đúng phần lịch sử
 * cần để đối chiếu.
 *
 * <p><b>Phạm vi đơn đã thu hẹp so với V39.</b> Bản V39 hiểu đơn là "nghỉ TỪ ngày X trở đi" và
 * duyệt đơn thì gọi thẳng {@code AssignmentService.cancel} — tức là cả phân công dài hạn dừng
 * lại. Thực tế xin nghỉ của giáo viên gần như luôn là nghỉ MỘT BUỔI (ốm, việc gia đình, đi
 * họp) rồi tuần sau vẫn dạy lớp đó; dùng luồng hủy phân công cho việc này là lấy dao mổ trâu
 * cắt tiết gà — mất luôn phiếu, mất luôn khung giờ đã giữ, và bỏ hủy được thì cũng phải dò
 * lại trùng lịch. Nay duyệt đơn chỉ tắt đúng buổi dạy hôm đó
 * ({@code Schedule.Status = 'CANCELLED'}, {@code CancelKind = 'LEAVE'}) còn phân công GIỮ
 * NGUYÊN.
 */
@Entity
@Table(name = "AssignmentLeaveRequest")
@Getter
@Setter
public class AssignmentLeaveRequest extends AuditableEntity {

    /** Chờ admin xử lý. */
    public static final String PENDING = "PENDING";

    /** Đã duyệt — buổi dạy ngày {@link #leaveDate} đã chuyển sang nghỉ có phép. */
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

    /**
     * NGÀY CỦA BUỔI xin nghỉ.
     *
     * <p>Vẫn nằm ở cột {@code EffectiveDate} của V39 — cột đó khi ấy mang nghĩa "nghỉ từ ngày
     * này". Không đẻ thêm migration chỉ để đổi tên một cột: kiểu dữ liệu, ràng buộc NOT NULL và
     * mọi dòng dữ liệu đang có đều đúng nguyên, chỉ có PHẠM VI nghiệp vụ là hẹp lại. Đổi tên ở
     * tầng Java để người đọc mã không hiểu nhầm, giữ tên cột để không đụng vào DB.
     *
     * <p>Không có cột {@code ScheduleId}: buổi cần tắt được suy ra từ cặp (phân công, ngày) lúc
     * duyệt. Cặp đó đủ để chỉ đúng buổi vì một phân công hiếm khi có hai tiết cùng một ngày;
     * nếu có thì cả hai tiết hôm đó cùng nghỉ — người ốm không dạy tiết 1 rồi lại dạy tiết 3.
     */
    @Column(name = "EffectiveDate", nullable = false)
    private LocalDate leaveDate;

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
