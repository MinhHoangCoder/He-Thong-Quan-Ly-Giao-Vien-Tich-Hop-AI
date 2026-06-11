package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.LessonDto.ChangeStatusRequest;
import com.kdc.tsdms.dto.LessonDto.LessonFileRequest;
import com.kdc.tsdms.dto.LessonDto.LessonFilter;
import com.kdc.tsdms.dto.LessonDto.LessonRequest;
import com.kdc.tsdms.dto.LessonDto.LessonResponse;
import com.kdc.tsdms.dto.LessonDto.LessonSummaryDto;
import com.kdc.tsdms.security.AuthPrincipal;
import com.kdc.tsdms.service.LessonService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller — Module Bài giảng
 * Base URL: /api/v1/lessons
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  MA TRẬN PHÂN QUYỀN                                             │
 * ├────────────────────┬────────┬──────────┬─────────┬─────────────┤
 * │  Hành động         │ ADMIN  │ EMPLOYEE │ TEACHER │   SCHOOL    │
 * ├────────────────────┼────────┼──────────┼─────────┼─────────────┤
 * │ Xem danh sách      │   ✓    │    ✓     │  ✓ (*)  │      ✗      │
 * │ Xem chi tiết       │   ✓    │    ✓     │  ✓ (*)  │      ✗      │
 * │ Xem stats          │   ✓    │    ✓     │    ✗    │      ✗      │
 * │ Tạo / Sửa / Xóa    │   ✓    │    ✓     │    ✗    │      ✗      │
 * │ Đổi trạng thái     │   ✓    │    ✓     │    ✗    │      ✗      │
 * │ Quản lý file       │   ✓    │    ✓     │    ✗    │      ✗      │
 * ├────────────────────┴────────┴──────────┴─────────┴─────────────┤
 * │  (*) TEACHER: chỉ thấy bài PUBLISHED được gán cho mình.        │
 * │      Service tự ép filter — dù frontend gửi tham số khác.      │
 * └─────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/v1/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    // ════════════════════════════════════════════════════════════════
    // READ — EMPLOYEE + ADMIN + TEACHER (có giới hạn bởi Service)
    // ════════════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE','TEACHER')")
    public ResponseEntity<Page<LessonSummaryDto>> getAll(@ModelAttribute LessonFilter filter) {
        return ResponseEntity.ok(lessonService.getAll(filter));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE','TEACHER')")
    public ResponseEntity<LessonResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(lessonService.getById(id));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<Map<String, Long>> getStats(@RequestParam(required = false) Integer branchId) {
        return ResponseEntity.ok(lessonService.getStatusStats(branchId));
    }

    // ════════════════════════════════════════════════════════════════
    // WRITE — chỉ ADMIN + EMPLOYEE
    // ════════════════════════════════════════════════════════════════

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<LessonResponse> create(
            @Valid @RequestBody LessonRequest req, @AuthenticationPrincipal AuthPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.create(req, principal.userId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<LessonResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody LessonRequest req,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(lessonService.update(id, req, principal.userId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<LessonResponse> changeStatus(
            @PathVariable Integer id,
            @Valid @RequestBody ChangeStatusRequest req,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(lessonService.changeStatus(id, req.getStatus(), principal.userId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id, @AuthenticationPrincipal AuthPrincipal principal) {
        lessonService.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<LessonResponse> addFile(
            @PathVariable Integer id,
            @Valid @RequestBody LessonFileRequest req,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.addFile(id, req, principal.userId()));
    }

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<Void> removeFile(
            @PathVariable Integer id, @PathVariable Integer fileId, @AuthenticationPrincipal AuthPrincipal principal) {
        lessonService.removeFile(id, fileId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
