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
        Integer appUserId,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static SchoolResponse fromEntity(School s, String branchName) {
        return new SchoolResponse(
                s.getId(),
                s.getBranchId(),
                branchName,
                s.getName(),
                s.getAddress(),
                s.getPhone(),
                s.getEmail(),
                s.getContactPerson(),
                s.getAppUserId(),
                s.getContractStartDate(),
                s.getContractEndDate(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
