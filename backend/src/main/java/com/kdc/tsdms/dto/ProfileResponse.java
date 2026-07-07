package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Hồ sơ cá nhân cho trang Cài đặt (GET /api/v1/me/profile).
 * {@code actorType} = TEACHER | EMPLOYEE | SCHOOL | NONE (admin/tài khoản hệ thống) —
 * FE dựa vào đây để biết có hiển thị ô SĐT/chức vụ hay không.
 * Họ tên KHÔNG cho tự sửa (dữ liệu pháp lý gắn hợp đồng/lương — thuộc quyền phòng Nhân sự).
 */
public record ProfileResponse(
        String username,
        String email,
        String fullName,
        String phone,
        String actorType,
        String position,
        List<String> roles) {}
