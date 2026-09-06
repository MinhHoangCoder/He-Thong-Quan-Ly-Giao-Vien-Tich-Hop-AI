package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.SchoolContractChangeLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolContractChangeLogRepository extends JpaRepository<SchoolContractChangeLog, Integer> {

    /**
     * Lịch sử đổi hạn hợp đồng của một trường, MỚI NHẤT TRƯỚC — thứ tự mà mục "Lịch sử hợp đồng"
     * trong modal sửa trường hiển thị.
     *
     * <p>Sắp thêm theo Id giảm dần vì {@code ChangedAt} chỉ có độ phân giải mili-giây: hai lần sửa
     * trong cùng một mili-giây (script nạp dữ liệu) mà chỉ sắp theo thời gian thì thứ tự do DB tự
     * quyết, và người đọc nhật ký thấy hai dòng đảo ngược nhau. Cùng khuôn với
     * {@code AttendanceChangeLogRepository} và {@code PayrollChangeLogRepository}.
     */
    List<SchoolContractChangeLog> findBySchoolIdOrderByChangedAtDescIdDesc(Integer schoolId);
}
