package com.kdc.tsdms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kdc.tsdms.entity.Certificate;

public interface CertificateRepository extends JpaRepository<Certificate, Integer> {
 
    List<Certificate> findByTeacherIdAndDeletedFalse(Integer teacherId);
 
    Optional<Certificate> findByIdAndTeacherIdAndDeletedFalse(Integer id, Integer teacherId);
}
