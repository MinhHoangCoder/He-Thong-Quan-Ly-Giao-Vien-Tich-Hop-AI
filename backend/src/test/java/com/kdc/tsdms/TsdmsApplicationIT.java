package com.kdc.tsdms;

import com.kdc.tsdms.integration.AbstractSqlServerIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test dựng TOÀN BỘ application context (web + security + JPA + Flyway) trên SQL Server
 * của Testcontainers. Thay cho {@code TsdmsApplicationTests} cũ vốn đòi SQL Server localhost +
 * biến môi trường DB_PASSWORD — khiến {@code mvnw test} đỏ trên máy chưa cài/chưa bật DB.
 * Đặt tên *IT để chạy ở phase verify (failsafe), tách khỏi lượt unit test thuần.
 */
@SpringBootTest
class TsdmsApplicationIT extends AbstractSqlServerIT {

    @Test
    void contextLoads() {}
}
