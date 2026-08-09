package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AttendanceAmendRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng AttendanceAmendRequest (khóa BIGINT → Long). */
public interface AttendanceAmendRequestRepository extends JpaRepository<AttendanceAmendRequest, Long> {

    /**
     * Yêu cầu đang chờ duyệt của một buổi dạy — chặn gửi trùng ở tầng service (hàng rào
     * cuối là index lọc UX_AttAmend_PendingSchedule).
     */
    Optional<AttendanceAmendRequest> findFirstByScheduleIdAndStatus(Long scheduleId, String status);

    /** Hộp chờ duyệt của admin. */
    List<AttendanceAmendRequest> findByStatusOrderByIdDesc(String status);

    /** Toàn bộ yêu cầu (mọi trạng thái) — admin xem lại lịch sử xử lý. */
    List<AttendanceAmendRequest> findByOrderByIdDesc();

    /** Danh sách yêu cầu của CHÍNH giáo viên đang đăng nhập. */
    List<AttendanceAmendRequest> findByTeacherIdOrderByIdDesc(Integer teacherId);
}
