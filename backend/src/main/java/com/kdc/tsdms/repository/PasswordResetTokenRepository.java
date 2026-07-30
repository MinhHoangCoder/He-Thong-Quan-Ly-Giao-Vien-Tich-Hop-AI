package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.PasswordResetToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Phiếu reset mới nhất của một tài khoản — dùng để tính khoảng nghỉ giữa 2 lần gửi mail. */
    Optional<PasswordResetToken> findTopByAppUserIdOrderByIdDesc(Integer appUserId);

    /** Đếm số phiếu đã phát cho tài khoản kể từ mốc thời gian (hạn mức 24 giờ). */
    long countByAppUserIdAndCreatedAtAfter(Integer appUserId, Instant since);

    /** Các phiếu CÒN dùng được của tài khoản — phát link mới thì vô hiệu hóa hết link cũ. */
    List<PasswordResetToken> findByAppUserIdAndUsedAtIsNullAndExpiresAtAfter(Integer appUserId, Instant now);
}
