package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.AttendanceChangeLogResponse;
import com.kdc.tsdms.dto.AttendanceRequest;
import com.kdc.tsdms.dto.AttendanceResponse;
import com.kdc.tsdms.service.AttendanceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
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

    /** JVM ghim UTC — "tháng hiện tại" mặc định phải tính theo giờ Việt Nam. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
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
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        return service.listMine(f, t, status);
    }

    /* ── GV tự check-in/out ───────────────────────────────────────── */

    /** Trạng thái check-in các buổi dạy HÔM NAY của chính GV (nút Check in/out ở dashboard). */
    @GetMapping("/checkin/today")
    @PreAuthorize("hasRole('TEACHER')")
    public AttendanceResponse.CheckinToday checkinToday() {
        return service.checkinToday();
    }

    /**
     * GV check-in một buổi dạy hôm nay (chỉ khi hôm nay có lịch dạy đã duyệt của chính mình).
     * Giờ vào = giờ SERVER, client không gửi được.
     */
    @PostMapping("/checkin")
    @PreAuthorize("hasRole('TEACHER')")
    public AttendanceResponse checkIn(@Valid @RequestBody AttendanceRequest.Checkin req) {
        return service.checkIn(req);
    }

    /** GV check-out buổi đã check-in — giờ ra = giờ SERVER. */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('TEACHER')")
    public AttendanceResponse checkOut(@Valid @RequestBody AttendanceRequest.Checkin req) {
        return service.checkOut(req);
    }

    /** Sinh chấm công hàng loạt từ các buổi đã duyệt trong khoảng ngày. */
    /**
     * Hộp "Cần xử lý" của kế toán: dòng hệ thống chốt hộ giờ ra, dòng hệ thống ghi Vắng, và
     * dòng còn treo chưa có giờ ra — gom một chỗ thay vì bắt dò cả bảng.
     */
    @GetMapping("/attention")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceResponse> attention(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.attention(from, to);
    }

    /**
     * Nhật ký thay đổi của một dòng chấm công — ai sửa, sửa gì, lúc nào.
     *
     * <p>ATTENDANCE_MANAGE chứ không phải ATTENDANCE_VIEW: GIÁO VIÊN cũng có quyền VIEW (để
     * xem bảng của chính mình, {@code list()} tự ép scope), nên gắn VIEW ở đây là mở toang
     * dữ liệu của người khác. Hai endpoint này là công cụ của kế toán.
     */
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceChangeLogResponse> logs(@PathVariable Long id) {
        return service.changeLog(id);
    }

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
