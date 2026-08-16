package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Assignment;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository bảng Assignment — thêm method truy vấn theo nhu cầu feature tại
 * đây.
 */
public interface AssignmentRepository extends JpaRepository<Assignment, Integer> {

    /** Trường đã từng/đang phân công GV này chưa (dùng khi SCHOOL chấm điểm). */
    boolean existsByTeacherIdAndSchoolIdAndDeletedFalse(Integer teacherId, Integer schoolId);

    /**
     * Đếm TOÀN BỘ phân công (mọi trạng thái, kể cả đã xóa mềm) từng gắn với 1
     * môn học — dùng để CHẶN hard-delete Subject: Assignment là dữ liệu lịch sử
     * phân công giảng dạy, không được cuốn theo khi xóa vĩnh viễn môn học.
     */
    long countBySubjectId(Integer subjectId);

    /**
     * Như trên nhưng LOẠI phân công theo status (vd CANCELLED — GV từ chối, chưa
     * từng dạy):
     * SCHOOL chỉ được chấm điểm / xem summary GV có phân công còn hiệu lực tại
     * trường mình.
     */
    boolean existsByTeacherIdAndSchoolIdAndDeletedFalseAndStatusNot(Integer teacherId, Integer schoolId, String status);

    /**
     * Danh sách TeacherId đã từng phân công tại 1 trường (dropdown đánh giá phía
     * School).
     */
    @Query("SELECT DISTINCT a.teacherId FROM Assignment a WHERE a.schoolId = :schoolId AND a.deleted = false")
    List<Integer> findDistinctTeacherIdsBySchoolId(@Param("schoolId") Integer schoolId);

    /** Load phân công của nhiều GV (để gắn tên trường trên option tìm kiếm). */
    @Query("SELECT a FROM Assignment a WHERE a.deleted = false AND a.teacherId IN :teacherIds")
    List<Assignment> findByTeacherIdInAndDeletedFalse(@Param("teacherIds") Collection<Integer> teacherIds);

    /** Danh sách phân công (mới nhất trước) cho trang Phân công. */
    List<Assignment> findByDeletedFalseOrderByIdDesc();

    /** Lọc theo giáo viên. */
    List<Assignment> findByTeacherIdAndDeletedFalseOrderByIdDesc(Integer teacherId);

    /** Chi tiết 1 phân công (chưa xóa mềm). */
    Optional<Assignment> findByIdAndDeletedFalse(Integer id);

    /** Thùng rác: các phân công đã xóa mềm (mới nhất trước). */
    List<Assignment> findByDeletedTrueOrderByIdDesc();

    /** Một phân công trong thùng rác — cho khôi phục / xóa vĩnh viễn. */
    Optional<Assignment> findByIdAndDeletedTrue(Integer id);

    /** Số phân công (chưa xóa mềm) gắn lớp — chặn xóa lớp đang được phân công. */
    long countByClassIdAndDeletedFalse(Integer classId);

    /**
     * Phân công CÒN HIỆU LỰC của một trường. Chỉ đếm ACTIVE + PENDING: hai trạng thái này
     * vẫn giữ chỗ khung giờ và vẫn sinh buổi dạy, nên xóa trường lúc đó là bỏ lại lịch chạy
     * cho một nơi không còn tồn tại. REJECTED/EXPIRED/CANCELLED là dữ liệu lịch sử, không chặn.
     */
    long countBySchoolIdAndStatusInAndDeletedFalse(Integer schoolId, java.util.Collection<String> statuses);

    /** Phân công CÒN HIỆU LỰC của một giáo viên — cùng lý do như trên, phía giáo viên. */
    long countByTeacherIdAndStatusInAndDeletedFalse(Integer teacherId, java.util.Collection<String> statuses);

    /** Mọi phân công (kể cả xóa mềm) còn trỏ ClassId — chặn xóa vĩnh viễn lớp. */
    long countByClassId(Integer classId);

    /**
     * Phiếu đang chờ mà đã quá hạn trả lời — tác vụ nền quét để chuyển sang "Hết
     * hạn".
     */
    List<Assignment> findByStatusAndConfirmDeadlineBeforeAndDeletedFalse(String status, LocalDateTime deadline);
}
