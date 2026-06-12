package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.RolePermission;
import com.kdc.tsdms.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng nối RolePermission (khóa kép RoleId + PermissionId). */
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {}
