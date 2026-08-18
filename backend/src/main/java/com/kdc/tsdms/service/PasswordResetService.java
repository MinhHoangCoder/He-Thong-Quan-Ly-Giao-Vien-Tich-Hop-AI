package com.kdc.tsdms.service;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.PasswordResetToken;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.PasswordResetTokenRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.security.JwtService;
import com.kdc.tsdms.security.ResetProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Luồng QUÊN MẬT KHẨU: forgot (sinh token + gửi email) và reset (đổi mật khẩu bằng token). */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Cửa sổ tính hạn mức "tối đa N email/tài khoản". */
    private static final Duration DAILY_WINDOW = Duration.ofHours(24);

    /** Trạng thái tài khoản duy nhất được phép đặt lại mật khẩu. */
    private static final String ACTIVE = "ACTIVE";

    private final AppUserRepository appUserRepo;
    private final PasswordResetTokenRepository resetRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ResetProperties resetProps;
    private final DisplayNameResolver displayNameResolver;

    public PasswordResetService(
            AppUserRepository appUserRepo,
            PasswordResetTokenRepository resetRepo,
            RefreshTokenRepository refreshTokenRepo,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ResetProperties resetProps,
            DisplayNameResolver displayNameResolver) {
        this.appUserRepo = appUserRepo;
        this.resetRepo = resetRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.resetProps = resetProps;
        this.displayNameResolver = displayNameResolver;
    }

    @Transactional
    public void forgot(String email) {
        // Nếu email tồn tại -> sinh token + gửi mail. Nếu KHÔNG -> vẫn im lặng,
        // tránh để kẻ xấu dò email nào có trong hệ thống (user enumeration).
        appUserRepo.findByEmailAndDeletedFalse(email).ifPresent(user -> {
            Instant now = Instant.now();

            // Tài khoản bị khóa/ngưng thì link đặt lại cũng vô dụng (đăng nhập vẫn bị chặn ở
            // AuthService) -> đừng gửi mail làm gì. Vẫn im lặng để không lộ trạng thái tài khoản.
            if (!ACTIVE.equals(user.getStatus())) {
                log.warn(
                        "Bỏ qua yêu cầu đặt lại mật khẩu: tài khoản uid={} đang ở trạng thái {}",
                        user.getId(),
                        user.getStatus());
                return;
            }

            // Vượt hạn mức -> BỎ QUA, nhưng vẫn im lặng y hệt nhánh "email không tồn tại":
            // controller luôn trả về cùng một câu chung nên kẻ tấn công không phân biệt được
            // "email không có trong hệ thống" với "email có nhưng đang bị chặn".
            if (throttled(user.getId(), now)) {
                return;
            }

            // Mỗi lần phát link mới thì mọi link cũ còn sống bị vô hiệu -> chỉ 1 link dùng được
            // tại một thời điểm (mail cũ bị lộ về sau cũng vô dụng).
            expireActiveTokens(user.getId(), now);

            String rawToken = jwtService.generateOpaqueToken();

            PasswordResetToken prt = new PasswordResetToken();
            prt.setAppUserId(user.getId());
            prt.setTokenHash(jwtService.sha256(rawToken));
            prt.setExpiresAt(now.plus(resetProps.getTokenTtl()));
            resetRepo.save(prt);

            String link = resetProps.getBaseUrl() + "/reset-password?token=" + rawToken;
            emailService.sendPasswordReset(user.getEmail(), displayNameResolver.resolve(user), link);
        });
    }

    /**
     * Chặn quấy rối qua hộp thư (email bombing): rate limit hiện có đếm theo IP nên kẻ tấn
     * công đổi IP là gửi được vô hạn mail đặt lại mật khẩu vào hòm thư nạn nhân. Ở đây đếm
     * theo CHÍNH TÀI KHOẢN bị nhắm tới: phải nghỉ {@code resend-cooldown} giữa 2 lần và tối đa
     * {@code max-per-day} mail trong 24 giờ.
     *
     * <p>Người dùng thật lỡ bấm nhiều lần thì chờ 1 phút; hết hạn mức ngày thì nhờ admin đặt
     * lại mật khẩu hộ (Cài đặt → Tài khoản) — đánh đổi có chủ ý, thà phiền còn hơn để hòm thư
     * của họ bị dội mail.
     */
    private boolean throttled(Integer appUserId, Instant now) {
        long sentToday = resetRepo.countByAppUserIdAndCreatedAtAfter(appUserId, now.minus(DAILY_WINDOW));
        if (sentToday >= resetProps.getMaxPerDay()) {
            log.warn("Bỏ qua yêu cầu đặt lại mật khẩu: tài khoản uid={} đã vượt {} lần/24h", appUserId, sentToday);
            return true;
        }

        Instant lastIssuedAt = resetRepo
                .findTopByAppUserIdOrderByIdDesc(appUserId)
                .map(this::issuedAt)
                .orElse(null);
        if (lastIssuedAt != null
                && lastIssuedAt.plus(resetProps.getResendCooldown()).isAfter(now)) {
            log.warn("Bỏ qua yêu cầu đặt lại mật khẩu: tài khoản uid={} còn trong thời gian nghỉ", appUserId);
            return true;
        }
        return false;
    }

    /**
     * Thời điểm phát phiếu. {@code CreatedAt} do DB tự điền (insertable=false) nên bản ghi vừa
     * lưu trong CÙNG transaction có thể còn null — khi đó suy ngược từ hạn dùng.
     */
    private Instant issuedAt(PasswordResetToken token) {
        return token.getCreatedAt() != null
                ? token.getCreatedAt()
                : token.getExpiresAt().minus(resetProps.getTokenTtl());
    }

    /** Đẩy hạn dùng của mọi phiếu còn sống về hiện tại -> {@code isUsable()} trả về false. */
    private void expireActiveTokens(Integer appUserId, Instant now) {
        List<PasswordResetToken> active = resetRepo.findByAppUserIdAndUsedAtIsNullAndExpiresAtAfter(appUserId, now);
        for (PasswordResetToken token : active) {
            token.setExpiresAt(now);
        }
        resetRepo.saveAll(active);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken prt = resetRepo
                .findByTokenHash(jwtService.sha256(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Token không hợp lệ"));

        // Tách hẳn "đã dùng" khỏi "hết hạn". Gộp một câu thì người dùng không phân biệt nổi
        // "mình đổi xong rồi, cứ đăng nhập đi" với "link này bị link mới hơn thay thế, xin lại
        // cái khác" — hai tình huống cần hai hành động khác nhau. Nói rõ không lộ gì thêm:
        // người đang cầm token thì đã biết token đó có thật.
        if (prt.getUsedAt() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Liên kết này đã được dùng để đổi mật khẩu. Hãy đăng nhập bằng mật khẩu MỚI; "
                            + "nếu quên, xin lại liên kết ở mục Quên mật khẩu.");
        }
        if (!prt.isUsable()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Liên kết đã hết hạn hoặc đã bị thay bằng liên kết mới hơn. "
                            + "Hãy xin lại liên kết ở mục Quên mật khẩu và dùng email mới nhất.");
        }
        // Lọc deleted: tài khoản bị xóa mềm SAU khi phát link thì link cũng phải chết theo,
        // nếu không thì cái link 30 phút đó vẫn hồi sinh được mật khẩu của tài khoản đã gỡ.
        AppUser user = appUserRepo
                .findById(prt.getAppUserId())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Tài khoản không tồn tại"));

        // Bị khóa giữa chừng (admin khóa sau khi link đã gửi) -> không cho đặt lại. Người cầm
        // token đã biết tài khoản có thật rồi nên báo lỗi rõ ràng ở đây không lộ thêm gì.
        if (!ACTIVE.equals(user.getStatus())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa hoặc ngưng hoạt động. Vui lòng liên hệ quản trị.");
        }

        // Đặt lại đúng bằng mật khẩu cũ = không giải quyết được gì (thường người dùng vào đây
        // vì nghi lộ mật khẩu). Chặn cho khớp với luồng đổi mật khẩu ở Cài đặt.
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        prt.setUsedAt(Instant.now()); // đánh dấu token đã dùng -> không tái sử dụng

        // Đổi mật khẩu thường vì NGHI BỊ LỘ tài khoản -> thu hồi mọi refresh token đang
        // sống, đăng xuất mọi thiết bị. Kẻ đã chiếm phiên không thể tiếp tục dùng.
        refreshTokenRepo.revokeAllActiveByAppUserId(user.getId(), Instant.now());
    }
}
