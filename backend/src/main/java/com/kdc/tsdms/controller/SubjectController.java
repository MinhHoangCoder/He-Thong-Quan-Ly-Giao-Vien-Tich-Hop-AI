package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.SubjectRequest;
import com.kdc.tsdms.dto.SubjectResponse;
import com.kdc.tsdms.service.SubjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Môn học — /api/v1/subjects
 *
 * Môn học (Subject) luôn thuộc 1 nhóm (SubjectCategory) qua categoryId — quản
 * lý
 * lồng trong trang "Nhóm môn học" của frontend (mỗi nhóm mở rộng ra danh sách
 * môn).
 *
 * GET (list/detail) : LESSON_VIEW (ADMIN + EMPLOYEE + TEACHER)
 * POST/PUT/DELETE : LESSON_MANAGE (ADMIN + EMPLOYEE)
 */
@RestController
@RequestMapping("/api/v1/subjects")
public class SubjectController {

    private final SubjectService service;

    public SubjectController(SubjectService service) {
        this.service = service;
    }

    /**
     * Danh sách môn — lọc theo categoryId nếu có, không phân trang (số môn/nhóm
     * nhỏ).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public List<SubjectResponse> list(@RequestParam(required = false) Integer categoryId) {
        return service.listByCategory(categoryId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public SubjectResponse detail(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody SubjectRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public SubjectResponse update(@PathVariable Integer id, @Valid @RequestBody SubjectRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
