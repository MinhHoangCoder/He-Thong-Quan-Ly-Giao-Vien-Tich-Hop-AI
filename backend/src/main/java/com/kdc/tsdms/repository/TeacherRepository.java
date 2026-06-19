package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Teacher;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Integer> {

    /** Hồ sơ GV theo tài khoản (để ghép tên hiển thị khi đăng nhập). */
    Optional<Teacher> findByAppUserIdAndDeletedFalse(Integer appUserId);
}
