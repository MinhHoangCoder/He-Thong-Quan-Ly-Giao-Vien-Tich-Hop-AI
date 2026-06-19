package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.LessonFile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng LessonFile — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface LessonFileRepository extends JpaRepository<LessonFile, Integer> {}
