package com.kdc.tsdms;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling: bật tác vụ nền quét phân công quá hạn xác nhận (AssignmentApprovalService). */
@SpringBootApplication
@EnableScheduling
public class TsdmsApplication {

    public static void main(String[] args) {
        // Ép JVM chạy ở UTC để khớp quy ước "lưu UTC" của schema (xem application.yaml:
        // datetime2 lưu giờ UTC, hibernate.jdbc.time_zone=UTC). Nếu để JVM theo giờ máy
        // (vd +07/+08), các cột TIME/DATE (giờ vào/ra chấm công, khung tiết) bị lệch đúng
        // bằng offset khi đọc — đặt mặc định UTC ở đây để nhất quán mọi cách chạy (IDE/mvnw/jar).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TsdmsApplication.class, args);
    }
}
