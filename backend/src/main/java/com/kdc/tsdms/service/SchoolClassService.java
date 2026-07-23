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

    /** Khối hợp lệ theo chuẩn phổ thông VN. */
    private static final Set<String> VALID_GRADES = Set.of(
            "Khối 1", "Khối 2", "Khối 3", "Khối 4", "Khối 5", "Khối 6", "Khối 7", "Khối 8", "Khối 9", "Khối 10",
            "Khối 11", "Khối 12");

    private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final Pattern GRADE_NUM_PATTERN = Pattern.compile("^Khối (1[0-2]|[1-9])$");
    /** Tên lớp gợi ý bắt đầu bằng số khối (vd 10A1) hoặc chữ (vd A1) — cho phép linh hoạt. */
    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("^[\\p{L}0-9][\\p{L}0-9\\s._-]{0,49}$");

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

    /** Dropdown trường cho form tạo/sửa lớp. */
    @Transactional(readOnly = true)
    public List<OptionItem> listSchoolOptions() {
        return schoolRepo.findByDeletedFalseOrderByNameAsc().stream()
                .map(s -> new OptionItem(s.getId(), s.getName()))
                .toList();
    }

    /** Dropdown các khối đang có dữ liệu (lọc danh sách). */
    @Transactional(readOnly = true)
    public List<String> listExistingGradeLevels() {
        return classRepo.findDistinctGradeLevels();
    }

    /** Dropdown lớp ACTIVE theo trường (dùng cho form phân công/lịch nếu cần). */
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
        if (classRepo.existsBySchoolIdAndNameAndSchoolYearAndDeletedFalse(
                req.schoolId(), fields.name(), fields.year())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Lớp '" + fields.name() + "' năm học " + fields.year() + " đã tồn tại ở trường này");
        }
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
        if (classRepo.existsBySchoolIdAndNameAndSchoolYearAndDeletedFalseAndIdNot(
                req.schoolId(), fields.name(), fields.year(), id)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Lớp '" + fields.name() + "' năm học " + fields.year() + " đã tồn tại ở trường này");
        }
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
     * Validate nghiệp vụ sâu (không chỉ Bean Validation):
     * - khối thuộc danh mục chuẩn
     * - năm học liên tiếp (YYYY+1) và trong cửa sổ ±5 năm quanh năm học hiện tại
     * - nếu tên lớp bắt đầu bằng số khối thì số đó phải khớp khối đã chọn
     */
    private ValidatedClassFields validateBusiness(SchoolClassRequest req) {
        String name = normalizeSpaces(req.name());
        String grade = normalizeSpaces(req.gradeLevel());
        String year = normalizeSpaces(req.schoolYear());
        String status = req.status() != null ? req.status() : "ACTIVE";

        if (name == null || !CLASS_NAME_PATTERN.matcher(name).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tên lớp không hợp lệ");
        }
        if (grade == null || !VALID_GRADES.contains(grade)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Khối không hợp lệ — chọn Khối 1 … Khối 12");
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

        // Nếu tên bắt đầu bằng số khối (10A1, 6B…) thì bắt buộc khớp khối đã chọn.
        Matcher gm = GRADE_NUM_PATTERN.matcher(grade);
        if (gm.matches()) {
            String gradeNum = gm.group(1);
            if (name.matches("^" + gradeNum + "[A-Za-z].*") || name.matches("^" + gradeNum + "$")) {
                // ok: 10A1 với Khối 10
            } else if (name.matches("^[1-9][0-9]?[A-Za-z].*") || name.matches("^[1-9][0-9]?$")) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Tên lớp bắt đầu bằng số khối khác với khối đã chọn (khối " + gradeNum + ")");
            }
        }

        return new ValidatedClassFields(name, grade, year, status);
    }

    private static int currentSchoolYearStart() {
        LocalDate today = LocalDate.now();
        // Năm học VN thường bắt đầu tháng 9
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
