package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.AttendanceAmendCreateRequest;
import com.kdc.tsdms.dto.AttendanceAmendResponse;
import com.kdc.tsdms.dto.AttendanceAmendReviewRequest;
import com.kdc.tsdms.service.AttendanceAmendService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Yêu cầu bổ sung chấm công — /api/v1/attendance/amend-requests.
 *
 * <p>GIÁO VIÊN gửi và xem yêu cầu của chính mình; ADMIN/kế toán (ATTENDANCE_MANAGE) xem hộp
 * chờ và duyệt/từ chối. Không có endpoint nào cho giáo viên sửa hay hủy: gửi rồi là chờ
 * người duyệt, đúng tinh thần "công phải có người xác nhận".
 */
@RestController
@RequestMapping("/api/v1/attendance/amend-requests")
public class AttendanceAmendController {

    private final AttendanceAmendService service;

    public AttendanceAmendController(AttendanceAmendService service) {
        this.service = service;
    }

    /** Giáo viên xin bổ sung công cho một buổi đã lỡ. */
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AttendanceAmendResponse> create(@Valid @RequestBody AttendanceAmendCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    /** Yêu cầu của CHÍNH giáo viên đang đăng nhập — không nhận teacherId (chống IDOR). */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public List<AttendanceAmendResponse> mine() {
        return service.listMine();
    }

    /** Hộp yêu cầu của admin. {@code status} bỏ trống = tất cả. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceAmendResponse> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public AttendanceAmendResponse approve(
            @PathVariable Long id, @Valid @RequestBody(required = false) AttendanceAmendReviewRequest req) {
        return service.approve(id, req != null ? req : new AttendanceAmendReviewRequest(null, null));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public AttendanceAmendResponse reject(@PathVariable Long id, @Valid @RequestBody AttendanceAmendReviewRequest req) {
        return service.reject(id, req);
    }
}
