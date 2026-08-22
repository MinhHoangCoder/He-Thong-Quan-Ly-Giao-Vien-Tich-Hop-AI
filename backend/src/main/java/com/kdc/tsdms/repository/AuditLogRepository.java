package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng AuditLog (khóa BIGINT → Long) — nhật ký chỉ GHI THÊM, không sửa/xóa. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Nhật ký có lọc, mới nhất trước.
     *
     * <p>Lọc bằng kiểu "tham số null = bỏ qua điều kiện" để một câu phục vụ mọi tổ hợp bộ lọc,
     * thay vì bốn phương thức khác nhau rồi phải chọn đúng cái ở tầng service.
     *
     * <p>{@code entityId} so bằng chuỗi vì cột lưu chuỗi — có bảng dùng khóa ghép.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action   IS NULL OR a.action = :action)
              AND (:entity   IS NULL OR a.entity = :entity)
              AND (:actorId  IS NULL OR a.actorUserId = :actorId)
              AND (:tu       IS NULL OR a.createdAt >= :tu)
              AND (:den      IS NULL OR a.createdAt <= :den)
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<AuditLog> search(
            @Param("action") String action,
            @Param("entity") String entity,
            @Param("actorId") Integer actorId,
            @Param("tu") Instant tu,
            @Param("den") Instant den,
            Pageable pageable);

    /** Các loại thao tác đã từng ghi — đổ vào ô lọc, không hard-code danh sách ở frontend. */
    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    /** Các bảng đã từng bị ghi nhật ký. */
    @Query("SELECT DISTINCT a.entity FROM AuditLog a WHERE a.entity IS NOT NULL ORDER BY a.entity")
    List<String> findDistinctEntities();
}
