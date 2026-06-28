package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Teacher;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Integer> {
       /** Danh sách tất cả GV chưa xóa mềm */
    List<Teacher> findByDeletedFalse();
 
    /** Tìm 1 GV theo id, chưa xóa mềm */
    Optional<Teacher> findByIdAndDeletedFalse(Integer id);
 
    /** Danh sách GV theo chi nhánh */
    List<Teacher> findByBranchIdAndDeletedFalse(Integer branchId);
 
    /** Kiểm tra AppUserId đã có GV chưa (quan hệ 1-1) */
    boolean existsByAppUserIdAndDeletedFalse(Integer appUserId);

}
