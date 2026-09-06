package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Lịch dạy (bảng Schedule) — từng BUỔI dạy cụ thể sinh từ một Assignment, có quy trình
 * duyệt PENDING → APPROVED/REJECTED/CANCELLED.
 *
 * <p>KHÔNG extends AuditableEntity vì bảng này KHÔNG có cột CreatedBy (người tạo nằm ở
 * CreatedByUserId) — map tay từng cột audit còn lại.
 *
 * <p>Lưu ý trigger: DB có TR_Schedule_StatusLog tự ghi ScheduleStatusLog mỗi khi Status
 * đổi — service phải set {@code updatedBy} = id người thao tác TRƯỚC khi update.
 */
@Entity
@Table(name = "Schedule")
@Getter
@Setter
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id; // BIGINT — bảng sự kiện tích lũy nhanh

    @Column(name = "AssignmentId", nullable = false)
    private Integer assignmentId;

    /** Lưu kèm (trùng với GV của Assignment) để dò TRÙNG LỊCH nhanh bằng index. */
    @Column(name = "TeacherId", nullable = false)
    private Integer teacherId;

    @Column(name = "RoomId")
    private Integer roomId;

    /** Giờ bắt đầu buổi dạy — DATETIME2(0), giờ "treo tường" không múi giờ → LocalDateTime. */
    @Column(name = "StartTime", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "EndTime", nullable = false)
    private LocalDateTime endTime;

    /** PENDING | APPROVED | REJECTED | CANCELLED */
    @Column(name = "Status", nullable = false)
    private String status = "PENDING";

    /**
     * LÝ DO buổi bị hủy — LEAVE = nghỉ có phép | HOLIDAY = nghỉ lễ | NULL = admin hủy hành
     * chính (V40).
     *
     * <p>Tách khỏi {@code status} thay vì nới CK_Schedule_Status thêm giá trị mới: mọi câu
     * truy vấn đang lọc {@code Status <> 'CANCELLED'} để đếm buổi thật sẽ lặng lẽ đếm sai
     * nếu có giá trị thứ năm. Status trả lời "buổi còn hiệu lực không", cột này trả lời "vì
     * sao không" — hai câu hỏi khác nhau thì hai cột.
     */
    @Column(name = "CancelKind")
    private String cancelKind;

    /**
     * Kỳ nghỉ đã làm buổi này rơi (→ Holiday) — chỉ có giá trị khi {@code cancelKind =
     * 'HOLIDAY'} (V40).
     *
     * <p>Có cột này thì lúc xoá kỳ nghỉ mới trả lại được ĐÚNG những buổi kỳ nghỉ đó đã đụng
     * vào; quét mù theo khoảng ngày sẽ trả nhầm cả buổi admin hủy tay trong cùng khoảng.
     */
    @Column(name = "HolidayId")
    private Integer holidayId;

    /** MANUAL = nhập tay | AI = do AI xếp. */
    @Column(name = "Source", nullable = false)
    private String source = "MANUAL";

    /** Tiết học của buổi (→ Period) — buổi sinh từ AssignmentSlot mang theo "tiết mấy" (V9). */
    @Column(name = "PeriodId")
    private Integer periodId;

    /** Slot gốc đã sinh ra buổi này (→ AssignmentSlot) — để sinh lại / hủy khi slot đổi (V9). */
    @Column(name = "SourceSlotId")
    private Integer sourceSlotId;

    /** Người tạo lịch (→ AppUser). */
    @Column(name = "CreatedByUserId")
    private Integer createdByUserId;

    /** Người duyệt/từ chối (→ AppUser). */
    @Column(name = "ApprovedByUserId")
    private Integer approvedByUserId;

    @Column(name = "ApprovedAt")
    private Instant approvedAt;

    @Column(name = "RejectionReason")
    private String rejectionReason;

    @Column(name = "IsDeleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "DeletedAt")
    private Instant deletedAt;

    @Column(name = "DeletedBy")
    private Integer deletedBy;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UpdatedAt")
    private Instant updatedAt;

    /** Trigger đọc cột này để biết AI đổi trạng thái → set trước khi update Status. */
    @Column(name = "UpdatedBy")
    private Integer updatedBy;
}
