package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.OptionItem;
import com.kdc.tsdms.dto.SchoolClassRequest;
import com.kdc.tsdms.dto.SchoolClassResponse;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.ClassEnrollmentRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
     * 7}). Seed cũ có thể còn "Lớp 10" — khi sửa sẽ được chuẩn hóa.
     */
    private static final Set<String> VALID_GRADES = Set.of(
            "Khối 1", "Khối 2", "Khối 3", "Khối 4", "Khối 5", "Khối 6", "Khối 7", "Khối 8", "Khối 9", "Khối 10",
            "Khối 11", "Khối 12");

    private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final Pattern GRADE_NUM_IN_TEXT = Pattern.compile("(\\d{1,2})");
    /**
     * Tên lớp: {khối 1–12}{đúng 1 chữ}{số 1–20 bắt buộc}. VD: 7A1, 6B20. Không 7A, không 7AB, không
     * 7A21.
     */
    private static final Pattern CLASS_NAME_STRICT = Pattern.compile("^([1-9]|1[0-2])([A-Z])(20|[1-9]|1[0-9])$");

    private static final int YEAR_RANGE = 5;

    private final SchoolClassRepository classRepo;
    private final SchoolRepository schoolRepo;
    private final ClassEnrollmentRepository enrollmentRepo;
    private final AssignmentRepository assignmentRepo;

    public SchoolClassService(
            SchoolClassRepository classRepo,
            SchoolRepository schoolRepo,
            ClassEnrollmentRepository enrollmentRepo,
            AssignmentRepository assignmentRepo) {
        this.classRepo = classRepo;
        this.schoolRepo = schoolRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.assignmentRepo = assignmentRepo;
    }

    @Transactional(readOnly = true)
    public List<OptionItem> listSchoolOptions() {
        return schoolRepo.findByDeletedFalseOrderByNameAsc().stream()
                .map(s -> new OptionItem(s.getId(), s.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listExistingGradeLevels() {
        return classRepo.findDistinctGradeLevels();
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
        String kw = blankToNull(keyword);
        String st = blankToNull(status);
        String gl = blankToNull(gradeLevel);
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
        requireSchool(req.schoolId());
        ValidatedClassFields fields = validateBusiness(req);
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
        ValidatedClassFields fields = validateBusiness(req);
        assertNoDuplicate(req.schoolId(), fields.name(), fields.year(), id);
        apply(sc, req.schoolId(), fields);
        sc.setUpdatedAt(Instant.now());
        sc.setUpdatedBy(SecurityUtils.currentUserId());
        return SchoolClassResponse.fromEntity(classRepo.save(sc), schoolName(sc.getSchoolId()));
    }

    @Transactional
    public void delete(Integer id) {
        SchoolClass sc = getOrThrow(id);
        long students = enrollmentRepo.countByClassId(id);
        if (students > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Không thể xóa: lớp đang có " + students + " học sinh");
        }
        long assignments = assignmentRepo.countByClassIdAndDeletedFalse(id);
        if (assignments > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Không thể xóa: lớp đang gắn " + assignments + " phân công");
        }
        sc.setDeleted(true);
        sc.setDeletedAt(Instant.now());
        sc.setDeletedBy(SecurityUtils.currentUserId());
        classRepo.save(sc);
    }

    /* ── PRIVATE ── */

    private record ValidatedClassFields(String name, String gradeLevel, String year, String status) {}

    /**
     * Không trùng lớp trong cùng trường + cùng năm học (khớp unique index
     * UX_Class_School_Name_Year). So khớp không phân biệt hoa thường.
     */
    private void assertNoDuplicate(Integer schoolId, String name, String year, Integer excludeId) {
        boolean exists = excludeId == null
                ? classRepo.existsBySchoolIdAndNameAndSchoolYearAndDeletedFalse(schoolId, name, year)
                : classRepo.existsBySchoolIdAndNameAndSchoolYearAndDeletedFalseAndIdNot(
                        schoolId, name, year, excludeId);
        // Thêm kiểm tra case-insensitive qua danh sách cùng trường (phòng client gửi 7a1 vs 7A1)
        if (!exists) {
            exists = classRepo.findBySchoolIdAndDeletedFalseAndStatusOrderByName(schoolId, "ACTIVE").stream()
                            .anyMatch(c -> c.getSchoolYear().equalsIgnoreCase(year)
                                    && c.getName().equalsIgnoreCase(name)
                                    && (excludeId == null || !excludeId.equals(c.getId())))
                    || classRepo.findBySchoolIdAndDeletedFalseAndStatusOrderByName(schoolId, "INACTIVE").stream()
                            .anyMatch(c -> c.getSchoolYear().equalsIgnoreCase(year)
                                    && c.getName().equalsIgnoreCase(name)
                                    && (excludeId == null || !excludeId.equals(c.getId())));
        }
        if (exists) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Lớp '" + name + "' năm học " + year + " đã tồn tại ở trường này — không được trùng");
        }
    }

    private ValidatedClassFields validateBusiness(SchoolClassRequest req) {
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "Khối không hợp lệ — chọn Khối 1 … Khối 12");
        }
        // Số trong "Khối 7" phải khớp số đầu tên lớp "7A1"
        String gradeNum = grade.replace("Khối ", "");
        if (!gradeNum.equals(gradeNumFromName)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Tên lớp bắt đầu bằng " + gradeNumFromName + " nhưng khối đã chọn là " + grade);
        }

        Matcher ym = YEAR_PATTERN.matcher(year == null ? "" : year);
        if (!ym.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Năm học phải dạng YYYY-YYYY");
        }
        int y1 = Integer.parseInt(ym.group(1));
        int y2 = Integer.parseInt(ym.group(2));
        if (y2 != y1 + 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Năm học phải liên tiếp (vd: 2025-2026)");
        }
        int currentStart = currentSchoolYearStart();
        if (y1 < currentStart - YEAR_RANGE || y1 > currentStart + YEAR_RANGE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Năm học chỉ cho phép trong khoảng ±" + YEAR_RANGE + " năm so với năm học hiện tại");
        }

        return new ValidatedClassFields(name, grade, year, status);
    }

    /**
     * Chuẩn hóa mọi dạng client/seed → {@code Khối N}: "7", "Lớp 7", "Khối 7" → "Khối 7".
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
            if (n >= 1 && n <= 12) {
                return "Khối " + n;
            }
        }
        return t;
    }

    private static int currentSchoolYearStart() {
        LocalDate today = LocalDate.now();
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

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeSpaces(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }
}
