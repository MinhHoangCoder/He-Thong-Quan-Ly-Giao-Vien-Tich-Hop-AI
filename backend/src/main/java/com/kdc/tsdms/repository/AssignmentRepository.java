package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Assignment — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface AssignmentRepository extends JpaRepository<Assignment, Integer> {}
