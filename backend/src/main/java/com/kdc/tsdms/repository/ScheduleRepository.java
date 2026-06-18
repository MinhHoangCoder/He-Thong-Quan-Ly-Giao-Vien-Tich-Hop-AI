package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository bảng Schedule (khóa BIGINT → Long).
 * Lưu ý khi làm feature xếp lịch: kiểm tra TRÙNG LỊCH GV/PHÒNG bằng truy vấn khoảng
 * thời gian (đã có index IX_Schedule_Teacher_Time / IX_Schedule_Room_Time hỗ trợ).
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {}
