package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.EvaluationRequest;
import com.kdc.tsdms.dto.EvaluationResponse;
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
import java.time.LocalDate;
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

    /**
     * Preset kỳ theo năm học hiện tại + vài mục cố định.
     * Vẫn là text lưu vào PeriodNote (không có bảng danh mục riêng).
     */
    @Transactional(readOnly = true)
    public List<String> periodPresets() {
        String ay = academicYearLabel(LocalDate.now());
        return List.of("HK1 " + ay, "HK2 " + ay, "Tổng kết năm " + ay, "Tổng kết hợp đồng", "Đánh giá thử việc");
    }

    /** Preset + kỳ gợi ý theo tháng (HK1: 8–12, HK2: 1–5, 6–7: tổng kết năm). */
    @Transactional(readOnly = true)
    public EvaluationResponse.PeriodMeta periodMeta() {
        return new EvaluationResponse.PeriodMeta(periodPresets(), suggestedPeriod(LocalDate.now()));
    }

    /**
     * Kiểm tra trùng: cùng GV + người chấm hiện tại + kỳ (so khớp sau normalize).
     * {@code excludeId} dùng khi sửa phiếu (bỏ qua chính nó).
     */
    @Transactional(readOnly = true)
    public EvaluationResponse.DuplicateCheck checkDuplicate(Integer teacherId, String periodNote, Integer excludeId) {
        if (teacherId == null || periodNote == null || periodNote.isBlank()) {
            return new EvaluationResponse.DuplicateCheck(false, 0, null);
        }
        assertCanManage();
        Integer evaluatorId = requireCurrentUserId();
        long count = countDuplicates(teacherId, evaluatorId, periodNote, excludeId);
        if (count == 0) {
            return new EvaluationResponse.DuplicateCheck(false, 0, null);
        }
        String msg = "Bạn đã đánh giá giáo viên này trong kỳ \""
                + periodNote.trim()
                + "\" ("
                + count
                + " phiếu). Vẫn muốn lưu thêm?";
        return new EvaluationResponse.DuplicateCheck(true, count, msg);
    }

    /**
     * Dropdown GV thông minh: kèm TB / số lượt / đã chấm kỳ chưa.
     * {@code periodNote} rỗng → dùng kỳ gợi ý hiện tại.
     * {@code keyword} lọc tên (không phân biệt hoa thường).
     * Sắp xếp: chưa chấm kỳ trước, rồi theo tên.
     */
    @Transactional(readOnly = true)
    public List<EvaluationResponse.TeacherOption> teacherOptions(String periodNote, String keyword) {
        if (isTeacherOnly()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Giáo viên không tạo đánh giá");
        }
        String period = resolvePeriod(periodNote);
        List<Teacher> source = filterByKeyword(resolveCandidateTeachers(), keyword);
        return buildTeacherOptions(source, period, /*onlyUnevaluated*/ false);
    }

    /**
     * GV chưa có đánh giá trong kỳ (phạm vi role). Dùng KPI + panel “còn bao nhiêu người”.
     */
    @Transactional(readOnly = true)
    public EvaluationResponse.Unevaluated unevaluatedTeachers(String periodNote, String keyword) {
        if (isTeacherOnly()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Giáo viên không xem danh sách này");
        }
        // VIEW cũng được xem (ACADEMIC/SCHOOL có VIEW); manage không bắt buộc
        if (!isStaffOrAdmin() && !isSchoolActor() && !SecurityUtils.hasAuthority("EVALUATION_VIEW")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền xem");
        }
        String period = resolvePeriod(periodNote);
        List<Teacher> candidates = resolveCandidateTeachers();
        long total = candidates.size();
        List<EvaluationResponse.TeacherOption> allOpts = buildTeacherOptions(candidates, period, false);
        long evaluated = allOpts.stream()
                .filter(EvaluationResponse.TeacherOption::evaluatedInPeriod)
                .count();
        List<EvaluationResponse.TeacherOption> uneval = allOpts.stream()
                .filter(o -> !o.evaluatedInPeriod())
                .filter(o -> matchKeyword(o.name(), keyword))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
        return new EvaluationResponse.Unevaluated(period, total, evaluated, uneval.size(), uneval);
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
        String period = requirePeriodNote(req.periodNote());

        Integer evaluatorId = requireCurrentUserId();
        Integer schoolId = null;

        if (isSchoolActor()) {
            School school = requireMySchool();
            schoolId = school.getId();
        } else if (isTeacherOnly()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Giáo viên không được gửi đánh giá");
        }

        // Cảnh báo trùng kỳ — client gửi forceDuplicate=true sau khi user xác nhận.
        if (!Boolean.TRUE.equals(req.forceDuplicate())) {
            long dup = countDuplicates(teacher.getId(), evaluatorId, period, null);
            if (dup > 0) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Bạn đã đánh giá giáo viên này trong kỳ \""
                                + period
                                + "\" ("
                                + dup
                                + " phiếu). Gửi lại kèm xác nhận để lưu thêm.");
            }
        }

        TeacherEvaluation e = new TeacherEvaluation();
        e.setTeacherId(teacher.getId());
        e.setEvaluatorUserId(evaluatorId);
        e.setSchoolId(schoolId);
        e.setScore(req.score());
        e.setComment(trimToNull(req.comment()));
        e.setPeriodNote(period);
        e.setCreatedBy(evaluatorId);

        TeacherEvaluation saved = evaluationRepo.save(e);
        return toResponse(saved, loadNames(List.of(saved)));
    }

    @Transactional
    public EvaluationResponse update(Integer id, EvaluationRequest req) {
        TeacherEvaluation e = getActiveOrThrow(id);
        assertCanEdit(e);

        requireActiveTeacher(req.teacherId());
        String period = requirePeriodNote(req.periodNote());
        // Không cho đổi sang GV khác nếu là SCHOOL (tránh "chuyển" đánh giá sang GV ngoài phạm vi)
        if (isSchoolActor() && !Objects.equals(e.getTeacherId(), req.teacherId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không được đổi giáo viên của phiếu đánh giá");
        }

        e.setTeacherId(req.teacherId());
        e.setScore(req.score());
        e.setComment(trimToNull(req.comment()));
        e.setPeriodNote(period);
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
    public EvaluationResponse.Stats stats(Integer teacherId, Integer schoolId, String source) {
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

        return new EvaluationResponse.Stats(total, avg, high, teachers, dist[1], dist[2], dist[3], dist[4], dist[5]);
    }

    @Transactional(readOnly = true)
    public EvaluationResponse.Summary teacherSummary(Integer teacherId) {
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
        return new EvaluationResponse.Summary(
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

    private String resolvePeriod(String periodNote) {
        String t = trimToNull(periodNote);
        return t != null ? t : suggestedPeriod(LocalDate.now());
    }

    /**
     * Staff/admin: mọi GV ACTIVE. School: ưu tiên GV đã phân công; fallback full ACTIVE (demo).
     */
    private List<Teacher> resolveCandidateTeachers() {
        List<Teacher> all = teacherRepo.findByDeletedFalse().stream()
                .filter(t -> "ACTIVE".equals(t.getStatus()))
                .toList();
        if (isSchoolActor() && !isStaffOrAdmin()) {
            Integer schoolId = requireMySchool().getId();
            Set<Integer> assigned = new HashSet<>(assignmentRepo.findDistinctTeacherIdsBySchoolId(schoolId));
            List<Teacher> scoped =
                    all.stream().filter(t -> assigned.contains(t.getId())).toList();
            return scoped.isEmpty() ? all : scoped;
        }
        return all;
    }

    private List<Teacher> filterByKeyword(List<Teacher> teachers, String keyword) {
        if (keyword == null || keyword.isBlank()) return teachers;
        return teachers.stream()
                .filter(t -> matchKeyword(teacherFullName(t), keyword))
                .toList();
    }

    private static boolean matchKeyword(String name, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        if (name == null) return false;
        return name.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    /**
     * Build options kèm thống kê. Scope đánh giá: SCHOOL chỉ tính phiếu SchoolId = trường mình.
     */
    private List<EvaluationResponse.TeacherOption> buildTeacherOptions(
            List<Teacher> teachers, String periodNote, boolean onlyUnevaluated) {
        if (teachers.isEmpty()) return List.of();
        Integer schoolFilter =
                isSchoolActor() && !isStaffOrAdmin() ? requireMySchool().getId() : null;
        String normPeriod = normalizePeriod(periodNote);

        Set<Integer> ids = teachers.stream().map(Teacher::getId).collect(Collectors.toSet());
        List<TeacherEvaluation> evals = evaluationRepo.findByTeacherIdInAndDeletedFalse(ids);
        if (schoolFilter != null) {
            evals = evals.stream()
                    .filter(e -> Objects.equals(e.getSchoolId(), schoolFilter))
                    .toList();
        }

        Map<Integer, List<TeacherEvaluation>> byTeacher =
                evals.stream().collect(Collectors.groupingBy(TeacherEvaluation::getTeacherId));

        List<EvaluationResponse.TeacherOption> result = new ArrayList<>();
        for (Teacher t : teachers) {
            List<TeacherEvaluation> list = byTeacher.getOrDefault(t.getId(), List.of());
            long total = 0;
            double sum = 0;
            long inPeriod = 0;
            for (TeacherEvaluation e : list) {
                if (e.getScore() != null && e.getScore() >= 1 && e.getScore() <= 5) {
                    total++;
                    sum += e.getScore();
                }
                if (normPeriod.equals(normalizePeriod(e.getPeriodNote()))) {
                    inPeriod++;
                }
            }
            boolean evaluated = inPeriod > 0;
            if (onlyUnevaluated && evaluated) continue;
            Double avg = total == 0 ? null : Math.round((sum / total) * 100.0) / 100.0;
            result.add(new EvaluationResponse.TeacherOption(
                    t.getId(), teacherFullName(t), t.getStatus(), total, avg, evaluated, inPeriod));
        }
        // Chưa chấm kỳ trước, rồi theo tên
        result.sort((a, b) -> {
            if (a.evaluatedInPeriod() != b.evaluatedInPeriod()) {
                return a.evaluatedInPeriod() ? 1 : -1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });
        return result;
    }

    private static String requirePeriodNote(String periodNote) {
        String t = trimToNull(periodNote);
        if (t == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kỳ đánh giá không được để trống");
        }
        if (t.length() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kỳ đánh giá tối đa 50 ký tự");
        }
        return t;
    }

    /** So khớp kỳ: trim, gộp khoảng trắng, không phân biệt hoa thường. */
    private static String normalizePeriod(String periodNote) {
        if (periodNote == null) return "";
        return periodNote.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private long countDuplicates(Integer teacherId, Integer evaluatorId, String periodNote, Integer excludeId) {
        String norm = normalizePeriod(periodNote);
        if (norm.isEmpty()) return 0;
        return evaluationRepo.findByTeacherIdAndEvaluatorUserIdAndDeletedFalse(teacherId, evaluatorId).stream()
                .filter(e -> excludeId == null || !Objects.equals(e.getId(), excludeId))
                .filter(e -> normalizePeriod(e.getPeriodNote()).equals(norm))
                .count();
    }

    /** Năm học dạng 2025-2026 (bắt đầu tháng 8). */
    static String academicYearLabel(LocalDate date) {
        int y = date.getYear();
        int m = date.getMonthValue();
        if (m >= 8) {
            return y + "-" + (y + 1);
        }
        return (y - 1) + "-" + y;
    }

    /** Gợi ý kỳ theo tháng lịch. */
    static String suggestedPeriod(LocalDate date) {
        String ay = academicYearLabel(date);
        int m = date.getMonthValue();
        if (m >= 8 && m <= 12) {
            return "HK1 " + ay;
        }
        if (m >= 1 && m <= 5) {
            return "HK2 " + ay;
        }
        // 6–7: cuối năm học
        return "Tổng kết năm " + ay;
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
