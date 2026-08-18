package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.School;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolRepository extends JpaRepository<School, Integer> {

    /** Hồ sơ trường theo tài khoản (để lấy tên hiển thị khi đăng nhập). */
    Optional<School> findByAppUserIdAndDeletedFalse(Integer appUserId);

    Optional<School> findByIdAndDeletedFalse(Integer id);

    /** Dropdown trường (chưa xóa mềm), sắp theo tên. */
    List<School> findByDeletedFalseOrderByNameAsc();

    /** Tra 1 trường trong thùng rác (đã xóa mềm). */
    Optional<School> findByIdAndDeletedTrue(Integer id);

    /**
     * Danh sách trường có phân trang + tìm kiếm theo tên/địa chỉ/người liên hệ/SĐT,
     * lọc theo chi nhánh và trạng thái (đều tùy chọn — null = bỏ qua điều kiện).
     */
    @Query("""
            SELECT s FROM School s
            WHERE s.deleted = false
              AND (:keyword IS NULL OR s.name LIKE %:keyword% OR s.address LIKE %:keyword%
                   OR s.contactPerson LIKE %:keyword% OR s.phone LIKE %:keyword%)
              AND (:branchId IS NULL OR s.branchId = :branchId)
              AND (:status IS NULL OR s.status = :status)
            """)
    Page<School> search(
            @Param("keyword") String keyword,
            @Param("branchId") Integer branchId,
            @Param("status") String status,
            Pageable pageable);

    /** Đếm trường còn hợp tác của một chi nhánh — chặn xóa chi nhánh còn trường. */
    long countByBranchIdAndDeletedFalse(Integer branchId);
}
