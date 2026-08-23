package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.Paging;
import com.kdc.tsdms.dto.MessageResponse;
import com.kdc.tsdms.dto.RoleMatrixResponse;
import com.kdc.tsdms.dto.UpdateUserRolesRequest;
import com.kdc.tsdms.dto.UpdateUserStatusRequest;
import com.kdc.tsdms.dto.UserAccountResponse;
import com.kdc.tsdms.service.UserAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CÀI ĐẶT HỆ THỐNG (/api/v1/admin/**) — chặn theo QUYỀN (permission) chứ không theo
 * role, để V11 chỉ cần gán {@code USER_VIEW}/{@code USER_MANAGE}/{@code ROLE_MANAGE}
 * cho chức danh trưởng phòng là dùng được ngay, không sửa code. ADMIN đi tắt như quy ước.
 * Luật chống leo quyền (không tự khóa mình, chỉ ADMIN đụng ADMIN…) nằm ở UserAdminService.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /** Danh sách tài khoản (tìm kiếm + phân trang). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_VIEW') or hasAuthority('USER_MANAGE')")
    @GetMapping("/users")
    public Page<UserAccountResponse> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return userAdminService.listUsers(keyword, Paging.safePage(page), Paging.safeSize(size));
    }

    /** Khóa / mở tài khoản (ACTIVE | LOCKED). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
    @PatchMapping("/users/{id}/status")
    public MessageResponse updateStatus(@PathVariable Integer id, @Valid @RequestBody UpdateUserStatusRequest request) {
        userAdminService.updateStatus(id, request.status());
        return new MessageResponse("Đã cập nhật trạng thái tài khoản");
    }

    /** Gán chức danh: THAY danh sách role của tài khoản. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")
    @PutMapping("/users/{id}/roles")
    public MessageResponse updateRoles(@PathVariable Integer id, @Valid @RequestBody UpdateUserRolesRequest request) {
        List<String> assigned = userAdminService.updateRoles(id, request.roles());
        return new MessageResponse("Đã gán vai trò: " + String.join(", ", assigned));
    }

    /** Ma trận Role × Permission (chỉ đọc — sửa ma trận là việc của migration V11). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE') or hasAuthority('USER_VIEW')")
    @GetMapping("/roles")
    public List<RoleMatrixResponse> roles() {
        return userAdminService.rolesWithPermissions();
    }
}
