package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.EvaluationRequest;
import com.kdc.tsdms.dto.EvaluationResponse;
import com.kdc.tsdms.dto.EvaluationStatsResponse;
import com.kdc.tsdms.dto.EvaluationTeacherOption;
import com.kdc.tsdms.dto.TeacherEvaluationSummaryResponse;
import com.kdc.tsdms.service.EvaluationService;
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

/**
 * REST API module Đánh giá giáo viên — {@code /api/v1/evaluations}.
 *
 * <p>Quyền:
 * <ul>
 *   <li>{@code EVALUATION_VIEW} — xem danh sách / chi tiết / stats (scope theo role ở service)</li>
 *   <li>{@code EVALUATION_MANAGE} — tạo / sửa / xóa mềm</li>
 *   <li>ADMIN — full (bypass permission)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /* ── Metadata ─────────────────────────────────────────────────── */

    /** Gợi ý kỳ đánh giá cho dropdown form. */
    @GetMapping("/period-presets")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_VIEW') or hasAuthority('EVALUATION_MANAGE')")
    public List<String> periodPresets() {
        return evaluationService.periodPresets();
    }

    /** Dropdown giáo viên khi tạo đánh giá (SCHOOL được scope theo phân công nếu có). */
    @GetMapping("/teachers")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_MANAGE')")
    public List<EvaluationTeacherOption> teachers() {
        return evaluationService.teacherOptions();
    }

    /* ── KPI / summary ────────────────────────────────────────────── */

    /**
     * GET /api/v1/evaluations/stats — KPI trong phạm vi quyền hiện tại.
     * Params: teacherId, schoolId, source=CENTER|SCHOOL
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_VIEW')")
    public EvaluationStatsResponse stats(
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) String source) {
        return evaluationService.stats(teacherId, schoolId, source);
    }

    /** Tổng hợp theo 1 GV: điểm TB + phân bố sao. */
    @GetMapping("/teachers/{teacherId}/summary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_VIEW')")
    public TeacherEvaluationSummaryResponse teacherSummary(@PathVariable Integer teacherId) {
        return evaluationService.teacherSummary(teacherId);
    }

    /* ── List / detail ────────────────────────────────────────────── */

    /**
     * GET /api/v1/evaluations
     * Params: teacherId, schoolId, score, periodNote, source, keyword, page, size
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_VIEW')")
    public Page<EvaluationResponse> list(
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) Short score,
            @RequestParam(required = false) String periodNote,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable =
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        return evaluationService.search(teacherId, schoolId, score, periodNote, source, keyword, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_VIEW')")
    public EvaluationResponse detail(@PathVariable Integer id) {
        return evaluationService.getById(id);
    }

    /* ── CRUD ─────────────────────────────────────────────────────── */

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_MANAGE')")
    public ResponseEntity<EvaluationResponse> create(@Valid @RequestBody EvaluationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_MANAGE')")
    public EvaluationResponse update(@PathVariable Integer id, @Valid @RequestBody EvaluationRequest request) {
        return evaluationService.update(id, request);
    }

    /** Xóa mềm. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('EVALUATION_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        evaluationService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
