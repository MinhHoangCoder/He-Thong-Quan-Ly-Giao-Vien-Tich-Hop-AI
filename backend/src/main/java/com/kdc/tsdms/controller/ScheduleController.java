package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.MyScheduleFilters;
import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.ScheduleEventResponse;
import com.kdc.tsdms.dto.ScheduleFilterOptions;
import com.kdc.tsdms.service.ScheduleService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Lịch dạy (read-only) — /api/v1/schedules. Cần quyền SCHEDULE_VIEW (ADMIN đi tắt).
 */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    /**
     * Buổi dạy trong [from, to], lọc theo GV / trường / lớp / trạng thái (đều tùy chọn).
     * Không truyền status thì chỉ trả buổi ĐÃ DUYỆT như trước.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHEDULE_VIEW')")
    public List<ScheduleEventResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) Integer classId,
            @RequestParam(required = false) String status) {
        return service.list(from, to, teacherId, schoolId, classId, status);
    }

    /** Ngày nghỉ chạm vào khoảng đang xem — để lịch tô màu và ghi tên kỳ nghỉ. */
    @GetMapping("/holidays")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHEDULE_VIEW') or hasRole('TEACHER')")
    public List<ScheduleService.HolidayMark> holidays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer schoolId) {
        return service.holidaysIn(from, to, schoolId);
    }

    /**
     * "Lịch dạy của tôi" — buổi ĐÃ DUYỆT của CHÍNH giáo viên đang đăng nhập trong [from, to].
     * Không nhận teacherId: service tự lấy hồ sơ GV từ token (chống IDOR).
     */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public List<ScheduleEventResponse> listMine(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) Integer classId) {
        return service.listMine(from, to, schoolId, classId);
    }

    /** Trường + lớp mà chính giáo viên đang đăng nhập có dạy — cho bộ lọc "Lịch dạy của tôi". */
    @GetMapping("/mine/filters")
    @PreAuthorize("hasRole('TEACHER')")
    public MyScheduleFilters myFilters() {
        return service.myFilterOptions();
    }

    /** GV + trường cho bộ lọc. */
    @GetMapping("/filters")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHEDULE_VIEW')")
    public ScheduleFilterOptions filters() {
        return service.filterOptions();
    }

    /** Lớp theo trường (bộ lọc cấp 2). */
    @GetMapping("/filters/{schoolId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHEDULE_VIEW')")
    public List<OptionItem> classes(@PathVariable Integer schoolId) {
        return service.classesOf(schoolId);
    }
}
