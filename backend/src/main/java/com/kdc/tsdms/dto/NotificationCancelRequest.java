package com.kdc.tsdms.dto;

/** Body khi giáo viên TỪ CHỐI (Hủy) một thông báo phân công — bắt buộc kèm lý do. */
public record NotificationCancelRequest(String reason) {}
