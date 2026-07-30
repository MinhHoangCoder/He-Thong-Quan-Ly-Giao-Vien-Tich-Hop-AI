package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.SubjectCategoryRequest;
import com.kdc.tsdms.dto.SubjectCategoryResponse;
import com.kdc.tsdms.entity.Lesson;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.SubjectCategory;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.LessonRepository;
import com.kdc.tsdms.repository.SubjectCategoryRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectCategoryService {

    /**
     * Giới hạn mô tả nhóm môn học: tối đa 200 TỪ (không phải ký tự). @Size trên
     * SubjectCategoryRequest chỉ đếm ký tự nên không đủ để chặn theo số từ ->
     * chặn thêm ở đây, trước khi lưu (áp dụng cho cả tạo mới lẫn sửa).
     */
    private static final int MAX_DESCRIPTION_WORDS = 200;

    private final SubjectCategoryRepository categoryRepo;
    private final SubjectRepository subjectRepo;
    private final LessonRepository lessonRepo;

    public SubjectCategoryService(
            SubjectCategoryRepository categoryRepo, SubjectRepository subjectRepo, LessonRepository lessonRepo) {
        this.categoryRepo = categoryRepo;
        this.subjectRepo = subjectRepo;
        this.lessonRepo = lessonRepo;
    }

    /* ── Dropdown cho form (chỉ ACTIVE) ── */
    @Transactional(readOnly = true)
    public List<SubjectCategoryResponse> listActive() {
        return categoryRepo.findByStatusAndDeletedFalseOrderByName("ACTIVE").stream()
                .map(sc -> SubjectCategoryResponse.fromEntity(sc, countSubjects(sc.getId())))
                .toList();
    }

    /* ── Danh sách có phân trang ── */
    @Transactional(readOnly = true)
    public Page<SubjectCategoryResponse> search(String keyword, Pageable pageable) {
        return categoryRepo
                .search(keyword, pageable)
                .map(sc -> SubjectCategoryResponse.fromEntity(sc, countSubjects(sc.getId())));
    }

    /* ── Chi tiết ── */
    @Transactional(readOnly = true)
    public SubjectCategoryResponse getById(Integer id) {
        SubjectCategory sc = getOrThrow(id);
        return SubjectCategoryResponse.fromEntity(sc, countSubjects(id));
    }

    /* ── Tạo mới ── */
    @Transactional
    public SubjectCategoryResponse create(SubjectCategoryRequest req) {
        if (categoryRepo.existsByCodeAndDeletedFalse(req.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Code '" + req.code() + "' đã tồn tại");
        }
        validateDescriptionWordLimit(req.description());
        SubjectCategory sc = new SubjectCategory();
        apply(sc, req);
        sc.setCreatedBy(SecurityUtils.currentUserId());
        return SubjectCategoryResponse.fromEntity(categoryRepo.save(sc), 0);
    }

    /* ── Cập nhật ── */
    @Transactional
    public SubjectCategoryResponse update(Integer id, SubjectCategoryRequest req) {
        SubjectCategory sc = getOrThrow(id);
        if (categoryRepo.existsByCodeAndDeletedFalseAndIdNot(req.code(), id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Code '" + req.code() + "' đã được dùng bởi nhóm môn khác");
        }
        validateDescriptionWordLimit(req.description());
        apply(sc, req);
        sc.setUpdatedAt(Instant.now());
        sc.setUpdatedBy(SecurityUtils.currentUserId());
        return SubjectCategoryResponse.fromEntity(categoryRepo.save(sc), countSubjects(id));
    }

    /* ── Xóa mềm ── */
    /**
     * FIX (2026-07-30): quy tắc xóa nhóm môn học nay CHỈ dựa vào trạng thái
     * (status):
     * - status = ACTIVE (đang hoạt động) -> LUÔN chặn xóa, kể cả khi nhóm chưa
     * có môn học nào, để tránh xóa nhầm 1 nhóm đang được dùng.
     * - status = DISABLED (đã tắt hoạt động) -> cho phép xóa, đồng thời xóa mềm
     * (cascade) toàn bộ môn học thuộc nhóm này, và với mỗi môn học đó, cascade
     * tiếp xuống toàn bộ bài giảng đang thuộc môn — tránh để lại môn học/bài
     * giảng "mồ côi" trỏ tới 1 nhóm môn đã bị xóa.
     */
    @Transactional
    public void delete(Integer id) {
        SubjectCategory sc = getOrThrow(id);
        if (!"DISABLED".equals(sc.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Không thể xóa: nhóm môn học đang ở trạng thái hoạt động. Vui lòng tắt trạng thái hoạt động trước khi xóa.");
        }
        List<Subject> subjects = subjectRepo.findByCategoryIdAndDeletedFalseOrderByName(id);
        if (!subjects.isEmpty()) {
            Instant now = Instant.now();
            Integer uid = SecurityUtils.currentUserId();
            for (Subject s : subjects) {
                List<Lesson> lessons = lessonRepo.findBySubjectIdAndDeletedFalse(s.getId());
                if (!lessons.isEmpty()) {
                    for (Lesson l : lessons) {
                        l.setDeleted(true);
                        l.setDeletedAt(now);
                        l.setDeletedBy(uid);
                    }
                    lessonRepo.saveAll(lessons);
                }
                s.setDeleted(true);
                s.setDeletedAt(now);
                s.setDeletedBy(uid);
            }
            subjectRepo.saveAll(subjects);
        }
        sc.setDeleted(true);
        sc.setDeletedAt(Instant.now());
        sc.setDeletedBy(SecurityUtils.currentUserId());
        categoryRepo.save(sc);
    }

    /* ── PRIVATE ── */

    /**
     * Đếm số từ (tách theo khoảng trắng) của mô tả và chặn nếu vượt quá
     * {@link #MAX_DESCRIPTION_WORDS}. Mô tả rỗng/null luôn hợp lệ.
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

    private SubjectCategory getOrThrow(Integer id) {
        return categoryRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm môn id=" + id));
    }

    private void apply(SubjectCategory sc, SubjectCategoryRequest req) {
        sc.setCode(req.code());
        sc.setName(req.name());
        sc.setDescription(req.description());
        sc.setStatus(req.status() != null ? req.status() : "ACTIVE");
    }

    /**
     * Đếm số môn ACTIVE chưa xóa mềm đang dùng nhóm môn này.
     * Dùng SubjectRepository.countByCategoryIdAndStatusAndDeletedFalse (cần thêm
     * method).
     */
    private long countSubjects(Integer categoryId) {
        return subjectRepo.countByCategoryIdAndDeletedFalse(categoryId);
    }
}
