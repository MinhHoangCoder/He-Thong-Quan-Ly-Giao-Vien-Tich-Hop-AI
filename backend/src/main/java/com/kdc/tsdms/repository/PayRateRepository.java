package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.PayRate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng PayRate — đơn giá tiết dạy theo khối, có hiệu lực theo thời gian (V38). */
public interface PayRateRepository extends JpaRepository<PayRate, Integer> {

    /**
     * Toàn bộ bảng giá, mới nhất trước.
     *
     * <p>Cố ý KHÔNG viết query lọc theo khối + ngày: bảng này chỉ vài dòng (mỗi cấp học một
     * mức, mỗi lần đổi giá thêm một dòng), nên nạp hết một lần rồi tra trong bộ nhớ nhanh hơn
     * hỏi DB cho từng buổi chấm công. Xem {@code PayrollService.rateTable()}.
     */
    List<PayRate> findAllByOrderByEffectiveFromDescGradeFromAsc();
}
