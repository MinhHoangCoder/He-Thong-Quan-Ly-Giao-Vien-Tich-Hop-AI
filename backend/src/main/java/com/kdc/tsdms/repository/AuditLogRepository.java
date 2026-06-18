package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng AuditLog (khóa BIGINT → Long) — nhật ký chỉ GHI THÊM, không sửa/xóa. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
