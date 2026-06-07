package com.kdc.tsdms.dto;

import java.util.List;

/** Thông tin user trả về cho client (KHÔNG chứa mật khẩu). */
public record UserInfo(Integer id, String username, String fullName, String email, List<String> roles) {}
