package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Payroll — mỗi GV mỗi tháng đúng 1 dòng (UNIQUE Teacher+Year+Month). */
public interface PayrollRepository extends JpaRepository<Payroll, Integer> {}
