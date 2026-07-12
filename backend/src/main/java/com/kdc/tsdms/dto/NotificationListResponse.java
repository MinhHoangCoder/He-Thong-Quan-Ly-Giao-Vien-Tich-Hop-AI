package com.kdc.tsdms.dto;

import java.util.List;

/** Gói danh sách thông báo + số chưa đọc (để FE cập nhật badge trong 1 request). */
public record NotificationListResponse(long unreadCount, List<NotificationResponse> items) {}
