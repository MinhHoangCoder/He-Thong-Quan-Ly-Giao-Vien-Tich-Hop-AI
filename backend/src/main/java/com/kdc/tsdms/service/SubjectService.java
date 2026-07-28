package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.SubjectRequest;
import com.kdc.tsdms.dto.SubjectResponse;
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

    private final SubjectRepository subjectRepo;
    private final SubjectCategoryRepository categoryRepo;
    private final LessonRepository lessonRepo;

    public SubjectService(
            SubjectRepository subjectRepo, SubjectCategoryRepository categoryRepo, LessonRepository lessonRepo) {
        this.subjectRepo = subjectRepo;
        this.categoryRepo = categoryRepo;
        this.lessonRepo = lessonRepo;
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
        if (subjectRepo.existsByCodeAndDeletedFalse(req.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Mã môn '" + req.code() + "' đã tồn tại");
        }
        SubjectCategory category = getCategoryOrThrow(req.categoryId());

        Subject s = new Subject();
        apply(s, req, category);
        s.setCreatedBy(SecurityUtils.currentUserId());
        return SubjectResponse.fromEntity(subjectRepo.save(s), 0);
    }

    @Transactional
    public SubjectResponse update(Integer id, SubjectRequest req) {
        Subject s = getOrThrow(id);
        if (subjectRepo.existsByCodeAndDeletedFalseAndIdNot(req.code(), id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Mã môn '" + req.code() + "' đã được dùng bởi môn khác");
        }
        SubjectCategory category = getCategoryOrThrow(req.categoryId());

        apply(s, req, category);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        return SubjectResponse.fromEntity(subjectRepo.save(s), countLessons(id));
    }

    @Transactional
    public void delete(Integer id) {
        Subject s = getOrThrow(id);
        long used = countLessons(id);
        if (used > 0) {
            // FIX (2026-07-28): trước đây LUÔN chặn xóa môn học nếu còn bài giảng
            // tham chiếu, bất kể trạng thái. Theo yêu cầu mới: nếu môn học đã bị
            // TẮT HOẠT ĐỘNG (status = DISABLED) thì cho phép xóa, đồng thời xóa
            // mềm (cascade) luôn TẤT CẢ bài giảng đang thuộc môn này — tránh để
            // lại bài giảng "mồ côi" trỏ tới 1 môn học đã bị xóa.
            // Môn học còn ACTIVE thì vẫn chặn như cũ để không xóa nhầm dữ liệu
            // đang được dùng.
            if (!"DISABLED".equals(s.getStatus())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Không thể xóa: môn học đang được dùng bởi " + used
                                + " bài giảng. Vui lòng tắt trạng thái hoạt động trước khi xóa.");
            }
            Instant now = Instant.now();
            Integer uid = SecurityUtils.currentUserId();
            List<Lesson> lessons = lessonRepo.findBySubjectIdAndDeletedFalse(id);
            for (Lesson l : lessons) {
                l.setDeleted(true);
                l.setDeletedAt(now);
                l.setDeletedBy(uid);
            }
            lessonRepo.saveAll(lessons);
        }
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

    private void apply(Subject s, SubjectRequest req, SubjectCategory category) {
        s.setCode(req.code());
        s.setName(req.name());
        s.setCategory(category);
        s.setDescription(req.description());
        s.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "ACTIVE");
    }

    private long countLessons(Integer subjectId) {
        return lessonRepo.countBySubjectIdAndDeletedFalse(subjectId);
    }
}
