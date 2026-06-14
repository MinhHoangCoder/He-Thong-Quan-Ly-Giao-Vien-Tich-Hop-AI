package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.UserRole;
import com.kdc.tsdms.entity.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    /** Lấy danh sách TÊN vai trò của 1 tài khoản (vd: ["ADMIN"]). */
    @Query("SELECT r.name FROM UserRole ur, Role r " + "WHERE ur.roleId = r.id AND ur.appUserId = :appUserId")
    List<String> findRoleNamesByAppUserId(@Param("appUserId") Integer appUserId);

    /**
     * Lấy danh sách MÃ QUYỀN (permission code) của 1 tài khoản, GỘP từ TẤT CẢ role mà
     * user đó nắm. Join 3 bảng: UserRole → RolePermission → Permission.
     * DISTINCT vì 2 role khác nhau có thể cùng cấp 1 quyền → tránh trùng lặp.
     */
    @Query("SELECT DISTINCT p.code FROM UserRole ur, RolePermission rp, Permission p "
            + "WHERE ur.roleId = rp.roleId AND rp.permissionId = p.id AND ur.appUserId = :appUserId")
    List<String> findPermissionCodesByAppUserId(@Param("appUserId") Integer appUserId);
}
