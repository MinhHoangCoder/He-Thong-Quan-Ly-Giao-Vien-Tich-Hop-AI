package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.HolidayAbsenceResponse;
import com.kdc.tsdms.dto.HolidayDeleteImpactResponse;
import com.kdc.tsdms.dto.HolidayFixAbsencesRequest;
import com.kdc.tsdms.dto.HolidayImpactResponse;
import com.kdc.tsdms.dto.HolidayRequest;
import com.kdc.tsdms.dto.HolidayResponse;
import com.kdc.tsdms.dto.PayrollHolidayIssueResponse;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LỊCH NGHỈ — ngày lễ và kỳ nghỉ mà hệ thống KHÔNG sinh buổi dạy (bảng Holiday, Flyway V29).
 *
 * <p>Ba việc tách bạch:
 *
 * <ul>
 *   <li><b>Khai báo</b> kỳ nghỉ — ảnh hưởng tới lịch sinh RA SAU đó
 *       ({@code AssignmentService.generateSchedules} hỏi bảng này mỗi lần trải ô thời khóa biểu).
 *   <li><b>Dọn buổi CHƯA diễn ra</b> đã sinh trước khi khai báo — không tự động, phải bấm. Xem
 *       {@link #impact(Integer)} và {@link #cancelSessions(Integer)}.
 *   <li><b>Sửa hậu quả của buổi ĐÃ diễn ra</b> — buổi "ma" đã qua thì {@code
 *       AttendanceSweepService} đã kịp ghi VẮNG cho giáo viên, và hủy buổi KHÔNG xóa dòng vắng
 *       đó. Xem {@link #absences(Integer)} và {@link #fixAbsences(Integer,
 *       HolidayFixAbsencesRequest)}.
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

    /** Số dòng làm mẫu trong hộp xác nhận — vừa đủ nhìn ra mình đang hủy cái gì, không phải cuộn. */
    private static final int SAMPLE_SIZE = 5;

    private final HolidayRepository holidayRepo;
    private final SchoolRepository schoolRepo;
    private final ScheduleRepository scheduleRepo;
    private final AssignmentSlotRepository slotRepo;
    private final AttendanceRepository attendanceRepo;
    private final TeacherRepository teacherRepo;
    private final AttendanceService attendanceService;
    private final NotificationService notificationService;

    public HolidayService(
            HolidayRepository holidayRepo,
            SchoolRepository schoolRepo,
            ScheduleRepository scheduleRepo,
            AssignmentSlotRepository slotRepo,
            AttendanceRepository attendanceRepo,
            TeacherRepository teacherRepo,
            AttendanceService attendanceService,
            NotificationService notificationService) {
        this.holidayRepo = holidayRepo;
        this.schoolRepo = schoolRepo;
        this.scheduleRepo = scheduleRepo;
        this.slotRepo = slotRepo;
        this.attendanceRepo = attendanceRepo;
        this.teacherRepo = teacherRepo;
        this.attendanceService = attendanceService;
        this.notificationService = notificationService;
    }

    /* ─────────────────────────── ĐỌC ─────────────────────────── */

    @Transactional(readOnly = true)
    public Page<HolidayResponse> search(
            String keyword, String kind, LocalDate from, LocalDate to, Integer schoolId, Pageable pageable) {
        Page<Holiday> page = holidayRepo.search(blankToNull(keyword), blankToNull(kind), from, to, schoolId, pageable);
        Map<Integer, String> schoolNames = schoolNameCache(page.getContent());
        return page.map(h -> HolidayResponse.fromEntity(h, schoolNames.get(h.getSchoolId())));
    }

    /**
     * Trong các kỳ nghỉ được hỏi, kỳ nào CÒN VIỆC phải xử lý ở hộp thoại "Buổi dạy" — để màn
     * hình danh sách biết có vẽ nút đó cho dòng ấy hay không.
     *
     * <p>"Còn việc" định nghĩa đúng bằng thứ hộp thoại sẽ hiện: có buổi dạy chưa hủy rơi vào
     * khoảng ngày, HOẶC có dòng chấm công VẮNG do máy tự ghi trong khoảng đó. Nhờ vậy giữ được
     * một luật đơn giản: có nút ⇔ mở ra là có nội dung.
     *
     * <p>ĐỂ RIÊNG, KHÔNG GỘP VÀO {@link #search}: hai câu bên dưới quét toàn bảng Schedule và
     * Attendance (~0,4 giây trên bộ 86.865 buổi) vì không có chỉ mục nào seek được theo
     * StartTime — mà chỉ mục {@code (IsDeleted, StartTime)} thì đã thử rồi phải gỡ vì làm hỏng
     * kế hoạch của truy vấn lịch sắp tới. Gộp vào danh sách là bắt bảng kỳ nghỉ chờ chừng đó
     * mỗi lần mở trang, chỉ để biết có vẽ một cái nút hay không.
     */
    @Transactional(readOnly = true)
    public List<Integer> holidaysWithIssues(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Holiday> holidays = holidayRepo.findAllById(ids);
        if (holidays.isEmpty()) {
            return List.of();
        }
        LocalDate from = holidays.stream()
                .map(Holiday::getFromDate)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate to = holidays.stream()
                .map(Holiday::getToDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        // Một cửa sổ chung cho cả trang thay vì mỗi kỳ nghỉ một câu: cắt theo ngày ở đây chỉ để
        // đỡ phải chuyển về những ngày chắc chắn không ai hỏi tới.
        List<Object[]> days = new ArrayList<>(holidayRepo.sessionDaysInRange(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        days.addAll(holidayRepo.systemAbsenceDaysInRange(from, to));

        List<Integer> out = new ArrayList<>();
        for (Holiday h : holidays) {
            for (Object[] row : days) {
                LocalDate day = toLocalDate(row[0]);
                Integer school = (Integer) row[1];
                boolean trongKhoang = !day.isBefore(h.getFromDate()) && !day.isAfter(h.getToDate());
                boolean dungPhamVi = h.getSchoolId() == null || h.getSchoolId().equals(school);
                if (trongKhoang && dungPhamVi) {
                    out.add(h.getId());
                    break;
                }
            }
        }
        return out;
    }

    /** Cột DATE của truy vấn native về dưới dạng nào là tùy driver — nhận cả hai kiểu. */
    private static LocalDate toLocalDate(Object value) {
        return value instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate() : (LocalDate) value;
    }

    @Transactional(readOnly = true)
    public HolidayResponse getById(Integer id) {
        Holiday h = getOrThrow(id);
        return HolidayResponse.fromEntity(h, schoolNameOf(h.getSchoolId()));
    }

    /**
     * Kỳ nghỉ SẮP khai báo sẽ đụng vào những buổi dạy nào — màn hình hỏi TRƯỚC khi lưu.
     *
     * <p>Nhận nguyên {@link HolidayRequest} chứ không nhận id: lúc này kỳ nghỉ chưa tồn tại
     * trong DB. Đây chính là điểm khác với {@link #impact(Integer)} — cùng một phép đếm nhưng
     * một cái hỏi về kỳ nghỉ đã có, một cái hỏi về kỳ nghỉ chưa có.
     *
     * <p>Vì sao phải hỏi trước khi lưu chứ không lưu rồi hỏi sau: từ V40 việc thêm kỳ nghỉ TỰ
     * hủy các buổi trùng ngày. Một thao tác hủy hàng loạt phải cho người dùng nhìn thấy nó
     * chạm vào cái gì trước khi nó chạy, không phải sau.
     */
    @Transactional(readOnly = true)
    public HolidayImpactResponse previewImpact(HolidayRequest req) {
        assertHopLe(req);
        return summarize(affectedSchedules(req.fromDate(), req.toDate(), req.schoolId()));
    }

    /* ─────────────────────────── GHI ─────────────────────────── */

    /**
     * Thêm kỳ nghỉ, rồi HỦY LUÔN các buổi dạy chưa diễn ra rơi vào những ngày đó.
     *
     * <p>Trước V40 bước hủy là một nút riêng người dùng phải nhớ bấm, vì buổi bị hủy không ghi
     * lại nó bị hủy vì cớ gì — tự động hủy thì không có đường lùi. Nay mỗi buổi mang theo
     * {@code CancelKind='HOLIDAY'} và {@code HolidayId}, nên xóa kỳ nghỉ trả lại được đúng
     * chừng ấy buổi ({@link #delete(Integer)}). Thao tác đã lùi được thì không có lý do bắt
     * người dùng làm hai bước cho một ý định.
     *
     * <p>Vẫn KHÔNG đụng buổi đã diễn ra — xem {@link #cancelSessions(Integer)}.
     */
    @Transactional
    public HolidayResponse create(HolidayRequest req) {
        Holiday h = new Holiday();
        apply(h, req);
        h.setCreatedBy(SecurityUtils.currentUserId());
        Holiday saved = holidayRepo.save(h);
        cancelFutureSessions(saved);
        return HolidayResponse.fromEntity(saved, schoolNameOf(saved.getSchoolId()));
    }

    /**
     * Sửa kỳ nghỉ. Buổi nào bị chính kỳ này hủy mà nay đã RA NGOÀI khoảng ngày mới thì được
     * trả lại lịch.
     *
     * <p>Không làm bước đó thì rút ngắn kỳ nghỉ để lại một vệt buổi hủy vĩnh viễn ở phần vừa
     * cắt bỏ — mà rút ngắn chính là cách người dùng sửa lỗi gõ nhầm ngày. Chiều ngược lại (nới
     * rộng kỳ nghỉ) KHÔNG tự hủy thêm: đó là hủy hàng loạt, phải đi qua hộp thoại "Buổi dạy"
     * để người dùng nhìn con số trước.
     */
    @Transactional
    public HolidayResponse update(Integer id, HolidayRequest req) {
        Holiday h = getOrThrow(id);
        apply(h, req);
        h.setUpdatedAt(Instant.now());
        h.setUpdatedBy(SecurityUtils.currentUserId());
        Holiday saved = holidayRepo.save(h);
        restoreSessionsOutsideRange(saved);
        return HolidayResponse.fromEntity(saved, schoolNameOf(saved.getSchoolId()));
    }

    /**
     * Xóa mềm kỳ nghỉ, và TRẢ LẠI LỊCH các buổi mà chính nó đã hủy.
     *
     * <p>Bản trước V40 cố ý KHÔNG trả lại, vì buổi bị hủy chỉ có mỗi chữ CANCELLED: muốn khôi
     * phục thì phải quét cả khoảng ngày, mà làm vậy là dựng dậy luôn cả buổi admin hủy tay
     * hôm đó — sai còn tệ hơn không làm gì. Cột {@code HolidayId} lật ngược cán cân: hỏi đúng
     * được thì trả đúng được, và khai nhầm một kỳ nghỉ không còn là vết thương vĩnh viễn trên
     * thời khóa biểu.
     *
     * <p>Trả về {@code APPROVED} chứ không về trạng thái cũ: buổi chỉ bị kỳ nghỉ đụng vào khi
     * nó đang có hiệu lực, mà buổi có hiệu lực trong hệ thống này chỉ có một trạng thái. Lưu
     * thêm một cột "trạng thái trước khi hủy" chỉ để diễn tả lại đúng điều đó là thừa.
     */
    @Transactional
    public void delete(Integer id) {
        Holiday h = getOrThrow(id);
        Integer userId = SecurityUtils.currentUserId();
        restoreSessions(holidayRepo.sessionsCancelledByHoliday(id), userId);

        h.setDeleted(true);
        h.setDeletedAt(Instant.now());
        h.setDeletedBy(userId);
        holidayRepo.save(h);
    }

    /** Trả lại lịch những buổi kỳ nghỉ đã hủy mà nay không còn nằm trong khoảng ngày của nó. */
    private void restoreSessionsOutsideRange(Holiday h) {
        List<Schedule> ngoaiKhoang = holidayRepo.sessionsCancelledByHoliday(h.getId()).stream()
                .filter(s -> {
                    LocalDate d = s.getStartTime().toLocalDate();
                    return d.isBefore(h.getFromDate()) || d.isAfter(h.getToDate());
                })
                .toList();
        restoreSessions(ngoaiKhoang, SecurityUtils.currentUserId());
    }

    /**
     * Gỡ dấu nghỉ lễ khỏi các buổi đã cho: về {@code APPROVED}, xóa {@code CancelKind} và
     * {@code HolidayId}.
     *
     * @return số buổi đã trả lại lịch
     */
    private int restoreSessions(List<Schedule> sessions, Integer userId) {
        for (Schedule s : sessions) {
            s.setStatus("APPROVED");
            s.setCancelKind(null);
            s.setHolidayId(null);
            s.setUpdatedAt(Instant.now());
            // Trigger TR_Schedule_StatusLog đọc UpdatedBy để ghi nhật ký đổi trạng thái —
            // phải set TRƯỚC khi lưu, nếu không nhật ký ghi người thao tác là NULL.
            s.setUpdatedBy(userId);
            scheduleRepo.save(s);
        }
        return sessions.size();
    }

    /* ──────────────── THÙNG RÁC ──────────────── */

    /** Kỳ nghỉ đã xóa — để khôi phục lại. */
    @Transactional(readOnly = true)
    public Page<HolidayResponse> trash(String keyword, Pageable pageable) {
        Page<Holiday> page = holidayRepo.searchTrash(blankToNull(keyword), pageable);
        Map<Integer, String> schoolNames = schoolNameCache(page.getContent());
        return page.map(h -> HolidayResponse.fromEntity(h, schoolNames.get(h.getSchoolId())));
    }

    /**
     * Đưa một kỳ nghỉ từ thùng rác về danh sách chính.
     *
     * <p>Chỉ bỏ cờ xóa, KHÔNG hủy lại các buổi dạy đã sinh trong lúc kỳ nghỉ nằm ở thùng rác —
     * cùng lý lẽ với {@link #delete(Integer)} theo chiều ngược lại. Muốn dọn thì bấm "Hủy buổi
     * dạy" như bình thường, để người dùng nhìn con số trước khi quyết.
     */
    @Transactional
    public HolidayResponse restore(Integer id) {
        Holiday h = holidayRepo
                .findById(id)
                .filter(Holiday::isDeleted)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy kỳ nghỉ id=" + id + " trong thùng rác."));
        h.setDeleted(false);
        h.setDeletedAt(null);
        h.setDeletedBy(null);
        h.setUpdatedAt(Instant.now());
        h.setUpdatedBy(SecurityUtils.currentUserId());
        return HolidayResponse.fromEntity(holidayRepo.save(h), schoolNameOf(h.getSchoolId()));
    }

    /**
     * Kỳ nghỉ này đã để lại những gì — màn hình hỏi trước khi xóa.
     *
     * <p>Kỳ nghỉ KHÔNG bị chặn xóa theo dữ liệu con như Trường/Lớp: kỳ gõ nhầm năm vừa là kỳ
     * để lại nhiều hậu quả nhất vừa là kỳ cần xóa gấp nhất, chặn cứng ở đây là tự nhốt mình.
     * Đổi lại phải kể đủ những gì sẽ KHÔNG được hoàn lại.
     */
    @Transactional(readOnly = true)
    public HolidayDeleteImpactResponse deleteImpact(Integer id) {
        Holiday h = getOrThrow(id);
        LocalDateTime now = BusinessTime.now();
        long future = affectedSchedules(h.getFromDate(), h.getToDate(), h.getSchoolId()).stream()
                .filter(s -> s.getStartTime().isAfter(now))
                .count();
        return new HolidayDeleteImpactResponse(
                holidayRepo.countSessionsCancelledByHoliday(id),
                attendanceRepo.countByStatusAndWorkDateBetween("LEAVE", h.getFromDate(), h.getToDate()),
                future);
    }

    /* ──────────────── DỌN BUỔI DẠY ĐÃ SINH TRƯỚC ĐÓ ──────────────── */

    /**
     * Đếm buổi dạy đang rơi vào kỳ nghỉ — để màn hình hỏi trước khi hủy.
     *
     * <p>Còn cần đến sau khi {@link #create} đã tự hủy: kỳ nghỉ khai từ trước vẫn gặp buổi mới
     * sinh sau đó (sửa phân công, xếp bù), và người dùng vẫn sửa được KHOẢNG NGÀY của kỳ nghỉ
     * đã lưu.
     */
    @Transactional(readOnly = true)
    public HolidayImpactResponse impact(Integer id) {
        Holiday h = getOrThrow(id);
        return summarize(affectedSchedules(h.getFromDate(), h.getToDate(), h.getSchoolId()));
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
        return cancelFutureSessions(getOrThrow(id));
    }

    /**
     * Ruột chung của {@link #create} và {@link #cancelSessions(Integer)}: đóng dấu HOLIDAY lên
     * các buổi chưa diễn ra của kỳ nghỉ.
     *
     * <p>Ba cột đi liền một khối — {@code Status} nói buổi hết hiệu lực, {@code CancelKind} nói
     * vì sao, {@code HolidayId} nói vì kỳ nào. Thiếu cột thứ ba thì bảng lương phân biệt được
     * "nghỉ lễ" với "admin hủy" nhưng xóa kỳ nghỉ vẫn không biết đường lùi.
     */
    private int cancelFutureSessions(Holiday h) {
        LocalDateTime now = BusinessTime.now();
        Integer userId = SecurityUtils.currentUserId();
        int count = 0;
        for (Schedule s : affectedSchedules(h.getFromDate(), h.getToDate(), h.getSchoolId())) {
            if (!s.getStartTime().isAfter(now)) {
                continue;
            }
            s.setStatus("CANCELLED");
            s.setCancelKind("HOLIDAY");
            s.setHolidayId(h.getId());
            s.setUpdatedAt(Instant.now());
            // Trigger TR_Schedule_StatusLog ghi nhật ký theo UpdatedBy — set TRƯỚC khi lưu.
            s.setUpdatedBy(userId);
            scheduleRepo.save(s);
            count++;
        }
        return count;
    }

    /**
     * Gộp danh sách buổi bị ảnh hưởng thành con số cho màn hình: chưa diễn ra / đã diễn ra /
     * số giáo viên / khoảng ngày / vài dòng làm mẫu.
     */
    private HolidayImpactResponse summarize(List<Schedule> affected) {
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
        return new HolidayImpactResponse(future.size(), teachers.size(), first, last, past, samplesOf(future));
    }

    /**
     * {@value #SAMPLE_SIZE} buổi sớm nhất, đã ghép tên giáo viên và tên trường.
     *
     * <p>Chỉ tra tên cho ĐÚNG chừng ấy dòng: danh sách đầy đủ có thể là vài trăm buổi, mà hộp
     * thoại chỉ đọc được vài dòng đầu — nạp hết chỉ để vứt đi là trả tiền cho thứ không ai thấy.
     */
    private List<HolidayImpactResponse.Session> samplesOf(List<Schedule> future) {
        List<Schedule> head = future.stream()
                .sorted(java.util.Comparator.comparing(Schedule::getStartTime))
                .limit(SAMPLE_SIZE)
                .toList();
        if (head.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> names = new HashMap<>();
        for (Teacher t : teacherRepo.findAllById(
                head.stream().map(Schedule::getTeacherId).distinct().toList())) {
            names.put(t.getId(), (t.getLastName() + " " + t.getFirstName()).trim());
        }
        Map<Long, Integer> schoolBySchedule = schoolOfSchedules(head);
        Map<Integer, String> schoolNames = new HashMap<>();
        for (School s : schoolRepo.findAllById(schoolBySchedule.values().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList())) {
            schoolNames.put(s.getId(), s.getName());
        }

        List<HolidayImpactResponse.Session> out = new ArrayList<>();
        for (Schedule s : head) {
            out.add(new HolidayImpactResponse.Session(
                    s.getStartTime().toLocalDate(),
                    s.getStartTime().toLocalTime(),
                    names.getOrDefault(s.getTeacherId(), "(GV #" + s.getTeacherId() + ")"),
                    schoolNames.getOrDefault(schoolBySchedule.get(s.getId()), "(không rõ trường)")));
        }
        return out;
    }

    /* ──────────────── SỬA DÒNG VẮNG GIẢ CỦA BUỔI ĐÃ QUA ──────────────── */

    /**
     * Các dòng chấm công VẮNG do job nền tự ghi cho buổi rơi vào kỳ nghỉ này.
     *
     * <p>{@link #cancelSessions(Integer)} chỉ cứu được buổi CHƯA diễn ra. Buổi đã qua thì
     * {@code AttendanceSweepService} ghi Vắng mất rồi, và dòng vắng đó KHÔNG biến mất khi buổi
     * bị hủy — nó nằm lại trong hồ sơ chuyên cần của giáo viên, còn job thì đã kịp nhắn cho họ
     * là "bạn vắng buổi này".
     *
     * <p>Chỉ lấy dòng nguồn SYSTEM: dòng kế toán ghi tay là một phán quyết có người chịu trách
     * nhiệm (giáo viên vẫn phải dạy bù hôm đó mà bỏ), không được đè lên.
     */
    @Transactional(readOnly = true)
    public HolidayAbsenceResponse absences(Integer id) {
        Holiday h = getOrThrow(id);
        List<Attendance> candidates =
                scopeFilter(h, attendanceRepo.findSystemAbsencesBetween(h.getFromDate(), h.getToDate()));

        Map<Integer, String> names = teacherNames(candidates);
        List<HolidayAbsenceResponse.Row> rows = new ArrayList<>();
        int lockedCount = 0;
        Set<String> lockedPeriods = new LinkedHashSet<>();

        for (Attendance a : candidates) {
            // Kỳ lương đã chốt thì mọi thao tác ghi lên chấm công bị chặn (assertPeriodOpen).
            // Tách ra báo riêng, thay vì để người dùng bấm rồi ăn lỗi 409 không hiểu vì sao.
            if (attendanceService.isPeriodLocked(a.getTeacherId(), a.getWorkDate())) {
                lockedCount++;
                lockedPeriods.add(
                        a.getWorkDate().getMonthValue() + "/" + a.getWorkDate().getYear());
                continue;
            }
            rows.add(new HolidayAbsenceResponse.Row(
                    a.getId(),
                    a.getTeacherId(),
                    names.getOrDefault(a.getTeacherId(), "(GV #" + a.getTeacherId() + ")"),
                    a.getWorkDate(),
                    a.getScheduleId(),
                    a.getNote()));
        }
        return new HolidayAbsenceResponse(rows, lockedCount, new ArrayList<>(lockedPeriods));
    }

    /**
     * Chuyển các dòng Vắng đã chọn sang NGHỈ PHÉP.
     *
     * <p>Vì sao là Nghỉ phép chứ không phải Có mặt: buổi đó KHÔNG diễn ra. Đánh Có mặt là khai
     * khống một tiết dạy và cộng thêm tiền cho buổi chưa từng tồn tại (PayrollService trả tiền
     * theo dòng PRESENT/LATE). Nghỉ phép cũng không được tính tiết nên số tiền giữ nguyên —
     * việc này chỉ làm sạch hồ sơ chuyên cần, không đụng tới lương.
     *
     * <p>Danh sách id gửi lên được LỌC LẠI theo đúng luật của {@link #absences(Integer)} chứ
     * không tin thẳng: id có thể đã cũ (kỳ lương vừa bị chốt trong lúc người dùng đang xem)
     * hoặc bị sửa tay để lôi vào một dòng chấm công không liên quan.
     *
     * @return số dòng đã sửa
     */
    @Transactional
    public int fixAbsences(Integer id, HolidayFixAbsencesRequest req) {
        Holiday h = getOrThrow(id);
        Set<Long> allowed = absences(id).rows().stream()
                .map(HolidayAbsenceResponse.Row::attendanceId)
                .collect(Collectors.toSet());

        Integer userId = SecurityUtils.currentUserId();
        String reason = req.reason().trim();
        Map<Integer, Integer> fixedByTeacher = new LinkedHashMap<>();
        int fixed = 0;

        for (Long attendanceId : req.attendanceIds()) {
            if (attendanceId == null || !allowed.contains(attendanceId)) {
                continue;
            }
            Attendance a = attendanceRepo.findById(attendanceId).orElse(null);
            if (a == null) {
                continue;
            }
            a.setStatus("LEAVE");
            a.setAdjustReason(reason);
            a.setUpdatedBy(userId);
            a.setUpdatedAt(Instant.now());
            // Trigger TR_Attendance_ChangeLog (V24) tự ghi vết theo UpdatedBy — không cần
            // chép tay vào AttendanceChangeLog ở đây.
            attendanceRepo.save(a);
            fixedByTeacher.merge(a.getTeacherId(), 1, Integer::sum);
            fixed++;
        }

        // Job nền đã nhắn cho giáo viên "buổi dạy chưa được chấm công". Không đóng lại vòng
        // thông tin đó thì người bị ghi oan không bao giờ biết là đã được xử lý.
        fixedByTeacher.forEach((teacherId, count) -> notificationService.publishToTeacher(
                teacherId,
                "Đã điều chỉnh chấm công ngày nghỉ",
                count + " buổi bị ghi Vắng nhầm trong kỳ nghỉ " + h.getName()
                        + " đã được chuyển thành Nghỉ phép — hôm đó trường không hoạt động.",
                "ATTENDANCE",
                "Attendance",
                null,
                false));
        return fixed;
    }

    /* ──────────────── CẢNH BÁO CHO BẢNG LƯƠNG ──────────────── */

    /**
     * Kỳ lương {@code month/year} còn dòng Vắng nào rơi vào ngày nghỉ không.
     *
     * <p>Dùng cho cảnh báo TRƯỚC khi chốt lương: chốt xong là chấm công của kỳ bị khóa, tức là
     * khóa luôn lỗi vào trong. Trả kèm id kỳ nghỉ để màn hình trỏ thẳng người dùng sang chỗ
     * sửa — phát hiện mà không chỉ được đường sửa thì cảnh báo chỉ làm người ta bực.
     */
    @Transactional(readOnly = true)
    public PayrollHolidayIssueResponse holidayIssues(short year, short month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        List<Holiday> holidays = holidayRepo.findOverlapping(from, to);
        List<Attendance> absences = holidays.isEmpty() ? List.of() : attendanceRepo.findSystemAbsencesBetween(from, to);
        if (absences.isEmpty()) {
            return new PayrollHolidayIssueResponse(0, 0, List.of());
        }
        Map<Long, Integer> schoolByAttendance = schoolOfAttendances(absences);

        Map<Integer, Integer> countByHoliday = new LinkedHashMap<>();
        Map<Integer, String> nameByHoliday = new HashMap<>();
        Set<Integer> teachers = new HashSet<>();
        int total = 0;

        for (Attendance a : absences) {
            Integer schoolId = schoolByAttendance.get(a.getId());
            Holiday hit = holidays.stream()
                    .filter(h -> h.getSchoolId() == null || h.getSchoolId().equals(schoolId))
                    .filter(h -> !a.getWorkDate().isBefore(h.getFromDate())
                            && !a.getWorkDate().isAfter(h.getToDate()))
                    .findFirst()
                    .orElse(null);
            if (hit == null) {
                continue; // dòng vắng thật, không dính ngày nghỉ
            }
            countByHoliday.merge(hit.getId(), 1, Integer::sum);
            nameByHoliday.putIfAbsent(hit.getId(), hit.getName());
            teachers.add(a.getTeacherId());
            total++;
        }

        List<PayrollHolidayIssueResponse.HolidayRef> refs = countByHoliday.entrySet().stream()
                .map(e -> new PayrollHolidayIssueResponse.HolidayRef(
                        e.getKey(), nameByHoliday.get(e.getKey()), e.getValue()))
                .toList();
        return new PayrollHolidayIssueResponse(total, teachers.size(), refs);
    }

    /* ─────────────────────────── PRIVATE ─────────────────────────── */

    /**
     * Buổi dạy còn hiệu lực nằm trong khoảng ngày của kỳ nghỉ, đã lọc theo phạm vi trường.
     *
     * <p>Nhận thẳng ba giá trị thay vì nhận một {@link Holiday}: hộp xác nhận lúc THÊM kỳ nghỉ
     * phải hỏi được câu này khi kỳ nghỉ chưa có trong DB. Truyền entity thì chỗ đó buộc phải
     * dựng một Holiday giả chỉ để gọi hàm — một đối tượng không tương ứng với dòng nào.
     *
     * <p>Trường của một buổi lấy từ Ô THỜI KHÓA BIỂU sinh ra nó (V27), không phải trường cấp
     * phiếu: một phiếu nay trải được nhiều trường, mà kỳ nghỉ riêng chỉ thuộc về một trường.
     *
     * @param schoolId phạm vi kỳ nghỉ; {@code null} = toàn hệ thống
     */
    private List<Schedule> affectedSchedules(LocalDate fromDate, LocalDate toDate, Integer schoolId) {
        LocalDateTime from = fromDate.atStartOfDay();
        // findBy...Between sinh ra SQL BETWEEN, tức là ĐÓNG CẢ HAI ĐẦU. Dùng
        // toDate.plusDays(1).atStartOfDay() thì buổi bắt đầu đúng 00:00:00 của NGÀY SAU kỳ
        // nghỉ cũng bị tính là bị ảnh hưởng.
        LocalDateTime to = toDate.atTime(LocalTime.MAX);
        List<Schedule> inRange = scheduleRepo.findByStartTimeBetweenAndDeletedFalse(from, to).stream()
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .toList();
        if (schoolId == null || inRange.isEmpty()) {
            return inRange;
        }
        Map<Long, Integer> schoolBySchedule = schoolOfSchedules(inRange);
        List<Schedule> out = new ArrayList<>();
        for (Schedule s : inRange) {
            if (schoolId.equals(schoolBySchedule.get(s.getId()))) {
                out.add(s);
            }
        }
        return out;
    }

    /** scheduleId → trường của buổi, đi qua ô thời khóa biểu sinh ra nó (V27). */
    private Map<Long, Integer> schoolOfSchedules(List<Schedule> rows) {
        Map<Integer, Integer> schoolBySlot = new HashMap<>();
        for (AssignmentSlot slot : slotRepo.findAllById(rows.stream()
                .map(Schedule::getSourceSlotId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList())) {
            schoolBySlot.put(slot.getId(), slot.getSchoolId());
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Schedule s : rows) {
            out.put(s.getId(), s.getSourceSlotId() == null ? null : schoolBySlot.get(s.getSourceSlotId()));
        }
        return out;
    }

    /** Giữ lại các dòng chấm công thuộc phạm vi trường của kỳ nghỉ (null = toàn hệ thống). */
    private List<Attendance> scopeFilter(Holiday h, List<Attendance> rows) {
        if (h.getSchoolId() == null || rows.isEmpty()) {
            return rows;
        }
        Map<Long, Integer> schoolByAttendance = schoolOfAttendances(rows);
        List<Attendance> out = new ArrayList<>();
        for (Attendance a : rows) {
            if (h.getSchoolId().equals(schoolByAttendance.get(a.getId()))) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * attendanceId → trường của buổi dạy tương ứng.
     *
     * <p>Đi đường buổi dạy → Ô THỜI KHÓA BIỂU sinh ra nó (V27), cùng lý do với {@link
     * #affectedSchedules(Holiday)}: một phiếu trải được nhiều trường nên trường cấp phiếu
     * không nói lên buổi đó thuộc trường nào.
     */
    private Map<Long, Integer> schoolOfAttendances(List<Attendance> rows) {
        List<Long> scheduleIds = rows.stream()
                .map(Attendance::getScheduleId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Integer> slotBySchedule = new HashMap<>();
        for (Schedule s : scheduleRepo.findAllById(scheduleIds)) {
            if (s.getSourceSlotId() != null) {
                slotBySchedule.put(s.getId(), s.getSourceSlotId());
            }
        }
        Map<Integer, Integer> schoolBySlot = new HashMap<>();
        for (AssignmentSlot slot :
                slotRepo.findAllById(slotBySchedule.values().stream().distinct().toList())) {
            schoolBySlot.put(slot.getId(), slot.getSchoolId());
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Attendance a : rows) {
            Integer slotId = slotBySchedule.get(a.getScheduleId());
            out.put(a.getId(), slotId == null ? null : schoolBySlot.get(slotId));
        }
        return out;
    }

    private Map<Integer, String> teacherNames(List<Attendance> rows) {
        List<Integer> ids =
                rows.stream().map(Attendance::getTeacherId).distinct().toList();
        Map<Integer, String> out = new HashMap<>();
        for (Teacher t : teacherRepo.findAllById(ids)) {
            out.put(t.getId(), (t.getLastName() + " " + t.getFirstName()).trim());
        }
        return out;
    }

    /**
     * Ba rào chắn của một kỳ nghỉ hợp lệ. Tách khỏi {@link #apply} vì hộp xác nhận trước khi
     * lưu cũng phải chạy qua chúng: đếm hậu quả cho một khoảng ngày ngược đầu hay dài 400 ngày
     * là trả lời một câu hỏi vô nghĩa, và người dùng sẽ gặp lỗi ở bước sau khi đã bấm đồng ý.
     */
    private void assertHopLe(HolidayRequest req) {
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
    }

    private void apply(Holiday h, HolidayRequest req) {
        assertHopLe(req);
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
