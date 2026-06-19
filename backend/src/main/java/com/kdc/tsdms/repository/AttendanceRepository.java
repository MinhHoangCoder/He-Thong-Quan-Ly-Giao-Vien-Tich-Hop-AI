package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Attendance (khóa BIGINT → Long). */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {}
