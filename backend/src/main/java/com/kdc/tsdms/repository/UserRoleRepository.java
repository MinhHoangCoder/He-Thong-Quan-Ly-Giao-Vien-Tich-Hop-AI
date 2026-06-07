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
}
