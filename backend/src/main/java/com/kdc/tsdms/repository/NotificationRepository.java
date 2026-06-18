package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Notification (khóa BIGINT → Long). */
public interface NotificationRepository extends JpaRepository<Notification, Long> {}
