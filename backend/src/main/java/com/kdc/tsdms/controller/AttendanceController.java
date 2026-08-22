package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.AttendanceChangeLogResponse;
import com.kdc.tsdms.dto.AttendanceRequest;
import com.kdc.tsdms.dto.AttendanceResponse;
import com.kdc.tsdms.dto.AttendanceSummaryResponse;
import com.kdc.tsdms.dto.AttendanceTodayResponse;
import com.kdc.tsdms.service.AttendanceDailyService;
import com.kdc.tsdms.service.AttendanceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final AttendanceDailyService dailyService;

    public AttendanceController(AttendanceService service, AttendanceDailyService dailyService) {
        this.service = service;
        this.dailyService = dailyService;
    }

    /**
     * Bảng chấm công theo khoảng ngày (mặc định tháng hiện tại), CÓ PHÂN TRANG.
     *
     * <p>{@code keyword} tìm theo tên giáo viên; {@code status} lọc PRESENT/LATE/LEAVE/ABSENT.
     * Sắp xếp cố định trong câu query (ngày giảm dần) nên không nhận tham số sort từ client.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_VIEW')")
    public Page<AttendanceResponse> list(
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        LocalDate today = BusinessTime.today();
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        return service.list(teacherId, f, t, status, keyword, PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
    }

    /** Ba thẻ tổng quan cho ĐÚNG bộ lọc đang dùng — tính trên cả kỳ, không riêng trang đang xem. */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_VIEW')")
    public AttendanceSummaryResponse summary(
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        LocalDate today = BusinessTime.today();
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        return service.summary(teacherId, f, t, status, keyword);
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
        LocalDate today = BusinessTime.today();
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

    /**
     * Tab "Hôm nay" của admin — mọi buổi dạy trong ngày kèm buổi nào đã chấm, buổi nào chưa.
     *
     * <p>Dựng từ LỊCH DẠY: buổi giáo viên chưa bấm gì thì chưa có dòng chấm công nào, nhìn
     * bảng chấm công sẽ không thấy nó — mà đó đúng là buổi cần để mắt.
     */
    @GetMapping("/today")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
    public AttendanceTodayResponse today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dailyService.forDate(date != null ? date : BusinessTime.today());
    }

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
