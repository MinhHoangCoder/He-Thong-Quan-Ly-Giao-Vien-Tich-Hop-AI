package com.kdc.tsdms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Payload tạo/sửa Trường khách hàng (bảng School) — validate mirror ràng buộc ở DB. */
public record SchoolRequest(
        @NotNull(message = "Vui lòng chọn chi nhánh phụ trách") Integer branchId,

        @NotBlank(message = "Tên trường không được để trống") @Size(max = 200) String name,

        @Size(max = 255) String address,

        @Pattern(regexp = "^$|^(\\+84|0)\\d{9,10}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @Email(message = "Email không hợp lệ") @Size(max = 100, message = "Email tối đa 100 ký tự") String email,

        @Size(max = 150, message = "Tên người liên hệ tối đa 150 ký tự") String contactPerson,

        Integer appUserId,

        LocalDate contractStartDate,
        LocalDate contractEndDate,

        @Pattern(regexp = "^(ACTIVE|INACTIVE|EXPIRED)$", message = "Trạng thái phải là ACTIVE, INACTIVE hoặc EXPIRED")
        String status) {}
