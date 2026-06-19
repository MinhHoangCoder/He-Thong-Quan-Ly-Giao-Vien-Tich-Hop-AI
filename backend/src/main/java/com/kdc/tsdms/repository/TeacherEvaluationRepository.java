package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.TeacherEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng TeacherEvaluation — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface TeacherEvaluationRepository extends JpaRepository<TeacherEvaluation, Integer> {}
