package com.kdc.tsdms.dto;

import java.util.List;

/** Options phụ thuộc trường đã chọn: lớp + khung tiết của trường đó. */
public record SchoolScopedOptions(List<OptionItem> classes, List<PeriodOption> periods) {}
