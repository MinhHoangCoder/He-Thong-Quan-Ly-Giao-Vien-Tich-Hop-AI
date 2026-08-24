package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.TeacherSubject;
import com.kdc.tsdms.entity.TeacherSubjectId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng nối TeacherSubject (khóa kép TeacherId + SubjectId). */
public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, TeacherSubjectId> {

    /**
     * Số giáo viên CÒN SỐNG đang được gắn dạy một môn — rào chắn khi xóa môn học.
     *
     * <p>Phải loại giáo viên đã ở thùng rác, vì {@code TeacherSubject} không có cờ xóa mềm và
     * không ai dọn nó khi xóa giáo viên. Bản cũ ({@code countBySubjectId}) đếm cả dòng của
     * người đã bị xóa nên môn học rơi vào BẾ TẮC VĨNH VIỄN: thông báo bảo "còn giáo viên đang
     * phụ trách môn", người dùng mở danh sách giáo viên tìm mãi không thấy ai (họ nằm trong
     * thùng rác), mà cũng không có màn hình nào gỡ được liên kết đó ra.
     *
     * <p>Đánh đổi đã cân: giáo viên trong thùng rác vẫn giữ nguyên liên kết, nên khôi phục họ
     * sau khi môn đã bị xóa thì liên kết trỏ vào một môn đã xóa. Chấp nhận được vì
     * {@code TeacherSubject} chỉ được đọc ở đúng một chỗ (gợi ý ghép giáo viên ở
     * {@code AssignmentService}) và chỗ đó lọc theo môn còn sống.
     */
    @Query("SELECT COUNT(ts) FROM TeacherSubject ts WHERE ts.subjectId = :subjectId"
            + " AND ts.teacherId IN (SELECT t.id FROM Teacher t WHERE t.deleted = false)")
    long demGiaoVienConSongTheoMon(@Param("subjectId") Integer subjectId);
}
