package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.AttendanceRequest;
import com.kdc.tsdms.dto.AttendanceResponse;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Chấm công (Attendance).
 *
 * <p>Nguồn buổi dạy = {@link Schedule} đã duyệt. Có thể "sinh" chấm công hàng loạt từ lịch
 * (mỗi buổi 1 dòng, mặc định PRESENT theo giờ tiết) rồi kế toán chỉnh lại; hoặc ghi/sửa lẻ.
 * Số giờ dạy tự tính từ giờ vào/ra — làm đầu vào cho Bảng lương.
 */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepo;
    private final ScheduleRepository scheduleRepo;
    private final TeacherRepository teacherRepo;

    public AttendanceService(
            AttendanceRepository attendanceRepo, ScheduleRepository scheduleRepo, TeacherRepository teacherRepo) {
        this.attendanceRepo = attendanceRepo;
        this.scheduleRepo = scheduleRepo;
        this.teacherRepo = teacherRepo;
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> list(Integer teacherId, LocalDate from, LocalDate to) {
        // CHỐNG IDOR: GV (có ATTENDANCE_VIEW nhưng không phải staff) chỉ xem chấm công
        // của CHÍNH MÌNH — ép teacherId về hồ sơ người gọi, bỏ qua tham số client gửi.
        Integer scoped = scopedTeacherId(teacherId);
        List<Attendance> items = scoped != null
                ? attendanceRepo.findByTeacherIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(scoped, from, to)
                : attendanceRepo.findByWorkDateBetweenOrderByWorkDateDescIdDesc(from, to);
        Map<Integer, String> nameCache = new HashMap<>();
        return items.stream()
                .map(a -> AttendanceResponse.fromEntity(a, teacherName(a.getTeacherId(), nameCache)))
                .toList();
    }

    /** Staff xem tất (teacherId lọc tùy chọn); GV thường luôn bị ép về chính mình. */
    private Integer scopedTeacherId(Integer requested) {
        boolean isStaff = SecurityUtils.hasRole("ADMIN")
                || SecurityUtils.hasRole("EMPLOYEE")
                || SecurityUtils.hasRole("ACCOUNTANT")
                || SecurityUtils.hasRole("HR")
                || SecurityUtils.hasRole("ACADEMIC")
                || SecurityUtils.hasRole("SALES");
        if (isStaff) {
            return requested;
        }
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(SecurityUtils.currentUserId())
                .map(Teacher::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không có hồ sơ giáo viên"));
    }

    /**
     * Sinh chấm công từ các buổi ĐÃ DUYỆT trong [from, to] chưa có bản ghi.
     *
     * @return số dòng chấm công vừa tạo
     */
    @Transactional
    public int generateFromSchedules(LocalDate from, LocalDate to) {
        List<Schedule> schedules = scheduleRepo.findByStartTimeBetweenAndStatusAndDeletedFalseOrderByStartTime(
                from.atStartOfDay(), to.atTime(LocalTime.MAX), "APPROVED");
        Integer userId = SecurityUtils.currentUserId();
        int created = 0;
        for (Schedule s : schedules) {
            if (attendanceRepo.existsByScheduleId(s.getId())) {
                continue;
            }
            Attendance a = new Attendance();
            a.setTeacherId(s.getTeacherId());
            a.setScheduleId(s.getId());
            a.setWorkDate(s.getStartTime().toLocalDate());
            a.setCheckIn(s.getStartTime().toLocalTime());
            a.setCheckOut(s.getEndTime().toLocalTime());
            a.setStatus("PRESENT");
            a.setCheckInMethod("EMPLOYEE");
            a.setCreatedBy(userId);
            attendanceRepo.save(a);
            created++;
        }
        return created;
    }

    @Transactional
    public AttendanceResponse create(AttendanceRequest req) {
        Teacher teacher = validate(req);
        Attendance a = new Attendance();
        a.setCreatedBy(SecurityUtils.currentUserId());
        apply(a, req);
        attendanceRepo.save(a);
        return AttendanceResponse.fromEntity(a, fullName(teacher));
    }

    @Transactional
    public AttendanceResponse update(Long id, AttendanceRequest req) {
        Attendance a = attendanceRepo
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy dòng chấm công id=" + id));
        Teacher teacher = validate(req);
        apply(a, req);
        attendanceRepo.save(a);
        return AttendanceResponse.fromEntity(a, fullName(teacher));
    }

    /**
     * Validate chung cho create/update: GV phải tồn tại (update trước đây bỏ sót —
     * đổi teacherId bậy vẫn lưu được), giờ ra phải SAU giờ vào (lọt thì hours=0 âm
     * thầm, lương sai), scheduleId nếu gửi phải có thật (không thì nổ FK 500).
     */
    private Teacher validate(AttendanceRequest req) {
        Teacher teacher = teacherRepo
                .findByIdAndDeletedFalse(req.teacherId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy giáo viên"));
        if (req.checkIn() != null && req.checkOut() != null && !req.checkOut().isAfter(req.checkIn())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Giờ ra phải sau giờ vào");
        }
        if (req.scheduleId() != null && !scheduleRepo.existsById(req.scheduleId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy buổi dạy id=" + req.scheduleId());
        }
        return teacher;
    }

    private void apply(Attendance a, AttendanceRequest req) {
        a.setTeacherId(req.teacherId());
        a.setScheduleId(req.scheduleId());
        a.setWorkDate(req.workDate());
        a.setCheckIn(req.checkIn());
        a.setCheckOut(req.checkOut());
        a.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "PRESENT");
        a.setNote(req.note());
        if (a.getCheckInMethod() == null) {
            a.setCheckInMethod("EMPLOYEE");
        }
    }

    private String teacherName(Integer teacherId, Map<Integer, String> cache) {
        return cache.computeIfAbsent(
                teacherId,
                id -> teacherRepo.findById(id).map(AttendanceService::fullName).orElse("(GV #" + id + ")"));
    }

    private static String fullName(Teacher t) {
        return (t.getLastName() + " " + t.getFirstName()).trim();
    }
}
