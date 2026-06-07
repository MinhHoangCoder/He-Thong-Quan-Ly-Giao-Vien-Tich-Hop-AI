package com.kdc.tsdms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Gửi email. Hiện chỉ có 1 loại: email đặt lại mật khẩu. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender, @Value("${tsdms.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendPasswordReset(String to, String name, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("TSDMS - Đặt lại mật khẩu");
        message.setText("Xin chào " + name + ",\n\n"
                + "Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu. Nhấp vào liên kết sau để đặt"
                + " lại (hết hạn sau 30 phút):\n\n"
                + resetLink
                + "\n\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            // Không để lỗi gửi mail làm hỏng request. Log link để dev test khi chưa cấu hình SMTP.
            log.warn("Gửi email reset thất bại ({}). Link (DEV) cho {}: {}", ex.getMessage(), to, resetLink);
        }
    }
}
