package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.School;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, Integer> {

    /** Hồ sơ trường theo tài khoản (để lấy tên hiển thị khi đăng nhập). */
    Optional<School> findByAppUserIdAndDeletedFalse(Integer appUserId);

    Optional<School> findByIdAndDeletedFalse(Integer id);

    /** Dropdown trường (chưa xóa mềm), sắp theo tên. */
    List<School> findByDeletedFalseOrderByNameAsc();
}
