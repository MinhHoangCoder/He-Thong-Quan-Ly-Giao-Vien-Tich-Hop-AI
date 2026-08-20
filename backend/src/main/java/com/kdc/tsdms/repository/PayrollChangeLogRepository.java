package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.PayrollChangeLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollChangeLogRepository extends JpaRepository<PayrollChangeLog, Long> {

    /** Lịch sử chốt/mở lại của một phiếu — mới nhất trước, đúng thứ tự người đọc cần. */
    List<PayrollChangeLog> findByPayrollIdOrderByChangedAtDescIdDesc(Integer payrollId);
}
