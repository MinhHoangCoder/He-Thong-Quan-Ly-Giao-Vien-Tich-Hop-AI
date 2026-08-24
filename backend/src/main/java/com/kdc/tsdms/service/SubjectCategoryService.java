package com.kdc.tsdms.service;

import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.dto.SubjectCategoryRequest;
import com.kdc.tsdms.dto.SubjectCategoryResponse;
import com.kdc.tsdms.entity.Subject;
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

    /**
     * Giới hạn mô tả nhóm môn học: tối đa 200 TỪ (không phải ký tự). @Size trên
     * SubjectCategoryRequest chỉ đếm ký tự nên không đủ để chặn theo số từ ->
     * chặn thêm ở đây, trước khi lưu (áp dụng cho cả tạo mới lẫn sửa).
     */
    private static final int MAX_DESCRIPTION_WORDS = 200;

    private final SubjectCategoryRepository categoryRepo;
    private final SubjectRepository subjectRepo;
    private final SubjectService subjectService;

    public SubjectCategoryService(
            SubjectCategoryRepository categoryRepo, SubjectRepository subjectRepo, SubjectService subjectService) {
        this.categoryRepo = categoryRepo;
        this.subjectRepo = subjectRepo;
        this.subjectService = subjectService;
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
     * Xóa mềm nhóm môn học, CASCADE xuống các môn con.
     *
     * <p>Vì sao vẫn cascade chứ không chặn hẳn như Chi nhánh/Trường: môn học BẮT BUỘC thuộc
     * một nhóm ({@code CategoryId} không cho null từ V8), nên để lại môn khi nhóm đã biến mất
     * là đẻ ra đúng cái "môn mồ côi" làm dropdown Kho bài giảng trống rỗng.
     *
     * <p>ĐỢT 5 (2026-08-24) — VÁ LỖ HỔNG NẶNG NHẤT CỦA CHUỖI XÓA. Bản cũ cascade THẲNG: gắn
     * cờ xóa cho mọi môn trong nhóm rồi cho mọi bài giảng của các môn đó, KHÔNG hỏi han gì.
     * Nghĩa là nó đi vòng qua trọn vẹn {@code DeleteGuard} của {@link SubjectService}: xóa một
     * môn còn 1 bài giảng thì bị chặn, còn xóa cả nhóm chứa 20 môn và 300 bài giảng thì trôi
     * tuột. Đường đi chỉ 2 bước và chỉ cần quyền {@code LESSON_MANAGE}: sửa nhóm → đổi trạng
     * thái sang Đã tắt (không có rào nào), rồi bấm Xóa.
     *
     * <p>Nay hỏi lại ĐÚNG bộ rào chắn của từng môn con qua
     * {@link SubjectService#raoChanXoaMon} — một môn vướng là cả thao tác dừng, và thông báo
     * kể rõ môn nào vướng cái gì. Hệ quả: cascade chỉ còn chạm tới môn RỖNG, nên vòng lặp xóa
     * bài giảng của bản cũ không còn lý do tồn tại và đã được bỏ.
     *
     * <p>CỐ Ý KHÔNG bắt từng môn con phải ở trạng thái DISABLED (điều kiện của
     * {@link SubjectService#delete}): việc nhóm đã bị tắt là đủ làm cửa xác nhận, mà môn đã
     * qua được rào dữ liệu ở trên thì đằng nào cũng là môn rỗng.
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

        // Hỏi rào chắn của TẤT CẢ môn con TRƯỚC, chưa đụng vào dòng nào. Gom hết môn vướng rồi
        // báo một lần — người dùng thấy trọn bức tranh thay vì gỡ từng môn rồi bấm lại.
        DeleteGuard guard = DeleteGuard.of("nhóm môn học " + sc.getName());
        for (Subject s : subjects) {
            List<String> lyDo =
                    subjectService.raoChanXoaMon(s.getId(), s.getName()).lyDo();
            guard.blockWhen(!lyDo.isEmpty(), "môn " + s.getName() + " (" + String.join(", ", lyDo) + ")");
        }
        guard.huongDan("Xóa nhóm môn sẽ xóa theo mọi môn trong nhóm, nên môn nào còn dữ liệu là cả "
                        + "thao tác dừng lại. Hãy xử lý dữ liệu của những môn kể trên trước khi xóa cả nhóm.")
                .check();

        Instant now = Instant.now();
        Integer uid = SecurityUtils.currentUserId();
        for (Subject s : subjects) {
            s.setDeleted(true);
            s.setDeletedAt(now);
            s.setDeletedBy(uid);
        }
        subjectRepo.saveAll(subjects);

        sc.setDeleted(true);
        sc.setDeletedAt(now);
        sc.setDeletedBy(uid);
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
