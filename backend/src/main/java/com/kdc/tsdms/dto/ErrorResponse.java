package com.kdc.tsdms.dto;

/** Cấu trúc lỗi trả về thống nhất. */
public record ErrorResponse(int status, String error, String message) {}
