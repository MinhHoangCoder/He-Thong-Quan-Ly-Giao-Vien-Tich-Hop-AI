package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.Paging;
import com.kdc.tsdms.dto.SubjectCategoryRequest;
import com.kdc.tsdms.dto.SubjectCategoryResponse;
import com.kdc.tsdms.service.SubjectCategoryService;
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
 * REST API Nhóm môn — /api/v1/subject-categories
 *
 * GET (list/detail) : LESSON_VIEW (ADMIN + EMPLOYEE + TEACHER)
 * POST/PUT/DELETE : LESSON_MANAGE (ADMIN + EMPLOYEE)
 */
@RestController
@RequestMapping("/api/v1/subject-categories")
public class SubjectCategoryController {

    private final SubjectCategoryService service;

    public SubjectCategoryController(SubjectCategoryService service) {
        this.service = service;
    }

    /** Dropdown: chỉ trả ACTIVE, không phân trang. */
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public List<SubjectCategoryResponse> listActive() {
        return service.listActive();
    }

    /**
     * Danh sách có phân trang + tìm kiếm (xem được bởi mọi vai trò có LESSON_VIEW).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public Page<SubjectCategoryResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = Paging.of(page, size, Sort.by("name").ascending());
        return service.search(keyword, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public SubjectCategoryResponse detail(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<SubjectCategoryResponse> create(@Valid @RequestBody SubjectCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public SubjectCategoryResponse update(@PathVariable Integer id, @Valid @RequestBody SubjectCategoryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
