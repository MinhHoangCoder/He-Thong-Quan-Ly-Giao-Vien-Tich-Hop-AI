package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.PartTimeShiftRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartTimeShiftRequestRepository extends JpaRepository<PartTimeShiftRequest, Integer> {

    /** Đăng ký của một NV trong khoảng ngày (màn hình HR xếp lịch tuần). */
    List<PartTimeShiftRequest> findByEmployeeIdAndWorkDateBetweenAndDeletedFalse(
            Integer employeeId, LocalDate from, LocalDate to);

    /** Các đăng ký theo trạng thái (vd PENDING để HR duyệt). */
    List<PartTimeShiftRequest> findByStatusAndDeletedFalse(String status);
}
