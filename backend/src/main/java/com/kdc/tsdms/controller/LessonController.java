package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.CanvaLinkRequest;
import com.kdc.tsdms.dto.LessonFileResponse;
import com.kdc.tsdms.dto.LessonRequest;
import com.kdc.tsdms.dto.LessonResponse;
import com.kdc.tsdms.dto.LessonSummary;
import com.kdc.tsdms.dto.SubjectDto;
import com.kdc.tsdms.security.SecurityUtils;
import com.kdc.tsdms.service.LessonService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API module Bài giảng — /api/v1/lessons
 *
 * Danh mục (Category) là 1 trong 4 giá trị cố định: Tin học / Tiếng Anh /
 * STEM - AI / Kĩ năng sống — gắn trực tiếp trên Lesson, không qua Subject.
 *
 * Quyền:
 * LESSON_VIEW : ADMIN, EMPLOYEE, TEACHER (TEACHER chỉ thấy PUBLISHED)
 * LESSON_MANAGE : ADMIN, EMPLOYEE (tạo/sửa/xóa/upload)
 */
@RestController
@RequestMapping("/api/v1/lessons")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    /* ── Metadata cho form/bộ lọc ───────────────────────────────────── */

    /** Danh sách môn học ACTIVE (id, name, category) — dùng cho dropdown form. */
    @GetMapping("/subjects")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public List<SubjectDto> subjects() {
        return lessonService.getSubjects().stream()
                .map(s -> new SubjectDto(s.getId(), s.getName(), s.getCategory()))
                .toList();
    }

    /**
     * Danh sách category duy nhất (sorted) từ bảng Subject — dùng cho dropdown bộ
     * lọc.
     */
    @GetMapping("/categories")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public List<String> categories() {
        return lessonService.getCategories();
    }

    /** Danh sách khối lớp gợi ý cho dropdown (text tự do, không ràng buộc DB). */
    @GetMapping("/grade-levels")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACADEMIC') or hasAuthority('LESSON_VIEW')")
    public List<String> gradeLevels() {
        return lessonService.getGradeLevels();
    }

    /* ── 1. DANH SÁCH ───────────────────────────────────────────────── */

    /**
     * GET /api/v1/lessons — params: category, gradeLevel, status, keyword, page,
     * size
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public Page<LessonSummary> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable =
                PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(Sort.Direction.DESC, "updatedAt"));
        return lessonService.search(category, gradeLevel, status, keyword, isTeacherOnly(), pageable);
    }

    /* ── 2. CHI TIẾT ────────────────────────────────────────────────── */

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
    public LessonResponse detail(@PathVariable Integer id) {
        return lessonService.getDetail(id, isTeacherOnly());
    }

    /* ── 3. TẠO MỚI ─────────────────────────────────────────────────── */

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<LessonResponse> create(@Valid @RequestBody LessonRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.create(req));
    }

    /* ── 4. SỬA ─────────────────────────────────────────────────────── */

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public LessonResponse update(@PathVariable Integer id, @Valid @RequestBody LessonRequest req) {
        return lessonService.update(id, req);
    }

    /* ── 5. XÓA MỀM ─────────────────────────────────────────────────── */

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        lessonService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /* ── 6. UPLOAD FILE PPT ─────────────────────────────────────────── */

    @PostMapping("/{id}/files")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<List<LessonFileResponse>> uploadFiles(
            @PathVariable Integer id, @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.uploadFiles(id, files));
    }

    /* ── 7. THÊM LINK CANVA ─────────────────────────────────────────── */

    @PostMapping("/{id}/canva")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<LessonFileResponse> addCanvaLink(
            @PathVariable Integer id, @Valid @RequestBody CanvaLinkRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.addCanvaLink(id, req));
    }

    /* ── 8. XÓA FILE ĐÍNH KÈM ───────────────────────────────────────── */

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_MANAGE')")
    public ResponseEntity<Void> deleteFile(@PathVariable Integer id, @PathVariable Integer fileId) {
        lessonService.deleteFile(id, fileId);
        return ResponseEntity.noContent().build();
    }

    /* ── Helper ──────────────────────────────────────────────────────── */

    /**
     * TEACHER chỉ được xem bài PUBLISHED; staff (ADMIN/EMPLOYEE/ACADEMIC) xem tất
     * cả.
     */
    private boolean isTeacherOnly() {
        return !SecurityUtils.hasRole("ADMIN")
                && !SecurityUtils.hasRole("EMPLOYEE")
                && !SecurityUtils.hasRole("ACADEMIC");
    }
}
