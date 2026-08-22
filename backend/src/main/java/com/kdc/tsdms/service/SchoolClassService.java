package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.common.SearchText;
import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.SchoolClassRequest;
import com.kdc.tsdms.dto.SchoolClassResponse;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.ClassEnrollmentRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolClassService {

    /**
     * Chuẩn lưu GradeLevel trong DB: luôn dạng {@code Khối 7} (không chỉ {@code 7}, không {@code Lớp
     * 7}). Chỉ nhận khối 1-9 — cấp 3 đã bị bỏ khỏi hệ thống (V26).
     */
    private static final Set<String> VALID_GRADES = Set.of("1", "2", "3", "4", "5", "6", "7", "8", "9");

    private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final Pattern GRADE_NUM_IN_TEXT = Pattern.compile("(\\d{1,2})");
    /**
     * Tên lớp: {khối 1–9}{đúng 1 chữ}{số 1–20 bắt buộc}. VD: 7A1, 6B20. Không 7A, không 7AB, không
     * 7A21.
     */
    private static final Pattern CLASS_NAME_STRICT = Pattern.compile("^([1-9])([A-Z])(20|[1-9]|1[0-9])$");

    private static final int YEAR_RANGE = 5;

    private final SchoolClassRepository classRepo;
    private final SchoolRepository schoolRepo;
    private final ClassEnrollmentRepository enrollmentRepo;
    private final AssignmentRepository assignmentRepo;
    private final PeriodRepository periodRepo;
    private final AssignmentSlotRepository slotRepo;
    private final AuditService auditService;

    public SchoolClassService(
            SchoolClassRepository classRepo,
            SchoolRepository schoolRepo,
            ClassEnrollmentRepository enrollmentRepo,
            AssignmentRepository assignmentRepo,
            PeriodRepository periodRepo,
            AssignmentSlotRepository slotRepo,
            AuditService auditService) {
        this.classRepo = classRepo;
        this.schoolRepo = schoolRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.assignmentRepo = assignmentRepo;
        this.periodRepo = periodRepo;
        this.slotRepo = slotRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OptionItem> listSchoolOptions() {
        return schoolRepo.findByDeletedFalseOrderByNameAsc().stream()
                .map(s -> new OptionItem(s.getId(), s.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listExistingGradeLevels() {
        // Sắp theo SỐ khối, không theo chuỗi: dữ liệu cũ còn dạng chữ "Khối 7" lẫn số "7"
        return classRepo.findDistinctGradeLevels().stream()
                .sorted(Comparator.comparingInt(SchoolClassService::gradeSortKey)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static int gradeSortKey(String gradeLevel) {
        Matcher m = GRADE_NUM_IN_TEXT.matcher(gradeLevel == null ? "" : gradeLevel);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    @Transactional(readOnly = true)
    public List<OptionItem> listActiveBySchool(Integer schoolId) {
        requireSchool(schoolId);
        return classRepo.findBySchoolIdAndDeletedFalseAndStatusOrderByName(schoolId, "ACTIVE").stream()
                .map(c -> new OptionItem(c.getId(), c.getName() + " (" + c.getSchoolYear() + ")"))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SchoolClassResponse> search(
            String keyword, Integer schoolId, String status, String gradeLevel, Pageable pageable) {
        String kw = SearchText.escapeLike(SearchText.blankToNull(keyword));
        String st = SearchText.blankToNull(status);
        String gl = SearchText.blankToNull(gradeLevel);
        Page<SchoolClass> page = classRepo.search(kw, schoolId, st, gl, pageable);
        Map<Integer, String> schoolNames = loadSchoolNames(page.getContent().stream()
                .map(SchoolClass::getSchoolId)
                .distinct()
                .toList());
        return page.map(sc -> SchoolClassResponse.fromEntity(sc, schoolNames.getOrDefault(sc.getSchoolId(), "—")));
    }

    @Transactional(readOnly = true)
    public SchoolClassResponse getById(Integer id) {
        SchoolClass sc = getOrThrow(id);
        return SchoolClassResponse.fromEntity(sc, schoolName(sc.getSchoolId()));
    }

    @Transactional
    public SchoolClassResponse create(SchoolClassRequest req) {
        requireSchoolConHopTac(req.schoolId());
        ValidatedClassFields fields = validateBusiness(req, null);
        assertNoDuplicate(req.schoolId(), fields.name(), fields.year(), null);
        SchoolClass sc = new SchoolClass();
        apply(sc, req.schoolId(), fields);
        sc.setCreatedBy(SecurityUtils.currentUserId());
        return SchoolClassResponse.fromEntity(classRepo.save(sc), schoolName(sc.getSchoolId()));
    }

    @Transactional
    public SchoolClassResponse update(Integer id, SchoolClassRequest req) {
        SchoolClass sc = getOrThrow(id);
        requireSchool(req.schoolId());
        ValidatedClassFields fields = validateBusiness(req, sc.getSchoolYear());
        assertNoDuplicate(req.schoolId(), fields.name(), fields.year(), id);
        // Đổi TRƯỜNG/NĂM HỌC của lớp còn học sinh/phân công sẽ làm dữ liệu lệch âm thầm
        // (Assignment.schoolId vẫn trỏ trường cũ, HS trường A nằm trong lớp trường B…)
        // — chặn giống luật chặn xóa lớp.
        boolean movingSchool = !Objects.equals(sc.getSchoolId(), req.schoolId());
        boolean changingYear = sc.getSchoolYear() != null && !sc.getSchoolYear().equalsIgnoreCase(fields.year());
        if (movingSchool) {
            requireSchoolConHopTac(req.schoolId());
        }
        if (movingSchool || changingYear) {
            long students = enrollmentRepo.countByClassId(id);
            long assignments = assignmentRepo.countByClassIdAndDeletedFalse(id);
            if (students > 0 || assignments > 0) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Không thể đổi trường/năm học của lớp '" + sc.getName() + "': đang có "
                                + students + " học sinh và " + assignments
                                + " phân công gắn với lớp — gỡ hết trước khi chuyển");
            }
        }
        apply(sc, req.schoolId(), fields);
        sc.setUpdatedAt(Instant.now());
        sc.setUpdatedBy(SecurityUtils.currentUserId());
        return SchoolClassResponse.fromEntity(classRepo.save(sc), schoolName(sc.getSchoolId()));
    }

    @Transactional
    public void delete(Integer id) {
        SchoolClass sc = getOrThrow(id);
        softDelete(sc);
    }

    /** Xóa mềm nhiều lớp — dừng ngay nếu 1 id lỗi (transaction rollback). */
    @Transactional
    public void deleteMany(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Danh sách id rỗng");
        }
        for (Integer id : ids) {
            if (id == null) continue;
            softDelete(getOrThrow(id));
        }
    }

    /** Thùng rác: lớp đã xóa mềm (mới ẩn trước). */
    @Transactional(readOnly = true)
    public List<SchoolClassResponse> listTrash() {
        return classRepo.findByDeletedTrueOrderByDeletedAtDesc().stream()
                .map(sc -> SchoolClassResponse.fromEntity(sc, schoolName(sc.getSchoolId())))
                .toList();
    }

    /** Khôi phục từ thùng rác — chặn nếu (trường+tên+năm) đã có lớp active. */
    @Transactional
    public SchoolClassResponse restore(Integer id) {
        SchoolClass sc = classRepo
                .findByIdAndDeletedTrue(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp trong thùng rác id=" + id));
        if (classRepo.existsBySchoolIdAndNameAndDeletedFalse(sc.getSchoolId(), sc.getName())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Không khôi phục được: lớp '" + sc.getName() + "' đã tồn tại ở trường này");
        }
        sc.setDeleted(false);
        sc.setDeletedAt(null);
        sc.setDeletedBy(null);
        sc.setUpdatedAt(Instant.now());
        sc.setUpdatedBy(SecurityUtils.currentUserId());
        return SchoolClassResponse.fromEntity(classRepo.save(sc), schoolName(sc.getSchoolId()));
    }

    /** Khôi phục nhiều lớp từ thùng rác — 1 request, dừng ngay nếu 1 id lỗi (rollback cả lô). */
    @Transactional
    public List<SchoolClassResponse> restoreMany(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Danh sách id rỗng");
        }
        return ids.stream().filter(Objects::nonNull).map(this::restore).toList();
    }

    /**
     * Xóa mềm một lớp — chỉ khi không còn gì trỏ vào nó.
     *
     * <p>Phải đếm CẢ Ô THỜI KHÓA BIỂU, không chỉ phiếu phân công. Từ V16 lớp thật nằm ở từng ô;
     * lớp ở cấp phiếu chỉ là giá trị đại diện của ô đầu tiên. Bản cũ chỉ hỏi cấp phiếu nên
     * chặn hụt: đo trên dữ liệu demo có 674 lớp đang nằm trong thời khóa biểu nhưng không phải
     * lớp đại diện của phiếu nào — bấm Xóa là mất sạch, giáo viên vẫn tới trường dạy còn ô
     * lịch thì trỏ vào một cái tên không còn tồn tại.
     */
    private void softDelete(SchoolClass sc) {
        Integer id = sc.getId();
        DeleteGuard.of("lớp " + sc.getName())
                .blockIf(enrollmentRepo.countByClassId(id), "học sinh")
                .blockIf(assignmentRepo.countByClassIdAndDeletedFalse(id), "phân công")
                .blockIf(slotRepo.countByClassIdAndDeletedFalse(id), "ô thời khóa biểu")
                .check();
        auditService.ghi("XOA_LOP", "SchoolClass", id, sc.getName() + " · năm học " + sc.getSchoolYear(), "Xóa mềm");
        sc.setDeleted(true);
        sc.setDeletedAt(Instant.now());
        sc.setDeletedBy(SecurityUtils.currentUserId());
        classRepo.save(sc);
    }

    /* ── PRIVATE ── */

    /** Bộ giá trị đã chuẩn hóa của một lớp — kết quả của {@link #validateBusiness}. */
    public record ValidatedClassFields(String name, String gradeLevel, String year, String status) {}

    /**
     * Kiểm + chuẩn hóa MỘT dòng lớp học, ném {@link ApiException} kèm lý do nếu sai.
     *
     * <p>Mở ra cho {@code BulkClassService} dùng lại: màn "Thêm lớp hàng loạt" cần chấm từng
     * dòng rồi kể lỗi ra màn hình, chứ không dừng ở dòng sai đầu tiên. Viết bộ kiểm thứ hai
     * cho luồng hàng loạt thì hai bộ sẽ trôi ra khác nhau, và người dùng gặp cảnh một tên lớp
     * bị từ chối khi thêm lẻ nhưng lọt qua khi nhập hàng loạt.
     */
    public ValidatedClassFields kiemTraMotDong(SchoolClassRequest req) {
        return validateBusiness(req, null);
    }

    /**
     * Không trùng lớp trong cùng trường + CÙNG NĂM HỌC — khớp đúng chỉ mục
     * {@code UX_Class_School_Name_Year}. So khớp không phân biệt hoa thường.
     *
     * <p>NĂM HỌC LÀ MỘT PHẦN CỦA KHÓA, KHÔNG ĐƯỢC BỎ. Bản cũ nhận tham số {@code year} nhưng
     * không dùng tới, chỉ hỏi "trường này đã có lớp tên 6A1 chưa". Hệ quả: sang năm học mới,
     * KHÔNG tạo được lớp nào cả — mọi tên lớp đều đã tồn tại ở năm cũ, và thông báo lỗi
     * ("Lớp 6A1 đã tồn tại ở trường này") không hề gợi ý rằng nó đang nói về năm học khác.
     * Lỗi chỉ lộ ra đúng một lần mỗi năm, vào lúc bận nhất.
     *
     * <p>Chỉ mục dưới database vẫn cho phép trùng tên khác năm, nên tầng nghiệp vụ đang CHẶT
     * HƠN cả ràng buộc thật của dữ liệu — kiểu lệch khó thấy nhất vì không có gì báo lỗi.
     */
    private void assertNoDuplicate(Integer schoolId, String name, String year, Integer excludeId) {
        boolean exists = classRepo.findBySchoolIdAndDeletedFalseOrderByName(schoolId).stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(name)
                        && cungNamHoc(c.getSchoolYear(), year)
                        && (excludeId == null || !excludeId.equals(c.getId())));
        if (exists) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Lớp '" + name + "' đã tồn tại ở trường này trong năm học " + year
                            + " — không được trùng tên lớp trong cùng một năm học");
        }
    }

    /** Hai năm học coi là một khi bằng nhau (bỏ qua hoa thường); cùng để trống cũng là một. */
    private static boolean cungNamHoc(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * @param existingYear năm học hiện tại của lớp (khi SỬA) — GIỮ NGUYÊN năm cũ nếu client không gửi.
     */
    private ValidatedClassFields validateBusiness(SchoolClassRequest req, String existingYear) {
        String rawName = normalizeSpaces(req.name());
        String year = normalizeSpaces(req.schoolYear());
        String status = req.status() != null ? req.status() : "ACTIVE";

        if (rawName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tên lớp không được để trống");
        }
        // Chuẩn hóa tên: chữ cái viết hoa (7a1 → 7A1)
        String name = rawName.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        Matcher nm = CLASS_NAME_STRICT.matcher(name);
        if (!nm.matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Tên lớp không hợp lệ: dạng 7A1 / 6B20 (1 chữ + số 1–20 bắt buộc, không chỉ 7A)");
        }
        String gradeNumFromName = nm.group(1);

        String grade = normalizeGradeLevel(req.gradeLevel());
        if (grade == null || !VALID_GRADES.contains(grade)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Khối không hợp lệ — chọn khối 1 … 9");
        }
        // Số khối "7" phải khớp số đầu tên lớp "7A1"
        if (!grade.equals(gradeNumFromName)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Tên lớp bắt đầu bằng " + gradeNumFromName + " nhưng khối đã chọn là " + grade);
        }
        assertKhoiHopCapTruong(req.schoolId(), Integer.parseInt(grade));

        // Nếu client không gửi year (UI đã bỏ trường Năm học), tự động chọn năm học hiện tại
        if (year == null || year.isBlank()) {
            if (existingYear != null && !existingYear.isBlank()) {
                year = existingYear;
            } else {
                int currentStart = currentSchoolYearStart();
                year = currentStart + "-" + (currentStart + 1);
            }
        } else {
            Matcher ym = YEAR_PATTERN.matcher(year);
            if (!ym.matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Năm học phải dạng YYYY-YYYY");
            }
            int y1 = Integer.parseInt(ym.group(1));
            int y2 = Integer.parseInt(ym.group(2));
            if (y2 != y1 + 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Năm học phải liên tiếp (vd: 2025-2026)");
            }
            boolean keepingOldYear = existingYear != null && existingYear.equalsIgnoreCase(year);
            if (!keepingOldYear) {
                int currentStart = currentSchoolYearStart();
                if (y1 < currentStart - YEAR_RANGE || y1 > currentStart + YEAR_RANGE) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "Năm học chỉ cho phép trong khoảng ±" + YEAR_RANGE + " năm so với năm học hiện tại");
                }
            }
        }

        return new ValidatedClassFields(name, grade, year, status);
    }

    /**
     * Khối phải hợp với CẤP HỌC của trường: tiểu học chỉ khối 1-5, THCS chỉ khối 6-9.
     *
     * <p>Trước đây luật này chỉ tồn tại ở frontend dưới dạng lọc danh sách khối theo tên
     * trường — nghĩa là gọi thẳng API là mở được lớp 7 ở một trường tiểu học. Lớp sai cấp
     * không chỉ trông kỳ: đơn giá tiết dạy tra theo KHỐI, nên một lớp 7 nằm ở trường tiểu học
     * sẽ được trả theo barem THCS.
     *
     * <p>Cấp học suy từ SỐ TIẾT trong khung tiết (tiểu học 10, THCS 9) — dữ liệu vận hành thật
     * của từng trường, chắc hơn đoán theo tên. Trường chưa có khung tiết thì bỏ qua luật này
     * thay vì chặn: không suy được cấp không có nghĩa là dữ liệu sai.
     */
    private void assertKhoiHopCapTruong(Integer schoolId, int khoi) {
        if (schoolId == null) {
            return;
        }
        long soTiet = periodRepo.countBySchoolIdAndDeletedFalse(schoolId);
        if (soTiet == 0) {
            return;
        }
        boolean tieuHoc = soTiet >= 10;
        if (tieuHoc && khoi > 5) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Trường tiểu học chỉ mở được khối 1-5, không mở được khối " + khoi);
        }
        if (!tieuHoc && khoi < 6) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Trường THCS chỉ mở được khối 6-9, không mở được khối " + khoi);
        }
    }

    /**
     * Chuẩn hóa mọi dạng client/seed → {@code N}: "7", "Lớp 7", "Khối 7" → "7".
     *
     * <p>Trung tâm chỉ dạy khối 1–9. Dữ liệu cũ còn "Lớp 10"/"11"/"12" (từ thời có trường cấp 3)
     * được trả nguyên văn, KHÔNG chuẩn hóa thành số — để {@link #VALID_GRADES} chặn lại ở bước
     * sau kèm thông báo rõ, thay vì lặng lẽ nhận rồi tính sai đơn giá lương.
     */
    private static String normalizeGradeLevel(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return null;
        }
        if (VALID_GRADES.contains(t)) {
            return t;
        }
        Matcher m = GRADE_NUM_IN_TEXT.matcher(t);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= 9) {
                return String.valueOf(n);
            }
        }
        return t;
    }

    private static int currentSchoolYearStart() {
        LocalDate today = BusinessTime.today();
        return today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
    }

    private SchoolClass getOrThrow(Integer id) {
        return classRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học id=" + id));
    }

    private School requireSchool(Integer schoolId) {
        return schoolRepo
                .findByIdAndDeletedFalse(schoolId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Trường không tồn tại hoặc đã bị xóa"));
    }

    private void apply(SchoolClass sc, Integer schoolId, ValidatedClassFields fields) {
        sc.setSchoolId(schoolId);
        sc.setName(fields.name());
        sc.setGradeLevel(fields.gradeLevel());
        sc.setSchoolYear(fields.year());
        sc.setStatus(fields.status());
    }

    private String schoolName(Integer schoolId) {
        return schoolRepo.findByIdAndDeletedFalse(schoolId).map(School::getName).orElse("—");
    }

    private Map<Integer, String> loadSchoolNames(List<Integer> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return Map.of();
        }
        return schoolRepo.findAllById(schoolIds).stream()
                .filter(s -> !s.isDeleted())
                .collect(Collectors.toMap(School::getId, School::getName, (a, b) -> a));
    }

    private static String normalizeSpaces(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    /**
     * Trường phải còn hợp tác thì mới nhận LỚP MỚI — cùng luật với phân công (xem {@code
     * School.conHopTac}). Mở lớp ở một trường đã hết hạn hợp đồng là bước đầu tiên của cả chuỗi
     * lớp -> phân công -> lịch dạy -> chấm công -> lương cho nơi trung tâm không còn dạy.
     *
     * <p>Chỉ áp lúc TẠO lớp và lúc CHUYỂN lớp sang trường khác. Sửa tên/năm học của lớp cũ ở
     * trường đã ngừng thì vẫn cho: chặn cả thao tác sửa là nhốt luôn dữ liệu cũ, gõ sai một chữ
     * cũng không sửa được nữa.
     */
    private School requireSchoolConHopTac(Integer schoolId) {
        School s = requireSchool(schoolId);
        if (!s.conHopTac(BusinessTime.today())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Trường " + s.getName()
                            + " đã ngừng hợp tác hoặc hết hạn hợp đồng nên không mở thêm lớp mới được.");
        }
        return s;
    }
}
