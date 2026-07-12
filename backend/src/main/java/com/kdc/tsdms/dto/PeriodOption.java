package com.kdc.tsdms.dto;

import java.time.LocalTime;

/** Tiết học của một trường — phục vụ dropdown chọn tiết khi phân công. */
public record PeriodOption(
        Integer id, Short periodNumber, String sessionType, LocalTime startTime, LocalTime endTime, String label) {}
