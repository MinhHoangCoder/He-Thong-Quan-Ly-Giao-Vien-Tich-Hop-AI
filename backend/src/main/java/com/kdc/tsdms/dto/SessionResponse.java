package com.kdc.tsdms.dto;

import java.time.Instant;

/**
 * Một "phiên đăng nhập" (refresh token đang sống) trong trang Cài đặt.
 * {@code current} = phiên của chính thiết bị đang xem trang (so theo hash token).
 * KHÔNG bao giờ trả token thô/hash về client.
 */
public record SessionResponse(Long id, Instant createdAt, Instant expiresAt, boolean current) {}
