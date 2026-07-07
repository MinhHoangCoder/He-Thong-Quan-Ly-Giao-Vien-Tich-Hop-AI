package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Body cho PUT /api/v1/admin/users/{id}/roles — THAY danh sách role của tài khoản.
 * Không cho danh sách rỗng: tài khoản không role = đăng nhập được nhưng không thấy gì
 * (đúng cái bẫy "tài khoản không quyền"); muốn chặn đăng nhập thì dùng khóa tài khoản.
 */
public record UpdateUserRolesRequest(
        @NotEmpty(message = "Danh sách vai trò không được rỗng — muốn chặn đăng nhập hãy khóa tài khoản") List<String> roles) {}
