package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Notification (khóa BIGINT → Long). */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Thông báo của một người dùng, mới nhất trước. */
    List<Notification> findByRecipientUserIdOrderByIdDesc(Integer recipientUserId);

    /** Số thông báo CHƯA đọc của một người dùng (badge trên chuông). */
    long countByRecipientUserIdAndReadFalse(Integer recipientUserId);

    /** Các thông báo chưa đọc — để đánh dấu đã đọc tất cả. */
    List<Notification> findByRecipientUserIdAndReadFalse(Integer recipientUserId);
}
