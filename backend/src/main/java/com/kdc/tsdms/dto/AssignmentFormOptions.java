package com.kdc.tsdms.dto;

import java.util.List;

/** Dữ liệu nạp form "Tạo phân công": danh sách GV, môn, trường (dropdown cấp 1). */
public record AssignmentFormOptions(List<OptionItem> teachers, List<OptionItem> subjects, List<OptionItem> schools) {}
