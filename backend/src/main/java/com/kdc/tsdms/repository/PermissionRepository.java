package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Permission — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface PermissionRepository extends JpaRepository<Permission, Integer> {}
