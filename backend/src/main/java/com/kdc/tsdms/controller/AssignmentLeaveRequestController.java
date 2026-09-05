package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.Paging;
import com.kdc.tsdms.dto.AssignmentResponse;
import com.kdc.tsdms.dto.LeaveRequestCreateRequest;
import com.kdc.tsdms.dto.LeaveRequestDecisionRequest;
import com.kdc.tsdms.dto.LeaveRequestResponse;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.service.AssignmentLeaveRequestService;
import com.kdc.tsdms.service.AssignmentService;
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
 * REST API ĐƠN XIN NGHỈ DẠY — /api/v1/leave-requests (V39).
 *
 * <p>Phần của GIÁO VIÊN cố tình KHÔNG gắn quyền quản trị: vai trò TEACHER chỉ được seed 4 quyền
 * (SCHEDULE_VIEW, ATTENDANCE_VIEW, EVALUATION_VIEW, LESSON_VIEW) nên đòi ASSIGNMENT_VIEW ở đây là
 * khóa cửa chính giáo viên. Chốt chặn là QUYỀN SỞ HỮU do tầng service ép theo hồ sơ giáo viên của
 * người đang đăng nhập — cùng cách {@code NotificationController} làm với lời mời dạy.
 */
@RestController
@RequestMapping("/api/v1/leave-requests")
public class AssignmentLeaveRequestController {

    /** Trần của {@link Paging} — đủ xa so với số phiếu một giáo viên đang dạy cùng lúc. */
    private static final int MAX_OPTIONS = Paging.MAX_SIZE;

    private final AssignmentLeaveRequestService service;
    private final AssignmentService assignmentService;

    public AssignmentLeaveRequestController(
            AssignmentLeaveRequestService service, AssignmentService assignmentService) {
        this.service = service;
        this.assignmentService = assignmentService;
    }

    /* ── Giáo viên ── */

    /**
     * Các phân công ĐANG DẠY của chính người đang đăng nhập — nguồn cho ô chọn "xin nghỉ phân công
     * nào". Dùng lại {@code AssignmentService.list}: nó tự ép phạm vi về hồ sơ giáo viên của người
     * gọi (chống IDOR) và đã dựng sẵn tên trường/lớp/môn + các tiết trong tuần.
     */
    @GetMapping("/my-assignments")
    public List<AssignmentResponse> myAssignments() {
        return assignmentService
                .list(null, null, AssignmentStatus.ACTIVE, Paging.of(0, MAX_OPTIONS))
                .getContent();
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
