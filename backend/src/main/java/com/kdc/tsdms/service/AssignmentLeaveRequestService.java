package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.LeaveRequestCreateRequest;
import com.kdc.tsdms.dto.LeaveRequestResponse;
import com.kdc.tsdms.dto.LeaveRequestSessionOption;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentLeaveRequest;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentLeaveRequestRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ĐƠN XIN NGHỈ MỘT BUỔI DẠY (bảng AssignmentLeaveRequest, V39) — giáo viên gửi, admin duyệt.
 *
 * <p><b>Duyệt đơn KHÔNG hủy phân công.</b> Bản V39 gọi thẳng
 * {@code AssignmentService.cancel} nên duyệt một đơn xin nghỉ ngày thứ Ba là giết cả phiếu
 * phân công từ ngày đó tới cuối kỳ: giáo viên xin nghỉ một buổi ốm, tỉnh dậy thì mất lớp.
 * Nay duyệt đơn chỉ đụng đúng buổi hôm ấy:
 *
 * <ul>
 *   <li>{@code Schedule.Status = 'CANCELLED'} + {@code CancelKind = 'LEAVE'} — buổi biến khỏi
 *       lịch dạy nhưng nói được VÌ SAO, để màn hình hiện "Nghỉ có phép" chứ không phải một chữ
 *       "Đã hủy" chung với buổi bị admin cắt (nhãn tính ở tầng service, không lưu DB — đúng lối
 *       V39/V40 đã chọn khi từ chối nới CHECK constraint);
 *   <li>{@code Attendance.Status = 'LEAVE'} — buổi không diễn ra thì không được ghi Có mặt
 *       (khai khống một tiết) mà cũng không được ghi Vắng (oan cho người đã xin phép). Dòng
 *       Nghỉ phép không nằm trong nhóm PRESENT/LATE nên {@code PayrollService} không trả tiền
 *       cho nó;
 *   <li>{@code Assignment} giữ nguyên — tuần sau giáo viên vẫn dạy lớp ấy, khung Thứ+Tiết vẫn
 *       do phiếu này giữ, không ai xếp đè vào.
 * </ul>
 *
 * <p>Việc dừng hẳn một phân công vẫn còn nguyên ở nút Hủy của admin
 * ({@code AssignmentService.cancel}) — hai nghiệp vụ khác nhau thì hai đường, không dùng chung.
 * Chức năng bố trí người DẠY THAY nằm ngoài phạm vi đợt này.
 */
@Service
public class AssignmentLeaveRequestService {

    private static final DateTimeFormatter NGAY_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter GIO_VN = DateTimeFormatter.ofPattern("HH:mm");

    /** Buổi dạy đã được duyệt mới xin nghỉ được — buổi chờ/bị hủy thì không có gì để nghỉ. */
    private static final String SCHEDULE_APPROVED = "APPROVED";

    private static final String SCHEDULE_CANCELLED = "CANCELLED";

    /** Lý do hủy buổi, ghi vào {@code Schedule.CancelKind} — xem bảng từ vựng ở V40. */
    private static final String CANCEL_KIND_LEAVE = "LEAVE";

    /**
     * Ô chọn "xin nghỉ buổi nào" chỉ trải bấy nhiêu ngày tới.
     *
     * <p>Bốn tuần là đủ xa cho mọi lý do xin nghỉ có thật (ốm, việc nhà, đi họp, cưới hỏi) mà
     * vẫn đủ gần để danh sách còn đọc được: một giáo viên dạy 3 buổi/tuần, trải cả học kỳ là
     * hơn 50 dòng cho một ô chọn.
     */
    private static final int SO_NGAY_CHON = 28;

    private final AssignmentLeaveRequestRepository repo;
    private final AssignmentRepository assignmentRepo;
    private final AssignmentSlotRepository slotRepo;
    private final ScheduleRepository scheduleRepo;
    private final AttendanceRepository attendanceRepo;
    private final PeriodRepository periodRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final AssignmentApprovalService approvalService;
    private final NotificationService notificationService;

    public AssignmentLeaveRequestService(
            AssignmentLeaveRequestRepository repo,
            AssignmentRepository assignmentRepo,
            AssignmentSlotRepository slotRepo,
            ScheduleRepository scheduleRepo,
            AttendanceRepository attendanceRepo,
            PeriodRepository periodRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            SubjectRepository subjectRepo,
            AssignmentApprovalService approvalService,
            NotificationService notificationService) {
        this.repo = repo;
        this.assignmentRepo = assignmentRepo;
        this.slotRepo = slotRepo;
        this.scheduleRepo = scheduleRepo;
        this.attendanceRepo = attendanceRepo;
        this.periodRepo = periodRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.approvalService = approvalService;
        this.notificationService = notificationService;
    }

    /* ═══════════════════════ GIÁO VIÊN ═══════════════════════ */

    /**
     * Các BUỔI DẠY sắp tới của chính người đang đăng nhập — nguồn cho ô chọn "xin nghỉ buổi nào".
     *
     * <p>Gộp theo (phân công, ngày) chứ không theo từng buổi: đơn được nhận diện bằng cặp đó
     * (bảng V39 không có cột ScheduleId), nên hai tiết cùng ngày của cùng một phiếu phải là MỘT
     * lựa chọn — bày ra hai dòng thì chọn dòng nào cũng dẫn tới cùng một đơn và người dùng
     * tưởng mình chỉ nghỉ một tiết.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestSessionOption> mySessions() {
        Teacher me = currentTeacher();
        LocalDateTime tu = BusinessTime.now();
        LocalDateTime den = tu.toLocalDate().plusDays(SO_NGAY_CHON).atTime(23, 59, 59);

        // Chỉ phiếu ĐANG DẠY của chính giáo viên mới xin nghỉ được; nạp một lần để vừa lọc buổi
        // vừa lấy được lớp/môn mà không hỏi lại DB cho từng buổi.
        Map<Integer, Assignment> phieu = new LinkedHashMap<>();
        for (Assignment a : assignmentRepo.findByTeacherIdAndDeletedFalseOrderByIdDesc(me.getId())) {
            if (AssignmentStatus.ACTIVE.equals(a.getStatus())) {
                phieu.put(a.getId(), a);
            }
        }
        if (phieu.isEmpty()) {
            return List.of();
        }
        Set<Integer> dangCho = pendingAssignmentIds();

        // Gom trước theo khoá (phân công|ngày) rồi mới dựng dòng: có gom mới biết hôm đó phiếu
        // này có mấy tiết để nói "và 1 tiết nữa" — người chọn phải thấy mình đang xin nghỉ
        // những gì, không phải chỉ tiết đầu tiên.
        Map<String, List<Schedule>> gop = new LinkedHashMap<>();
        for (Schedule s : scheduleRepo.findByTeacherIdAndStatusAndStartTimeBetweenAndDeletedFalseOrderByStartTime(
                me.getId(), SCHEDULE_APPROVED, tu, den)) {
            if (phieu.containsKey(s.getAssignmentId())) {
                gop.computeIfAbsent(s.getAssignmentId() + "|" + s.getStartTime().toLocalDate(), k -> new ArrayList<>())
                        .add(s);
            }
        }

        List<LeaveRequestSessionOption> ketQua = new ArrayList<>();
        MoTaBuoi moTa = new MoTaBuoi();
        for (Map.Entry<String, List<Schedule>> e : gop.entrySet()) {
            List<Schedule> buoi = e.getValue();
            Schedule dau = buoi.get(0);
            Assignment a = phieu.get(dau.getAssignmentId());
            String mo = moTa.of(dau) + (buoi.size() > 1 ? " và " + (buoi.size() - 1) + " tiết nữa cùng ngày" : "");
            ketQua.add(new LeaveRequestSessionOption(
                    e.getKey(),
                    a.getId(),
                    dau.getStartTime().toLocalDate(),
                    mo,
                    schoolName(moTa.schoolIdOf(dau, a)),
                    className(moTa.classIdOf(dau, a)),
                    subjectName(a.getSubjectId()),
                    dangCho.contains(a.getId())));
        }
        ketQua.sort(Comparator.comparing(LeaveRequestSessionOption::date));
        return ketQua;
    }

    /** Giáo viên gửi đơn xin nghỉ MỘT BUỔI của CHÍNH MÌNH. */
    @Transactional
    public LeaveRequestResponse create(LeaveRequestCreateRequest req) {
        Teacher me = currentTeacher();
        Assignment a = assignmentRepo
                .findByIdAndDeletedFalse(req.assignmentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công."));
        if (!me.getId().equals(a.getTeacherId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Phân công này không thuộc về thầy/cô.");
        }
        if (!AssignmentStatus.ACTIVE.equals(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chỉ xin nghỉ được buổi của phân công ĐANG DẠY. Phân công chưa xác nhận thì thầy/cô từ chối"
                            + " thẳng ở lời mời trong chuông thông báo.");
        }
        LocalDate homNay = BusinessTime.today();
        if (req.leaveDate().isBefore(homNay)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Không xin nghỉ lùi cho buổi đã qua. Buổi đã dạy hoặc đã bị ghi Vắng thì báo trung tâm"
                            + " chỉnh lại ở bảng chấm công.");
        }
        // Chốt chặn quan trọng nhất: đơn phải trỏ vào một buổi CÓ THẬT trong lịch. Không kiểm ở
        // đây thì đơn nằm chờ đến lúc duyệt mới lòi ra là chẳng có gì để tắt, mà lúc ấy người
        // duyệt không biết phải trả lời giáo viên thế nào.
        List<Schedule> buoi = liveSessions(a.getId(), req.leaveDate());
        if (buoi.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày " + req.leaveDate().format(NGAY_VN)
                            + " thầy/cô không có buổi dạy nào của phân công này (hoặc buổi đó đã bị hủy).");
        }
        // Chặn ở tầng service để người dùng nhận câu tiếng Việt, thay vì để index
        // UX_AssignmentLeaveRequest_Pending bung ra lỗi SQL thô.
        repo.findFirstByAssignmentIdAndStatus(a.getId(), AssignmentLeaveRequest.PENDING)
                .ifPresent(cu -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "Thầy/cô đã gửi đơn xin nghỉ buổi ngày "
                                    + cu.getLeaveDate().format(NGAY_VN)
                                    + " của phân công này và đơn đang chờ trung tâm xử lý.");
                });

        AssignmentLeaveRequest don = new AssignmentLeaveRequest();
        don.setAssignmentId(a.getId());
        don.setTeacherId(me.getId());
        don.setLeaveDate(req.leaveDate());
        don.setReason(req.reason().trim());
        don.setStatus(AssignmentLeaveRequest.PENDING);
        don.setCreatedBy(SecurityUtils.currentUserId());
        repo.save(don);

        approvalService.notifyAdminsLeaveRequested(a, don.getId(), don.getLeaveDate(), don.getReason());
        return toResponse(don, a);
    }

    /** "Đơn của tôi" — màn giáo viên, mới nhất lên đầu. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> mine() {
        return repo.findByTeacherIdOrderByIdDesc(currentTeacher().getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /* ═══════════════════════ ADMIN ═══════════════════════ */

    /** Hàng đợi đơn đang chờ xử lý. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> pending() {
        return repo.findByStatusOrderByIdDesc(AssignmentLeaveRequest.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Một đơn theo id — trang Phân công gọi khi được điều hướng từ dòng thông báo
     * ({@code /assignments?leaveRequest=<id>}) để mở thẳng hộp thoại duyệt.
     *
     * <p>Không dùng lại {@link #pending()} rồi lọc ở trình duyệt: đơn có thể đã được người khác
     * xử lý trong lúc thông báo nằm trong chuông, và khi ấy màn hình phải nói "đơn này đã được
     * duyệt/từ chối rồi" chứ không phải im lặng không mở gì cả.
     */
    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(Integer id) {
        return toResponse(repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn xin nghỉ.")));
    }

    /**
     * DUYỆT đơn → tắt đúng buổi dạy hôm ấy và ghi chấm công Nghỉ phép.
     *
     * <p>Ghi sẵn dòng chấm công thay vì để trống còn có tác dụng phụ đáng giá: job quét
     * {@code AttendanceSweepService} thấy buổi đã hết giờ mà không có dòng chấm công nào thì tự
     * ghi VẮNG. Không đặt sẵn Nghỉ phép ở đây thì người đã được duyệt đơn vẫn bị máy ghi vắng
     * vào tối hôm đó.
     */
    @Transactional
    public LeaveRequestResponse approve(Integer id, String note) {
        AssignmentLeaveRequest don = pendingOrThrow(id);
        Assignment a = liveAssignment(don);
        Integer userId = SecurityUtils.currentUserId();

        List<Schedule> buoi = liveSessions(don.getAssignmentId(), don.getLeaveDate());
        if (buoi.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Buổi dạy ngày " + don.getLeaveDate().format(NGAY_VN)
                            + " không còn trong lịch (đã bị hủy hoặc xóa) — không còn gì để duyệt nghỉ.");
        }
        String lyDo = "Nghỉ có phép — đơn #" + don.getId() + ": " + don.getReason();
        for (Schedule s : buoi) {
            capNhatChamCong(s, lyDo, userId);
            s.setUpdatedBy(userId); // trigger TR_Schedule_StatusLog đọc cột này
            s.setStatus(SCHEDULE_CANCELLED);
            s.setCancelKind(CANCEL_KIND_LEAVE);
            s.setUpdatedAt(Instant.now());
            scheduleRepo.save(s);
        }

        decide(don, AssignmentLeaveRequest.APPROVED, note);
        approvalService.notifyTeacherLeaveDecision(a, don.getLeaveDate(), true, note);
        return toResponse(don, a);
    }

    /** TỪ CHỐI đơn — bắt buộc nêu lý do, vì giáo viên phải biết mình vẫn phải đi dạy và vì sao. */
    @Transactional
    public LeaveRequestResponse reject(Integer id, String note) {
        if (note == null || note.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối đơn.");
        }
        AssignmentLeaveRequest don = pendingOrThrow(id);
        Assignment a = liveAssignment(don);
        decide(don, AssignmentLeaveRequest.REJECTED, note);
        approvalService.notifyTeacherLeaveDecision(a, don.getLeaveDate(), false, note);
        return toResponse(don, a);
    }

    /* ═══════════════════════ helpers ═══════════════════════ */

    /**
     * Ghi/sửa dòng chấm công của buổi được duyệt nghỉ sang NGHỈ PHÉP.
     *
     * <p>Có dòng rồi thì sửa (giữ nguyên id để trigger {@code TR_Attendance_ChangeLog} ghi được
     * vết "ai đổi, từ gì sang gì"); chưa có thì tạo mới với nguồn {@code EMPLOYEE} — đây là phán
     * quyết của người trực trung tâm chứ không phải hệ quả máy móc của một job nền, và chính chỗ
     * ghi nguồn này là thứ {@code AttendanceRepository.findSystemAbsencesBetween} dùng để phân
     * biệt hai loại.
     */
    private void capNhatChamCong(Schedule s, String lyDo, Integer userId) {
        Attendance att =
                attendanceRepo.findFirstByScheduleIdOrderByIdAsc(s.getId()).orElse(null);
        if (att == null) {
            att = new Attendance();
            att.setTeacherId(s.getTeacherId());
            att.setScheduleId(s.getId());
            att.setWorkDate(s.getStartTime().toLocalDate());
            att.setCheckInMethod("EMPLOYEE");
            att.setCreatedBy(userId);
        }
        att.setStatus("LEAVE");
        att.setAdjustReason(lyDo);
        att.setUpdatedBy(userId);
        att.setUpdatedAt(Instant.now());
        attendanceRepo.save(att);
    }

    /**
     * Các buổi CÒN HIỆU LỰC của một phân công trong đúng một ngày.
     *
     * <p>Lọc trong Java trên {@code findByAssignmentIdAndDeletedFalse} thay vì thêm một câu
     * truy vấn theo ngày: một phiếu chỉ có vài chục buổi, không đáng để mở rộng
     * {@code ScheduleRepository} — file đó đang do phần Lịch nghỉ giữ trong đợt này.
     */
    private List<Schedule> liveSessions(Integer assignmentId, LocalDate ngay) {
        List<Schedule> ketQua = new ArrayList<>();
        for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(assignmentId)) {
            if (!SCHEDULE_CANCELLED.equals(s.getStatus())
                    && s.getStartTime().toLocalDate().equals(ngay)) {
                ketQua.add(s);
            }
        }
        ketQua.sort(Comparator.comparing(Schedule::getStartTime));
        return ketQua;
    }

    /** Các phân công đang có đơn chờ — để ô chọn khoá sẵn đúng những buổi không gửi thêm được. */
    private Set<Integer> pendingAssignmentIds() {
        return repo.findByStatusOrderByIdDesc(AssignmentLeaveRequest.PENDING).stream()
                .map(AssignmentLeaveRequest::getAssignmentId)
                .collect(Collectors.toSet());
    }

    private void decide(AssignmentLeaveRequest don, String status, String note) {
        Integer userId = SecurityUtils.currentUserId();
        don.setStatus(status);
        don.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        don.setDecidedByUserId(userId);
        don.setDecidedAt(Instant.now());
        don.setUpdatedAt(Instant.now());
        don.setUpdatedBy(userId);
        repo.save(don);
        // Đơn được phát cho MỌI người có quyền quản lý phân công → đóng việc trên chuông của tất
        // cả, không riêng người vừa bấm, để dòng thông báo hiện ngay "đã duyệt / đã không duyệt".
        notificationService.closePendingActions(
                "AssignmentLeaveRequest",
                don.getId().longValue(),
                AssignmentLeaveRequest.APPROVED.equals(status) ? "CONFIRMED" : "CANCELLED");
    }

    private AssignmentLeaveRequest pendingOrThrow(Integer id) {
        AssignmentLeaveRequest don =
                repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn."));
        if (!AssignmentLeaveRequest.PENDING.equals(don.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Đơn này đã được xử lý.");
        }
        return don;
    }

    private Assignment liveAssignment(AssignmentLeaveRequest don) {
        return assignmentRepo
                .findByIdAndDeletedFalse(don.getAssignmentId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "Phân công của đơn này đã bị xóa — đơn không còn hiệu lực."));
    }

    private Teacher currentTeacher() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN, "Tài khoản này chưa được liên kết với hồ sơ giáo viên."));
    }

    private LeaveRequestResponse toResponse(AssignmentLeaveRequest don) {
        return toResponse(don, assignmentRepo.findById(don.getAssignmentId()).orElse(null));
    }

    private LeaveRequestResponse toResponse(AssignmentLeaveRequest don, Assignment a) {
        String teacherName = teacherRepo
                .findById(don.getTeacherId())
                .map(t -> (t.getLastName() + " " + t.getFirstName()).trim())
                .orElse("Giáo viên #" + don.getTeacherId());
        if (a == null) {
            return LeaveRequestResponse.fromEntity(don, teacherName, null, null, null, null);
        }
        // Buổi đã bị tắt lúc duyệt nên đơn ĐÃ DUYỆT không còn buổi "còn hiệu lực" nào: tra cả
        // buổi đã hủy để hộp thoại vẫn kể được lớp nào, tiết nào — nếu không thì mở lại một đơn
        // vừa duyệt xong sẽ thấy trống trơn.
        Schedule buoi = scheduleRepo.findByAssignmentIdAndDeletedFalse(a.getId()).stream()
                .filter(s -> s.getStartTime().toLocalDate().equals(don.getLeaveDate()))
                .min(Comparator.comparing(Schedule::getStartTime))
                .orElse(null);
        MoTaBuoi moTa = new MoTaBuoi();
        return LeaveRequestResponse.fromEntity(
                don,
                teacherName,
                schoolName(buoi == null ? a.getSchoolId() : moTa.schoolIdOf(buoi, a)),
                subjectName(a.getSubjectId()),
                className(buoi == null ? a.getClassId() : moTa.classIdOf(buoi, a)),
                buoi == null ? null : moTa.of(buoi));
    }

    private String schoolName(Integer id) {
        return id == null ? null : schoolRepo.findById(id).map(School::getName).orElse(null);
    }

    private String className(Integer id) {
        return id == null
                ? null
                : classRepo.findById(id).map(SchoolClass::getName).orElse(null);
    }

    private String subjectName(Integer id) {
        return id == null
                ? null
                : subjectRepo.findById(id).map(Subject::getName).orElse(null);
    }

    /**
     * Dựng câu mô tả một buổi dạy ("Thứ 2, 08/09/2026 · Sáng · Tiết 3 (07:00–07:45)") và tra
     * lớp/trường THẬT của buổi đó.
     *
     * <p>Lớp và trường phải lấy theo Ô THỜI KHÓA BIỂU sinh ra buổi ({@code SourceSlotId}) chứ
     * không theo phiếu: từ V16/V27 một phiếu trải nhiều lớp và nhiều trường, đọc cấp phiếu là
     * dán nhầm tên lớp lên đơn xin nghỉ — đúng thứ người duyệt cần chính xác nhất.
     *
     * <p>Không phải bean Spring: mỗi lần dựng một danh sách thì tạo một thể hiện và đệm tiết/ô
     * trong phạm vi lần đó, cùng lối {@link PeriodSessionIndex}.
     */
    private final class MoTaBuoi {

        private final PeriodSessionIndex tietTrongBuoi = new PeriodSessionIndex(periodRepo);
        private final Map<Integer, AssignmentSlot> demO = new LinkedHashMap<>();

        String of(Schedule s) {
            StringBuilder sb = new StringBuilder();
            sb.append(thuVN(s.getStartTime().toLocalDate()))
                    .append(", ")
                    .append(s.getStartTime().toLocalDate().format(NGAY_VN));
            Period p = s.getPeriodId() == null
                    ? null
                    : periodRepo.findById(s.getPeriodId()).orElse(null);
            if (p != null) {
                tietTrongBuoi.napTruoc(List.of(p));
                Short trongBuoi = tietTrongBuoi.of(p);
                sb.append(" · ")
                        .append("AFTERNOON".equals(p.getSessionType()) ? "Chiều" : "Sáng")
                        .append(" · Tiết ")
                        .append(trongBuoi != null ? trongBuoi : p.getPeriodNumber());
            }
            sb.append(" (")
                    .append(s.getStartTime().format(GIO_VN))
                    .append("–")
                    .append(s.getEndTime().format(GIO_VN))
                    .append(")");
            return sb.toString();
        }

        Integer classIdOf(Schedule s, Assignment a) {
            AssignmentSlot o = o(s);
            return o != null && o.getClassId() != null ? o.getClassId() : a.getClassId();
        }

        Integer schoolIdOf(Schedule s, Assignment a) {
            AssignmentSlot o = o(s);
            return o != null && o.getSchoolId() != null ? o.getSchoolId() : a.getSchoolId();
        }

        private AssignmentSlot o(Schedule s) {
            if (s.getSourceSlotId() == null) {
                return null;
            }
            return demO.computeIfAbsent(
                    s.getSourceSlotId(), id -> slotRepo.findById(id).orElse(null));
        }
    }

    /** "Thứ 2".."Chủ nhật" — cùng bộ chữ với {@code AssignmentSlotResponse.mapDayLabel}. */
    private static String thuVN(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ nhật";
        };
    }
}
