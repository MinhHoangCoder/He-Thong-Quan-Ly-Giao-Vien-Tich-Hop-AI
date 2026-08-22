package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AssignmentSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentSlotRepository extends JpaRepository<AssignmentSlot, Integer> {

    /** Thời khóa biểu (các tiết/tuần) của một phân công. */
    List<AssignmentSlot> findByAssignmentIdAndDeletedFalse(Integer assignmentId);

    /** Mọi slot của phân công (kể cả đã xóa mềm) — cho khôi phục / hiển thị thùng rác. */
    List<AssignmentSlot> findByAssignmentId(Integer assignmentId);

    /** Phục vụ dò trùng lịch GV: cùng GV + thứ + tiết (Service còn so chồng khoảng kỳ). */
    List<AssignmentSlot> findByTeacherIdAndDayOfWeekAndPeriodIdAndDeletedFalse(
            Integer teacherId, String dayOfWeek, Integer periodId);

    /**
     * Dò trùng lịch theo GIỜ THỰC (V16): mọi ô lịch của GV trong một Thứ, KHÔNG lọc theo
     * periodId. Bắt buộc phải quét rộng như vậy vì Period thuộc về từng trường — "tiết 1"
     * của hai trường là hai periodId khác nhau nhưng giờ có thể đè lên nhau.
     */
    List<AssignmentSlot> findByTeacherIdAndDayOfWeekAndDeletedFalse(Integer teacherId, String dayOfWeek);

    /** Mọi ô lịch còn hiệu lực của một GV — dựng bảng "giờ bận" cho form phân công. */
    List<AssignmentSlot> findByTeacherIdAndDeletedFalse(Integer teacherId);

    /**
     * Chiều NGƯỢC lại của dò trùng: mọi ô lịch của một LỚP trong một Thứ. Luật cũ chỉ hỏi
     * "giáo viên này có bận không" nên xếp được ba giáo viên vào cùng một lớp cùng một tiết —
     * lớp chỉ có một, mà cả ba đều được tính công.
     */
    List<AssignmentSlot> findByClassIdAndDayOfWeekAndDeletedFalse(Integer classId, String dayOfWeek);

    /** Toàn bộ ô lịch còn hiệu lực — quét trùng giờ trên cả hệ thống. */
    List<AssignmentSlot> findByDeletedFalse();

    /**
     * Ô lịch của NHIỀU phiếu trong một câu — chống N+1 ở màn danh sách phân công.
     *
     * <p>Bản cũ gọi findByAssignmentId cho từng phiếu: 444 phiếu là 444 câu, chưa kể mỗi ô
     * lại tra tiếp tiết/lớp/trường. Trả về cả ô ĐÃ XÓA MỀM để thùng rác dùng chung được câu
     * này; bên gọi tự lọc theo cờ.
     */
    List<AssignmentSlot> findByAssignmentIdIn(List<Integer> assignmentIds);

    /**
     * Mọi ô lịch còn hiệu lực của một TRƯỜNG (V27) — nguồn cho lưới xếp lịch khóa sẵn những ô
     * đã có giáo viên khác dạy. Hỏi theo trường chứ không theo từng lớp vì lưới hiện cả trường
     * một lúc: hỏi lẻ từng lớp là 20+ lượt gọi cho một lần mở form.
     */
    List<AssignmentSlot> findBySchoolIdAndDeletedFalse(Integer schoolId);

    /** Xóa CỨNG mọi slot của phân công — dùng khi xóa vĩnh viễn. */
    @Modifying
    @Query("DELETE FROM AssignmentSlot s WHERE s.assignmentId = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") Integer assignmentId);

    /** Ô thời khóa biểu hằng tuần còn sống đang GẮN một phòng — chặn xóa phòng đang dùng. */
    /**
     * Ô lịch còn trỏ vào một LỚP — chốt chặn xóa lớp.
     *
     * <p>KHÔNG dùng {@code Assignment.ClassId} cho việc này: từ V16 lớp thật nằm ở TỪNG Ô LỊCH,
     * còn lớp ở cấp phiếu chỉ là giá trị đại diện của ô đầu tiên. Đếm theo cấp phiếu là chặn
     * hụt — đo trên dữ liệu demo có 674 lớp đang nằm trong thời khóa biểu nhưng không phải lớp
     * đại diện của phiếu nào, tức là xóa được sạch sẽ dù giáo viên vẫn đang dạy chúng.
     */
    long countByClassIdAndDeletedFalse(Integer classId);

    /** Y hệt cho TRƯỜNG — từ V27 trường thật cũng nằm ở từng ô lịch. */
    long countBySchoolIdAndDeletedFalseAndTeacherIdIsNotNull(Integer schoolId);

    long countByRoomIdAndDeletedFalse(Integer roomId);

    /** Ô thời khóa biểu hằng tuần còn sống đang DÙNG một tiết — chặn xóa tiết đang dùng. */
    long countByPeriodIdAndDeletedFalse(Integer periodId);
}
