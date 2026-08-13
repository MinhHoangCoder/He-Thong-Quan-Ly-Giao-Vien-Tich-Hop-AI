package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AttendanceChangeLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceChangeLogRepository extends JpaRepository<AttendanceChangeLog, Long> {

    /** Lịch sử của một dòng chấm công, mới nhất trước. */
    List<AttendanceChangeLog> findByAttendanceIdOrderByChangedAtDescIdDesc(Long attendanceId);
}
