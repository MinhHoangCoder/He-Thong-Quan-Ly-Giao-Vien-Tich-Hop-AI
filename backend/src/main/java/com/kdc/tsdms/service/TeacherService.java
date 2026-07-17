package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.TeacherResponse;
import com.kdc.tsdms.entity.Certificate;
import com.kdc.tsdms.entity.Contract;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TeacherService {

    private static final String UPLOAD_ROOT = "uploads/teachers";

    private final TeacherRepository teacherRepo;
    private final CertificateRepository ceRepo;
    private final ContractRepository contractRepo;

    public TeacherService(
            TeacherRepository teacherRepo, CertificateRepository ceRepo, ContractRepository contractRepo) {
        this.teacherRepo = teacherRepo;
        this.ceRepo = ceRepo;
        this.contractRepo = contractRepo;
    }

    // DANH SÁCH  ======================================

    public List<TeacherResponse.Response> getAllTeachers() {
        return teacherRepo.findByDeletedFalse().stream()
                .map(t -> toResponse(t, false))
                .toList();
    }

    // Xem chi tiết thông tin gv + chứng chỉ + hợp đồng

    /**
     * Xem đầy đủ thông tin 1 GV, kèm danh sách chứng chỉ + hợp đồng hiện tại.
     *
     * <p>Chống IDOR: hồ sơ chứa CCCD + lương hợp đồng nên chỉ staff
     * (ADMIN/EMPLOYEE hoặc quyền TEACHER_VIEW) hoặc CHÍNH CHỦ mới được xem —
     * đúng mẫu chuẩn mô tả ở {@link SecurityUtils}.
     */
    public TeacherResponse.Response getTeacherById(Integer id) {
        Teacher t = findActiveOrThrow(id);
        boolean isStaff = SecurityUtils.hasRole("ADMIN")
                || SecurityUtils.hasRole("EMPLOYEE")
                || SecurityUtils.hasAuthority("TEACHER_VIEW");
        boolean isOwner = t.getAppUserId() != null && t.getAppUserId().equals(SecurityUtils.currentUserId());
        if (!isStaff && !isOwner) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem hồ sơ này");
        }
        return toResponse(t, true);
    }

    // Create

    @Transactional
    public TeacherResponse.Response createTeacher(TeacherResponse.CreateRequest req) {
        if (teacherRepo.findByAppUserIdAndDeletedFalse(req.getAppUserId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "AppUserId " + req.getAppUserId() + " đã có hồ sơ giáo viên");
        }
        if (req.getIdCardNo() != null
                && !req.getIdCardNo().isBlank()
                && teacherRepo.existsByIdCardNoAndDeletedFalse(req.getIdCardNo())) {
            throw new ApiException(HttpStatus.CONFLICT, "Số CCCD " + req.getIdCardNo() + " đã tồn tại trong hệ thống");
        }

        Teacher t = new Teacher();
        t.setAppUserId(req.getAppUserId());
        t.setBranchId(req.getBranchId());
        t.setFirstName(req.getFirstName());
        t.setLastName(req.getLastName());
        t.setDateOfBirth(req.getDateOfBirth());
        t.setGender(req.getGender());
        t.setIdCardNo(req.getIdCardNo());
        t.setPhone(req.getPhone());
        t.setAddress(req.getAddress());
        t.setHireDate(req.getHireDate());
        t.setEmploymentType(req.getEmploymentType());
        t.setStatus(req.getStatus() != null && !req.getStatus().isBlank() ? req.getStatus() : "ACTIVE");

        Teacher saved = teacherRepo.save(t);

        if (req.getCertificates() != null) {
            req.getCertificates().forEach(cr -> ceRepo.save(buildCert(saved.getId(), cr)));
        }
        if (req.getContract() != null) {
            saveContract(saved.getId(), req.getContract());
        }

        return toResponse(saved, true);
    }

    // update

    @Transactional
    public TeacherResponse.Response updateTeacher(Integer id, TeacherResponse.UpdateRequest req) {
        Teacher t = findActiveOrThrow(id);

        boolean idCardChanged = req.getIdCardNo() != null && !req.getIdCardNo().equals(t.getIdCardNo());
        if (idCardChanged
                && !req.getIdCardNo().isBlank()
                && teacherRepo.existsByIdCardNoAndDeletedFalse(req.getIdCardNo())) {
            throw new ApiException(HttpStatus.CONFLICT, "Số CCCD " + req.getIdCardNo() + " đã tồn tại trong hệ thống");
        }

        t.setBranchId(req.getBranchId());
        t.setFirstName(req.getFirstName());
        t.setLastName(req.getLastName());
        t.setStatus(req.getStatus());
        t.setEmploymentType(req.getEmploymentType());
        t.setDateOfBirth(req.getDateOfBirth());
        t.setGender(req.getGender());
        t.setIdCardNo(req.getIdCardNo());
        t.setPhone(req.getPhone());
        t.setAddress(req.getAddress());
        t.setHireDate(req.getHireDate());
        t.setUpdatedAt(Instant.now());
        t.setUpdatedBy(SecurityUtils.currentUserId());

        return toResponse(teacherRepo.save(t), true);
    }
    // delete (Chỉ Admin xóa) (ẩn khỏi ds)

    @Transactional
    public void deleteTeacher(Integer id) {
        Teacher t = findActiveOrThrow(id);
        t.setDeleted(true);
        t.setDeletedAt(Instant.now());
        t.setDeletedBy(SecurityUtils.currentUserId());
        teacherRepo.save(t);
    }
    // History

    public List<TeacherResponse.HistoryItem> getTrash() {
        return teacherRepo.findByDeletedTrueOrderByDeletedAtDesc().stream()
                .map(this::toHistoryItem)
                .toList();
    }

    /** Khôi phục GV từ thùng rác về danh sách active. */
    @Transactional
    public TeacherResponse.Response restoreTeacher(Integer id) {
        Teacher t = teacherRepo
                .findByIdAndDeletedTrue(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giáo viên đã xóa với id=" + id));
        t.setDeleted(false);
        t.setDeletedAt(null);
        t.setDeletedBy(null);
        t.setUpdatedAt(Instant.now());
        t.setUpdatedBy(SecurityUtils.currentUserId());
        return toResponse(teacherRepo.save(t), false);
    }

    // ════════════════════════════════════════════════════════════════
    // CHỨNG CHỈ — quản lý ĐỘC LẬP sau khi GV đã tồn tại
    // (KHÁC với certificates trong createTeacher: đó chỉ là lúc TẠO MỚI,
    //  còn đây là thêm/xóa/sửa chứng chỉ cho GV ĐÃ CÓ SẴN, dùng quanh năm)
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public TeacherResponse.CertificateDTO addCertificate(Integer teacherId, TeacherResponse.CertificateRequest req) {
        findActiveOrThrow(teacherId); // đảm bảo GV tồn tại và chưa bị xóa trước khi thêm
        return toCertDTO(ceRepo.save(buildCert(teacherId, req)));
    }

    @Transactional
    public void deleteCertificate(Integer teacherId, Integer certId) {
        Certificate c = ceRepo.findByIdAndTeacherIdAndDeletedFalse(certId, teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng chỉ id=" + certId));
        c.setDeleted(true);
        c.setDeletedAt(Instant.now());
        c.setDeletedBy(SecurityUtils.currentUserId());
        ceRepo.save(c);
    }

    @Transactional
    public TeacherResponse.CertificateDTO updateCertificate(
            Integer teacherId, Integer certId, TeacherResponse.CertificateRequest req) {

        Certificate c = ceRepo.findByIdAndTeacherIdAndDeletedFalse(certId, teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng chỉ"));

        c.setName(req.getName());
        c.setIssuer(req.getIssuer());
        c.setIssueDate(req.getIssueDate());
        c.setExpiryDate(req.getExpiryDate());
        c.setFileUrl(req.getFileUrl());

        return toCertDTO(ceRepo.save(c));
    }

    @Transactional
    public TeacherResponse.CertificateDTO uploadCertificateFile(Integer teacherId, Integer certId, MultipartFile file) {
        findActiveOrThrow(teacherId);
        Certificate c = ceRepo.findByIdAndTeacherIdAndDeletedFalse(certId, teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng chỉ id=" + certId));

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn file");
        }

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot) : "";
        String stored = UUID.randomUUID() + ext;

        Path dir = Path.of(UPLOAD_ROOT, String.valueOf(teacherId));
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi lưu file: " + original);
        }

        c.setFileUrl("/" + UPLOAD_ROOT + "/" + teacherId + "/" + stored);
        return toCertDTO(ceRepo.save(c));
    }

    // HỢP ĐỒNG — upsert ĐỘC LẬP sau khi GV đã tồn tại
    // (DB chỉ cho 1 hợp đồng active/GV nên không có "addContract nhiều lần")
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public TeacherResponse.ContractDTO upsertContract(Integer teacherId, TeacherResponse.ContractRequest req) {
        findActiveOrThrow(teacherId);
        return toContractDTO(saveContract(teacherId, req));
    }

    private Contract saveContract(Integer teacherId, TeacherResponse.ContractRequest req) {
        Contract contract = contractRepo
                .findByTeacherIdAndDeletedFalse(teacherId)
                .orElseGet(() -> {
                    Contract c = new Contract();
                    c.setTeacherId(teacherId);
                    return c;
                });
        contract.setContractNo(req.getContractNo());
        contract.setStartDate(req.getStartDate());
        contract.setEndDate(req.getEndDate());
        contract.setBaseSalary(req.getBaseSalary());
        contract.setAllowance(req.getAllowance());
        contract.setFileUrl(req.getFileUrl());
        contract.setStatus("ACTIVE");
        return contractRepo.save(contract);
    }

    /** "Họ và tên đệm" + "Tên" — đúng thứ tự đọc tiếng Việt. */
    private static String fullName(String lastName, String firstName) {
        return ((lastName == null ? "" : lastName) + " " + (firstName == null ? "" : firstName)).trim();
    }

    // PRIVATE HELPERS — KHÔNG phải API, chỉ là hàm dùng nội bộ trong class
    // ════════════════════════════════════════════════════════════════

    private Teacher findActiveOrThrow(Integer id) {
        return teacherRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giáo viên id=" + id));
    }

    private Certificate buildCert(Integer teacherId, TeacherResponse.CertificateRequest req) {
        Certificate c = new Certificate();
        c.setTeacherId(teacherId);
        c.setName(req.getName());
        c.setIssuer(req.getIssuer());
        c.setIssueDate(req.getIssueDate());
        c.setExpiryDate(req.getExpiryDate());
        c.setFileUrl(req.getFileUrl());
        return c;
    }

    private TeacherResponse.Response toResponse(Teacher t, boolean loadDetail) {
        List<TeacherResponse.CertificateDTO> certs = Collections.emptyList();
        TeacherResponse.ContractDTO contract = null;

        if (loadDetail) {
            certs = ceRepo.findByTeacherIdAndDeletedFalse(t.getId()).stream()
                    .map(this::toCertDTO)
                    .toList();
            contract = contractRepo
                    .findByTeacherIdAndDeletedFalse(t.getId())
                    .map(this::toContractDTO)
                    .orElse(null);
        }

        return TeacherResponse.Response.builder()
                .id(t.getId())
                .appUserId(t.getAppUserId())
                .branchId(t.getBranchId())
                .firstName(t.getFirstName())
                .lastName(t.getLastName())
                .fullName(fullName(t.getLastName(), t.getFirstName()))
                .dateOfBirth(t.getDateOfBirth())
                .gender(t.getGender())
                .idCardNo(t.getIdCardNo())
                .phone(t.getPhone())
                .address(t.getAddress())
                .hireDate(t.getHireDate())
                .employmentType(t.getEmploymentType())
                .status(t.getStatus())
                .certificates(certs)
                .contract(contract)
                .build();
    }

    private TeacherResponse.HistoryItem toHistoryItem(Teacher t) {
        return TeacherResponse.HistoryItem.builder()
                .id(t.getId())
                .fullName(fullName(t.getLastName(), t.getFirstName()))
                .branchId(t.getBranchId())
                .employmentType(t.getEmploymentType())
                .status(t.getStatus())
                .deletedAt(t.getDeletedAt())
                .deletedBy(t.getDeletedBy())
                .phone(t.getPhone())
                .idCardNo(t.getIdCardNo())
                .build();
    }

    private TeacherResponse.CertificateDTO toCertDTO(Certificate c) {
        return TeacherResponse.CertificateDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .issuer(c.getIssuer())
                .issueDate(c.getIssueDate())
                .expiryDate(c.getExpiryDate())
                .fileUrl(c.getFileUrl())
                .build();
    }

    private TeacherResponse.ContractDTO toContractDTO(Contract c) {
        return TeacherResponse.ContractDTO.builder()
                .id(c.getId())
                .contractNo(c.getContractNo())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .baseSalary(c.getBaseSalary())
                .allowance(c.getAllowance())
                .status(c.getStatus())
                .fileUrl(c.getFileUrl())
                .build();
    }
}
