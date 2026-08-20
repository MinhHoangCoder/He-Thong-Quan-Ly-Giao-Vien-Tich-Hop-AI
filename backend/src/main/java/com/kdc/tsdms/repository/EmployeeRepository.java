package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Employee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Employee — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    /** Hồ sơ nhân viên theo tài khoản (để ghép tên hiển thị khi đăng nhập). */
    Optional<Employee> findByAppUserIdAndDeletedFalse(Integer appUserId);

    /** Nhân viên còn sống — dùng cho luồng xóa có kiểm soát ({@code EmployeeService.delete}). */
    Optional<Employee> findByIdAndDeletedFalse(Integer id);

    /** Đếm nhân viên còn sống của một chi nhánh — chặn xóa chi nhánh còn người. */
    long countByBranchIdAndDeletedFalse(Integer branchId);
}
