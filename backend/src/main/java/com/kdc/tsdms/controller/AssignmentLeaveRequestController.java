package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.LeaveRequestCreateRequest;
import com.kdc.tsdms.dto.LeaveRequestDecisionRequest;
import com.kdc.tsdms.dto.LeaveRequestResponse;
import com.kdc.tsdms.dto.LeaveRequestSessionOption;
import com.kdc.tsdms.service.AssignmentLeaveRequestService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API ĐƠN XIN NGHỈ MỘT BUỔI DẠY — /api/v1/leave-requests (bảng V39).
 *
 * <p>Phần của GIÁO VIÊN cố tình KHÔNG gắn quyền quản trị: vai trò TEACHER chỉ được seed 4 quyền
 * (SCHEDULE_VIEW, ATTENDANCE_VIEW, EVALUATION_VIEW, LESSON_VIEW) nên đòi ASSIGNMENT_VIEW ở đây là
 * khóa cửa chính giáo viên. Chốt chặn là QUYỀN SỞ HỮU do tầng service ép theo hồ sơ giáo viên của
 * người đang đăng nhập — cùng cách {@code NotificationController} làm với lời mời dạy.
 */
@RestController
@RequestMapping("/api/v1/leave-requests")
public class AssignmentLeaveRequestController {

    private final AssignmentLeaveRequestService service;

    public AssignmentLeaveRequestController(AssignmentLeaveRequestService service) {
        this.service = service;
    }

    /* ── Giáo viên ── */

    /**
     * Các BUỔI DẠY sắp tới của chính mình — nguồn cho ô chọn "xin nghỉ buổi nào".
     *
     * <p>Đơn nay chỉ nghỉ một buổi nên ô chọn phải là BUỔI CÓ THẬT trong lịch, không phải một ô
     * ngày trống để gõ: gõ ngày thì đơn dễ trỏ vào hôm giáo viên vốn không có tiết ở phiếu đó.
     */
    @GetMapping("/my-sessions")
    public List<LeaveRequestSessionOption> mySessions() {
        return service.mySessions();
    }

    @GetMapping("/mine")
    public List<LeaveRequestResponse> mine() {
        return service.mine();
    }

    @PostMapping
    public ResponseEntity<LeaveRequestResponse> create(@Valid @RequestBody LeaveRequestCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    /* ── Admin ── */

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public List<LeaveRequestResponse> pending() {
        return service.pending();
    }

    /**
     * Một đơn theo id — trang Phân công gọi khi được điều hướng từ dòng thông báo
     * ({@code /assignments?leaveRequest=<id>}) để mở thẳng hộp thoại duyệt đúng đơn đó.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public LeaveRequestResponse getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public LeaveRequestResponse approve(
            @PathVariable Integer id, @RequestBody(required = false) LeaveRequestDecisionRequest body) {
        return service.approve(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public LeaveRequestResponse reject(@PathVariable Integer id, @Valid @RequestBody LeaveRequestDecisionRequest body) {
        return service.reject(id, body == null ? null : body.note());
    }
}
