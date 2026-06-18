package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.ScheduleStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng ScheduleStatusLog — bản ghi do trigger DB tạo, Java chủ yếu ĐỌC. */
public interface ScheduleStatusLogRepository extends JpaRepository<ScheduleStatusLog, Long> {}
