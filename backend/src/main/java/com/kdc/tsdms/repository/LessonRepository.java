package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Lesson — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface LessonRepository extends JpaRepository<Lesson, Integer> {}
