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
        String status,

        /**
         * Cấp học của trường — CHỈ dùng lúc TẠO MỚI để sinh sẵn khung tiết chuẩn (tiểu học 10
         * tiết 35 phút, THCS 9 tiết 45 phút). KHÔNG lưu vào bảng School: cấp học thật của trường
         * về sau luôn suy được từ khối lớp cao nhất, thêm một cột nữa là thêm chỗ để hai nguồn
         * mâu thuẫn nhau.
         *
         * <p>Để trống thì hệ thống tự đoán từ TÊN trường; đoán không ra thì bỏ qua, không sinh
         * khung. Lúc SỬA trường, giá trị này bị bỏ qua hoàn toàn — khung tiết đã dùng để xếp lịch
         * thì không được đổi ngầm.
         */
        @Pattern(regexp = "^$|^(TH|THCS)$", message = "Cấp học chỉ nhận TH hoặc THCS")
        String educationLevel) {}
