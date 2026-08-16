package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Teacher;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Đếm dòng còn trỏ vào một giáo viên ở TẤT CẢ 11 bảng con, mỗi bảng một dòng kết quả
     * {@code [mã loại, số dòng]}. Chỉ trả về bảng nào thật sự còn dòng ({@code HAVING}).
     *
     * <p><b>KHÔNG lọc {@code IsDeleted}</b> — khác mọi query nghiệp vụ khác trong dự án. Ở đây
     * đang chuẩn bị xóa CỨNG dòng Teacher, mà khóa ngoại chặn theo sự TỒN TẠI của dòng con chứ
     * không quan tâm cờ xóa mềm: một chứng chỉ đã "xóa" vẫn khiến {@code DELETE Teacher} nổ.
     *
     * <p>Vì sao gom vào một câu SQL thay vì tiêm 11 repository: {@code TeacherService} đã có 8
     * dependency, thêm 9 cái nữa chỉ để gọi {@code count} là đổi một class thành cái tủ chứa
     * repository. Quan trọng hơn, danh sách bảng con nằm gọn ở ĐÚNG MỘT chỗ — ai thêm bảng mới
     * trỏ vào Teacher thì thêm đúng một dòng {@code UNION ALL} vào đây.
     *
     * <p>Mã loại để ASCII, nhãn tiếng Việt nằm bên Java cùng chỗ với mọi thông báo khác.
     */
    @Query(value = """
                    SELECT 'certificate' AS loai, COUNT(*) AS soLuong FROM Certificate           WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'contract',   COUNT(*) FROM Contract               WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'subject',    COUNT(*) FROM TeacherSubject         WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'assignment', COUNT(*) FROM Assignment             WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'slot',       COUNT(*) FROM AssignmentSlot         WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'schedule',   COUNT(*) FROM Schedule               WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'attendance', COUNT(*) FROM Attendance             WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'amend',      COUNT(*) FROM AttendanceAmendRequest WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'payroll',    COUNT(*) FROM Payroll                WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'evaluation', COUNT(*) FROM TeacherEvaluation      WHERE TeacherId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'lesson',     COUNT(*) FROM Lesson                 WHERE TeacherId = :id HAVING COUNT(*) > 0
                    """, nativeQuery = true)
    List<Object[]> countChildRowsByTeacherId(@Param("id") Integer id);
}
