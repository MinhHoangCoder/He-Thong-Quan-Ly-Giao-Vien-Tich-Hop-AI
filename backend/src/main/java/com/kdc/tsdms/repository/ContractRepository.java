package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Contract;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Contract — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface ContractRepository extends JpaRepository<Contract, Integer> {
    /** Hợp đồng đang active của GV — tối đa 1 bản ghi theo ràng buộc DB. */
    Optional<Contract> findByTeacherIdAndDeletedFalse(Integer teacherId);

    List<Contract> findByTeacherId(Integer teacherId);
}
