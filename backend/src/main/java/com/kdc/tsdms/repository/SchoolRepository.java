package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.School;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolRepository extends JpaRepository<School, Integer> {

    /** Hồ sơ trường theo tài khoản (để lấy tên hiển thị khi đăng nhập). */
    Optional<School> findByIdAndDeletedFalse(Integer id);

    /** Dropdown trường (chưa xóa mềm), sắp theo tên. */
    List<School> findByDeletedFalseOrderByNameAsc();

    /** Tra 1 trường trong thùng rác (đã xóa mềm). */
    Optional<School> findByIdAndDeletedTrue(Integer id);

    /** Thùng rác: trường đã xóa mềm, mới ẩn trước. */
    List<School> findByDeletedTrueOrderByDeletedAtDesc();

    /**
     * Danh sách trường có phân trang + tìm kiếm theo tên/địa chỉ/người liên hệ/SĐT, lọc theo chi
     * nhánh và trạng thái (đều tùy chọn — null = bỏ qua điều kiện).
     *
     * <p>Keyword do service escape sẵn (escapeLike) với ký tự thoát '!': %, _, [ là wildcard của
     * LIKE — không escape thì gõ '%' ra toàn bộ danh sách.
     *
     * <p>{@code hetHanTruoc} (tùy chọn) lọc "sắp hết hạn": hợp đồng CÒN hạn nhưng kết thúc trước
     * ngày này. Truyền null thì bỏ qua.
     *
     * <p>Lọc trạng thái so với trạng thái SUY RA chứ không so thẳng cột Status, để tab "Hết hạn"
     * đếm đúng số trường thật sự đã quá hạn hợp đồng. Biểu thức CASE ở đây phải khớp với {@code
     * School.effectiveStatus} — lệch nhau thì bộ lọc trả về một đằng, badge hiện một nẻo.
     */
    @Query("""
            SELECT s FROM School s
            WHERE s.deleted = false
              AND (:branchId IS NULL OR s.branchId = :branchId)
              AND (:keyword IS NULL
                   OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
                   OR LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
                   OR LOWER(s.contactPerson) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
                   OR s.phone LIKE CONCAT('%', :keyword, '%') ESCAPE '!')
              AND (:hetHanTruoc IS NULL
                   OR (s.contractEndDate IS NOT NULL
                       AND s.contractEndDate >= :today AND s.contractEndDate <= :hetHanTruoc))
              AND (:status IS NULL OR :status = CASE
                     WHEN s.status = 'ACTIVE' AND s.contractEndDate IS NOT NULL AND s.contractEndDate < :today
                          THEN 'EXPIRED'
                     ELSE s.status END)
            """)
    Page<School> search(
            @Param("keyword") String keyword,
            @Param("branchId") Integer branchId,
            @Param("status") String status,
            @Param("today") LocalDate today,
            @Param("hetHanTruoc") LocalDate hetHanTruoc,
            Pageable pageable);

    /** Đếm trường còn hợp tác của một chi nhánh — chặn xóa chi nhánh còn trường. */
    long countByBranchIdAndDeletedFalse(Integer branchId);

    /**
     * Trùng tên trong CÙNG chi nhánh (bỏ qua trường đã xóa mềm).
     *
     * <p>So khớp không phân biệt hoa/thường và dấu là do COLLATION của cột (Vietnamese_CI_AS) lo,
     * không phải do câu lệnh này — chạy trên DB đặt collation phân biệt hoa thường thì luật lỏng
     * đi, nhưng chỉ số lọc UX_School_BranchName ở V36 vẫn là chốt cuối.
     */
    boolean existsByBranchIdAndNameAndDeletedFalse(Integer branchId, String name);

    boolean existsByBranchIdAndNameAndDeletedFalseAndIdNot(Integer branchId, String name, Integer id);

    /**
     * Đếm Ô THỜI KHÓA BIỂU của một trường mà phiếu phân công CHA còn hiệu lực — rào chắn thứ hai
     * khi xóa mềm trường.
     *
     * <p>Vì sao không đếm thẳng {@code AssignmentSlot.SchoolId} như trước: từ V27 một phiếu phân
     * công trải được nhiều trường, trường thật nằm ở TỪNG Ô LỊCH. Đếm ở cấp phiếu
     * ({@code Assignment.SchoolId}) thì các trường phụ lọt lưới; đếm ở cấp ô mà không nhìn trạng
     * thái phiếu thì ngược lại — ô của phiếu đã hết hạn/đã hủy vẫn nằm nguyên trong bảng và chặn
     * vĩnh viễn một trường đã ngừng hợp tác. Câu này lấy đúng phần giao: ô của trường NÀY, thuộc
     * phiếu CÒN hiệu lực.
     *
     * <p>Viết bằng JPQL chứ không phải native để dùng lại đúng bộ trạng thái
     * {@code SchoolService.PHAN_CONG_CON_HIEU_LUC} mà rào chắn cấp phiếu đang dùng — hai rào
     * chắn của cùng một luật mà lệch danh sách trạng thái là loại lỗi không ai phát hiện được.
     */
    @Query("""
            SELECT COUNT(o) FROM AssignmentSlot o, Assignment pc
            WHERE o.assignmentId = pc.id
              AND o.schoolId = :schoolId
              AND o.deleted = false
              AND pc.deleted = false
              AND pc.status IN :statuses
            """)
    long demOLichConHieuLuc(@Param("schoolId") Integer schoolId, @Param("statuses") List<String> statuses);

    /**
     * Đếm dòng con của MỘT trường ở mọi bảng trỏ vào School, gom trong một câu — dùng trước khi
     * XÓA CỨNG (xóa vĩnh viễn từ thùng rác).
     *
     * <p><b>KHÔNG lọc IsDeleted</b>: khóa ngoại chặn theo sự TỒN TẠI của dòng con, một lớp đã
     * "xóa" vẫn khiến {@code DELETE School} nổ. Cùng khuôn với {@code
     * TeacherRepository.countChildRowsByTeacherId}.
     *
     * <p>KHÔNG kể Period và Room: đó là cấu hình của chính trường (khung tiết, phòng học), bị
     * xóa kèm ở {@code SchoolService.purge} chứ không phải rào chắn. Mọi trường đều có khung
     * tiết nên tính chúng là rào thì nút "Xóa vĩnh viễn" không bao giờ bấm được.
     */
    @Query(value = """
                    SELECT 'class' AS loai, COUNT(*) AS soLuong FROM SchoolClass WHERE SchoolId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'student',    COUNT(*) FROM Student           WHERE SchoolId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'assignment', COUNT(*) FROM Assignment        WHERE SchoolId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'slot',       COUNT(*) FROM AssignmentSlot    WHERE SchoolId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'contract',   COUNT(*) FROM ServiceContract   WHERE SchoolId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'holiday',    COUNT(*) FROM Holiday           WHERE SchoolId = :id HAVING COUNT(*) > 0
                    UNION ALL SELECT 'evaluation', COUNT(*) FROM TeacherEvaluation WHERE SchoolId = :id HAVING COUNT(*) > 0
                    """, nativeQuery = true)
    List<Object[]> countChildRowsBySchoolId(@Param("id") Integer id);
}
