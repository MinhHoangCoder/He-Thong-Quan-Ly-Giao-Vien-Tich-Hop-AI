package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.UserRole;
import com.kdc.tsdms.entity.UserRoleId;
import java.util.Collection;
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

    /**
     * Chiều NGƯỢC lại của {@link #findPermissionCodesByAppUserId}: những tài khoản NÀO đang
     * nắm một quyền cụ thể. Dùng khi tác vụ nền cần báo tin cho "người chịu trách nhiệm việc
     * X" — bám theo quyền chứ không hardcode tên role, nên đổi phân quyền là danh sách người
     * nhận tự đổi theo. Lọc tài khoản đã xóa mềm để không gửi vào chỗ không ai đọc.
     */
    @Query("SELECT DISTINCT ur.appUserId FROM UserRole ur, RolePermission rp, Permission p, AppUser u "
            + "WHERE ur.roleId = rp.roleId AND rp.permissionId = p.id AND p.code = :code "
            + "AND u.id = ur.appUserId AND u.deleted = false")
    List<Integer> findAppUserIdsByPermissionCode(@Param("code") String code);

    /**
     * Tài khoản theo TÊN VAI TRÒ. Cần vì ADMIN cố tình KHÔNG được seed dòng RolePermission
     * nào (V3: "ADMIN đi tắt bằng hasRole('ADMIN')") — tra theo quyền thôi là sót đúng người
     * quản trị. Chỗ nào gửi tin theo quyền thì gộp thêm danh sách này.
     */
    @Query("SELECT DISTINCT ur.appUserId FROM UserRole ur, Role r, AppUser u "
            + "WHERE ur.roleId = r.id AND u.id = ur.appUserId AND u.deleted = false AND r.name = :name")
    List<Integer> findAppUserIdsByRoleName(@Param("name") String roleName);

    /**
     * Cặp (appUserId, tên role) cho NHIỀU tài khoản một lượt — trang quản trị hiển thị
     * cột vai trò cho cả trang danh sách bằng 1 query thay vì N query.
     */
    @Query("SELECT ur.appUserId, r.name FROM UserRole ur, Role r "
            + "WHERE ur.roleId = r.id AND ur.appUserId IN :appUserIds")
    List<Object[]> findRoleNamePairsByAppUserIds(@Param("appUserIds") Collection<Integer> appUserIds);

    /** Toàn bộ dòng gán role của 1 tài khoản — dùng khi thay danh sách role (gán chức danh). */
    List<UserRole> findByAppUserId(Integer appUserId);
}
