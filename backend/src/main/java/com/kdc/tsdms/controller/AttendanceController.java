package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.AttendanceRequest;
import com.kdc.tsdms.dto.AttendanceResponse;
import com.kdc.tsdms.service.AttendanceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Chấm công — /api/v1/attendance.
 *
 * <p>GET (xem) : ATTENDANCE_VIEW. POST/PUT/generate (ghi) : ATTENDANCE_MANAGE. ADMIN đi tắt.
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    /** Bảng chấm công theo khoảng ngày (mặc định tháng hiện tại), lọc theo GV nếu có. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_VIEW')")
    public List<AttendanceResponse> list(
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        return service.list(teacherId, f, t);
    }

    /**
     * Bảng chấm công của CHÍNH giáo viên đang đăng nhập (read-only, mặc định tháng hiện tại).
     * KHÔNG nhận teacherId — backend tự lấy từ token (chống IDOR). Lọc tùy chọn theo trạng thái.
     */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public List<AttendanceResponse> listMine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        LocalDate today = LocalDate.now();
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        return service.listMine(f, t, status);
    }

    /** Sinh chấm công hàng loạt từ các buổi đã duyệt trong khoảng ngày. */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public Map<String, Object> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int created = service.generateFromSchedules(from, to);
        return Map.of("created", created, "message", "Đã sinh " + created + " dòng chấm công");
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public ResponseEntity<AttendanceResponse> create(@Valid @RequestBody AttendanceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public AttendanceResponse update(@PathVariable Long id, @Valid @RequestBody AttendanceRequest req) {
        return service.update(id, req);
    }
}
