package com.kdc.tsdms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kdc.tsdms.entity.Contract;

public interface ContractRepository extends JpaRepository<Contract, Integer> {
 
    /** DB có ràng buộc 1 GV chỉ có 1 hợp đồng chưa xóa */
    Optional<Contract> findByTeacherIdAndDeletedFalse(Integer teacherId);
 
    List<Contract> findAllByTeacherIdAndDeletedFalse(Integer teacherId);
}
