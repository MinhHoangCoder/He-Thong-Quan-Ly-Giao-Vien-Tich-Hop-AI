package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.HolidayImpactResponse;
import com.kdc.tsdms.dto.HolidayRequest;
import com.kdc.tsdms.dto.HolidayResponse;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LỊCH NGHỈ — ngày lễ và kỳ nghỉ mà hệ thống KHÔNG sinh buổi dạy (bảng Holiday, Flyway V29).
 *
 * <p>Hai việc tách bạch:
 *
 * <ul>
 *   <li><b>Khai báo</b> kỳ nghỉ — ảnh hưởng tới lịch sinh RA SAU đó
 *       ({@code AssignmentService.generateSchedules} hỏi bảng này mỗi lần trải ô thời khóa biểu).
 *   <li><b>Dọn</b> các buổi ĐÃ sinh trước khi khai báo — không tự động, phải bấm. Xem
 *       {@link #impact(Integer)} và {@link #cancelSessions(Integer)}.
 * </ul>
 *
 * <p>Vì sao không tự dọn: hủy hàng loạt buổi dạy là việc khó lùi lại, và một kỳ nghỉ nhập sai
 * ngày (gõ nhầm năm) sẽ quét sạch lịch mà không ai kịp nhìn. Cho người dùng thấy con số rồi
 * tự quyết.
 */
@Service
public class HolidayService {

    /** Kỳ nghỉ dài hơn ngần này gần như chắc chắn là gõ nhầm ngày, không phải nghỉ thật. */
    private static final int MAX_DAYS = 120;

    private final HolidayRepository holidayRepo;
    private final SchoolRepository schoolRepo;
    private final ScheduleRepository scheduleRepo;
    private final AssignmentSlotRepository slotRepo;

    public HolidayService(
            HolidayRepository holidayRepo,
            SchoolRepository schoolRepo,
            ScheduleRepository scheduleRepo,
            AssignmentSlotRepository slotRepo) {
        this.holidayRepo = holidayRepo;
        this.schoolRepo = schoolRepo;
        this.scheduleRepo = scheduleRepo;
        this.slotRepo = slotRepo;
    }

    /* ─────────────────────────── ĐỌC ─────────────────────────── */

    @Transactional(readOnly = true)
    public Page<HolidayResponse> search(
            String keyword, String kind, LocalDate from, LocalDate to, Integer schoolId, Pageable pageable) {
        Page<Holiday> page = holidayRepo.search(blankToNull(keyword), blankToNull(kind), from, to, schoolId, pageable);
        Map<Integer, String> schoolNames = schoolNameCache(page.getContent());
        return page.map(h -> HolidayResponse.fromEntity(h, schoolNames.get(h.getSchoolId())));
    }

    @Transactional(readOnly = true)
    public HolidayResponse getById(Integer id) {
        Holiday h = getOrThrow(id);
        return HolidayResponse.fromEntity(h, schoolNameOf(h.getSchoolId()));
    }

    /* ─────────────────────────── GHI ─────────────────────────── */

    @Transactional
    public HolidayResponse create(HolidayRequest req) {
        Holiday h = new Holiday();
        apply(h, req);
        h.setCreatedBy(SecurityUtils.currentUserId());
        return HolidayResponse.fromEntity(holidayRepo.save(h), schoolNameOf(h.getSchoolId()));
    }

    @Transactional
    public HolidayResponse update(Integer id, HolidayRequest req) {
        Holiday h = getOrThrow(id);
        apply(h, req);
        h.setUpdatedAt(Instant.now());
        h.setUpdatedBy(SecurityUtils.currentUserId());
        return HolidayResponse.fromEntity(holidayRepo.save(h), schoolNameOf(h.getSchoolId()));
    }

    /**
     * Xóa mềm. KHÔNG dựng lại các buổi dạy đã hủy theo kỳ nghỉ này: buổi đã hủy có thể đã được
     * xếp bù bằng phiếu khác, hồi sinh hàng loạt sẽ đẻ ra trùng lịch.
     */
    @Transactional
    public void delete(Integer id) {
        Holiday h = getOrThrow(id);
        h.setDeleted(true);
        h.setDeletedAt(Instant.now());
        h.setDeletedBy(SecurityUtils.currentUserId());
        holidayRepo.save(h);
    }

    /* ──────────────── DỌN BUỔI DẠY ĐÃ SINH TRƯỚC ĐÓ ──────────────── */

    /** Đếm buổi dạy đang rơi vào kỳ nghỉ — để màn hình hỏi trước khi hủy. */
    @Transactional(readOnly = true)
    public HolidayImpactResponse impact(Integer id) {
        Holiday h = getOrThrow(id);
        List<Schedule> affected = affectedSchedules(h);
        LocalDateTime now = BusinessTime.now();

        List<Schedule> future =
                affected.stream().filter(s -> s.getStartTime().isAfter(now)).toList();
        int past = affected.size() - future.size();

        Set<Integer> teachers = new HashSet<>();
        LocalDate first = null;
        LocalDate last = null;
        for (Schedule s : future) {
            teachers.add(s.getTeacherId());
            LocalDate d = s.getStartTime().toLocalDate();
            if (first == null || d.isBefore(first)) {
                first = d;
            }
            if (last == null || d.isAfter(last)) {
                last = d;
            }
        }
        return new HolidayImpactResponse(future.size(), teachers.size(), first, last, past);
    }

    /**
     * Hủy các buổi CHƯA diễn ra rơi vào kỳ nghỉ.
     *
     * <p>Cố ý không đụng buổi đã qua: chúng có thể đã gắn dòng chấm công và đã vào bảng lương
     * của kỳ trước. Hủy chúng là sửa lại quá khứ và làm lệch số tiền đã trả.
     *
     * @return số buổi đã hủy
     */
    @Transactional
    public int cancelSessions(Integer id) {
        Holiday h = getOrThrow(id);
        LocalDateTime now = BusinessTime.now();
        Integer userId = SecurityUtils.currentUserId();
        int count = 0;
        for (Schedule s : affectedSchedules(h)) {
            if (!s.getStartTime().isAfter(now)) {
                continue;
            }
            s.setStatus("CANCELLED");
            s.setUpdatedAt(Instant.now());
            s.setUpdatedBy(userId);
            scheduleRepo.save(s);
            count++;
        }
        return count;
    }

    /* ─────────────────────────── PRIVATE ─────────────────────────── */

    /**
     * Buổi dạy còn hiệu lực nằm trong khoảng ngày của kỳ nghỉ, đã lọc theo phạm vi trường.
     *
     * <p>Trường của một buổi lấy từ Ô THỜI KHÓA BIỂU sinh ra nó (V27), không phải trường cấp
     * phiếu: một phiếu nay trải được nhiều trường, mà kỳ nghỉ riêng chỉ thuộc về một trường.
     */
    private List<Schedule> affectedSchedules(Holiday h) {
        LocalDateTime from = h.getFromDate().atStartOfDay();
        LocalDateTime to = h.getToDate().plusDays(1).atStartOfDay();
        List<Schedule> inRange = scheduleRepo.findByStartTimeBetweenAndDeletedFalse(from, to).stream()
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .toList();
        if (h.getSchoolId() == null || inRange.isEmpty()) {
            return inRange;
        }
        Map<Integer, Integer> schoolBySlot = new HashMap<>();
        for (AssignmentSlot slot : slotRepo.findAllById(inRange.stream()
                .map(Schedule::getSourceSlotId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList())) {
            schoolBySlot.put(slot.getId(), slot.getSchoolId());
        }
        List<Schedule> out = new ArrayList<>();
        for (Schedule s : inRange) {
            Integer schoolId = s.getSourceSlotId() == null ? null : schoolBySlot.get(s.getSourceSlotId());
            if (h.getSchoolId().equals(schoolId)) {
                out.add(s);
            }
        }
        return out;
    }

    private void apply(Holiday h, HolidayRequest req) {
        if (req.toDate().isBefore(req.fromDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(req.fromDate(), req.toDate()) + 1;
        if (days > MAX_DAYS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Kỳ nghỉ dài " + days + " ngày — vượt mức cho phép " + MAX_DAYS
                            + " ngày. Kiểm tra lại năm của ngày kết thúc.");
        }
        if (req.schoolId() != null
                && schoolRepo
                        .findById(req.schoolId())
                        .filter(s -> !s.isDeleted())
                        .isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy trường đã chọn.");
        }
        h.setFromDate(req.fromDate());
        h.setToDate(req.toDate());
        h.setName(req.name().trim());
        h.setKind(req.kind() != null ? req.kind() : "NATIONAL");
        h.setSchoolId(req.schoolId());
        h.setNote(blankToNull(req.note()));
    }

    private Holiday getOrThrow(Integer id) {
        return holidayRepo
                .findById(id)
                .filter(h -> !h.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ nghỉ id=" + id));
    }

    private Map<Integer, String> schoolNameCache(List<Holiday> holidays) {
        List<Integer> ids = holidays.stream()
                .map(Holiday::getSchoolId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, String> out = new HashMap<>();
        for (School s : schoolRepo.findAllById(ids)) {
            out.put(s.getId(), s.getName());
        }
        return out;
    }

    private String schoolNameOf(Integer schoolId) {
        if (schoolId == null) {
            return null;
        }
        return schoolRepo.findById(schoolId).map(School::getName).orElse(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
