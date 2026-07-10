package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Assignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Assignment — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface AssignmentRepository extends JpaRepository<Assignment, Integer> {

    /** Danh sách phân công (mới nhất trước) cho trang Phân công. */
    List<Assignment> findByDeletedFalseOrderByIdDesc();

    /** Lọc theo giáo viên. */
    List<Assignment> findByTeacherIdAndDeletedFalseOrderByIdDesc(Integer teacherId);

    /** Chi tiết 1 phân công (chưa xóa mềm). */
    Optional<Assignment> findByIdAndDeletedFalse(Integer id);
}
