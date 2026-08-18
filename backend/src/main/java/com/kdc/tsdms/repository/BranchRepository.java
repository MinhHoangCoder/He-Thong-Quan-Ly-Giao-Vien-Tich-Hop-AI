package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Branch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Integer> {

    /** Chi nhánh còn sống — dùng cho luồng xóa có kiểm soát ({@code BranchService.delete}). */
    Optional<Branch> findByIdAndDeletedFalse(Integer id);
}
