package com.kdc.tsdms.service;

import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.dto.SchoolRequest;
import com.kdc.tsdms.dto.SchoolResponse;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.ServiceContractRepository;
import com.kdc.tsdms.repository.StudentRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ CRUD "Trường khách hàng" (bảng School). */
@Service
public class SchoolService {

    /** Trạng thái phân công còn GIỮ CHỖ khung giờ — xem {@code Assignment.holdsTimeSlot()}. */
    private static final List<String> PHAN_CONG_CON_HIEU_LUC =
            List.of(AssignmentStatus.ACTIVE, AssignmentStatus.PENDING);

    private final SchoolRepository sRepo;
    private final BranchRepository bRepo;
    private final SchoolClassRepository classRepo;
    private final AssignmentRepository assignmentRepo;
    private final ServiceContractRepository serviceContractRepo;
    private final StudentRepository studentRepo;

    public SchoolService(
            SchoolRepository schoolRepo,
            BranchRepository branchRepo,
            SchoolClassRepository classRepo,
            AssignmentRepository assignmentRepo,
            ServiceContractRepository serviceContractRepo,
            StudentRepository studentRepo) {
        this.sRepo = schoolRepo;
        this.bRepo = branchRepo;
        this.classRepo = classRepo;
        this.assignmentRepo = assignmentRepo;
        this.serviceContractRepo = serviceContractRepo;
        this.studentRepo = studentRepo;
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

    /**
     * Xóa mềm một trường.
     *
     * <p>Luật RESTRICT: còn lớp, phân công đang chạy, hợp đồng dịch vụ hoặc hồ sơ học sinh thì
     * CẤM xóa. Trước đây hàm này không kiểm gì cả — xóa xong thì lớp, lịch dạy, hợp đồng của
     * trường vẫn sống nguyên và vẫn hiện ở mọi màn hình, chỉ là trỏ vào một cái tên đã biến mất
     * (không query nào trong dự án lọc theo cờ xóa của bảng CHA). Buổi dạy vẫn được chấm công,
     * vẫn vào lương, cho một trường về mặt sổ sách đã không còn.
     *
     * <p>CỐ Ý KHÔNG chặn theo Room và Period: đó là cấu hình thuộc về chính trường (phòng học,
     * khung tiết), không phải dữ liệu nghiệp vụ độc lập — chặn theo chúng thì mọi trường đã
     * seed đều không bao giờ xóa được.
     */
    @Transactional
    public void delete(Integer id) {
        School s = findActiveOrThrow(id);
        DeleteGuard.of("trường " + s.getName())
                .blockIf(classRepo.countBySchoolIdAndDeletedFalse(id), "lớp học")
                .blockIf(
                        assignmentRepo.countBySchoolIdAndStatusInAndDeletedFalse(id, PHAN_CONG_CON_HIEU_LUC),
                        "phân công đang chạy")
                .blockIf(serviceContractRepo.countBySchoolIdAndDeletedFalse(id), "hợp đồng dịch vụ")
                .blockIf(studentRepo.countBySchoolIdAndDeletedFalse(id), "hồ sơ học sinh")
                .check();
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
