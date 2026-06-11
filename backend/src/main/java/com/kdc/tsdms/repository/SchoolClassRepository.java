package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng SchoolClass — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Integer> {}
