package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.School;
import java.time.Instant;
import java.time.LocalDate;

public record SchoolResponse(
        Integer id,
        Integer branchId,
        /** Tên chi nhánh — chỉ để hiển thị, không phải cột thật của School. */
        String branchName,
        String name,
        String address,
        String phone,
        String email,
        String contactPerson,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        /** Trạng thái ĐANG LƯU trong cột Status — form sửa phải nạp đúng giá trị này. */
        String status,
        /** Trạng thái THẬT hôm nay (xem {@code School.effectiveStatus}) — cái bảng hiển thị. */
        String effectiveStatus,
        /** Số ngày còn lại của hợp đồng; null nếu chưa nhập hạn, âm nếu đã quá hạn. */
        Long daysLeft,
        /** Số tiết trong khung của trường — 0 nghĩa là chưa xếp phân công được. */
        int periodCount,
        Instant createdAt,
        Instant updatedAt) {

    public static SchoolResponse fromEntity(School s, String branchName, int periodCount, LocalDate today) {
        return new SchoolResponse(
                s.getId(),
                s.getBranchId(),
                branchName,
                s.getName(),
                s.getAddress(),
                s.getPhone(),
                s.getEmail(),
                s.getContactPerson(),
                s.getContractStartDate(),
                s.getContractEndDate(),
                s.getStatus(),
                s.effectiveStatus(today),
                s.soNgayConLai(today),
                periodCount,
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
