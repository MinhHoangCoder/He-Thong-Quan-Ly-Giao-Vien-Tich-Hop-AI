package com.kdc.tsdms.dto;

import java.util.List;

/** Options bộ lọc cho trang Lịch dạy: danh sách GV + trường (lớp lấy theo trường riêng). */
public record ScheduleFilterOptions(List<OptionItem> teachers, List<OptionItem> schools) {}
