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
    public List<AssignmentResponse> list(@RequestParam(required = false) Integer teacherId) {
        return service.list(teacherId);
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
}
