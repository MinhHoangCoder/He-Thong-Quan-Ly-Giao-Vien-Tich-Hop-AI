package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Feedback — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface FeedbackRepository extends JpaRepository<Feedback, Integer> {}
