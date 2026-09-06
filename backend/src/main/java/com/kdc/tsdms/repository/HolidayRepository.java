package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Holiday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, Integer> {

    /**
     * Các kỳ nghỉ CHẠM vào đoạn {@code [from, to]} — nạp một lần rồi lọc trong bộ nhớ, thay vì
     * hỏi DB cho từng ngày của cả học kỳ.
     *
     * <p>Điều kiện giao nhau viết theo dạng chuẩn ({@code fromDate <= to AND toDate >= from}):
     * đúng cho cả kỳ nghỉ nằm gọn bên trong lẫn kỳ nghỉ trùm lên hai đầu.
     */
    @Query("""
            SELECT h FROM Holiday h
            WHERE h.deleted = false
              AND h.fromDate <= :to
              AND h.toDate >= :from
            """)
    List<Holiday> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Danh sách có phân trang cho màn quản lý.
     *
     * <p>Lọc theo trường trả về CẢ kỳ nghỉ toàn hệ thống ({@code schoolId} null): đó chính là
     * những ngày trường đó cũng nghỉ. Lọc ra mỗi kỳ nghỉ riêng thì người xem tưởng trường chỉ
     * nghỉ đúng mấy hôm sửa chữa, quên mất 30/4 và 2/9.
     */
    @Query("""
            SELECT h FROM Holiday h
            WHERE h.deleted = false
              AND (:keyword IS NULL OR LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:kind IS NULL OR h.kind = :kind)
              AND (:from IS NULL OR h.toDate >= :from)
              AND (:to IS NULL OR h.fromDate <= :to)
              AND (:schoolId IS NULL OR h.schoolId IS NULL OR h.schoolId = :schoolId)
            """)
    Page<Holiday> search(
            @Param("keyword") String keyword,
            @Param("kind") String kind,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("schoolId") Integer schoolId,
            Pageable pageable);

    long countBySchoolIdAndDeletedFalse(Integer schoolId);

    /** Kỳ nghỉ trong thùng rác — cùng bộ lọc từ khóa với danh sách chính. */
    @Query("""
            SELECT h FROM Holiday h
            WHERE h.deleted = true
              AND (:keyword IS NULL OR LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Holiday> searchTrash(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Các buổi dạy mà CHÍNH kỳ nghỉ này đã hủy — nguyên liệu để trả chúng về lịch khi kỳ nghỉ
     * bị xóa.
     *
     * <p>Trước V40 chỗ này phải quét theo KHOẢNG NGÀY vì buổi bị hủy không ghi lại ai hủy nó,
     * nên chỉ dám đưa ra một con số ước lượng chứ không dám khôi phục: trả mù cả khoảng thì
     * buổi admin hủy tay hôm đó cũng sống lại. Có cột {@code HolidayId} thì câu hỏi trở nên
     * chính xác tuyệt đối, và {@code IX_Schedule_Holiday} (index lọc) seek thẳng tới đúng
     * chừng ấy dòng.
     *
     * <p>Vì sao câu này nằm ở HolidayRepository chứ không ở ScheduleRepository: nó chỉ có
     * nghĩa trong nghiệp vụ lịch nghỉ, và {@code sessionDaysInRange} bên dưới đã đặt sẵn tiền
     * lệ hỏi bảng Schedule từ đây.
     */
    @Query("""
            SELECT s FROM Schedule s
            WHERE s.deleted = false
              AND s.holidayId = :holidayId
            """)
    List<com.kdc.tsdms.entity.Schedule> sessionsCancelledByHoliday(@Param("holidayId") Integer holidayId);

    /** Như trên nhưng chỉ đếm — hộp thoại xác nhận xóa không cần nạp cả danh sách entity. */
    @Query("""
            SELECT COUNT(s) FROM Schedule s
            WHERE s.deleted = false
              AND s.holidayId = :holidayId
            """)
    long countSessionsCancelledByHoliday(@Param("holidayId") Integer holidayId);

    /**
     * Những NGÀY còn có buổi dạy chưa hủy, kèm trường của buổi — nguyên liệu để biết kỳ nghỉ nào
     * còn việc phải xử lý ở hộp thoại "Buổi dạy".
     *
     * <p>VÌ SAO KHÔNG NỐI THẲNG VỚI BẢNG Holiday TRONG SQL: đã thử ba cách (truy vấn con tương
     * quan, JOIN + GROUP BY, CTE gom trước) và cả ba đều nổ khi chạy THẬT qua JDBC. Lý do: driver
     * JDBC để {@code ARITHABORT OFF} còn sqlcmd để ON, hai chế độ cho hai kế hoạch khác nhau —
     * cùng một câu JOIN + GROUP BY chạy 0,5 giây trong sqlcmd nhưng 147 GIÂY qua ứng dụng (đo
     * thật, không phải ước lượng). Bản CTE cũng 26 giây vì SQL Server bung CTE ra chứ không
     * hiện thực hóa nó.
     *
     * <p>Câu này thì không có gì để optimizer bóp méo: một lượt quét, gom trùng, xong. Đo qua
     * chế độ của JDBC: ~230 ms cho 5.200 dòng trên bộ 86.865 buổi. Việc đối chiếu ngày với
     * khoảng của từng kỳ nghỉ làm bên Java — vài trăm nghìn phép so sánh, tính bằng micro giây.
     *
     * <p>Trường lấy từ ô thời khóa biểu sinh ra buổi, NULL nếu buổi không gắn ô nào — kỳ nghỉ
     * toàn hệ thống nhận mọi ngày, kỳ nghỉ của một trường chỉ nhận ngày của trường đó.
     *
     * @return các dòng {ngày, schoolId}
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT CAST(s.StartTime AS DATE) AS Ngay, sl.SchoolId
              FROM Schedule s
              LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
             WHERE s.IsDeleted = 0
               AND s.Status <> 'CANCELLED'
               AND s.StartTime >= :from
               AND s.StartTime < :to
            """)
    List<Object[]> sessionDaysInRange(
            @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);

    /**
     * Những NGÀY có dòng chấm công VẮNG do hệ thống tự ghi, kèm trường — nửa còn lại của phép
     * kiểm tra ở {@link #sessionDaysInRange}.
     *
     * <p>Phải hỏi riêng chứ không suy ra từ buổi dạy: hủy buổi KHÔNG xóa dòng vắng đã ghi, nên
     * một kỳ nghỉ đã hủy sạch buổi vẫn còn việc phải dọn.
     *
     * @return các dòng {ngày, schoolId}
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT a.WorkDate AS Ngay, sl.SchoolId
              FROM Attendance a
              LEFT JOIN Schedule s ON s.Id = a.ScheduleId
              LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
             WHERE a.Status = 'ABSENT'
               AND a.CheckInMethod = 'SYSTEM'
               AND a.WorkDate BETWEEN :from AND :to
            """)
    List<Object[]> systemAbsenceDaysInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
