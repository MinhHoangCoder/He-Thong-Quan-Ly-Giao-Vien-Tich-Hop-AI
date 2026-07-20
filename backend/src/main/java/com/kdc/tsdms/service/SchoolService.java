package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.SchoolRequest;
import com.kdc.tsdms.dto.SchoolResponse;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ CRUD "Trường khách hàng" (bảng School). */
@Service
public class SchoolService {

    private final SchoolRepository sRepo;
    private final BranchRepository bRepo;

    public SchoolService(SchoolRepository schoolRepo, BranchRepository branchRepo) {
        this.sRepo = schoolRepo;
        this.bRepo = branchRepo;
    }

    /* ── Danh sách có phân trang + tìm kiếm/lọc ── */
    @Transactional(readOnly = true)
    public Page<SchoolResponse> search(String keyword, Integer branchId, String status, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String st = (status == null || status.isBlank()) ? null : status;
        return sRepo.search(kw, branchId, st, pageable).map(this::toResponse);
    }

    /* ── Chi tiết ── */
    @Transactional(readOnly = true)
    public SchoolResponse getById(Integer id) {
        return toResponse(findActiveOrThrow(id));
    }

    /* ── Tạo mới ── */
    @Transactional
    public SchoolResponse create(SchoolRequest req) {
        validateBranch(req.branchId());

        School s = new School();
        apply(s, req);
        s.setCreatedBy(SecurityUtils.currentUserId());
        return toResponse(sRepo.save(s));
    }

    /* ── Cập nhật ── */
    @Transactional
    public SchoolResponse update(Integer id, SchoolRequest req) {
        School s = findActiveOrThrow(id);
        validateBranch(req.branchId());

        apply(s, req);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        return toResponse(sRepo.save(s));
    }

    /* ── Xóa mềm ── */
    @Transactional
    public void delete(Integer id) {
        School s = findActiveOrThrow(id);
        s.setDeleted(true);
        s.setDeletedAt(Instant.now());
        s.setDeletedBy(SecurityUtils.currentUserId());
        s.setStatus("INACTIVE");
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        sRepo.save(s);
    }

    /* ── PRIVATE ── */

    private School findActiveOrThrow(Integer id) {
        return sRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy trường id=" + id));
    }

    private void validateBranch(Integer branchId) {
        if (!bRepo.existsById(branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chi nhánh id=" + branchId + " không tồn tại");
        }
    }

    private void apply(School s, SchoolRequest req) {
        s.setBranchId(req.branchId());
        s.setName(req.name());
        s.setAddress(req.address());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setContactPerson(req.contactPerson());
        s.setContractStartDate(req.contractStartDate());
        s.setContractEndDate(req.contractEndDate());
        s.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "ACTIVE");
    }

    private SchoolResponse toResponse(School s) {
        String branchName = bRepo.findById(s.getBranchId()).map(Branch::getName).orElse(null);
        return SchoolResponse.fromEntity(s, branchName);
    }
}
