package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.Paging;
import com.kdc.tsdms.dto.AssignmentBulkRequest;
import com.kdc.tsdms.dto.AssignmentBulkResult;
import com.kdc.tsdms.dto.AssignmentCreateRequest;
import com.kdc.tsdms.dto.AssignmentFormOptions;
import com.kdc.tsdms.dto.AssignmentResponse;
import com.kdc.tsdms.dto.AssignmentUpdateRequest;
import com.kdc.tsdms.dto.ClassBusySlot;
import com.kdc.tsdms.dto.SchoolScopedOptions;
import com.kdc.tsdms.dto.TeacherBusySlot;
import com.kdc.tsdms.service.AssignmentApprovalService;
import com.kdc.tsdms.service.AssignmentService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Phân công giảng dạy — /api/v1/assignments.
 *
 * <p>GET : ASSIGNMENT_VIEW (ADMIN đi tắt). POST/cancel : ASSIGNMENT_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {

    private final AssignmentService service;
    private final AssignmentApprovalService approvalService;

    public AssignmentController(AssignmentService service, AssignmentApprovalService approvalService) {
        this.service = service;
        this.approvalService = approvalService;
    }

    /**
     * Danh sách phân công, PHÂN TRANG PHÍA SERVER.
     *
     * <p>Trước đây trả cả 444 phiếu kèm toàn bộ ô lịch (1,5 MB, 4,8 giây) cho một màn hình
     * chỉ hiện 10 dòng — trình duyệt tải hết rồi cắt trang bằng JavaScript.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_VIEW')")
    public Page<AssignmentResponse> list(
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.list(teacherId, keyword, status, Paging.of(page, size));
    }

    /** Số phiếu theo từng trạng thái — badge/tab trên màn Phân công. */
    @GetMapping("/status-counts")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_VIEW')")
    public Map<String, Long> statusCounts() {
        return service.statusCounts();
    }

    /** Thùng rác: các phân công đã xóa mềm. */
    @GetMapping("/trash")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public List<AssignmentResponse> trash() {
        return service.listTrash();
    }

    /** Dữ liệu nạp form: GV / môn / trường. */
    @GetMapping("/options")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentFormOptions options() {
        return service.formOptions();
    }

    /** Lớp + khung tiết theo trường đã chọn. */
    @GetMapping("/options/{schoolId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public SchoolScopedOptions schoolOptions(@PathVariable Integer schoolId) {
        return service.schoolOptions(schoolId);
    }

    /**
     * Giờ bận của một GV (mọi trường) trong giai đoạn đang định xếp — form dùng để khóa
     * sẵn các tiết đè giờ, kể cả tiết ở trường khác.
     */
    @GetMapping("/teacher-busy")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public List<TeacherBusySlot> teacherBusy(
            @RequestParam Integer teacherId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.teacherBusy(teacherId, startDate, endDate);
    }

    /**
     * Các ô lịch ĐÃ BỊ CHIẾM của một trường trong giai đoạn đang xếp — lưới thời khóa biểu dùng
     * để tô xám sẵn ô "lớp này đã có giáo viên khác dạy".
     *
     * <p>Hỏi theo TRƯỜNG chứ không theo từng lớp: lưới hiện cả trường một lúc nên hỏi lẻ từng
     * lớp là 20+ lượt gọi cho một lần mở form.
     */
    @GetMapping("/class-busy")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public List<ClassBusySlot> classBusy(
            @RequestParam Integer schoolId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.classBusy(schoolId, startDate, endDate);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_VIEW')")
    public AssignmentResponse detail(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public ResponseEntity<AssignmentResponse> create(@Valid @RequestBody AssignmentCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentResponse cancel(@PathVariable Integer id) {
        return service.cancel(id);
    }

    /**
     * Sửa phiếu CHƯA được xác nhận rồi gửi lại lời mời (đổi được cả giáo viên). Phiếu quay về
     * Chờ xác nhận và hạn trả lời tính lại từ đầu.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentResponse update(@PathVariable Integer id, @Valid @RequestBody AssignmentUpdateRequest req) {
        return service.update(id, req);
    }

    /** Admin ÉP DUYỆT thay giáo viên (phiếu đang chờ hoặc đã hết hạn). */
    @PostMapping("/{id}/force-approve")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentResponse forceApprove(
            @PathVariable Integer id, @RequestBody(required = false) AssignmentBulkRequest body) {
        approvalService.forceApprove(id, body == null ? null : body.note());
        return service.getById(id);
    }

    /** Gửi lại lời mời cho phiếu đang chờ (thông báo cũ dễ trôi trong chuông). */
    @PostMapping("/{id}/remind")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentResponse remind(@PathVariable Integer id) {
        approvalService.remind(id);
        return service.getById(id);
    }

    /* ── Thao tác hàng loạt: đầu kỳ phân công cho cả chục GV, bấm từng dòng quá chậm ── */

    @PostMapping("/bulk/remind")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentBulkResult bulkRemind(@Valid @RequestBody AssignmentBulkRequest body) {
        return approvalService.bulk(body.ids(), AssignmentApprovalService.BulkAction.REMIND, null);
    }

    @PostMapping("/bulk/force-approve")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentBulkResult bulkForceApprove(@Valid @RequestBody AssignmentBulkRequest body) {
        return approvalService.bulk(body.ids(), AssignmentApprovalService.BulkAction.FORCE_APPROVE, body.note());
    }

    @PostMapping("/bulk/cancel")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentBulkResult bulkCancel(@Valid @RequestBody AssignmentBulkRequest body) {
        return service.bulkCancel(body.ids());
    }

    /** Bỏ hủy: đưa phân công đã hủy về lại ACTIVE (khôi phục khi lỡ bấm Hủy). */
    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentResponse reactivate(@PathVariable Integer id) {
        return service.reactivate(id);
    }

    /** Xóa mềm phân công đã hủy (đưa vào thùng rác). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /** Khôi phục phân công từ thùng rác. */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public AssignmentResponse restore(@PathVariable Integer id) {
        return service.restore(id);
    }
}
