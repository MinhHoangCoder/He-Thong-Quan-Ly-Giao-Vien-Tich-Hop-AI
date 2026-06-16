package com.kdc.tsdms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Phục vụ file tĩnh đã upload (vd: file đính kèm bài giảng) qua URL {@code /uploads/**}.
 *
 * <p>{@code LessonService} lưu file vật lý vào thư mục {@code uploads/} (tương đối working
 * directory khi chạy app) và ghi {@code FileUrl = /uploads/...} vào DB — khớp với pattern
 * đã seed trong {@code database/seed/TSDMS_Seed_Demo.sql}. Cấu hình dưới đây ánh xạ URL đó
 * tới đúng thư mục trên đĩa. {@code SecurityConfig} đã thêm {@code /uploads/**} vào
 * {@code permitAll()} để client xem/tải file trực tiếp không cần JWT.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve file upload từ thư mục ngoài jar
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:uploads/");
    }
}
