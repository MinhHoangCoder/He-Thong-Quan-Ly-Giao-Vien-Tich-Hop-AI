# Gia cố bảo mật luồng Auth — bản vá tạm thời (trước khi lên Cookie + Bucket4j/Redis)

> Ngày: 2026-06-10 · Phần: fullstack (chủ yếu backend) · Nhánh: `feature/auth-jwt`

## 1. Bối cảnh

Rà soát bảo mật 4 luồng: **đăng nhập / đăng ký / đăng xuất / quên mật khẩu**. Nền tảng đã tốt
(BCrypt, JWT ngắn hạn, refresh token opaque lưu hash + rotation, chống enumeration, rate limit),
nhưng còn vài lỗ hổng thật sự. Hai nâng cấp lớn được **chủ động hoãn lại sau tốt nghiệp**:

| Nâng cấp lớn (làm sau) | Thay bằng bản vá tạm thời (đã làm) |
|---|---|
| Refresh token chuyển từ localStorage → cookie `HttpOnly` | **Reuse detection**: token đã rotate mà bị dùng lại → thu hồi TOÀN BỘ phiên của user |
| Rate limit in-memory → **Bucket4j + Redis** | **Chặn giả mạo `X-Forwarded-For`** bằng cờ cấu hình (mặc định không tin header này) |

Lý do hoãn: cookie kéo theo bật lại CSRF + sửa FE đáng kể; Redis thêm một hạ tầng phải cài/giải thích
khi bảo vệ. Bản vá tạm thời bịt được phần lớn rủi ro với chi phí nhỏ.

## 2. Các thay đổi đã làm

### 2.1. Login: kiểm tra MẬT KHẨU trước, STATUS sau — `AuthService.login()`

**Lỗ hổng:** thứ tự cũ là *tìm user → kiểm status → kiểm mật khẩu*. Kẻ tấn công gửi username bất kỳ
kèm mật khẩu rác: nếu nhận **403 "Tài khoản đang bị khóa"** thì biết chắc username tồn tại — phá vỡ
nguyên tắc "một thông báo chung" mà code đã cố giữ (user enumeration qua mã lỗi).

**Sửa:** đảo thứ tự — sai mật khẩu thì luôn 401 "Sai tài khoản hoặc mật khẩu" như user không tồn tại;
chỉ ai có **mật khẩu đúng** mới biết tài khoản bị khóa (403). Test mới:
`login_lockedAccountWrongPassword_throws401NotForbidden`.

### 2.2. Refresh token reuse detection — `AuthService.refresh()`

**Kịch bản tấn công:** refresh token nằm ở localStorage → XSS có thể đánh cắp. Rotation hiện tại đã
giúp: token cũ bị thu hồi mỗi lần refresh. Nhưng khi một token **đã thu hồi** lại được gửi lên
(replay), trước đây chỉ trả 401 — trong khi đây chính là **tín hiệu token bị đánh cắp** (hoặc kẻ trộm
dùng bản cũ, hoặc chủ thật dùng bản cũ vì kẻ trộm đã rotate mất).

**Sửa:** không phân biệt được ai là ai → **thu hồi mọi refresh token còn sống của user đó**, buộc
đăng nhập lại bằng mật khẩu trên mọi thiết bị. Kẻ trộm bị cắt phiên ngay khi chủ thật quay lại.

```java
if (stored.getRevokedAt() != null) {                      // token đã rotate mà còn dùng lại
    refreshTokenRepo.revokeAllActiveByAppUserId(stored.getAppUserId(), Instant.now());
    throw new ApiException(HttpStatus.UNAUTHORIZED, "...");
}
```

**Bẫy transaction quan trọng (đáng nhớ):** `refresh()` có `@Transactional`, mà `ApiException` là
`RuntimeException` → mặc định Spring **rollback**, tức lệnh revoke phía trên sẽ bị... hủy ngay sau khi
chạy! Phải khai báo `@Transactional(noRollbackFor = ApiException.class)`. An toàn vì mọi nhánh ném
`ApiException` khác trong `refresh()` đều xảy ra **trước khi ghi gì vào DB**.

Repository thêm bulk update (1 câu UPDATE thay vì load từng entity):

```java
@Modifying
@Query("update RefreshToken rt set rt.revokedAt = :now "
        + "where rt.appUserId = :userId and rt.revokedAt is null")
int revokeAllActiveByAppUserId(@Param("userId") Integer userId, @Param("now") Instant now);
```

### 2.3. Reset mật khẩu → đăng xuất mọi thiết bị — `PasswordResetService.reset()`

Người ta đổi mật khẩu thường vì **nghi bị lộ tài khoản**. Trước đây các refresh token cũ vẫn sống
tới 7 ngày → kẻ đã chiếm phiên cứ thế dùng tiếp dù nạn nhân đã đổi mật khẩu. Giờ `reset()` gọi
`revokeAllActiveByAppUserId(...)` sau khi đổi hash — mọi phiên cũ chết ngay.

### 2.4. Rate limit: chặn giả mạo `X-Forwarded-For`

**Lỗ hổng:** filter cũ tin `X-Forwarded-For` vô điều kiện. Header này do **client tự đặt được**
(`curl -H "X-Forwarded-For: 1.2.3.{i}"`) → mỗi request một "IP" giả → mỗi lần một bucket mới →
rate limit **vô tác dụng hoàn toàn**, kèm phình bộ nhớ map bucket.

**Sửa:** thêm `tsdms.rate-limit.trust-forwarded-header` (mặc định **false** → dùng `remoteAddr`,
là IP của kết nối TCP thật, không giả được). Chỉ bật `true` khi deploy sau reverse-proxy tin cậy
(nginx) — vì lúc đó `remoteAddr` luôn là IP của proxy, phải đọc XFF do proxy gắn.

Tiện thể thêm `/api/v1/auth/reset-password` vào danh sách giới hạn (chống dò token reset — dù token
256-bit gần như không thể đoán, đây là defense-in-depth rẻ tiền).

### 2.5. Chính sách mật khẩu mạnh (BE là chốt chặn, FE báo lỗi sớm)

- BE — `RegisterRequest.password` & `ResetPasswordRequest.newPassword`:
  `@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$")` — 8–72 ký tự, có hoa + thường + số.
  - `(?=...)` là **lookahead**: "nhìn trước" xem chuỗi có chứa loại ký tự đó không, không tiêu thụ ký tự.
  - **Max 72** vì BCrypt chỉ băm 72 byte đầu (dài hơn bị cắt âm thầm hoặc bị reject tùy phiên bản).
- FE — helper dùng chung `frontend/src/utils/password.js` (`isStrongPassword`, `PASSWORD_HINT`),
  dùng ở `RegisterUserPage.vue` và `ResetPasswordPage.vue`. **Regex FE phải khớp BE** — nếu sau này
  đổi chính sách, đổi CẢ HAI nơi.
- Mật khẩu demo `Tsdms@123` vẫn thỏa chính sách mới.

## 3. Lộ trình nâng cấp SAU tốt nghiệp (đã hoãn có chủ đích)

### 3.1. Refresh token → cookie `HttpOnly` (xóa hẳn rủi ro XSS đọc token)

1. BE `/login`, `/refresh`: set cookie `refreshToken` với `HttpOnly; Secure; SameSite=Strict;
   Path=/api/v1/auth` (path hẹp → cookie chỉ gửi tới nhóm endpoint auth, không đính kèm mọi request).
2. Bỏ `refreshToken` khỏi body `AuthResponse`; `/refresh` & `/logout` đọc token từ cookie thay vì body.
3. **Bật lại CSRF** cho 2 endpoint đó (hiện tắt vì "không dùng cookie" — chuyển sang cookie là điều
   kiện đó hết đúng), hoặc dựa vào `SameSite=Strict` + kiểm tra header `Origin`.
4. FE: axios thêm `withCredentials: true`; store bỏ `refreshToken` khỏi localStorage (chỉ còn `user`
   để dựng menu); khi F5 gọi `/refresh` "mò" — có cookie thì có phiên, 401 thì coi như chưa đăng nhập.
5. Bonus sửa luôn lỗi nhiều tab: cookie dùng chung mọi tab, không còn cảnh tab B giữ token cũ đã rotate.

### 3.2. Rate limit → Bucket4j + Redis (đa node + thuật toán đã kiểm chứng)

1. Dependency: `bucket4j-core` + `bucket4j-redis` (LettuceBasedProxyManager — Lettuce có sẵn trong
   `spring-boot-starter-data-redis`).
2. Thay `TokenBucket` tự viết bằng `BucketConfiguration` (capacity 5, refill 5/phút) + `ProxyManager`
   lấy bucket theo key `path|ip` từ Redis → mọi node chia sẻ cùng bộ đếm, restart không mất state.
3. Giữ nguyên `RateLimitingFilter` (chỉ đổi ruột chỗ lấy bucket); cờ `trust-forwarded-header` vẫn dùng.
4. Cần Redis trong hạ tầng (docker compose một dòng) — lý do chính khiến khoản này hoãn lại.

## 4. Đã cân nhắc nhưng KHÔNG làm trong đồ án (kèm lý do)

- **Gửi mail async (`@Async`)** để chống đo timing ở `/forgot-password` (email tồn tại → chờ SMTP →
  chậm hơn thấy rõ): lỗ hổng có thật nhưng khai thác cần đo đạc thống kê + đã bị rate limit 5 req/phút
  chặn họng. Không cấp thiết cho đồ án; nếu làm thì chỉ là `@EnableAsync` + `@Async` trên
  `EmailService.sendPasswordReset`.
- **Chống timing ở login** (user không tồn tại → bỏ qua BCrypt → trả lời nhanh hơn): fix chuẩn là
  `matches()` với một hash giả cố định khi không tìm thấy user. Cùng lý do trên — ghi nhận, chưa làm.
- **Khóa theo username / lockout lũy tiến**: rate limit hiện theo IP; tấn công phân tán nhiều IP vẫn
  dồn được 1 tài khoản. Ngoài phạm vi đồ án.
- **Job dọn bảng `RefreshToken`** (bản ghi revoked/expired tích tụ): vệ sinh dữ liệu, không phải lỗ hổng.

## 5. Kiểm chứng

- `mvnw spotless:apply test` → **12/12 pass** (2 test mới: locked + sai mật khẩu phải 401;
  replay token đã thu hồi → gọi `revokeAllActiveByAppUserId`, không cấp token mới).
- `npm run lint` (frontend) sạch.
- Thử tay nhanh:
  - `curl -X POST :8080/api/v1/auth/login -H "X-Forwarded-For: 9.9.9.9" ...` lặp >5 lần/phút → vẫn 429
    (trước đây đổi XFF mỗi lần là né được).
  - Login 2 lần lấy 2 refresh token; refresh bằng token A (rotate), refresh lại bằng token A lần nữa
    → 401 **và** token B cũng chết (reuse detection).

## 6. File đã sửa

| File | Thay đổi |
|---|---|
| `service/AuthService.java` | Đảo thứ tự password/status; reuse detection + `noRollbackFor` |
| `service/PasswordResetService.java` | Revoke mọi phiên sau reset |
| `repository/RefreshTokenRepository.java` | `revokeAllActiveByAppUserId` (bulk update) |
| `security/RateLimitingFilter.java` | Cờ tin XFF; thêm `/reset-password` vào danh sách giới hạn |
| `security/RateLimitProperties.java` | Property `trustForwardedHeader` (mặc định false) |
| `dto/RegisterRequest.java`, `dto/ResetPasswordRequest.java` | `@Pattern` mật khẩu mạnh |
| `resources/application.yaml` | `trust-forwarded-header: false` |
| `test/.../AuthServiceTest.java` | Sửa 1 test + thêm 2 test |
| `frontend/src/utils/password.js` (mới) | Helper validate mật khẩu dùng chung |
| `frontend/src/pages/RegisterUserPage.vue`, `ResetPasswordPage.vue` | Dùng helper, cập nhật nhãn/lỗi |
