package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Các "phiên đăng nhập" đang sống của 1 user (chưa thu hồi, chưa hết hạn), mới nhất
     * trước — trang Cài đặt dùng để liệt kê thiết bị đang đăng nhập.
     */
    List<RefreshToken> findByAppUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Integer appUserId, Instant now);

    /**
     * Thu hồi mọi phiên của user TRỪ phiên đang thao tác (user ở lại, thiết bị khác văng) —
     * dùng cho "đổi mật khẩu" và nút "đăng xuất thiết bị khác" ở trang Cài đặt.
     */
    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now "
            + "where rt.appUserId = :userId and rt.revokedAt is null and rt.tokenHash <> :keepHash")
    int revokeAllActiveByAppUserIdExceptHash(
            @Param("userId") Integer userId, @Param("keepHash") String keepHash, @Param("now") Instant now);

    /**
     * Thu hồi MỌI refresh token còn hiệu lực của một user (đăng xuất mọi thiết bị).
     * Dùng khi: (1) phát hiện token đã rotate bị dùng lại — dấu hiệu token bị đánh cắp;
     * (2) user đặt lại mật khẩu — phiên cũ không được phép sống tiếp.
     */
    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now " + "where rt.appUserId = :userId and rt.revokedAt is null")
    int revokeAllActiveByAppUserId(@Param("userId") Integer userId, @Param("now") Instant now);

    /**
     * Xóa token đã CHẾT (hết hạn hoặc bị thu hồi) từ trước mốc {@code nguong}.
     *
     * <p>Chỉ đụng vào token đã chết. Token còn sống không bao giờ nằm trong điều kiện này, nên
     * job dọn không thể đá ai ra khỏi phiên đang dùng — điều kiện thời gian là lưới an toàn thứ
     * hai bên cạnh việc chỉ chọn dòng đã chết.
     */
    @Modifying
    @Query("delete from RefreshToken rt "
            + "where (rt.revokedAt is not null and rt.revokedAt < :nguong) "
            + "   or (rt.expiresAt < :nguong)")
    int deleteChetTruoc(@Param("nguong") Instant nguong);
}
