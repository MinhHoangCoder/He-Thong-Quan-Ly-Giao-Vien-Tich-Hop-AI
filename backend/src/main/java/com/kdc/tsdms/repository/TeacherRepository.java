package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Teacher;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Integer> {

    /** Hồ sơ GV theo tài khoản (để ghép tên hiển thị khi đăng nhập). */
    Optional<Teacher> findByAppUserIdAndDeletedFalse(Integer appUserId);

    /** Danh sách tất cả GV  (cho trang danh sách). */
    List<Teacher> findByDeletedFalse();

    /** Xem chi tiết 1 GV  */
    Optional<Teacher> findByIdAndDeletedFalse(Integer id);

    /** Kiểm tra CCCD đã tồn tại chưa */
    boolean existsByIdCardNoAndDeletedFalse(String idCardNo);
    /**History */
    List<Teacher> findByDeletedTrueOrderByDeletedAtDesc();

    Optional<Teacher> findByIdAndDeletedTrue(Integer id);
}
