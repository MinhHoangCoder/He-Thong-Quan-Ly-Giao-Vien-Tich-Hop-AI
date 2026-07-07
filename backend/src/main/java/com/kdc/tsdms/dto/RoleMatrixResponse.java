package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Một role + danh sách quyền của nó (GET /api/v1/admin/roles) — FE dựng ma trận
 * Role × Permission (chỉ xem; sửa ma trận là việc của migration, không có API ghi).
 */
public record RoleMatrixResponse(Integer id, String name, String description, List<PermissionInfo> permissions) {

    /** Một quyền trong ma trận. */
    public record PermissionInfo(String code, String description) {}
}
