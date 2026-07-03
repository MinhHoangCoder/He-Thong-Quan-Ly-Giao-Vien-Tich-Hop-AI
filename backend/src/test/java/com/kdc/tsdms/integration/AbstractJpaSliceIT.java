package com.kdc.tsdms.integration;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Lớp nền cho integration test TẦNG JPA (slice, không dựng web/security): Flyway chạy đủ
 * V1→V10 trên DB rỗng của container, rồi Hibernate {@code ddl-auto=validate} đối chiếu TỪNG
 * cột của TỪNG entity với schema vừa migrate — entity lệch schema là test đỏ ngay từ lúc
 * dựng context, thay vì nổ ngầm khi query chạy thật.
 *
 * <p>Mọi lớp con dùng CHUNG một cấu hình context (Spring cache lại) → container + Flyway +
 * validate chỉ tốn 1 lần cho cả lượt test. Test mặc định transactional-rollback (của
 * {@code @DataJpaTest}) nên dữ liệu giữa các test không dây sang nhau.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractJpaSliceIT extends AbstractSqlServerIT {}
