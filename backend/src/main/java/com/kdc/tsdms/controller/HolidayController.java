package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.HolidayAbsenceResponse;
import com.kdc.tsdms.dto.HolidayFixAbsencesRequest;
import com.kdc.tsdms.dto.HolidayImpactResponse;
import com.kdc.tsdms.dto.HolidayRequest;
import com.kdc.tsdms.dto.HolidayResponse;
import com.kdc.tsdms.service.HolidayService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
 * REST API Lịch nghỉ — /api/v1/holidays
 *
 * <p>GET: HOLIDAY_VIEW · ghi: HOLIDAY_MANAGE (Flyway V30, cấp cho phòng Đào tạo).
 * ADMIN đi tắt bằng hasRole như mọi API khác trong dự án.
 *
 * <p>Người xếp lịch cũng đọc được (ASSIGNMENT_VIEW / SCHEDULE_VIEW): khi một buổi dạy "biến
 * mất" khỏi thời khóa biểu, câu hỏi đầu tiên luôn là hôm đó có phải ngày nghỉ không — bắt họ
 * đi hỏi phòng Đào tạo mới trả lời được là bắt sai người.
 */
@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayController {

    private static final String CAN_VIEW =
            "hasRole('ADMIN') or hasAuthority('HOLIDAY_VIEW') or hasAuthority('ASSIGNMENT_VIEW') or hasAuthority('SCHEDULE_VIEW')";
    private static final String CAN_MANAGE = "hasRole('ADMIN') or hasAuthority('HOLIDAY_MANAGE')";

    /**
     * Sửa hàng loạt chấm công theo kỳ nghỉ đòi CẢ HAI quyền.
     *
     * <p>Đây không còn là thao tác trên lịch nghỉ nữa mà là ghi đè hồ sơ chấm công của nhiều
     * giáo viên cùng lúc: người chỉ được giao việc khai ngày lễ không nên làm được. Ngược lại
     * cũng không mở cho mỗi ATTENDANCE_MANAGE, vì phải hiểu kỳ nghỉ mới biết dòng nào đáng sửa.
     *
     * <p>Hệ quả vận hành: phòng Đào tạo (đang giữ HOLIDAY_*) cần được cấp thêm
     * ATTENDANCE_MANAGE, hoặc bước này do kế toán bấm.
     */
    private static final String CAN_FIX_ABSENCES =
            "hasRole('ADMIN') or (hasAuthority('HOLIDAY_MANAGE') and hasAuthority('ATTENDANCE_MANAGE'))";

    private final HolidayService service;

    public HolidayController(HolidayService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public Page<HolidayResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), Math.max(size, 1), Sort.by("fromDate").descending());
        return service.search(keyword, kind, from, to, schoolId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    public HolidayResponse detail(@PathVariable Integer id) {
        return service.getById(id);
    }

    /** Số buổi dạy đã sinh đang rơi vào kỳ nghỉ — màn hình hỏi trước khi hủy. */
    @GetMapping("/{id}/impact")
    @PreAuthorize(CAN_VIEW)
    public HolidayImpactResponse impact(@PathVariable Integer id) {
        return service.impact(id);
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<HolidayResponse> create(@Valid @RequestBody HolidayRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public HolidayResponse update(@PathVariable Integer id, @Valid @RequestBody HolidayRequest req) {
        return service.update(id, req);
    }

    /** Hủy các buổi dạy CHƯA diễn ra rơi vào kỳ nghỉ. Buổi đã qua giữ nguyên. */
    @PostMapping("/{id}/cancel-sessions")
    @PreAuthorize(CAN_MANAGE)
    public Map<String, Integer> cancelSessions(@PathVariable Integer id) {
        return Map.of("cancelled", service.cancelSessions(id));
    }

    /**
     * Các dòng chấm công VẮNG mà hệ thống tự ghi cho buổi đã qua trong kỳ nghỉ.
     *
     * <p>Chỉ đọc nên mở cho CAN_FIX_ABSENCES lẫn người xem: nhìn thấy vấn đề là việc của mọi
     * người, sửa nó mới là việc cần quyền.
     */
    @GetMapping("/{id}/absences")
    @PreAuthorize(CAN_VIEW)
    public HolidayAbsenceResponse absences(@PathVariable Integer id) {
        return service.absences(id);
    }

    /** Chuyển các dòng Vắng đã chọn sang Nghỉ phép (buổi đó trường không hoạt động). */
    @PostMapping("/{id}/fix-absences")
    @PreAuthorize(CAN_FIX_ABSENCES)
    public Map<String, Integer> fixAbsences(
            @PathVariable Integer id, @Valid @RequestBody HolidayFixAbsencesRequest req) {
        return Map.of("fixed", service.fixAbsences(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
