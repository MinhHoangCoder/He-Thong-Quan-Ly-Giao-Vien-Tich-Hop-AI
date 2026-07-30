package com.kdc.tsdms.entity;

/**
 * Các giá trị {@code Assignment.Status} (khớp CK_Assignment_Status từ V17).
 *
 * <p>Vòng đời: phiếu sinh ra ở {@link #PENDING}; giáo viên xác nhận (hoặc admin ép duyệt)
 * → {@link #ACTIVE} và lúc đó buổi dạy mới được duyệt, mới chảy sang lịch giáo viên /
 * chấm công / lương / thống kê. Giáo viên từ chối → {@link #REJECTED}; hết hạn trả lời →
 * {@link #EXPIRED}. Cả hai nhánh này admin sửa lại rồi gửi lại thì quay về {@link #PENDING}.
 *
 * <p>Dùng hằng chuỗi (không phải enum) cho khớp cách toàn bộ code hiện có so sánh Status.
 */
public final class AssignmentStatus {

    /** Chờ giáo viên xác nhận — buổi dạy CHƯA có hiệu lực. */
    public static final String PENDING = "PENDING";

    /** Đang dạy — đã được xác nhận, buổi dạy đã duyệt. */
    public static final String ACTIVE = "ACTIVE";

    /** Giáo viên từ chối (kèm lý do). */
    public static final String REJECTED = "REJECTED";

    /** Quá hạn trả lời → coi như giáo viên không xác nhận. */
    public static final String EXPIRED = "EXPIRED";

    public static final String COMPLETED = "COMPLETED";

    /** Admin chủ động hủy (khác REJECTED là do giáo viên từ chối). */
    public static final String CANCELLED = "CANCELLED";

    /** Các trạng thái mà admin còn được sửa & gửi lại lời mời. */
    public static boolean isEditable(String status) {
        return PENDING.equals(status) || REJECTED.equals(status) || EXPIRED.equals(status);
    }

    private AssignmentStatus() {}
}
