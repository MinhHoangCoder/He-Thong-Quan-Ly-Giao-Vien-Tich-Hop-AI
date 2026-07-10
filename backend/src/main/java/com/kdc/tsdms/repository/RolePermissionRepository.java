package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.RolePermission;
import com.kdc.tsdms.entity.RolePermissionId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository bảng nối RolePermission (khóa kép RoleId + PermissionId). */
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    /**
     * Toàn bộ cặp (roleId, mã quyền, mô tả quyền) — trang "ma trận phân quyền" dựng bảng
     * Role × Permission bằng 1 query.
     */
    @Query("SELECT rp.roleId, p.code, p.description FROM RolePermission rp, Permission p "
            + "WHERE rp.permissionId = p.id")
    List<Object[]> findAllRolePermissionPairs();
}
