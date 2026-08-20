package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Employee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Employee — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    /** Hồ sơ nhân viên theo tài khoản (để ghép tên hiển thị khi đăng nhập). */
    Optional<Employee> findByAppUserIdAndDeletedFalse(Integer appUserId);
}
