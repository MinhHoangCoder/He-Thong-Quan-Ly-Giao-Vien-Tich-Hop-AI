package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.ChangePasswordRequest;
import com.kdc.tsdms.dto.MessageResponse;
import com.kdc.tsdms.dto.ProfileResponse;
import com.kdc.tsdms.dto.SessionResponse;
import com.kdc.tsdms.dto.TokenRequest;
import com.kdc.tsdms.dto.UpdateProfileRequest;
import com.kdc.tsdms.service.UserSettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CÀI ĐẶT CÁ NHÂN (/api/v1/me/**) — mọi endpoint thao tác trên CHÍNH tài khoản đang
 * đăng nhập (id lấy từ token), nên KHÔNG cần @PreAuthorize theo quyền: chỉ cần đã
 * đăng nhập (SecurityConfig chặn anonymous sẵn).
 *
 * <p>Lưu ý REST: 2 endpoint sessions dùng POST (không phải GET) vì phải nhận refresh
 * token trong BODY — token cấm xuất hiện trên URL (dính access log / lịch sử trình duyệt).
 */
@RestController
@RequestMapping("/api/v1/me")
public class UserSettingsController {

    private final UserSettingsService settingsService;

    public UserSettingsController(UserSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** Hồ sơ cá nhân (định danh + liên hệ + vai trò). */
    @GetMapping("/profile")
    public ProfileResponse profile() {
        return settingsService.getProfile();
    }

    /** Tự sửa thông tin liên hệ (email + SĐT). */
    @PutMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return settingsService.updateProfile(request);
    }

    /** Đổi mật khẩu khi đang đăng nhập (có rate-limit chống dò mật khẩu hiện tại). */
    @PostMapping("/change-password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        settingsService.changePassword(request);
        return new MessageResponse("Đổi mật khẩu thành công. Các thiết bị khác đã bị đăng xuất.");
    }

    /** Liệt kê phiên đăng nhập đang sống (body = refresh token để đánh dấu phiên hiện tại). */
    @PostMapping("/sessions")
    public List<SessionResponse> sessions(@Valid @RequestBody TokenRequest request) {
        return settingsService.listSessions(request.refreshToken());
    }

    /** Đăng xuất 1 thiết bị (thu hồi 1 phiên của chính mình). */
    @DeleteMapping("/sessions/{id}")
    public MessageResponse revokeSession(@PathVariable Long id) {
        settingsService.revokeSession(id);
        return new MessageResponse("Đã đăng xuất thiết bị");
    }

    /** Đăng xuất mọi thiết bị KHÁC (giữ phiên gửi trong body). */
    @PostMapping("/sessions/revoke-others")
    public MessageResponse revokeOthers(@Valid @RequestBody TokenRequest request) {
        int revoked = settingsService.revokeOtherSessions(request.refreshToken());
        return new MessageResponse("Đã đăng xuất " + revoked + " thiết bị khác");
    }
}
