package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Contract;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Contract — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface ContractRepository extends JpaRepository<Contract, Integer> {
    /** Hợp đồng đang active của GV — tối đa 1 bản ghi theo ràng buộc DB. */
    Optional<Contract> findByTeacherIdAndDeletedFalse(Integer teacherId);

    /**
     * Các phiên bản hợp đồng ĐÃ BỊ THAY THẾ của một GV, mới nhất trước.
     *
     * <p>{@code IsDeleted = 1} ở bảng này KHÔNG mang nghĩa "đã hủy" mà là "đã bị bản sau thay
     * thế" — xem {@code TeacherService.saveContract}. Đây là đường duy nhất đọc lại được mức
     * lương/thời hạn của các bản trước, thứ mà bản cũ ghi đè lên và làm mất vĩnh viễn.
     */
    List<Contract> findByTeacherIdAndDeletedTrueOrderByIdDesc(Integer teacherId);
}
