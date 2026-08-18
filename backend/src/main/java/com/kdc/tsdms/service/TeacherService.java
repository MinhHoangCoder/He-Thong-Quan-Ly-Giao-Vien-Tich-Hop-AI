package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.dto.TeacherResponse;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Certificate;
import com.kdc.tsdms.entity.Contract;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TeacherService {
    private final TeacherRepository teacherRepo;
    private final CertificateRepository ceRepo;
    private final ContractRepository contractRepo;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepo;
    private final AssignmentRepository assignmentRepo;
    private final ScheduleRepository scheduleRepo;

    /** Trạng thái phân công / buổi dạy còn hiệu lực — vẫn sinh công và vẫn vào lương. */
    private static final List<String> PHAN_CONG_CON_HIEU_LUC =
            List.of(AssignmentStatus.ACTIVE, AssignmentStatus.PENDING);

    private static final List<String> BUOI_CON_HIEU_LUC = List.of("PENDING", "APPROVED");

    /**
     * Nhãn tiếng Việt cho từng bảng con trỏ vào Teacher — khớp mã loại do
     * {@code TeacherRepository.countChildRowsByTeacherId} trả về. Thêm bảng con mới thì thêm
     * cả ở đây lẫn ở câu SQL bên repository.
     */
    private static final Map<String, String> NHAN_BANG_CON = Map.ofEntries(
            Map.entry("certificate", "chứng chỉ"),
            Map.entry("contract", "hợp đồng"),
            Map.entry("subject", "môn được phân dạy"),
            Map.entry("assignment", "phân công"),
            Map.entry("slot", "ô lịch tuần"),
            Map.entry("schedule", "buổi dạy"),
            Map.entry("attendance", "bản ghi chấm công"),
            Map.entry("amend", "yêu cầu bổ sung công"),
            Map.entry("payroll", "phiếu lương"),
            Map.entry("evaluation", "phiếu đánh giá"),
            Map.entry("lesson", "bài giảng"));

    private static final Path UPLOAD_ROOT =
            Paths.get("uploads/teachers").toAbsolutePath().normalize();
    private static final long MAX_UPLOAD_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20MB

    public TeacherService(
            TeacherRepository teacherRepo,
            CertificateRepository ceRepo,
            ContractRepository contractRepo,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepo,
            AssignmentRepository assignmentRepo,
            ScheduleRepository scheduleRepo) {
        this.teacherRepo = teacherRepo;
        this.ceRepo = ceRepo;
        this.contractRepo = contractRepo;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepo = refreshTokenRepo;
        this.assignmentRepo = assignmentRepo;
        this.scheduleRepo = scheduleRepo;
    }

    // DANH SÁCH  ======================================

    @Transactional(readOnly = true)
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
     * (ADMIN hoặc quyền TEACHER_VIEW) hoặc CHÍNH CHỦ mới được xem —
     * đúng mẫu chuẩn mô tả ở {@link SecurityUtils}.
     */
    @Transactional(readOnly = true)
    public TeacherResponse.Response getTeacherById(Integer id) {
        Teacher t = findActiveOrThrow(id);
        boolean isStaff = canViewAnyTeacher();
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
        t.setTeachingExperience(req.getTeachingExperience());
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
        t.setTeachingExperience(req.getTeachingExperience());
        t.setUpdatedAt(Instant.now());
        t.setUpdatedBy(SecurityUtils.currentUserId());

        syncAppUserAccount(t.getAppUserId(), req.getUsername(), req.getEmail(), null);

        return toResponse(teacherRepo.save(t), true);
    }

    /** Đọc username/email hiện tại — dùng cho GET /teacher/{id}/account (nạp form Sửa). */
    @Transactional(readOnly = true)
    public TeacherResponse.AccountInfo getAccount(Integer teacherId) {
        Teacher t = findActiveOrThrow(teacherId);
        boolean isStaff = canViewAnyTeacher();
        boolean isOwner = t.getAppUserId() != null && t.getAppUserId().equals(SecurityUtils.currentUserId());
        if (!isStaff && !isOwner) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem tài khoản này");
        }
        AppUser au = appUserRepository
                .findById(t.getAppUserId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản đăng nhập liên kết với giáo viên này"));
        return TeacherResponse.AccountInfo.builder()
                .id(au.getId())
                .username(au.getUsername())
                .email(au.getEmail())
                .build();
    }

    /**
     * Đổi RIÊNG username/email của tài khoản đăng nhập gắn với 1 GV — dùng cho
     * PUT /teacher/{id}/account, tách khỏi form Sửa hồ sơ chính (updateTeacher ở trên
     * vẫn nhận username/email luôn nếu FE gộp chung 1 request, 2 API dùng chung 1 hàm
     * đồng bộ syncAppUserAccount() nên không lặp logic).
     */
    @Transactional
    public TeacherResponse.Response updateAccount(Integer teacherId, TeacherResponse.AccountUpdateRequest req) {
        Teacher t = findActiveOrThrow(teacherId);
        syncAppUserAccount(t.getAppUserId(), req.getUsername(), req.getEmail(), req.getPassword());
        return toResponse(t, true);
    }

    //   Đồng bộ username/email/password sang bảng AppUser — dùng CHUNG cho updateTeacher() và
    //  updateAccount() để không viết trùng logic check-trùng 2 nơi.
    //  newUsername/newEmail/newPassword để trống (null hoặc "") nghĩa là GIỮ NGUYÊN, không đổi.
    //  Mật khẩu mới LUÔN được băm lại bằng BCrypt trước khi lưu — không bao giờ lưu thô.

    private AppUser syncAppUserAccount(Integer appUserId, String newUsername, String newEmail, String newPassword) {
        AppUser au = appUserRepository
                .findById(appUserId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản đăng nhập liên kết với giáo viên này"));
        boolean userChanged = false;
        if (newUsername != null && !newUsername.isBlank()) {
            String trimmed = newUsername.trim();
            if (!trimmed.equals(au.getUsername())) {
                if (appUserRepository.existsByUsernameAndDeletedFalseAndIdNot(trimmed, au.getId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "Tên đăng nhập \"" + trimmed + "\" đã được sử dụng");
                }
                au.setUsername(trimmed);
                userChanged = true;
            }
        }
        if (newEmail != null && !newEmail.isBlank()) {
            String trimmed = newEmail.trim();
            if (!trimmed.equalsIgnoreCase(au.getEmail())) {
                if (appUserRepository.existsByEmailAndDeletedFalseAndIdNot(trimmed, au.getId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "Email \"" + trimmed + "\" đã được sử dụng");
                }
                au.setEmail(trimmed);
                userChanged = true;
            }
        }
        // Admin đặt mật khẩu mới hộ GV — không cần biết mật khẩu cũ (khác với self change-password).
        boolean passwordChanged = newPassword != null && !newPassword.isBlank();
        if (passwordChanged) {
            au.setPasswordHash(passwordEncoder.encode(newPassword));
            userChanged = true;
        }
        if (userChanged) appUserRepository.save(au);

        // Đổi mật khẩu mà KHÔNG thu hồi phiên thì gần như vô nghĩa: refresh token cũ sống 7 ngày
        // nên kẻ đang chiếm phiên vẫn xin được access token mới bằng mật khẩu cũ đã bị thay.
        // Mà admin đặt lại mật khẩu hộ thường chính vì nghi tài khoản bị lộ.
        if (passwordChanged) {
            refreshTokenRepo.revokeAllActiveByAppUserId(au.getId(), Instant.now());
        }
        return au;
    }
    // delete (Chỉ Admin xóa) (ẩn khỏi ds)

    @Transactional
    public void deleteTeacher(Integer id) {
        Teacher t = findActiveOrThrow(id);
        DeleteGuard.of("giáo viên " + fullName(t.getLastName(), t.getFirstName()))
                .blockIf(
                        assignmentRepo.countByTeacherIdAndStatusInAndDeletedFalse(id, PHAN_CONG_CON_HIEU_LUC),
                        "phân công đang chạy")
                .blockIf(
                        scheduleRepo.countByTeacherIdAndStartTimeAfterAndStatusInAndDeletedFalse(
                                id, BusinessTime.now(), BUOI_CON_HIEU_LUC),
                        "buổi dạy sắp tới")
                .check();
        t.setDeleted(true);
        t.setDeletedAt(Instant.now());
        t.setDeletedBy(SecurityUtils.currentUserId());
        t.setStatus("RETIRED"); // GV bị ẩn khỏi ds chính -> luôn hiện "Ngừng hoạt động" trong thùng rác
        t.setUpdatedAt(Instant.now());
        t.setUpdatedBy(SecurityUtils.currentUserId());
        teacherRepo.save(t);
    }

    // History

    @Transactional(readOnly = true)
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
        t.setStatus("ACTIVE");
        t.setUpdatedAt(Instant.now());
        t.setUpdatedBy(SecurityUtils.currentUserId());
        return toResponse(teacherRepo.save(t), false);
    }

    /**
     * Xóa VĨNH VIỄN khỏi DB — CHỈ áp dụng cho GV đang nằm trong thùng rác (deleted=true).
     * Không thể hoàn tác.
     *
     * <p>Trước đây hàm này XÓA CỨNG toàn bộ chứng chỉ + hợp đồng để dọn đường cho câu
     * {@code DELETE Teacher}, rồi bọc tất cả trong một {@code catch DataIntegrityViolationException}
     * trả về đúng một câu "Không thể xóa vĩnh viễn: giáo viên id=7". Hai cái sai chồng nhau:
     *
     * <ul>
     *   <li><b>Sai về nghiệp vụ:</b> hợp đồng lao động và bằng cấp là hồ sơ pháp lý — chúng bị
     *       hủy như hiệu ứng phụ của một thao tác dọn dẹp, và vì là xóa cứng nên không có
     *       thùng rác nào giữ lại.
     *   <li><b>Sai về thông tin:</b> khi khóa ngoại chặn thật (còn phân công, chấm công, phiếu
     *       lương...) thì người dùng chỉ nhận được một câu không nói gì — không biết vướng ở
     *       đâu, không biết phải làm gì, và đằng sau lưng thì chứng chỉ + hợp đồng ĐÃ bị xóa
     *       mất rồi vì transaction cũng rollback nhưng bản thân người dùng không hề hay biết
     *       thao tác đã đi xa tới đâu.
     * </ul>
     *
     * <p>Nay: hỏi thẳng DB xem còn dòng con nào ở cả 11 bảng, kể tên đầy đủ, và KHÔNG xóa hộ
     * thứ gì cả. Hệ quả cố ý là giáo viên đã từng làm việc thật thì gần như không bao giờ xóa
     * cứng được — họ nằm lại trong thùng rác, khôi phục được. Chỉ hồ sơ tạo nhầm (chưa gắn dữ
     * liệu nào) mới xóa hẳn, đúng thứ tính năng này thực sự cần phục vụ.
     */
    @Transactional
    public void deleteTrueTeacher(Integer id) {
        Teacher t = teacherRepo
                .findByIdAndDeletedTrue(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "Giáo viên id=" + id + " không có trong thùng rác => không thể xóa "));
        DeleteGuard.of("vĩnh viễn giáo viên " + fullName(t.getLastName(), t.getFirstName()))
                .blockAll(moTaDuLieuCon(teacherRepo.countChildRowsByTeacherId(id)))
                .huongDan("Hợp đồng và bằng cấp là hồ sơ pháp lý, chấm công và phiếu lương là chứng từ tiền "
                        + "lương — không xóa được để dọn đường. Giáo viên vẫn nằm trong thùng rác và khôi "
                        + "phục lại được bất cứ lúc nào.")
                .check();
        teacherRepo.delete(t);
        teacherRepo.flush();
    }

    /**
     * Đổi kết quả thô của {@code countChildRowsByTeacherId} thành các cụm chữ kiểu
     * {@code "3 chứng chỉ"}. Mã loại lạ (bảng mới thêm mà quên khai nhãn) vẫn được kể ra bằng
     * chính mã đó — thà xấu còn hơn im lặng bỏ sót một rào chắn.
     */
    private static List<String> moTaDuLieuCon(List<Object[]> rows) {
        return rows.stream()
                .map(r -> {
                    String loai = String.valueOf(r[0]);
                    long soLuong = ((Number) r[1]).longValue();
                    return soLuong + " " + NHAN_BANG_CON.getOrDefault(loai, loai);
                })
                .toList();
    }

    // ════════════════════════════════════════════════════════════════
    // CHỨNG CHỈ — quản lý ĐỘC LẬP sau khi GV đã tồn tại
    // (KHÁC với certificates trong createTeacher: đó chỉ là lúc TẠO MỚI,
    //  còn đây là thêm/xóa chứng chỉ cho GV ĐÃ CÓ SẴN, dùng quanh năm)
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public TeacherResponse.CertificateDTO addCertificate(Integer teacherId, TeacherResponse.CertificateRequest req) {
        findActiveOrThrow(teacherId); // đảm bảo GV tồn tại và chưa bị xóa trước khi thêm
        return toCertDTO(ceRepo.save(buildCert(teacherId, req)));
    }

    /**
     * XÓA MỀM — bằng cấp/chứng chỉ là HỒ SƠ PHÁP LÝ, không phải rác tạm.
     *
     * <p>Bản cũ xóa hẳn dòng DB và {@code Files.deleteIfExists} luôn file PDF trên đĩa, với lý
     * do "xóa mềm chỉ tổ dồn rác". Nhưng thứ bị dọn ở đây là bản scan bằng đại học của một con
     * người: bấm nhầm một cái là mất vĩnh viễn, không có thùng rác, không khôi phục được, và
     * cũng không còn dấu vết ai đã bấm. Đổi lại, xóa mềm giữ đủ {@code DeletedAt/DeletedBy} —
     * vừa rút chứng chỉ khỏi hồ sơ hiển thị, vừa trả lời được câu "ai gỡ, gỡ lúc nào".
     *
     * <p>File PDF vì thế cũng phải GIỮ NGUYÊN trên đĩa: dòng DB vẫn trỏ vào nó, xóa file đi là
     * biến bản ghi còn sống thành cái vỏ rỗng, khôi phục cũng chẳng mở ra được gì.
     */
    @Transactional
    public void deleteCertificate(Integer teacherId, Integer certId) {
        Certificate c = ceRepo.findByIdAndTeacherIdAndDeletedFalse(certId, teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng chỉ id=" + certId));
        Integer nguoiXoa = SecurityUtils.currentUserId();
        c.setDeleted(true);
        c.setDeletedAt(Instant.now());
        c.setDeletedBy(nguoiXoa);
        c.setUpdatedAt(Instant.now());
        c.setUpdatedBy(nguoiXoa);
        ceRepo.save(c);
    }

    //   Upload file PDF THẬT cho 1 bằng cấp/chứng chỉ đã tồn tại — TRƯỚC ĐÂY addCertificate()
    //   chỉ lưu đúng chuỗi TÊN FILE do FE gửi lên (d.file.name), file thật chưa từng được
    //   truyền lên server, nên FileUrl trong DB chỉ là chuỗi vô nghĩa, không mở được.
    //   Dùng POST /teacher/{teacherId}/certificates/{certId}/file (multipart/form-data).

    @Transactional
    public TeacherResponse.CertificateDTO uploadCertificateFile(Integer teacherId, Integer certId, MultipartFile file) {
        findActiveOrThrow(teacherId);
        Certificate c = ceRepo.findByIdAndTeacherIdAndDeletedFalse(certId, teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng chỉ id=" + certId));

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn file");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";

        if (file.getSize() > MAX_UPLOAD_FILE_SIZE_BYTES) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "File \"" + original + "\" vượt quá dung lượng cho phép (tối đa 20MB)");
        }
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ cho phép upload file PDF");
        }
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase() : "";
        if (!"pdf".equals(ext)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ cho phép upload file PDF");
        }

        // Đọc file vào bộ nhớ ĐÚNG 1 LẦN rồi dùng lại (check magic number + ghi đĩa) —
        // trước đây gọi file.getInputStream() 2 lần riêng biệt, một số container servlet
        // không đảm bảo đọc lại được lần 2, gây lỗi 500 không rõ nguyên nhân.
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể đọc file PDF");
        }
        // Kiểm tra 4 byte đầu đúng magic number "%PDF" — chặn đổi đuôi .exe/.php thành .pdf giả
        if (bytes.length < 4 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File không phải PDF hợp lệ");
        }

        Path dir = UPLOAD_ROOT.resolve(String.valueOf(teacherId));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được thư mục lưu file");
        }
        String stored = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(stored).normalize();
        if (!target.startsWith(dir.normalize())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ");
        }
        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi lưu file: " + original);
        }

        c.setFileUrl(teacherId + "/" + stored); // đường dẫn TƯƠNG ĐỐI so với UPLOAD_ROOT
        return toCertDTO(ceRepo.save(c));
    }

    //   Mở/tải file PDF của 1 bằng cấp/chứng chỉ — trả "inline" (không phải "attachment")
    //   để trình duyệt MỞ THẲNG PDF ở tab mới thay vì ép tải về, đúng ý "bấm vào để xem".
    //   Cho phép cả staff (ADMIN / quyền TEACHER_VIEW) lẫn CHÍNH giáo viên đó xem hồ sơ của mình.

    @Transactional(readOnly = true)
    public ResponseEntity<?> openCertificateFile(Integer teacherId, Integer certId) {
        Teacher t = findActiveOrThrow(teacherId);
        boolean isStaff = canViewAnyTeacher();
        boolean isOwner = t.getAppUserId() != null && t.getAppUserId().equals(SecurityUtils.currentUserId());
        if (!isStaff && !isOwner) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem file này");
        }
        Certificate c = ceRepo.findByIdAndTeacherIdAndDeletedFalse(certId, teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng chỉ id=" + certId));
        if (c.getFileUrl() == null || c.getFileUrl().isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Chứng chỉ này chưa có file đính kèm");
        }
        try {
            Path path = UPLOAD_ROOT.resolve(c.getFileUrl()).normalize();
            if (!path.startsWith(UPLOAD_ROOT)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Đường dẫn file không hợp lệ");
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "File không tồn tại trên server");
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + c.getName() + ".pdf\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .body(resource);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy file");
        }
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

    /**
     * Ai được xem hồ sơ của NGƯỜI KHÁC: ADMIN (đi tắt theo quy ước RBAC của dự án) hoặc
     * người có quyền {@code TEACHER_VIEW}. Hồ sơ chứa CCCD, ngày sinh, địa chỉ và lương hợp
     * đồng nên chỉ chức danh được cấp quyền mới mở được — trước đây chỉ cần MANG tên role
     * {@code EMPLOYEE} là qua, mà role đó trong ma trận RolePermission không được cấp quyền
     * nào cả, nên nhân viên tạo thiếu UserRole vẫn đọc được sạch hồ sơ.
     */
    private static boolean canViewAnyTeacher() {
        return SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasAuthority("TEACHER_VIEW");
    }

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

        AppUser user = appUserRepository.findById(t.getAppUserId()).orElse(null);
        TeacherResponse.Response.ResponseBuilder builder = TeacherResponse.Response.builder()
                .id(t.getId())
                .appUserId(t.getAppUserId())
                .branchId(t.getBranchId())
                .firstName(t.getFirstName())
                .lastName(t.getLastName())
                .fullName(fullName(t.getLastName(), t.getFirstName()))
                .email(user != null ? user.getEmail() : null)
                .username(user != null ? user.getUsername() : null)
                .dateOfBirth(t.getDateOfBirth())
                .gender(t.getGender())
                .idCardNo(t.getIdCardNo())
                .phone(t.getPhone())
                .address(t.getAddress())
                .hireDate(t.getHireDate())
                .employmentType(t.getEmploymentType())
                .teachingExperience(t.getTeachingExperience())
                .status(t.getStatus())
                .certificates(certs)
                .contract(contract);

        return builder.build();
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
