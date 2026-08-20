package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.dto.StandardFrameResult;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khung TIẾT HỌC của từng trường.
 *
 * <p>Trước đây khung tiết chỉ tạo được bằng SQL tay (V22), nên mọi trường thêm qua giao diện đều
 * có 0 tiết và không phân công được. Service này không làm màn quản lý tiết đầy đủ, chỉ sinh BỘ
 * KHUNG CHUẨN theo cấp học — quy ước lấy nguyên từ V22, đối chiếu với TH Dư Hàng và THCS Chu
 * Văn An.
 */
@Service
public class PeriodService {

    /**
     * Quy ước khung tiết một cấp học.
     *
     * @param lengthMin độ dài một tiết
     * @param breakMin giờ ra chơi, chèn SAU tiết thứ 2 của mỗi buổi (mỗi buổi ra chơi đúng 1 lần)
     * @param morning số tiết buổi sáng, bắt đầu 07:00
     * @param afternoon số tiết buổi chiều, bắt đầu 14:00
     */
    private record Frame(int lengthMin, int breakMin, int morning, int afternoon) {}

    /** Tiểu học: tiết 35 phút, ra chơi 15 phút → sáng 5 tiết, chiều 5 tiết, tan 17:10. */
    private static final Frame TIEU_HOC = new Frame(35, 15, 5, 5);

    /** THCS: tiết 45 phút, ra chơi 10 phút → sáng 5 tiết, chiều 4 tiết, cũng tan 17:10. */
    private static final Frame TRUNG_HOC = new Frame(45, 10, 5, 4);

    private static final LocalTime MORNING_START = LocalTime.of(7, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(14, 0);

    /** Ra chơi rơi vào sau tiết thứ mấy của buổi. */
    private static final int BREAK_AFTER = 2;

    private final PeriodRepository periodRepo;
    private final SchoolRepository schoolRepo;
    private final SchoolClassRepository classRepo;
    private final AssignmentSlotRepository slotRepo;
    private final ScheduleRepository scheduleRepo;

    public PeriodService(
            PeriodRepository periodRepo,
            SchoolRepository schoolRepo,
            SchoolClassRepository classRepo,
            AssignmentSlotRepository slotRepo,
            ScheduleRepository scheduleRepo) {
        this.periodRepo = periodRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.slotRepo = slotRepo;
        this.scheduleRepo = scheduleRepo;
    }

    /**
     * Khối lớp CAO NHẤT của một trường ("1".."9"), null nếu không đọc được khối nào.
     *
     * <p>GradeLevel là chuỗi nên parse phòng dữ liệu bẩn: một dòng hỏng không được làm hỏng cả
     * kết luận về cấp học.
     */
    public static Integer maxGrade(List<SchoolClass> classes) {
        int max = 0;
        for (SchoolClass c : classes) {
            try {
                max = Math.max(max, Integer.parseInt(c.getGradeLevel().trim()));
            } catch (NumberFormatException | NullPointerException ignored) {
                // khối không đọc được thì bỏ qua
            }
        }
        return max == 0 ? null : max;
    }

    /** Nhãn cấp học suy từ khối lớp cao nhất (1–5 tiểu học, 6–9 THCS); null nếu chưa có lớp. */
    public static String levelLabel(List<SchoolClass> classes) {
        Integer max = maxGrade(classes);
        if (max == null) {
            return null;
        }
        return max <= 5 ? "Tiểu học" : "THCS";
    }

    /**
     * Sinh bộ khung tiết CHUẨN cho một trường chưa có khung nào.
     *
     * <p>Cấp học suy từ khối lớp cao nhất chứ KHÔNG hardcode theo SchoolId — trường mở thêm khối
     * thì lần áp sau tự ra đúng bộ.
     *
     * @throws ApiException 409 nếu trường đã có khung tiết (không ghi đè: sửa khung tiết đang
     *     dùng sẽ làm lệch giờ của những buổi dạy đã sinh ra trước đó)
     * @throws ApiException 400 nếu trường chưa có lớp nào — không có căn cứ suy cấp học, và đoán
     *     bừa thì cả trường chạy sai khung giờ
     */
    @Transactional
    public StandardFrameResult applyStandardFrame(Integer schoolId) {
        School school = schoolRepo
                .findById(schoolId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy trường."));

        if (!periodRepo
                .findBySchoolIdAndDeletedFalseOrderByPeriodNumber(schoolId)
                .isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Trường " + school.getName() + " đã có khung tiết. Không áp đè để tránh làm lệch giờ "
                            + "của các buổi dạy đã xếp theo khung cũ.");
        }

        List<SchoolClass> classes = classRepo.findAll().stream()
                .filter(c -> !c.isDeleted() && schoolId.equals(c.getSchoolId()))
                .toList();
        Integer max = maxGrade(classes);
        if (max == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Trường " + school.getName() + " chưa có lớp nào nên không xác định được cấp học. "
                            + "Hãy thêm lớp cho trường trước, rồi áp lại khung tiết.");
        }

        return generate(schoolId, school.getName(), max <= 5);
    }

    /**
     * Sinh khung tiết chuẩn với cấp học CHO SẴN, không suy từ lớp.
     *
     * <p>Dùng lúc TẠO MỚI trường, khi chưa có lớp nào nên {@link #applyStandardFrame(Integer)}
     * không dùng được. Cấp học do người tạo chọn, hoặc suy từ tên (xem {@link #suyCapTuTen}).
     *
     * <p>Trường đã có khung tiết thì trả {@code null}, KHÔNG ném lỗi — đây là bước phụ chạy kèm
     * việc tạo trường, không nên làm hỏng thao tác chính.
     */
    @Transactional
    public StandardFrameResult applyStandardFrame(Integer schoolId, String schoolName, boolean tieuHoc) {
        if (!periodRepo
                .findBySchoolIdAndDeletedFalseOrderByPeriodNumber(schoolId)
                .isEmpty()) {
            return null;
        }
        return generate(schoolId, schoolName, tieuHoc);
    }

    /**
     * Suy cấp học TỪ TÊN TRƯỜNG, dùng khi người tạo không chọn cấp.
     *
     * <p>{@code TRUE} = tiểu học, {@code FALSE} = THCS, {@code null} = không đoán được → KHÔNG
     * sinh khung, để người dùng tự bấm "Áp khung tiết chuẩn" sau khi thêm lớp. Gán nhầm khung 35
     * phút cho trường THCS là sai giờ toàn bộ lịch dạy mà không lỗi nào bắn ra.
     *
     * <p>HAI BẪY: (1) phải kiểm "THCS" TRƯỚC "TH" vì "THCS" cũng bắt đầu bằng "TH"; (2) phải BỎ
     * DẤU trước khi so, không thì trường gõ "Tieu hoc" âm thầm không có khung tiết.
     */
    public static Boolean suyCapTuTen(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String s = boDau(name.trim().toLowerCase());
        if (s.startsWith("thcs ") || s.contains(" thcs ") || s.contains("trung hoc co so")) {
            return Boolean.FALSE;
        }
        if (s.startsWith("th ") || s.contains(" th ") || s.contains("tieu hoc")) {
            return Boolean.TRUE;
        }
        return null;
    }

    /** Bỏ dấu tiếng Việt để so khớp không phụ thuộc người nhập có gõ dấu hay không. */
    private static String boDau(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    /** Dựng và lưu bộ tiết cho một trường. Người gọi chịu trách nhiệm kiểm trùng trước. */
    private StandardFrameResult generate(Integer schoolId, String schoolName, boolean tieuHoc) {
        Frame frame = tieuHoc ? TIEU_HOC : TRUNG_HOC;
        Integer userId = SecurityUtils.currentUserId();

        List<Period> out = new ArrayList<>();
        short number = 1;
        number = buildSession(out, schoolId, "MORNING", MORNING_START, frame, frame.morning(), number, userId);
        buildSession(out, schoolId, "AFTERNOON", AFTERNOON_START, frame, frame.afternoon(), number, userId);
        periodRepo.saveAll(out);

        return new StandardFrameResult(
                schoolId, schoolName, tieuHoc ? "Tiểu học" : "THCS", out.size(), frame.lengthMin());
    }

    /** Trải một buổi thành các tiết liên tiếp, chèn giờ ra chơi sau tiết thứ {@value #BREAK_AFTER}. */
    private short buildSession(
            List<Period> out,
            Integer schoolId,
            String sessionType,
            LocalTime start,
            Frame frame,
            int count,
            short firstNumber,
            Integer userId) {
        LocalTime cursor = start;
        short number = firstNumber;
        for (int i = 1; i <= count; i++) {
            Period p = new Period();
            p.setSchoolId(schoolId);
            p.setPeriodNumber(number++);
            p.setSessionType(sessionType);
            p.setStartTime(cursor);
            p.setEndTime(cursor.plusMinutes(frame.lengthMin()));
            p.setCreatedBy(userId);
            out.add(p);

            cursor = cursor.plusMinutes(frame.lengthMin());
            if (i == BREAK_AFTER) {
                cursor = cursor.plusMinutes(frame.breakMin());
            }
        }
        return number;
    }

    /** Buổi dạy còn hiệu lực — cùng bộ trạng thái với chốt xóa giáo viên/phòng học. */
    private static final List<String> BUOI_CON_HIEU_LUC = List.of("PENDING", "APPROVED");

    /**
     * Xóa mềm một tiết — luật RESTRICT. Tiết là chỗ neo của thời khóa biểu:
     * {@code AssignmentSlot.PeriodId} và {@code Schedule.PeriodId} đều trỏ vào đây, nên còn ai
     * dùng thì cấm, và báo rõ đang vướng bao nhiêu.
     *
     * <p>Buổi ĐÃ DẠY không chặn: xóa mềm giữ nguyên dòng Period nên giờ buổi cũ vẫn tra được.
     */
    @Transactional
    public void delete(Integer id) {
        Period p = periodRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tiết id=" + id));
        String truong =
                schoolRepo.findById(p.getSchoolId()).map(School::getName).orElse("trường id=" + p.getSchoolId());
        DeleteGuard.of("tiết " + p.getPeriodNumber() + " (" + tenBuoi(p.getSessionType()) + ") của " + truong)
                .blockIf(slotRepo.countByPeriodIdAndDeletedFalse(id), "ô thời khóa biểu hằng tuần đang dùng tiết này")
                .blockIf(
                        scheduleRepo.countByPeriodIdAndStartTimeAfterAndStatusInAndDeletedFalse(
                                id, BusinessTime.now(), BUOI_CON_HIEU_LUC),
                        "buổi dạy sắp tới xếp vào tiết này")
                .check();
        Integer nguoiXoa = SecurityUtils.currentUserId();
        p.setDeleted(true);
        p.setDeletedAt(Instant.now());
        p.setDeletedBy(nguoiXoa);
        p.setUpdatedAt(Instant.now());
        p.setUpdatedBy(nguoiXoa);
        periodRepo.save(p);
    }

    private static String tenBuoi(String sessionType) {
        return switch (sessionType == null ? "" : sessionType) {
            case "MORNING" -> "buổi sáng";
            case "AFTERNOON" -> "buổi chiều";
            default -> sessionType;
        };
    }
}
