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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolClassService {

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

    /** Dropdown lớp ACTIVE theo trường (dùng cho form phân công/lịch nếu cần). */
    @Transactional(readOnly = true)
    public List<OptionItem> listActiveBySchool(Integer schoolId) {
        requireSchool(schoolId);
        return classRepo.findBySchoolIdAndDeletedFalseAndStatusOrderByName(schoolId, "ACTIVE").stream()
                .map(c -> new OptionItem(c.getId(), c.getName() + " (" + c.getSchoolYear() + ")"))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SchoolClassResponse> search(String keyword, Integer schoolId, String status, Pageable pageable) {
        String kw = blankToNull(keyword);
        String st = blankToNull(status);
        Page<SchoolClass> page = classRepo.search(kw, schoolId, st, pageable);
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
        String name = req.name().trim();
        String year = req.schoolYear().trim();
        if (classRepo.existsBySchoolIdAndNameAndSchoolYearAndDeletedFalse(req.schoolId(), name, year)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Lớp '" + name + "' năm học " + year + " đã tồn tại ở trường này");
        }
        SchoolClass sc = new SchoolClass();
        apply(sc, req, name, year);
        sc.setCreatedBy(SecurityUtils.currentUserId());
        return SchoolClassResponse.fromEntity(classRepo.save(sc), schoolName(sc.getSchoolId()));
    }

    @Transactional
    public SchoolClassResponse update(Integer id, SchoolClassRequest req) {
        SchoolClass sc = getOrThrow(id);
        requireSchool(req.schoolId());
        String name = req.name().trim();
        String year = req.schoolYear().trim();
        if (classRepo.existsBySchoolIdAndNameAndSchoolYearAndDeletedFalseAndIdNot(req.schoolId(), name, year, id)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Lớp '" + name + "' năm học " + year + " đã tồn tại ở trường này");
        }
        apply(sc, req, name, year);
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

    private void apply(SchoolClass sc, SchoolClassRequest req, String name, String year) {
        sc.setSchoolId(req.schoolId());
        sc.setName(name);
        sc.setGradeLevel(blankToNull(req.gradeLevel()));
        sc.setSchoolYear(year);
        sc.setStatus(req.status() != null ? req.status() : "ACTIVE");
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
}
