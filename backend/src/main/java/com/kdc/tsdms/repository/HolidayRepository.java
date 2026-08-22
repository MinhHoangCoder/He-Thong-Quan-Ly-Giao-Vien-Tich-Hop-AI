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
     * Buổi dạy ĐÃ HỦY nằm trong khoảng ngày — dùng để ước lượng hậu quả trước khi xóa kỳ nghỉ.
     *
     * <p>Là ƯỚC LƯỢNG chứ không phải con số chính xác: buổi bị hủy không lưu lại nó bị hủy vì
     * kỳ nghỉ nào. Nhưng đủ để người dùng biết thao tác này đang đụng tới cái gì, và câu chữ
     * trên giao diện nói rõ đây là số buổi hủy trong khoảng ngày đó.
     */
    @Query("""
            SELECT COUNT(s) FROM Schedule s
            WHERE s.deleted = false
              AND s.status = 'CANCELLED'
              AND s.startTime >= :from
              AND s.startTime < :to
            """)
    long countCancelledSessionsInRange(
            @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);
}
