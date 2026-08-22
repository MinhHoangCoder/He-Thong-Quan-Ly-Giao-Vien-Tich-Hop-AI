package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.Paging;
import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.SchoolClassRequest;
import com.kdc.tsdms.dto.SchoolClassResponse;
import com.kdc.tsdms.service.SchoolClassService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Lớp học — /api/v1/classes
 *
 * <p>GET : CLASS_VIEW. POST/PUT/DELETE : CLASS_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/classes")
public class SchoolClassController {

    private final SchoolClassService service;

    public SchoolClassController(SchoolClassService service) {
        this.service = service;
    }

    /** Dropdown trường cho form. */
    @GetMapping("/school-options")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_VIEW')")
    public List<OptionItem> schoolOptions() {
        return service.listSchoolOptions();
    }

    /** Dropdown các khối đang tồn tại trong hệ thống (bộ lọc danh sách). */
    @GetMapping("/grade-levels")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_VIEW')")
    public List<String> gradeLevels() {
        return service.listExistingGradeLevels();
    }

    /** Thùng rác — lớp đã xóa mềm. */
    @GetMapping("/trash")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_MANAGE')")
    public List<SchoolClassResponse> trash() {
        return service.listTrash();
    }

    /** Dropdown lớp ACTIVE theo trường. */
    @GetMapping("/by-school/{schoolId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_VIEW')")
    public List<OptionItem> bySchool(@PathVariable Integer schoolId) {
        return service.listActiveBySchool(schoolId);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_VIEW')")
    public Page<SchoolClassResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = Paging.of(page, size, Sort.by(Sort.Order.desc("id")));
        return service.search(keyword, schoolId, status, gradeLevel, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_VIEW')")
    public SchoolClassResponse detail(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_MANAGE')")
    public ResponseEntity<SchoolClassResponse> create(@Valid @RequestBody SchoolClassRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_MANAGE')")
    public SchoolClassResponse update(@PathVariable Integer id, @Valid @RequestBody SchoolClassRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Xóa mềm nhiều lớp cùng lúc. Body: [1, 2, 3]. */
    @PostMapping("/batch-delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_MANAGE')")
    public ResponseEntity<Void> batchDelete(@RequestBody List<Integer> ids) {
        service.deleteMany(ids);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/trash/{id}/restore")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_MANAGE')")
    public SchoolClassResponse restore(@PathVariable Integer id) {
        return service.restore(id);
    }

    /** Khôi phục nhiều lớp từ thùng rác trong 1 request. Body: [1, 2, 3]. */
    @PostMapping("/trash/batch-restore")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CLASS_MANAGE')")
    public List<SchoolClassResponse> batchRestore(@RequestBody List<Integer> ids) {
        return service.restoreMany(ids);
    }
}
