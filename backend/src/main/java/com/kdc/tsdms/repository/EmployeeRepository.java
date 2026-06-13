package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Employee — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {}
