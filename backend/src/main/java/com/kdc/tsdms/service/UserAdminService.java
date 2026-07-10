package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.RoleMatrixResponse;
import com.kdc.tsdms.dto.UserAccountResponse;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Role;
import com.kdc.tsdms.entity.UserRole;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.RolePermissionRepository;
import com.kdc.tsdms.repository.RoleRepository;
import com.kdc.tsdms.repository.UserRoleRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khu CÀI ĐẶT HỆ THỐNG — quản lý tài khoản (khóa/mở) + gán chức danh (role).
 *
 * <p>Chống leo quyền (privilege escalation) — các luật cứng, áp dụng cả khi sau này
 * V11 trao {@code USER_MANAGE}/{@code ROLE_MANAGE} cho trưởng phòng nhân sự:
 * <ul>
 *   <li>Không thao tác lên CHÍNH MÌNH (tự khóa / tự đổi role -> khóa nhầm cửa).</li>
 *   <li>Chỉ ADMIN được đụng tới tài khoản đang giữ role ADMIN.</li>
 *   <li>Chỉ ADMIN được cấp role ADMIN cho người khác.</li>
 * </ul>
 */
@Service
public class UserAdminService {

    /** Trần kích thước trang — chặn ?size=100000 kéo cả bảng user. */
    private static final int MAX_PAGE_SIZE = 50;

    private final AppUserRepository appUserRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final DisplayNameResolver displayNameResolver;

    public UserAdminService(
            AppUserRepository appUserRepo,
            RoleRepository roleRepo,
            UserRoleRepository userRoleRepo,
            RolePermissionRepository rolePermissionRepo,
            RefreshTokenRepository refreshTokenRepo,
            DisplayNameResolver displayNameResolver) {
        this.appUserRepo = appUserRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.rolePermissionRepo = rolePermissionRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.displayNameResolver = displayNameResolver;
    }

    /** Danh sách tài khoản (phân trang + tìm theo username/email). */
    @Transactional(readOnly = true)
    public Page<UserAccountResponse> listUsers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE), Sort.by(Sort.Direction.ASC, "username"));
        Page<AppUser> users = (keyword == null || keyword.isBlank())
                ? appUserRepo.findByDeletedFalse(pageable)
                : appUserRepo.searchByKeyword(keyword.trim(), pageable);

        // Gom role của CẢ TRANG bằng 1 query (tránh N+1 theo từng dòng).
        List<Integer> ids = users.getContent().stream().map(AppUser::getId).toList();
        Map<Integer, List<String>> rolesByUser = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] pair : userRoleRepo.findRoleNamePairsByAppUserIds(ids)) {
                rolesByUser
                        .computeIfAbsent((Integer) pair[0], k -> new ArrayList<>())
                        .add((String) pair[1]);
            }
        }

        return users.map(u -> new UserAccountResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                displayNameResolver.resolve(u),
                u.getStatus(),
                u.getLastLoginAt(),
                rolesByUser.getOrDefault(u.getId(), List.of())));
    }

    /** Khóa / mở tài khoản. Khóa -> thu hồi luôn mọi refresh token (phiên chết theo). */
    @Transactional
    public void updateStatus(Integer targetId, String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase();
        if (!status.equals("ACTIVE") && !status.equals("LOCKED")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Trạng thái chỉ nhận ACTIVE hoặc LOCKED");
        }
        AppUser target = findTarget(targetId);
        if (target.getId().equals(SecurityUtils.currentUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể tự khóa/mở tài khoản của chính mình");
        }
        requireAdminIfTargetIsAdmin(target.getId());

        target.setStatus(status);
        if (!status.equals("ACTIVE")) {
            // Khóa phải có hiệu lực NGAY với phiên đang mở: không thu hồi thì refresh token
            // còn sống vẫn xin được access token mới (đã vá thêm check status ở AuthService.refresh).
            refreshTokenRepo.revokeAllActiveByAppUserId(target.getId(), Instant.now());
        }
    }

    /** THAY danh sách role của tài khoản (gán chức danh). */
    @Transactional
    public List<String> updateRoles(Integer targetId, List<String> rawRoles) {
        AppUser target = findTarget(targetId);
        if (target.getId().equals(SecurityUtils.currentUserId())) {
            // Tự bỏ ADMIN của chính mình = tự nhốt ngoài cửa; chặn luôn mọi self-edit cho chắc.
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể tự đổi vai trò của chính mình");
        }
        requireAdminIfTargetIsAdmin(target.getId());

        Set<String> names = new LinkedHashSet<>();
        rawRoles.forEach(r -> names.add(r.trim().toUpperCase()));
        if (names.contains("ADMIN") && !SecurityUtils.hasRole("ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ ADMIN mới được cấp vai trò ADMIN");
        }

        List<Role> roles = new ArrayList<>();
        for (String name : names) {
            roles.add(roleRepo.findByName(name)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Vai trò không tồn tại: " + name)));
        }

        // Thay trọn bộ: xóa dòng cũ, ghi dòng mới (bảng nối nhỏ, đơn giản hơn diff từng dòng).
        userRoleRepo.deleteAll(userRoleRepo.findByAppUserId(target.getId()));
        userRoleRepo.flush(); // ép DELETE chạy trước INSERT, tránh đụng khóa kép khi gán lại role cũ
        roles.forEach(r -> userRoleRepo.save(new UserRole(target.getId(), r.getId())));

        // Quyền nằm sẵn trong token -> thu hồi refresh token để bộ quyền MỚI có hiệu lực
        // ngay lần refresh/đăng nhập kế tiếp, quyền cũ không sống quá TTL access token (15').
        refreshTokenRepo.revokeAllActiveByAppUserId(target.getId(), Instant.now());
        return roles.stream().map(Role::getName).toList();
    }

    /** Ma trận Role × Permission (chỉ đọc) cho trang phân quyền. */
    @Transactional(readOnly = true)
    public List<RoleMatrixResponse> rolesWithPermissions() {
        Map<Integer, List<RoleMatrixResponse.PermissionInfo>> permsByRole = new HashMap<>();
        for (Object[] row : rolePermissionRepo.findAllRolePermissionPairs()) {
            permsByRole
                    .computeIfAbsent((Integer) row[0], k -> new ArrayList<>())
                    .add(new RoleMatrixResponse.PermissionInfo((String) row[1], (String) row[2]));
        }
        return roleRepo.findAll(Sort.by("id")).stream()
                .map(r -> {
                    var perms = permsByRole.getOrDefault(r.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(RoleMatrixResponse.PermissionInfo::code))
                            .toList();
                    return new RoleMatrixResponse(r.getId(), r.getName(), r.getDescription(), perms);
                })
                .toList();
    }

    private AppUser findTarget(Integer targetId) {
        return appUserRepo
                .findById(targetId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
    }

    /** Tài khoản đích đang giữ ADMIN thì chỉ ADMIN khác mới được thao tác lên nó. */
    private void requireAdminIfTargetIsAdmin(Integer targetId) {
        boolean targetIsAdmin = userRoleRepo.findRoleNamesByAppUserId(targetId).contains("ADMIN");
        if (targetIsAdmin && !SecurityUtils.hasRole("ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ ADMIN mới được thao tác lên tài khoản ADMIN khác");
        }
    }
}
