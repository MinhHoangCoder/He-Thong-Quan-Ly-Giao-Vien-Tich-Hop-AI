package com.kdc.tsdms.service;

import com.kdc.tsdms.security.ResetProperties;
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

    /**
     * Chữ ký cuối mọi email hệ thống gửi đi.
     *
     * <p>Hằng số chứ không gõ lại trong từng mẫu: thêm mẫu email thứ hai mà quên chữ ký là hai
     * email cùng hệ thống ký tên khác nhau. Trùng với tên hiển thị người gửi ở {@code
     * tsdms.mail.from} — người nhận thấy một cái tên nhất quán từ hòm thư đến cuối thư.
     */
    private static final String CHU_KY = "Trung tâm Sao Việt";

    private final JavaMailSender mailSender;
    private final String from;
    private final ResetProperties resetProps;

    /** Bật thì in link reset ra log khi gửi mail hỏng — CHỈ dùng khi chưa cấu hình SMTP. */
    private final boolean logResetLink;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${tsdms.mail.from}") String from,
            @Value("${tsdms.mail.log-reset-link:false}") boolean logResetLink,
            ResetProperties resetProps) {
        this.mailSender = mailSender;
        this.from = from;
        this.logResetLink = logResetLink;
        this.resetProps = resetProps;
    }

    public void sendPasswordReset(String to, String name, String resetLink) {
        long ttlMinutes = resetProps.getTokenTtl().toMinutes();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("TSDMS - Đặt lại mật khẩu");
        message.setText("Xin chào " + name + ",\n\n"
                + "Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu. Nhấp vào liên kết sau để đặt"
                + " lại (hết hạn sau " + ttlMinutes + " phút):\n\n"
                + resetLink
                + "\n\nNếu bạn không yêu cầu, hãy bỏ qua email này."
                + "\n\nTrân trọng,\n" + CHU_KY);
        try {
            mailSender.send(message);
            log.info("Đã gửi email đặt lại mật khẩu tới {}", to);
        } catch (Exception ex) {
            // Không để lỗi gửi mail làm hỏng request.
            //
            // BẢO MẬT: link reset chứa TOKEN còn sống — ai đọc được log là đổi được mật khẩu
            // của người đó. Log file bị commit nhầm / chụp màn hình đưa vào báo cáo / dán lên
            // nhóm chat đều làm lộ. Mặc định chỉ báo là gửi hỏng, KHÔNG in link.
            // Cần lại lối thoát cũ (máy chưa cấu hình SMTP) thì bật tsdms.mail.log-reset-link.
            if (logResetLink) {
                log.warn("Gửi email reset thất bại ({}). Link (DEV) cho {}: {}", ex.getMessage(), to, resetLink);
            } else {
                log.warn(
                        "Gửi email reset thất bại ({}) cho {}. Bật tsdms.mail.log-reset-link=true"
                                + " nếu cần in link ra log để test cục bộ.",
                        ex.getMessage(),
                        to);
            }
        }
    }
}
