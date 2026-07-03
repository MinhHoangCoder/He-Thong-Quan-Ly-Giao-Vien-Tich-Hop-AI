package com.kdc.tsdms.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

/**
 * Lớp nền cho INTEGRATION TEST cần SQL Server THẬT (Docker + Testcontainers): bật container
 * SQL Server 2022 (1 lần cho cả lượt test) → tạo database TSDMS rỗng (collation
 * Vietnamese_CI_AS như DB thật) → trỏ datasource của Spring vào đó. KHÔNG chọn kiểu context
 * ở đây — lớp con tự khai {@code @DataJpaTest} (xem {@link AbstractJpaSliceIT}) hoặc
 * {@code @SpringBootTest} (xem {@link com.kdc.tsdms.TsdmsApplicationIT}).
 *
 * <p>Máy KHÔNG có Docker: toàn bộ *IT tự SKIP ({@code disabledWithoutDocker}), build vẫn xanh.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractSqlServerIT {

    private static final String DB_NAME = "TSDMS";

    /**
     * Container SINGLETON: cố ý KHÔNG gắn {@code @Container} vì lifecycle per-class sẽ khởi
     * động lại SQL Server (~40–60s) cho MỖI lớp test. Start thủ công đúng 1 lần, mọi lớp *IT
     * dùng chung; Ryuk (container dọn dẹp của Testcontainers) tự xóa khi JVM thoát.
     */
    private static final MSSQLServerContainer MSSQL =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    @DynamicPropertySource
    static void sqlServerProperties(DynamicPropertyRegistry registry) {
        if (!MSSQL.isRunning()) {
            MSSQL.start();
            createTsdmsDatabase();
        }
        // Trỏ datasource vào DB TSDMS vừa tạo (JDBC URL mặc định của container là master).
        registry.add("spring.datasource.url", () -> MSSQL.getJdbcUrl() + ";databaseName=" + DB_NAME);
        registry.add("spring.datasource.username", MSSQL::getUsername);
        registry.add("spring.datasource.password", MSSQL::getPassword);
    }

    /** Tạo DB trống cùng collation với DB thật (header V1) — Flyway sẽ migrate từ V1. */
    private static void createTsdmsDatabase() {
        try (Connection conn =
                        DriverManager.getConnection(MSSQL.getJdbcUrl(), MSSQL.getUsername(), MSSQL.getPassword());
                Statement st = conn.createStatement()) {
            st.execute("IF DB_ID('" + DB_NAME + "') IS NULL CREATE DATABASE " + DB_NAME + " COLLATE Vietnamese_CI_AS");
        } catch (SQLException e) {
            throw new IllegalStateException("Không tạo được database " + DB_NAME + " trong container", e);
        }
    }
}
