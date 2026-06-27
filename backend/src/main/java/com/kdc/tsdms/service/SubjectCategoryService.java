package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.SubjectCategoryRequest;
import com.kdc.tsdms.dto.SubjectCategoryResponse;
import com.kdc.tsdms.entity.SubjectCategory;
import com.kdc.tsdms.exception.ApiException;
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

    private final SubjectCategoryRepository categoryRepo;
    private final SubjectRepository subjectRepo;

    public SubjectCategoryService(SubjectCategoryRepository categoryRepo, SubjectRepository subjectRepo) {
        this.categoryRepo = categoryRepo;
        this.subjectRepo = subjectRepo;
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
        apply(sc, req);
        sc.setUpdatedAt(Instant.now());
        sc.setUpdatedBy(SecurityUtils.currentUserId());
        return SubjectCategoryResponse.fromEntity(categoryRepo.save(sc), countSubjects(id));
    }

    /* ── Xóa mềm ── */
    @Transactional
    public void delete(Integer id) {
        SubjectCategory sc = getOrThrow(id);
        long used = countSubjects(id);
        if (used > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Không thể xóa: nhóm môn đang được dùng bởi " + used + " môn học");
        }
        sc.setDeleted(true);
        sc.setDeletedAt(Instant.now());
        sc.setDeletedBy(SecurityUtils.currentUserId());
        categoryRepo.save(sc);
    }

    /* ── PRIVATE ── */

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
