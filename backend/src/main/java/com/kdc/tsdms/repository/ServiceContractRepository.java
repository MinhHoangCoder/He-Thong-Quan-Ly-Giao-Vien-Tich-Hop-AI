package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.ServiceContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceContractRepository extends JpaRepository<ServiceContract, Integer> {

    /**
     * Tổng doanh thu theo khoảng thời gian (1 năm học). Quy tắc: ghi nhận doanh thu theo
     * NGÀY KÝ (StartDate). Năm học Y = [01/08/Y, 01/08/(Y+1)) → truyền from = 01/08/Y,
     * to = 01/08/(Y+1). COALESCE để trả 0 thay vì null khi chưa có hợp đồng nào.
     */
    @Query("SELECT COALESCE(SUM(s.contractValue), 0) FROM ServiceContract s "
            + "WHERE s.startDate >= :from AND s.startDate < :to AND s.deleted = false")
    BigDecimal sumRevenueByPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
