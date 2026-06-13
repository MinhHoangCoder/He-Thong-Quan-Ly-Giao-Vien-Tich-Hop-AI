package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Certificate — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface CertificateRepository extends JpaRepository<Certificate, Integer> {}
