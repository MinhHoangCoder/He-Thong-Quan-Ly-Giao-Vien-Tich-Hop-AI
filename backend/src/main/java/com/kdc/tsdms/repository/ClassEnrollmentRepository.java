package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.ClassEnrollment;
import com.kdc.tsdms.entity.ClassEnrollmentId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng nối ClassEnrollment (khóa kép ClassId + StudentId). */
public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, ClassEnrollmentId> {}
