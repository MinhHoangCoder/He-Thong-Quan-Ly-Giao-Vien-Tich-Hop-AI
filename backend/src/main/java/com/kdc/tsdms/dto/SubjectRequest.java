package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body tạo mới / sửa môn học (Subject), dùng trong trang "Nhóm môn học"
 * (quản lý môn lồng trong từng nhóm). categoryId BẮT BUỘC — đây là điểm mấu
 * chốt để tránh lặp lại lỗi "môn học không có nhóm" (CategoryId null) như cũ.
 *
 * <p>
 * code KHÔNG còn bắt buộc ở tầng DTO: khi TẠO MỚI,
 * {@link com.kdc.tsdms.service.SubjectService}
 * tự sinh mã (tăng dần theo nhóm môn) và bỏ qua giá trị client gửi lên — xem
 * SubjectService#generateNextCode. Khi SỬA, code vẫn bắt buộc và được service
 * tự kiểm tra (không dùng @NotBlank ở đây vì cùng record dùng chung cho cả
 * 2 luồng tạo/sửa).
 *
 * <p>
 * FIX (2026-08-13): description giờ giới hạn 200 TỪ (kiểm ở
 * SubjectService#validateDescriptionWordLimit), đồng bộ với Lesson và
 * SubjectCategory — trước đó giới hạn 200 KÝ TỰ, khác biệt không có lý do rõ
 * ràng. @Size ở đây chỉ còn là chặn an toàn payload quá khổ (2000 ký tự),
 * không phải giới hạn UX thật.
 */
public record SubjectRequest(
        @Pattern(regexp = "^$|^[A-Z0-9_]{2,20}$", message = "Mã môn chỉ gồm chữ hoa, số, dấu gạch dưới (2-20 ký tự)")
        String code,

        @NotBlank(message = "Tên môn học không được để trống") @Size(max = 150, message = "Tên môn tối đa 150 ký tự") String name,

        @NotNull(message = "Vui lòng chọn nhóm môn") Integer categoryId,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự") String description,

        @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "Status phải là ACTIVE hoặc DISABLED")
        String status) {}
