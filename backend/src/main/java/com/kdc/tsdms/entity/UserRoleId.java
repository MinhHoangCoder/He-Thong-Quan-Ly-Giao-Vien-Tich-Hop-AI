package com.kdc.tsdms.entity;

import java.io.Serializable;
import java.util.Objects;

/** Khóa kép (AppUserId, RoleId) cho bảng nối UserRole. */
public class UserRoleId implements Serializable {

    private Integer appUserId;
    private Integer roleId;

    public UserRoleId() {}

    public UserRoleId(Integer appUserId, Integer roleId) {
        this.appUserId = appUserId;
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRoleId that)) return false;
        return Objects.equals(appUserId, that.appUserId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appUserId, roleId);
    }
}
