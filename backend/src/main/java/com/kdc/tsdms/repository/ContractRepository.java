package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Contract;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Contract — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface ContractRepository extends JpaRepository<Contract, Integer> {
    /** Hợp đồng đang active của GV — tối đa 1 bản ghi theo ràng buộc DB. */
    Optional<Contract> findByTeacherIdAndDeletedFalse(Integer teacherId);

    /**
     * Hợp đồng của cả một nhóm giáo viên trong MỘT câu — dùng khi tính lương cả kỳ.
     *
     * <p>Gọi {@link #findByTeacherIdAndDeletedFalse} trong vòng lặp là năm chục câu SQL cho
     * một lần bấm nút, mà dữ liệu cần lấy hoàn toàn biết trước.
     */
    java.util.List<Contract> findByTeacherIdInAndDeletedFalse(java.util.Collection<Integer> teacherIds);
}
