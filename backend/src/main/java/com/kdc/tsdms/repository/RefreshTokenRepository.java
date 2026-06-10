package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi MỌI refresh token còn hiệu lực của một user (đăng xuất mọi thiết bị).
     * Dùng khi: (1) phát hiện token đã rotate bị dùng lại — dấu hiệu token bị đánh cắp;
     * (2) user đặt lại mật khẩu — phiên cũ không được phép sống tiếp.
     */
    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now " + "where rt.appUserId = :userId and rt.revokedAt is null")
    int revokeAllActiveByAppUserId(@Param("userId") Integer userId, @Param("now") Instant now);
}
