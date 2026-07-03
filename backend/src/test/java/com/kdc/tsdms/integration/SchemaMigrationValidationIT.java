package com.kdc.tsdms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdc.tsdms.entity.Branch;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * LƯỚI AN TOÀN schema ↔ entity. Vì app chạy {@code ddl-auto=none}, một {@code @Column} gõ
 * sai tên/kiểu sẽ im lặng cho tới khi query chạy thật mới nổ. Test này bắt sớm cả hai đầu:
 *
 * <ol>
 *   <li>Flyway V1→V10 chạy sạch trên DB RỖNG (đúng thứ tự, không lỗi) — chứng minh máy
 *       thành viên mới clone về là migrate được ngay;
 *   <li>Hibernate validate: context load được nghĩa là TOÀN BỘ entity đã được đối chiếu
 *       từng cột với schema (thiếu bảng/thiếu cột/sai kiểu → context nổ → test đỏ).
 * </ol>
 */
class SchemaMigrationValidationIT extends AbstractJpaSliceIT {

    /** Số migration hiện có (V1..V10) — thêm V mới thì tăng số này. */
    private static final int SO_MIGRATION_HIEN_TAI = 10;

    /** Số @Entity hiện có trong com.kdc.tsdms.entity (không tính @MappedSuperclass/@Embeddable). */
    private static final int SO_ENTITY_HIEN_TAI = 36;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void flyway_chayDuV1DenV10_thanhCongVaDungThuTu() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(rows).hasSizeGreaterThanOrEqualTo(SO_MIGRATION_HIEN_TAI);
        // Mọi migration đều success (Flyway fail thì context đã nổ, nhưng assert cho tường minh)
        assertThat(rows).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));

        List<Integer> versions = rows.stream()
                .map(row -> Integer.valueOf(String.valueOf(row.get("version"))))
                .toList();
        assertThat(versions).isSorted(); // áp dụng đúng thứ tự tăng dần
        assertThat(versions)
                .containsAll(
                        IntStream.rangeClosed(1, SO_MIGRATION_HIEN_TAI).boxed().toList());
    }

    @Test
    void hibernateValidate_phuDuToanBoEntity() {
        // ddl-auto=validate chạy NGAY lúc dựng EntityManagerFactory: đến được đây nghĩa là
        // phép đối chiếu đã pass. Assert này chốt thêm rằng nó thực sự phủ đủ số entity —
        // tránh trường hợp "pass vì chẳng scan được entity nào".
        assertThat(entityManager.getMetamodel().getEntities()).hasSizeGreaterThanOrEqualTo(SO_ENTITY_HIEN_TAI);
    }

    @Test
    void instant_ghiXuongDatetime2TheoUtc_docLenKhongLechGio() {
        // Chốt quy ước "DATETIME2 lưu giờ UTC" (header V1) ở tầng Hibernate: cặp cấu hình
        // preferred_instant_jdbc_type=TIMESTAMP + jdbc.time_zone=UTC phải làm Instant ghi
        // xuống ĐÚNG wall-clock UTC (không phải +07 theo múi giờ JVM) và đọc lên khớp 100%.
        Instant mocUtc = Instant.parse("2026-07-02T08:30:00.123Z");

        Branch chiNhanh = new Branch();
        chiNhanh.setName("CN UTC");
        chiNhanh.setUpdatedAt(mocUtc);
        entityManager.persist(chiNhanh);
        entityManager.flush();

        // 1) DB thực sự chứa wall-clock UTC (kiểm bằng SQL thô, không qua Hibernate)
        String trongDb = jdbc.queryForObject(
                "SELECT CONVERT(varchar(23), UpdatedAt, 121) FROM Branch WHERE Id = ?", String.class, chiNhanh.getId());
        assertThat(trongDb).isEqualTo("2026-07-02 08:30:00.123");

        // 2) Đọc lại qua Hibernate (xóa cache L1 để ép SELECT thật) → đúng Instant ban đầu
        entityManager.clear();
        Branch docLai = entityManager.find(Branch.class, chiNhanh.getId());
        assertThat(docLai.getUpdatedAt()).isEqualTo(mocUtc);
    }
}
