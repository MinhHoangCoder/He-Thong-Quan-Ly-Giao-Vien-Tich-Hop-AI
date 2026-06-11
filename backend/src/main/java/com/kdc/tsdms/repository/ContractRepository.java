package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Contract — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface ContractRepository extends JpaRepository<Contract, Integer> {}
