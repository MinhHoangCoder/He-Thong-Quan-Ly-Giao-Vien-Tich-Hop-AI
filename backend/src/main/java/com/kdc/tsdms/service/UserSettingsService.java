package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.ChangePasswordRequest;
import com.kdc.tsdms.dto.ProfileResponse;
import com.kdc.tsdms.dto.SessionResponse;
import com.kdc.tsdms.dto.UpdateProfileRequest;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.RefreshToken;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.EmployeeRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.repository.UserRoleRepository;
import com.kdc.tsdms.security.JwtService;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trang CÀI ĐẶT CÁ NHÂN — user thao tác trên CHÍNH tài khoản mình (không nhận id từ
 * ngoài vào -> miễn nhiễm IDOR): hồ sơ liên hệ, đổi mật khẩu, quản lý phiên đăng nhập.
 */
@Service
public class UserSettingsService {

    private final AppUserRepository appUserRepo;
    private final UserRoleRepository userRoleRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final TeacherRepository teacherRepo;
    private final EmployeeRepository employeeRepo;
    private final SchoolRepository schoolRepo;
    private final BranchRepository branchRepo;
    private final CertificateRepository certificateRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserSettingsService(
            AppUserRepository appUserRepo,
            UserRoleRepository userRoleRepo,
            RefreshTokenRepository refreshTokenRepo,
            TeacherRepository teacherRepo,
            EmployeeRepository employeeRepo,
            SchoolRepository schoolRepo,
            BranchRepository branchRepo,
            CertificateRepository certificateRepo,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.appUserRepo = appUserRepo;
        this.userRoleRepo = userRoleRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.teacherRepo = teacherRepo;
        this.employeeRepo = employeeRepo;
        this.schoolRepo = schoolRepo;
        this.branchRepo = branchRepo;
        this.certificateRepo = certificateRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Hồ sơ cá nhân: định danh (AppUser) + hồ sơ tác nhân theo vai trò.
     * GV nhận thêm khối {@code teacher} (CCCD, ngày sinh, địa chỉ, bằng cấp...) —
     * dữ liệu chính chủ tự xem nên trả đầy đủ, không che.
     */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        AppUser user = currentUser();
        List<String> roles = userRoleRepo.findRoleNamesByAppUserId(user.getId());

        var teacher = teacherRepo.findByAppUserIdAndDeletedFalse(user.getId());
        if (teacher.isPresent()) {
            var t = teacher.get();
            var certificates = certificateRepo.findByTeacherIdAndDeletedFalse(t.getId()).stream()
                    .map(c -> new ProfileResponse.CertificateItem(
                            c.getName(), c.getIssuer(), c.getIssueDate(), c.getExpiryDate()))
                    .toList();
            var detail = new ProfileResponse.TeacherDetail(
                    t.getIdCardNo(),
                    t.getDateOfBirth(),
                    t.getGender(),
                    t.getAddress(),
                    t.getHireDate(),
                    t.getTeachingExperience(),
                    certificates);
            return new ProfileResponse(
                    user.getUsername(),
                    user.getEmail(),
                    person(t.getLastName(), t.getFirstName(), user.getUsername()),
                    t.getPhone(),
                    "TEACHER",
                    null,
                    roles,
                    branchName(t.getBranchId()),
                    t.getEmploymentType(),
                    t.getStatus(),
                    detail);
        }
        var employee = employeeRepo.findByAppUserIdAndDeletedFalse(user.getId());
        if (employee.isPresent()) {
            var e = employee.get();
            return new ProfileResponse(
                    user.getUsername(),
                    user.getEmail(),
                    person(e.getLastName(), e.getFirstName(), user.getUsername()),
                    e.getPhone(),
                    "EMPLOYEE",
                    e.getPosition(),
                    roles,
                    branchName(e.getBranchId()),
                    e.getEmploymentType(),
                    e.getStatus(),
                    null);
        }
        var school = schoolRepo.findByAppUserIdAndDeletedFalse(user.getId());
        if (school.isPresent()) {
            var s = school.get();
            return new ProfileResponse(
                    user.getUsername(),
                    user.getEmail(),
                    s.getName(),
                    s.getPhone(),
                    "SCHOOL",
                    null,
                    roles,
                    null,
                    null,
                    s.getStatus(),
                    null);
        }
        // Tài khoản không gắn hồ sơ (admin hệ thống)
        return new ProfileResponse(
                user.getUsername(),
                user.getEmail(),
                user.getUsername(),
                null,
                "NONE",
                null,
                roles,
                null,
                null,
                null,
                null);
    }

    /** Tên chi nhánh theo id — null nếu không có (hồ sơ cũ/chi nhánh đã xóa). */
    private String branchName(Integer branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepo.findById(branchId).map(Branch::getName).orElse(null);
    }

    /**
     * Tự sửa thông tin LIÊN HỆ của chính mình: email (trên AppUser) + SĐT (trên hồ sơ
     * tác nhân). Họ tên KHÔNG cho tự sửa — dữ liệu pháp lý gắn hợp đồng/bảng lương,
     * thuộc quyền phòng Nhân sự.
     */
    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest req) {
        AppUser user = currentUser();

        String email = req.email().trim();
        // Email dùng cho luồng quên-mật-khẩu -> phải là duy nhất (loại trừ chính mình).
        var owner = appUserRepo.findByEmailAndDeletedFalse(email);
        if (owner.isPresent() && !owner.get().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email đã được tài khoản khác sử dụng");
        }
        user.setEmail(email);

        String phone = req.phone() == null || req.phone().isBlank()
                ? null
                : req.phone().trim();
        teacherRepo.findByAppUserIdAndDeletedFalse(user.getId()).ifPresent(t -> t.setPhone(phone));
        employeeRepo.findByAppUserIdAndDeletedFalse(user.getId()).ifPresent(e -> e.setPhone(phone));
        schoolRepo.findByAppUserIdAndDeletedFalse(user.getId()).ifPresent(s -> s.setPhone(phone));

        return getProfile();
    }

    /**
     * Đổi mật khẩu khi ĐANG đăng nhập: xác nhận chính chủ bằng mật khẩu hiện tại, sau đó
     * thu hồi mọi phiên KHÁC (kẻ đã chiếm phiên bị văng; thiết bị này ở lại nhờ refreshToken).
     */
    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        AppUser user = currentUser();
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));

        String keepHash = ownActiveSessionHash(user.getId(), req.refreshToken());
        Instant now = Instant.now();
        if (keepHash != null) {
            refreshTokenRepo.revokeAllActiveByAppUserIdExceptHash(user.getId(), keepHash, now);
        } else {
            refreshTokenRepo.revokeAllActiveByAppUserId(user.getId(), now);
        }
    }

    /** Liệt kê phiên đang sống; đánh dấu {@code current} bằng cách so hash refresh token FE gửi lên. */
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(String rawRefreshToken) {
        Integer userId = currentUser().getId();
        String currentHash = rawRefreshToken == null ? "" : jwtService.sha256(rawRefreshToken);
        return refreshTokenRepo
                .findByAppUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now())
                .stream()
                .map(rt -> new SessionResponse(
                        rt.getId(), rt.getCreatedAt(), rt.getExpiresAt(), currentHash.equals(rt.getTokenHash())))
                .toList();
    }

    /** Thu hồi 1 phiên theo id — chỉ phiên CỦA MÌNH (id người khác trả 404, không lộ tồn tại). */
    @Transactional
    public void revokeSession(Long sessionId) {
        Integer userId = currentUser().getId();
        RefreshToken rt = refreshTokenRepo
                .findById(sessionId)
                .filter(t -> t.getAppUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên đăng nhập"));
        if (rt.getRevokedAt() == null) {
            rt.setRevokedAt(Instant.now());
        }
    }

    /** Nút "đăng xuất thiết bị khác": thu hồi mọi phiên trừ phiên hiện tại. */
    @Transactional
    public int revokeOtherSessions(String rawRefreshToken) {
        Integer userId = currentUser().getId();
        String keepHash = ownActiveSessionHash(userId, rawRefreshToken);
        if (keepHash == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Phiên hiện tại không hợp lệ");
        }
        return refreshTokenRepo.revokeAllActiveByAppUserIdExceptHash(userId, keepHash, Instant.now());
    }

    /** Hash của refresh token nếu nó là phiên đang sống CỦA CHÍNH user; sai chủ/hết hạn -> null. */
    private String ownActiveSessionHash(Integer userId, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return null;
        }
        String hash = jwtService.sha256(rawRefreshToken);
        return refreshTokenRepo
                .findByTokenHash(hash)
                .filter(rt -> rt.getAppUserId().equals(userId) && rt.isActive())
                .map(RefreshToken::getTokenHash)
                .orElse(null);
    }

    /** Tài khoản đang đăng nhập (lấy id từ token — không nhận từ request). */
    private AppUser currentUser() {
        Integer userId = SecurityUtils.currentUserId();
        return appUserRepo
                .findById(userId == null ? -1 : userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Phiên không hợp lệ"));
    }

    private static String person(String lastName, String firstName, String fallback) {
        String full = ((lastName == null ? "" : lastName) + " " + (firstName == null ? "" : firstName)).trim();
        return full.isEmpty() ? fallback : full;
    }
}
