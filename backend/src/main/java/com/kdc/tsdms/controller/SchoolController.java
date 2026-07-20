package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.SchoolRequest;
import com.kdc.tsdms.dto.SchoolResponse;
import com.kdc.tsdms.service.SchoolService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Trường khách hàng — /api/v1/schools
 *
 * GET (list/detail)      : SCHOOL_VIEW (ADMIN + SALES)
 * POST/PUT/DELETE        : SCHOOL_MANAGE (ADMIN + SALES)
 */
@RestController
@RequestMapping("/api/v1/schools")
public class SchoolController {

    private final SchoolService sService;

    public SchoolController(SchoolService schoolService) {
        this.sService = schoolService;
    }

    /** Danh sách có phân trang + tìm kiếm + lọc theo chi nhánh/trạng thái. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_VIEW')")
    public Page<SchoolResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), Math.max(size, 1), Sort.by("name").ascending());
        return sService.search(keyword, branchId, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_VIEW')")
    public SchoolResponse detail(@PathVariable Integer id) {
        return sService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_MANAGE')")
    public ResponseEntity<SchoolResponse> create(@Valid @RequestBody SchoolRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_MANAGE')")
    public SchoolResponse update(@PathVariable Integer id, @Valid @RequestBody SchoolRequest req) {
        return sService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        sService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
