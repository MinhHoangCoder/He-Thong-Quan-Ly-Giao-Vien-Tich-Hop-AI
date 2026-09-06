package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.SchoolClass;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng SchoolClass — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Integer> {

    Optional<SchoolClass> findByIdAndDeletedFalse(Integer id);

    Optional<SchoolClass> findByIdAndDeletedTrue(Integer id);

    List<SchoolClass> findByDeletedTrueOrderByDeletedAtDesc();

    boolean existsBySchoolIdAndNameAndSchoolYearAndDeletedFalse(Integer schoolId, String name, String schoolYear);

    boolean existsBySchoolIdAndNameAndSchoolYearAndDeletedFalseAndIdNot(
            Integer schoolId, String name, String schoolYear, Integer id);

    boolean existsBySchoolIdAndNameAndDeletedFalse(Integer schoolId, String name);

    boolean existsBySchoolIdAndNameAndDeletedFalseAndIdNot(Integer schoolId, String name, Integer id);

    List<SchoolClass> findBySchoolIdAndDeletedFalseAndStatusOrderByName(Integer schoolId, String status);

    /** Mọi lớp còn sống của một trường (không lọc status) — dropdown lọc ở trang Lịch dạy. */
    List<SchoolClass> findBySchoolIdAndDeletedFalseOrderByName(Integer schoolId);

    /**
     * Keyword do service escape sẵn (escapeLike) với ký tự thoát '!': %, _, [ của
     * SQL Server LIKE là wildcard — không escape thì gõ '%' sẽ khớp tất cả.
     */
    @Query("""
            SELECT sc FROM SchoolClass sc
            WHERE sc.deleted = false
              AND (:schoolId IS NULL OR sc.schoolId = :schoolId)
              AND (:status IS NULL OR sc.status = :status)
              AND (:gradeLevel IS NULL OR sc.gradeLevel = :gradeLevel)
              AND (:keyword IS NULL
                   OR LOWER(sc.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
                   OR LOWER(sc.gradeLevel) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
                   OR LOWER(sc.schoolYear) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!')
            """)
    Page<SchoolClass> search(
            @Param("keyword") String keyword,
            @Param("schoolId") Integer schoolId,
            @Param("status") String status,
            @Param("gradeLevel") String gradeLevel,
            Pageable pageable);

    /** Dropdown lọc: các khối đang tồn tại (chưa xóa mềm). */
    @Query("""
            SELECT DISTINCT sc.gradeLevel FROM SchoolClass sc
            WHERE sc.deleted = false
              AND sc.gradeLevel IS NOT NULL
              AND sc.gradeLevel <> ''
            ORDER BY sc.gradeLevel
            """)
    List<String> findDistinctGradeLevels();

    /** Lớp còn sống của một trường — chặn xóa trường khi còn lớp. */
    long countBySchoolIdAndDeletedFalse(Integer schoolId);

    /**
     * Lớp ĐANG HOẠT ĐỘNG của một trường — chặn phân công vào trường chưa có lớp nào để dạy.
     *
     * <p>Đếm riêng theo Status chứ không dùng lại {@link #countBySchoolIdAndDeletedFalse}: một
     * trường có 26 lớp đã đóng (INACTIVE) thì con số kia trả về 26 trong khi thực tế không có
     * chỗ nào xếp tiết vào được.
     */
    long countBySchoolIdAndDeletedFalseAndStatus(Integer schoolId, String status);

    /**
     * Số buổi dạy CÒN HIỆU LỰC của một lớp kể từ mốc {@code tuLuc} — cơ sở để chặn chuyển lớp
     * sang trạng thái "Ngừng".
     *
     * <p>Đóng một lớp mà thời khóa biểu của nó vẫn chạy là kiểu hỏng âm thầm: buổi dạy vẫn
     * sinh ra, giáo viên vẫn tới trường, chấm công vẫn ghi, nhưng lớp thì đã bị đánh dấu ngừng
     * nên mọi màn lọc theo lớp hoạt động đều không thấy nó nữa.
     *
     * <p>Lớp của một buổi nằm ở Ô THỜI KHÓA BIỂU sinh ra buổi ({@code SourceSlotId}), lùi về
     * lớp cấp phiếu cho dữ liệu trước V16 — chép đúng luật đang dùng ở {@code
     * AttendanceRepository.findPayableWithGrade} chứ không nghĩ ra luật thứ ba, vì từ V16 một
     * phiếu phân công trải nhiều lớp và đọc lớp cấp phiếu là đếm nhầm sang lớp khác.
     *
     * <p>Native query vì Schedule / Assignment / AssignmentSlot nối nhau bằng khóa trần (không
     * có quan hệ JPA), và {@code COALESCE} hai cột lớp thì JPQL không diễn đạt gọn được.
     */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*)
              FROM Schedule s
              LEFT JOIN Assignment a      ON a.Id = s.AssignmentId
              LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
             WHERE s.IsDeleted = 0
               AND s.Status <> 'CANCELLED'
               AND s.StartTime >= :tuLuc
               AND COALESCE(sl.ClassId, a.ClassId) = :classId
            """)
    long countUpcomingSessionsByClass(@Param("classId") Integer classId, @Param("tuLuc") java.time.LocalDateTime tuLuc);
}
