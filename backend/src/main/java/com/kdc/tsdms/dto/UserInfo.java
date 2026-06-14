package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Thông tin user trả về cho client (KHÔNG chứa mật khẩu).
 * {@code roles} = vai trò/phòng ban; {@code perms} = mã quyền chi tiết để FE ẩn/hiện menu.
 */
public record UserInfo(
        Integer id, String username, String fullName, String email, List<String> roles, List<String> perms) {}
