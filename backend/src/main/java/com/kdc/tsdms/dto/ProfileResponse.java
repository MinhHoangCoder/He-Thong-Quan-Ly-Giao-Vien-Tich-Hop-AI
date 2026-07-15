package com.kdc.tsdms.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Hồ sơ cá nhân (GET /api/v1/me/profile) — dùng cho cả trang HỒ SƠ (chỉ xem) lẫn
 * tab liên hệ trong CÀI ĐẶT (sửa email/SĐT).
 *
 * <p>{@code actorType} = TEACHER | EMPLOYEE | SCHOOL | NONE (admin/tài khoản hệ thống) —
 * FE dựa vào đây để biết hiển thị nhóm thông tin nào.
 * {@code teacher} chỉ khác null với giáo viên (hồ sơ nghề nghiệp mở rộng).
 * Đây là dữ liệu CHÍNH CHỦ tự xem nên trả đủ CCCD/ngày sinh; các API dành cho người
 * khác xem (vd danh sách GV) vẫn phân quyền riêng như cũ.
 * Họ tên/CCCD KHÔNG cho tự sửa — dữ liệu pháp lý gắn hợp đồng/bảng lương, thuộc quyền
 * phòng Nhân sự.
 */
public record ProfileResponse(
        String username,
        String email,
        String fullName,
        String phone,
        String actorType,
        String position,
        List<String> roles,
        /** Tên chi nhánh làm việc — có với GV/NV, null với trường/admin. */
        String branchName,
        /** FULL_TIME | PART_TIME | CONTRACT — có với GV/NV. */
        String employmentType,
        /** Trạng thái hồ sơ tác nhân (ACTIVE...), khác với trạng thái tài khoản. */
        String profileStatus,
        TeacherDetail teacher) {

    /** Hồ sơ nghề nghiệp của giáo viên — chỉ chính chủ xem qua /me. */
    public record TeacherDetail(
            String idCardNo,
            LocalDate dateOfBirth,
            Boolean gender,
            String address,
            LocalDate hireDate,
            List<CertificateItem> certificates) {}

    /** Bằng cấp / chứng chỉ — không kèm fileUrl (file thuộc trang quản lý GV của staff). */
    public record CertificateItem(String name, String issuer, LocalDate issueDate, LocalDate expiryDate) {}
}
