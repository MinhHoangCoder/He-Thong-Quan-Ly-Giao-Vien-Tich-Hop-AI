package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Assignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng Assignment — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface AssignmentRepository extends JpaRepository<Assignment, Integer> {

    /** Trường đã từng/đang phân công GV này chưa (dùng khi SCHOOL chấm điểm). */
    boolean existsByTeacherIdAndSchoolIdAndDeletedFalse(Integer teacherId, Integer schoolId);

    /** Danh sách TeacherId đã từng phân công tại 1 trường (dropdown đánh giá phía School). */
    @Query("SELECT DISTINCT a.teacherId FROM Assignment a WHERE a.schoolId = :schoolId AND a.deleted = false")
    List<Integer> findDistinctTeacherIdsBySchoolId(@Param("schoolId") Integer schoolId);

    /** Load phân công của nhiều GV (để gắn tên trường trên option tìm kiếm). */
    @Query("SELECT a FROM Assignment a WHERE a.deleted = false AND a.teacherId IN :teacherIds")
    List<Assignment> findByTeacherIdInAndDeletedFalse(@Param("teacherIds") java.util.Collection<Integer> teacherIds);
}
