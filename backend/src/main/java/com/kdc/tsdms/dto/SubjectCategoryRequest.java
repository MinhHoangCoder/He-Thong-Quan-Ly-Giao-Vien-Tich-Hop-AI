package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubjectCategoryRequest(
                @NotBlank(message = "Code không được để trống") @Pattern(regexp = "^[A-Z0-9_]{2,50}$", message = "Code chỉ gồm chữ hoa, số, dấu gạch dưới (2-50 ký tự)") String code,

                @NotBlank(message = "Tên nhóm môn không được để trống") @Size(max = 100, message = "Tên tối đa 100 ký tự") String name,

                @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự") String description,

                @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "Status phải là ACTIVE hoặc DISABLED") String status) {
}