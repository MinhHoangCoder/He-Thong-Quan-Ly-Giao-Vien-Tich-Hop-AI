package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.EvaluationRequest;
import com.kdc.tsdms.dto.EvaluationResponse;
import com.kdc.tsdms.dto.EvaluationStatsResponse;
import com.kdc.tsdms.dto.EvaluationTeacherOption;
import com.kdc.tsdms.dto.TeacherEvaluationSummaryResponse;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.entity.TeacherEvaluation;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherEvaluationRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ module Đánh giá giáo viên ({@code TeacherEvaluation}).
 *
 * <p>Phạm vi theo vai trò (chống IDOR ở tầng service, không chỉ @PreAuthorize):
 * <ul>
 *   <li>ADMIN / staff có EVALUATION_* : xem/quản lý theo filter (mặc định toàn hệ thống)</li>
 *   <li>SCHOOL : chỉ đánh giá do trường mình tạo; tạo thì SchoolId = trường mình</li>
 *   <li>TEACHER : chỉ xem đánh giá về chính mình; không tạo/sửa/xóa</li>
 * </ul>
 */
@Service
public class EvaluationService {

    /** Gợi ý kỳ đánh giá cho dropdown FE (text tự do, không ràng buộc DB). */
    private static final List<String> PERIOD_PRESETS = List.of(
            "HK1 2025-2026", "HK2 2025-2026", "Tổng kết năm 2025-2026", "Tổng kết hợp đồng", "Đánh giá thử việc");

    private final TeacherEvaluationRepository evaluationRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final AppUserRepository userRepo;
    private final AssignmentRepository assignmentRepo;
    private final DisplayNameResolver displayNameResolver;

    public EvaluationService(
            TeacherEvaluationRepository evaluationRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            AppUserRepository userRepo,
            AssignmentRepository assignmentRepo,
            DisplayNameResolver displayNameResolver) {
        this.evaluationRepo = evaluationRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.displayNameResolver = displayNameResolver;
    }

    /* ── Metadata ─────────────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<String> periodPresets() {
        return PERIOD_PRESETS;
    }

    /**
     * Dropdown GV: staff/admin = mọi GV ACTIVE; school = GV từng được phân công tại trường
     * (fallback: mọi GV ACTIVE nếu trường chưa có assignment — vẫn chặn khi submit).
     */
    @Transactional(readOnly = true)
    public List<EvaluationTeacherOption> teacherOptions() {
        if (isTeacherOnly()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Giáo viên không tạo đánh giá");
        }
        List<Teacher> all = teacherRepo.findByDeletedFalse().stream()
                .filter(t -> "ACTIVE".equals(t.getStatus()))
                .toList();

        if (isSchoolActor()) {
            Integer schoolId = requireMySchool().getId();
            Set<Integer> assigned = new HashSet<>(assignmentRepo.findDistinctTeacherIdsBySchoolId(schoolId));
            List<Teacher> scoped =
                    all.stream().filter(t -> assigned.contains(t.getId())).toList();
            // Demo/seed có thể chưa gán assignment đủ — vẫn trả full ACTIVE để form dùng được,
            // create() sẽ soft-warn bằng check exists (không chặn cứng nếu không có assignment).
            List<Teacher> source = scoped.isEmpty() ? all : scoped;
            return source.stream()
                    .map(t -> new EvaluationTeacherOption(t.getId(), teacherFullName(t), t.getStatus()))
                    .toList();
        }

        return all.stream()
                .map(t -> new EvaluationTeacherOption(t.getId(), teacherFullName(t), t.getStatus()))
                .toList();
    }

    /* ── List / detail ────────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public Page<EvaluationResponse> search(
            Integer teacherId,
            Integer schoolId,
            Short score,
            String periodNote,
            String source,
            String keyword,
            Pageable pageable) {

        Scope scope = resolveReadScope(teacherId, schoolId, source);
        Specification<TeacherEvaluation> spec = buildSpec(scope, score, periodNote, keyword);

        Page<TeacherEvaluation> page = evaluationRepo.findAll(spec, pageable);
        NameBag names = loadNames(page.getContent());
        return page.map(e -> toResponse(e, names));
    }

    @Transactional(readOnly = true)
    public EvaluationResponse getById(Integer id) {
        TeacherEvaluation e = getActiveOrThrow(id);
        assertCanView(e);
        return toResponse(e, loadNames(List.of(e)));
    }

    /* ── CRUD ─────────────────────────────────────────────────────── */

    @Transactional
    public EvaluationResponse create(EvaluationRequest req) {
        assertCanManage();
        Teacher teacher = requireActiveTeacher(req.teacherId());

        Integer evaluatorId = requireCurrentUserId();
        Integer schoolId = null;

        if (isSchoolActor()) {
            School school = requireMySchool();
            schoolId = school.getId();
            // Gợi ý nghiệp vụ: ưu tiên GV đã phân công tại trường (không chặn cứng nếu chưa có data).
            // Không throw — seed/demo có thể thiếu Assignment.
        } else if (isTeacherOnly()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Giáo viên không được gửi đánh giá");
        }
        // Staff/ADMIN: schoolId = null (đánh giá nội bộ trung tâm)

        TeacherEvaluation e = new TeacherEvaluation();
        e.setTeacherId(teacher.getId());
        e.setEvaluatorUserId(evaluatorId);
        e.setSchoolId(schoolId);
        e.setScore(req.score());
        e.setComment(trimToNull(req.comment()));
        e.setPeriodNote(trimToNull(req.periodNote()));
        e.setCreatedBy(evaluatorId);

        TeacherEvaluation saved = evaluationRepo.save(e);
        return toResponse(saved, loadNames(List.of(saved)));
    }

    @Transactional
    public EvaluationResponse update(Integer id, EvaluationRequest req) {
        TeacherEvaluation e = getActiveOrThrow(id);
        assertCanEdit(e);

        requireActiveTeacher(req.teacherId());
        // Không cho đổi sang GV khác nếu là SCHOOL (tránh "chuyển" đánh giá sang GV ngoài phạm vi)
        if (isSchoolActor() && !Objects.equals(e.getTeacherId(), req.teacherId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không được đổi giáo viên của phiếu đánh giá");
        }

        e.setTeacherId(req.teacherId());
        e.setScore(req.score());
        e.setComment(trimToNull(req.comment()));
        e.setPeriodNote(trimToNull(req.periodNote()));
        e.setUpdatedAt(Instant.now());
        e.setUpdatedBy(requireCurrentUserId());

        TeacherEvaluation saved = evaluationRepo.save(e);
        return toResponse(saved, loadNames(List.of(saved)));
    }

    @Transactional
    public void softDelete(Integer id) {
        TeacherEvaluation e = getActiveOrThrow(id);
        assertCanEdit(e);
        e.setDeleted(true);
        e.setDeletedAt(Instant.now());
        e.setDeletedBy(requireCurrentUserId());
        evaluationRepo.save(e);
    }

    /* ── Stats / summary ──────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public EvaluationStatsResponse stats(Integer teacherId, Integer schoolId, String source) {
        Scope scope = resolveReadScope(teacherId, schoolId, source);
        List<Object[]> rows = evaluationRepo.countByScoreGrouped(
                scope.teacherId(), scope.schoolId(), scope.centerOnly(), scope.schoolOnly());

        long[] dist = new long[6]; // index 1..5
        long total = 0;
        double sum = 0;
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            int s = ((Number) row[0]).intValue();
            long c = ((Number) row[1]).longValue();
            if (s >= 1 && s <= 5) {
                dist[s] = c;
                total += c;
                sum += s * c;
            }
        }
        long high = evaluationRepo.countHighScores(
                scope.teacherId(), scope.schoolId(), scope.centerOnly(), scope.schoolOnly());
        long teachers = evaluationRepo.countDistinctTeachers(
                scope.teacherId(), scope.schoolId(), scope.centerOnly(), scope.schoolOnly());
        Double avg = total == 0 ? null : Math.round((sum / total) * 100.0) / 100.0;

        return new EvaluationStatsResponse(total, avg, high, teachers, dist[1], dist[2], dist[3], dist[4], dist[5]);
    }

    @Transactional(readOnly = true)
    public TeacherEvaluationSummaryResponse teacherSummary(Integer teacherId) {
        Teacher teacher = teacherRepo
                .findByIdAndDeletedFalse(teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giáo viên"));

        // TEACHER chỉ xem summary của chính mình
        if (isTeacherOnly()) {
            Teacher me = requireMyTeacher();
            if (!Objects.equals(me.getId(), teacherId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chỉ xem được đánh giá của chính mình");
            }
        }
        // SCHOOL: vẫn cho xem summary GV (chỉ dựa trên đánh giá trường mình đã chấm)
        Integer schoolFilter =
                isSchoolActor() && !isStaffOrAdmin() ? requireMySchool().getId() : null;

        List<TeacherEvaluation> list = evaluationRepo.findByTeacherIdAndDeletedFalseOrderByCreatedAtDesc(teacherId);
        if (schoolFilter != null) {
            list = list.stream()
                    .filter(e -> Objects.equals(e.getSchoolId(), schoolFilter))
                    .toList();
        }

        long[] dist = new long[6];
        long total = 0;
        double sum = 0;
        for (TeacherEvaluation e : list) {
            if (e.getScore() == null) continue;
            int s = e.getScore();
            if (s >= 1 && s <= 5) {
                dist[s]++;
                total++;
                sum += s;
            }
        }
        Double avg = total == 0 ? null : Math.round((sum / total) * 100.0) / 100.0;
        return new TeacherEvaluationSummaryResponse(
                teacher.getId(), teacherFullName(teacher), total, avg, dist[1], dist[2], dist[3], dist[4], dist[5]);
    }

    /* ── Ownership / scope ────────────────────────────────────────── */

    private record Scope(Integer teacherId, Integer schoolId, boolean centerOnly, boolean schoolOnly) {}

    /**
     * Áp scope bắt buộc theo role, rồi hợp nhất với filter client (client không được nới scope).
     */
    private Scope resolveReadScope(Integer teacherIdFilter, Integer schoolIdFilter, String source) {
        Integer teacherId = teacherIdFilter;
        Integer schoolId = schoolIdFilter;
        boolean centerOnly = false;
        boolean schoolOnly = false;

        if (source != null && !source.isBlank()) {
            String s = source.trim().toUpperCase();
            if ("CENTER".equals(s)) centerOnly = true;
            else if ("SCHOOL".equals(s)) schoolOnly = true;
        }

        if (isTeacherOnly()) {
            Teacher me = requireMyTeacher();
            teacherId = me.getId(); // ép cứng — bỏ qua filter client
            // Teacher thấy cả CENTER lẫn SCHOOL về mình
            centerOnly = false;
            schoolOnly = false;
            schoolId = null;
        } else if (isSchoolActor() && !isStaffOrAdmin()) {
            schoolId = requireMySchool().getId();
            // Trường không xem đánh giá nội bộ trung tâm
            schoolOnly = true;
            centerOnly = false;
        }

        return new Scope(teacherId, schoolId, centerOnly, schoolOnly);
    }

    private Specification<TeacherEvaluation> buildSpec(Scope scope, Short score, String periodNote, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get("deleted")));

            if (scope.teacherId() != null) {
                preds.add(cb.equal(root.get("teacherId"), scope.teacherId()));
            }
            if (scope.schoolId() != null) {
                preds.add(cb.equal(root.get("schoolId"), scope.schoolId()));
            }
            if (scope.centerOnly()) {
                preds.add(cb.isNull(root.get("schoolId")));
            }
            if (scope.schoolOnly()) {
                preds.add(cb.isNotNull(root.get("schoolId")));
            }
            if (score != null) {
                preds.add(cb.equal(root.get("score"), score));
            }
            if (periodNote != null && !periodNote.isBlank()) {
                preds.add(cb.like(
                        cb.lower(root.get("periodNote")),
                        "%" + periodNote.trim().toLowerCase() + "%"));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim().toLowerCase();
                // Tìm theo tên GV + comment + periodNote
                List<Integer> teacherIds = teacherRepo.findByDeletedFalse().stream()
                        .filter(t -> teacherFullName(t).toLowerCase().contains(kw))
                        .map(Teacher::getId)
                        .toList();
                Predicate byComment = cb.like(cb.lower(cb.coalesce(root.get("comment"), "")), "%" + kw + "%");
                Predicate byPeriod = cb.like(cb.lower(cb.coalesce(root.get("periodNote"), "")), "%" + kw + "%");
                if (teacherIds.isEmpty()) {
                    preds.add(cb.or(byComment, byPeriod));
                } else {
                    preds.add(cb.or(root.get("teacherId").in(teacherIds), byComment, byPeriod));
                }
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private void assertCanView(TeacherEvaluation e) {
        if (isStaffOrAdmin()) return;
        if (isTeacherOnly()) {
            Teacher me = requireMyTeacher();
            if (!Objects.equals(me.getId(), e.getTeacherId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đánh giá này");
            }
            return;
        }
        if (isSchoolActor()) {
            Integer mySchoolId = requireMySchool().getId();
            if (!Objects.equals(mySchoolId, e.getSchoolId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đánh giá này");
            }
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đánh giá");
    }

    private void assertCanManage() {
        if (isTeacherOnly()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Giáo viên không được quản lý đánh giá");
        }
        if (!(isStaffOrAdmin() || isSchoolActor() || SecurityUtils.hasAuthority("EVALUATION_MANAGE"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền tạo/sửa đánh giá");
        }
    }

    private void assertCanEdit(TeacherEvaluation e) {
        assertCanManage();
        if (isStaffOrAdmin()) return;
        // SCHOOL: chỉ sửa/xóa phiếu của trường mình (và nên là người tạo — nới: cùng schoolId)
        if (isSchoolActor()) {
            Integer mySchoolId = requireMySchool().getId();
            if (!Objects.equals(mySchoolId, e.getSchoolId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ sửa/xóa đánh giá của trường bạn");
            }
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền sửa đánh giá này");
    }

    private boolean canEditFlag(TeacherEvaluation e) {
        try {
            assertCanEdit(e);
            return true;
        } catch (ApiException ex) {
            return false;
        }
    }

    /* ── Mapping / names ──────────────────────────────────────────── */

    private record NameBag(
            Map<Integer, String> teacherNames, Map<Integer, String> userNames, Map<Integer, String> schoolNames) {}

    private NameBag loadNames(List<TeacherEvaluation> list) {
        Set<Integer> teacherIds =
                list.stream().map(TeacherEvaluation::getTeacherId).collect(Collectors.toSet());
        Set<Integer> userIds =
                list.stream().map(TeacherEvaluation::getEvaluatorUserId).collect(Collectors.toSet());
        Set<Integer> schoolIds = list.stream()
                .map(TeacherEvaluation::getSchoolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Integer, String> teacherNames = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            teacherRepo.findAllById(teacherIds).forEach(t -> teacherNames.put(t.getId(), teacherFullName(t)));
        }

        Map<Integer, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<AppUser> users = userRepo.findAllById(userIds);
            for (AppUser u : users) {
                userNames.put(u.getId(), displayNameResolver.resolve(u));
            }
        }

        Map<Integer, String> schoolNames = new HashMap<>();
        if (!schoolIds.isEmpty()) {
            schoolRepo.findAllById(schoolIds).forEach(s -> schoolNames.put(s.getId(), s.getName()));
        }
        return new NameBag(teacherNames, userNames, schoolNames);
    }

    private EvaluationResponse toResponse(TeacherEvaluation e, NameBag names) {
        String source = e.getSchoolId() == null ? "CENTER" : "SCHOOL";
        boolean editable = canEditFlag(e);
        return new EvaluationResponse(
                e.getId(),
                e.getTeacherId(),
                names.teacherNames().getOrDefault(e.getTeacherId(), "GV #" + e.getTeacherId()),
                e.getEvaluatorUserId(),
                names.userNames().getOrDefault(e.getEvaluatorUserId(), "User #" + e.getEvaluatorUserId()),
                e.getSchoolId(),
                e.getSchoolId() == null
                        ? null
                        : names.schoolNames().getOrDefault(e.getSchoolId(), "Trường #" + e.getSchoolId()),
                source,
                e.getScore(),
                e.getComment(),
                e.getPeriodNote(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                editable,
                editable);
    }

    /* ── Helpers ──────────────────────────────────────────────────── */

    private TeacherEvaluation getActiveOrThrow(Integer id) {
        return evaluationRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá id=" + id));
    }

    private Teacher requireActiveTeacher(Integer teacherId) {
        Teacher t = teacherRepo
                .findByIdAndDeletedFalse(teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giáo viên"));
        if (!"ACTIVE".equals(t.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ đánh giá giáo viên đang ACTIVE");
        }
        return t;
    }

    private Integer requireCurrentUserId() {
        Integer id = SecurityUtils.currentUserId();
        if (id == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return id;
    }

    private Teacher requireMyTeacher() {
        return teacherRepo
                .findByAppUserIdAndDeletedFalse(requireCurrentUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản chưa gắn hồ sơ giáo viên"));
    }

    private School requireMySchool() {
        return schoolRepo
                .findByAppUserIdAndDeletedFalse(requireCurrentUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản chưa gắn hồ sơ trường"));
    }

    private static String teacherFullName(Teacher t) {
        String full = ((t.getLastName() == null ? "" : t.getLastName()) + " "
                        + (t.getFirstName() == null ? "" : t.getFirstName()))
                .trim();
        return full.isEmpty() ? "GV #" + t.getId() : full;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private boolean isStaffOrAdmin() {
        if (SecurityUtils.hasRole("ADMIN")
                || SecurityUtils.hasRole("EMPLOYEE")
                || SecurityUtils.hasRole("ACADEMIC")
                || SecurityUtils.hasRole("HR")) {
            return true;
        }
        // Có quyền manage nhưng không thuộc portal SCHOOL/TEACHER thuần.
        return SecurityUtils.hasAuthority("EVALUATION_MANAGE")
                && !SecurityUtils.hasRole("SCHOOL")
                && !SecurityUtils.hasRole("TEACHER");
    }

    /** pure TEACHER (không kiêm staff/admin). */
    private boolean isTeacherOnly() {
        return SecurityUtils.hasRole("TEACHER") && !isStaffOrAdmin();
    }

    private boolean isSchoolActor() {
        return SecurityUtils.hasRole("SCHOOL");
    }
}
