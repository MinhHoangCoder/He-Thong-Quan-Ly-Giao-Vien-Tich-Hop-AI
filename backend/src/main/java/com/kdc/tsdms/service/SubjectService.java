package com.kdc.tsdms.service;

import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.dto.SubjectRequest;
import com.kdc.tsdms.dto.SubjectResponse;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.SubjectCategory;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.LessonRepository;
import com.kdc.tsdms.repository.SubjectCategoryRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherSubjectRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Môn học (Subject) — CRUD lồng trong trang "Nhóm môn học".
 *
 * <p>
 * categoryId luôn bắt buộc khi tạo/sửa (xem {@link SubjectRequest}) — đây là
 * điểm
 * mấu chốt để KHÔNG còn tình trạng môn học bị "mồ côi" nhóm (CategoryId null)
 * khiến
 * dropdown "Môn học" ở Kho bài giảng trống dù đã chọn Danh mục.
 */
@Service
public class SubjectService {

    private static final int MAX_DESCRIPTION_WORDS = 200;

    private final SubjectRepository subjectRepo;
    private final SubjectCategoryRepository categoryRepo;
    private final LessonRepository lessonRepo;
    private final TeacherSubjectRepository teacherSubjectRepo;
    private final AssignmentRepository assignmentRepo;

    public SubjectService(
            SubjectRepository subjectRepo,
            SubjectCategoryRepository categoryRepo,
            LessonRepository lessonRepo,
            TeacherSubjectRepository teacherSubjectRepo,
            AssignmentRepository assignmentRepo) {
        this.subjectRepo = subjectRepo;
        this.categoryRepo = categoryRepo;
        this.lessonRepo = lessonRepo;
        this.teacherSubjectRepo = teacherSubjectRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /**
     * Danh sách môn (mọi status) thuộc 1 nhóm — dùng cho khối mở rộng trong trang
     * Nhóm môn học.
     */
    @Transactional(readOnly = true)
    public List<SubjectResponse> listByCategory(Integer categoryId) {
        List<Subject> subjects = categoryId != null
                ? subjectRepo.findByCategoryIdAndDeletedFalseOrderByName(categoryId)
                : subjectRepo.findByDeletedFalseOrderByName();
        return subjects.stream()
                .map(s -> SubjectResponse.fromEntity(s, countLessons(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse getById(Integer id) {
        Subject s = getOrThrow(id);
        return SubjectResponse.fromEntity(s, countLessons(id));
    }

    @Transactional
    public SubjectResponse create(SubjectRequest req) {
        SubjectCategory category = getCategoryOrThrow(req.categoryId());
        validateDescriptionWordLimit(req.description());
        // Mã môn LUÔN tự sinh khi tạo mới (tăng dần theo nhóm môn) — bỏ qua mã
        // client gửi lên (nếu có), tránh trùng/sai định dạng do người dùng gõ tay.
        String code = generateNextCode(category);

        Subject s = new Subject();
        apply(s, req, category, code);
        s.setCreatedBy(SecurityUtils.currentUserId());
        return SubjectResponse.fromEntity(subjectRepo.save(s), 0);
    }

    @Transactional
    public SubjectResponse update(Integer id, SubjectRequest req) {
        Subject s = getOrThrow(id);
        // Khi SỬA, mã môn vẫn do người dùng nhập tay và bắt buộc (khác lúc TẠO
        // MỚI đã tự sinh) — @NotBlank được bỏ khỏi SubjectRequest nên phải tự
        // kiểm tra rỗng ở đây.
        String code = req.code() == null ? null : req.code().trim();
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mã môn không được để trống");
        }
        if (subjectRepo.existsByCodeAndDeletedFalseAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Mã môn '" + code + "' đã được dùng bởi môn khác");
        }
        SubjectCategory category = getCategoryOrThrow(req.categoryId());
        validateDescriptionWordLimit(req.description());

        apply(s, req, category, code);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        return SubjectResponse.fromEntity(subjectRepo.save(s), countLessons(id));
    }

    /**
     * Xóa MỀM môn học (2026-08-22 — trả lại xóa mềm, bỏ bản hard delete của 2026-08-07).
     *
     * <p>Bản cũ xóa hẳn dòng Subject và xóa kèm toàn bộ Lesson + LessonFile + TeacherSubject.
     * Hai chỗ sai: bài giảng là công sức soạn của giáo viên chứ không phải phụ kiện của môn
     * học, và một khi dòng Subject biến mất thì mọi bản ghi cũ trỏ vào nó chỉ còn lại con số
     * SubjectId không tra được ra tên.
     *
     * <p>Rào chắn giữ nguyên: môn đang hoạt động thì phải tắt trước, và môn còn dữ liệu con
     * (phân công, bài giảng, giáo viên phụ trách) thì chặn — {@link DeleteGuard} kể hết lý do
     * trong một lần thay vì bắt người dùng bấm lại nhiều vòng.
     */
    @Transactional
    public void delete(Integer id) {
        Subject s = getOrThrow(id);
        if (!"DISABLED".equals(s.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Không thể xóa: môn học đang ở trạng thái hoạt động. Vui lòng tắt trạng thái hoạt động trước khi xóa.");
        }
        DeleteGuard.of("môn học " + s.getName())
                .blockIf(assignmentRepo.countBySubjectId(id), "phân công giảng dạy")
                .blockIf(lessonRepo.countBySubjectIdAndDeletedFalse(id), "bài giảng")
                .blockIf(teacherSubjectRepo.countBySubjectId(id), "giáo viên đang phụ trách môn")
                .check();

        s.setDeleted(true);
        s.setDeletedAt(Instant.now());
        s.setDeletedBy(SecurityUtils.currentUserId());
        subjectRepo.save(s);
    }

    /* ── PRIVATE ── */

    private Subject getOrThrow(Integer id) {
        return subjectRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy môn học id=" + id));
    }

    private SubjectCategory getCategoryOrThrow(Integer categoryId) {
        return categoryRepo
                .findByIdAndDeletedFalse(categoryId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy nhóm môn id=" + categoryId));
    }

    private void apply(Subject s, SubjectRequest req, SubjectCategory category, String code) {
        s.setCode(code);
        s.setName(req.name());
        s.setCategory(category);
        s.setDescription(req.description());
        s.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "ACTIVE");
    }

    private long countLessons(Integer subjectId) {
        return lessonRepo.countBySubjectIdAndDeletedFalse(subjectId);
    }

    /**
     * FIX (2026-08-13): giới hạn mô tả 200 TỪ, đồng bộ với
     * SubjectCategoryService/LessonService (trước đó Subject là module DUY NHẤT
     * giới hạn theo ký tự thay vì từ). Mô tả rỗng/null luôn hợp lệ.
     */
    private void validateDescriptionWordLimit(String description) {
        if (description == null || description.isBlank()) return;
        int wordCount = description.trim().split("\\s+").length;
        if (wordCount > MAX_DESCRIPTION_WORDS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Mô tả tối đa " + MAX_DESCRIPTION_WORDS + " từ (hiện tại: " + wordCount + " từ)");
        }
    }

    /**
     * Sinh mã môn TIẾP THEO cho 1 nhóm môn: {prefix}{số thứ tự 2 chữ số}, vd
     * nhóm "STEM - AI" (code STEM_AI) -> SA01, SA02... Prefix lấy từ chữ cái
     * đầu mỗi cụm trong SubjectCategory.Code (tách bởi "_").
     * <p>
     * QUÉT TOÀN BỘ Subject (không chỉ trong nhóm) vì UX_Subject_Code là ràng
     * buộc UNIQUE toàn cục (V21), không phải duy nhất theo từng nhóm — 2 nhóm
     * khác nhau vẫn có thể vô tình ra cùng prefix.
     */
    private String generateNextCode(SubjectCategory category) {
        String prefix = buildCodePrefix(category);
        Pattern p = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$");

        int max = 0;
        for (Subject existing : subjectRepo.findByDeletedFalseOrderByName()) {
            if (existing.getCode() == null) continue;
            Matcher m = p.matcher(existing.getCode());
            if (m.matches()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }

        int next = max + 1;
        String code = prefix + String.format("%02d", next);
        // An toàn tuyệt đối: nếu vì lý do gì đó mã vẫn trùng (dữ liệu cũ méo mó),
        // tăng dần tới khi chắc chắn còn trống thay vì để lỗi 409 tới người dùng.
        while (subjectRepo.existsByCodeAndDeletedFalse(code)) {
            next++;
            code = prefix + String.format("%02d", next);
        }
        return code;
    }

    /**
     * Prefix mã môn suy ra từ SubjectCategory.Code, ví dụ TIN_HOC -> TH, STEM_AI ->
     * SA.
     */
    private String buildCodePrefix(SubjectCategory category) {
        String catCode = category.getCode() == null ? "" : category.getCode().toUpperCase();
        StringBuilder initials = new StringBuilder();
        for (String part : catCode.split("_+")) {
            if (!part.isBlank()) {
                initials.append(part.charAt(0));
            }
        }
        String prefix = initials.toString().replaceAll("[^A-Z0-9]", "");
        if (prefix.length() < 2) {
            // Nhóm chỉ có 1 từ / mã lạ -> lùi về lấy tối đa 4 ký tự đầu của mã nhóm.
            String compact = catCode.replaceAll("[^A-Z0-9]", "");
            prefix = compact.isEmpty() ? "MH" : compact.substring(0, Math.min(4, compact.length()));
        }
        return prefix;
    }
}
