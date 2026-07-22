package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.AssignmentCreateRequest;
import com.kdc.tsdms.dto.AssignmentFormOptions;
import com.kdc.tsdms.dto.AssignmentResponse;
import com.kdc.tsdms.dto.SchoolScopedOptions;
import com.kdc.tsdms.service.AssignmentService;
import jakarta.validation.Valid;
import java.util.List;
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

    public AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_VIEW')")
    public List<AssignmentResponse> list(
            @RequestParam(required = false) Integer teacherId, @RequestParam(required = false) String keyword) {
        return service.list(teacherId, keyword);
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

    /** Xóa VĨNH VIỄN phân công khỏi hệ thống (chỉ khi đang ở thùng rác). */
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGNMENT_MANAGE')")
    public ResponseEntity<Void> purge(@PathVariable Integer id) {
        service.purge(id);
        return ResponseEntity.noContent().build();
    }
}
