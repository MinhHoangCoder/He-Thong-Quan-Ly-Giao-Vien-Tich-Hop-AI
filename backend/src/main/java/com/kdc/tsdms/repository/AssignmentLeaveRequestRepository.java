package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AssignmentLeaveRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn ĐƠN XIN NGHỈ DẠY (V39). */
public interface AssignmentLeaveRequestRepository extends JpaRepository<AssignmentLeaveRequest, Integer> {

    /** "Đơn của tôi" — màn giáo viên, mới nhất lên đầu. */
    List<AssignmentLeaveRequest> findByTeacherIdOrderByIdDesc(Integer teacherId);

    /** Hàng đợi của admin: các đơn đang chờ xử lý. */
    List<AssignmentLeaveRequest> findByStatusOrderByIdDesc(String status);

    /**
     * Đơn đang chờ của một phân công — chặn gửi trùng ngay ở tầng service để người dùng nhận
     * câu tiếng Việt, thay vì để index UX_AssignmentLeaveRequest_Pending bung lỗi SQL thô.
     */
    Optional<AssignmentLeaveRequest> findFirstByAssignmentIdAndStatus(Integer assignmentId, String status);
}
