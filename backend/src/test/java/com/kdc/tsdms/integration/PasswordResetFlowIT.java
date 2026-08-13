package com.kdc.tsdms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.PasswordResetToken;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.PasswordResetTokenRepository;
import com.kdc.tsdms.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Luồng ĐẶT LẠI MẬT KHẨU (màn hình "Quên mật khẩu?" TRƯỚC khi đăng nhập) chạy thật:
 * HTTP → SecurityConfig → validator của DTO → service → SQL Server thật.
 *
 * <p>Vì sao cần tới mức này: đọc code chỉ chứng minh được các mảnh CÓ TỒN TẠI, không chứng minh
 * được chúng NỐI vào nhau. Ba thứ chỉ lộ ra khi chạy thật — endpoint có thực sự permitAll (không
 * thì 401 trước khi vào controller), validator có thực sự bắn (regex nằm ở DTO record), và mật
 * khẩu mới có thực sự XUỐNG ĐĨA (service không gọi {@code save()} mà dựa vào dirty checking của
 * JPA, sai transaction là âm thầm không lưu gì).
 *
 * <p>Không đụng tới mail: {@code /reset-password} không gửi mail, nên chỉ cần dựng sẵn bản ghi
 * token trong DB đúng như lúc {@code forgot()} đã phát ra.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PasswordResetFlowIT extends AbstractSqlServerIT {

    private static final String OLD_PASSWORD = "OldPass123";
    private static final String NEW_PASSWORD = "NewPass456";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository appUserRepo;

    @Autowired
    private PasswordResetTokenRepository resetRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    /** Bộ lọc giới hạn tần suất đếm theo IP; MockMvc gọi liên tiếp từ cùng một IP nên tắt đi. */
    @MockitoBean
    private com.kdc.tsdms.security.RateLimitingFilter rateLimitingFilter;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** Tài khoản thật trong DB + một phiếu đặt lại còn sống, đúng như sau khi bấm "Quên mật khẩu?". */
    private String givenUserWithLiveToken(String username) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setEmail(username + "@tsdms.local");
        u.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        u.setStatus("ACTIVE");
        AppUser saved = appUserRepo.save(u);

        String rawToken = "raw-token-" + username;
        PasswordResetToken prt = new PasswordResetToken();
        prt.setAppUserId(saved.getId());
        prt.setTokenHash(jwtService.sha256(rawToken));
        prt.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        resetRepo.save(prt);
        return rawToken;
    }

    private static String body(String token, String newPassword) {
        return "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";
    }

    @Test
    @Order(1)
    void datLaiMatKhau_doiThatTrongDb_vaDangNhapDuocBangMatKhauMoi() throws Exception {
        String rawToken = givenUserWithLiveToken("gv-reset-ok");

        mvc().perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(rawToken, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại."));

        // Điểm mấu chốt: mật khẩu phải THỰC SỰ đổi dưới DB, không chỉ trả 200 cho vui.
        AppUser after = appUserRepo.findByUsernameAndDeletedFalse("gv-reset-ok").orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, after.getPasswordHash()))
                .as("mật khẩu mới phải khớp hash đã lưu")
                .isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, after.getPasswordHash()))
                .as("mật khẩu cũ phải hết tác dụng")
                .isFalse();
        assertThat(resetRepo
                        .findByTokenHash(jwtService.sha256(rawToken))
                        .orElseThrow()
                        .getUsedAt())
                .as("phiếu phải bị đánh dấu đã dùng")
                .isNotNull();
    }

    @Test
    @Order(2)
    void dungLaiLinkCu_thiBiChan() throws Exception {
        String rawToken = givenUserWithLiveToken("gv-reset-lai");
        mvc().perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(rawToken, NEW_PASSWORD)))
                .andExpect(status().isOk());

        mvc().perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(rawToken, "KhacNua789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đã được dùng")));
    }

    @Test
    @Order(3)
    void matKhauYeu_bibatNgayOValidatorCuaBackend() throws Exception {
        String rawToken = givenUserWithLiveToken("gv-reset-yeu");

        mvc().perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(rawToken, "abc")))
                .andExpect(status().isBadRequest());

        // Chặn xong thì phiếu vẫn phải còn nguyên — người dùng gõ hụt một lần không được mất link.
        assertThat(resetRepo
                        .findByTokenHash(jwtService.sha256(rawToken))
                        .orElseThrow()
                        .getUsedAt())
                .as("gõ sai mật khẩu KHÔNG được tiêu mất phiếu")
                .isNull();
    }

    @Test
    @Order(4)
    void tokenBia_thiBaoKhongHopLe() throws Exception {
        mvc().perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("token-khong-co-that", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token không hợp lệ"));
    }
}
