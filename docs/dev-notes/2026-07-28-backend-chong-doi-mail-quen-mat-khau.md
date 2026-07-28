# Chống dội mail ở luồng Quên mật khẩu (per-account throttle)

**Ngày:** 2026-07-28
**Phạm vi:** backend — `PasswordResetService`, `PasswordResetTokenRepository`, `ResetProperties`, `application.yaml`
**Loại:** vá bảo mật (lạm dụng chức năng), không đổi API

---

## 1. Vấn đề

Endpoint `POST /api/v1/auth/forgot-password` là endpoint **công khai** (nằm trong danh sách
`permitAll` của `SecurityConfig`). Trước bản vá này, ai gõ được email của người khác là gửi
được **không giới hạn** mail "đặt lại mật khẩu" vào hòm thư của họ.

Có `RateLimitingFilter` rồi thì sao vẫn thủng? Vì bộ đếm của nó gắn khóa theo
`path|IP` (`RateLimitingFilter` + `TokenBucket`, ~5 request/phút/IP):

| Bộ đếm | Đếm theo | Kẻ tấn công né bằng cách |
| --- | --- | --- |
| `RateLimitingFilter` (đã có) | **IP** gọi API | Đổi IP / dùng proxy, mỗi IP vẫn dưới ngưỡng |
| Bản vá này | **Tài khoản bị nhắm tới** | Không né được — nạn nhân chỉ có một |

Đây là kiểu quấy rối gọi là *email bombing*: không chiếm được tài khoản, nhưng làm hòm thư
nạn nhân ngập mail và khiến họ mất niềm tin vào hệ thống. Nhân đây vá luôn chuyện **nhiều
link reset cùng sống một lúc**: mỗi lần bấm "Quên mật khẩu" lại sinh thêm một phiếu mới mà
phiếu cũ vẫn dùng được tới 30 phút.

## 2. Cách vá

Thêm hai mốc chặn, đếm theo **AppUserId** (bảng `PasswordResetToken` vốn đã có `AppUserId`
và `CreatedAt` → **không cần migration**):

```yaml
tsdms:
  reset:
    resend-cooldown: 1m   # nghỉ 1 phút giữa 2 mail của cùng tài khoản
    max-per-day: 5        # tối đa 5 mail / tài khoản / 24 giờ
```

`PasswordResetService.forgot()` giờ chạy theo thứ tự:

1. Tìm user theo email — không có thì **im lặng** (giữ nguyên chống dò email cũ).
2. `throttled()` — vượt hạn mức ngày hoặc còn trong thời gian nghỉ thì **im lặng** luôn.
3. `expireActiveTokens()` — đẩy hạn dùng của mọi phiếu còn sống về `now`.
4. Sinh phiếu mới + gửi mail.

## 3. Điểm dễ làm sai (đọc kỹ nếu sau này sửa)

**a. Bị chặn thì phải im lặng, KHÔNG được trả lỗi 429.**
Trả "bạn gửi quá nhiều lần" tức là gián tiếp xác nhận *email này có trong hệ thống* —
đúng bằng việc mở lại lỗ dò email (user enumeration) mà `forgot()` cố tình tránh. Cả 3 nhánh
(email lạ / bị chặn / gửi thành công) đều trả **cùng một câu** của `AuthController`.

**b. `CreatedAt` có thể null ngay sau khi `save()`.**
Entity khai `@Column(name = "CreatedAt", insertable = false, updatable = false)` vì cột này
do DB tự điền (`DEFAULT SYSUTCDATETIME()`). Bản ghi vừa lưu trong **cùng transaction** chưa
được đọc lại từ DB nên trường này còn null → `issuedAt()` suy ngược từ `ExpiresAt - tokenTtl`
khi gặp null. Đừng bỏ nhánh fallback đó.

**c. Hết hạn mức ngày thì người dùng thật làm sao?**
Nhờ admin đặt lại mật khẩu hộ (Cài đặt → Tài khoản). Đây là đánh đổi có chủ ý: 5 lần/ngày
đã quá đủ cho người dùng thật, còn kẻ quấy rối thì bị chặn cứng.

**d. Vô hiệu link cũ = mail cũ bấm vào sẽ báo hết hạn.**
Nếu ai đó xin link 2 lần rồi bấm vào mail **thứ nhất**, họ sẽ thấy "Token đã được dùng hoặc
đã hết hạn". Đúng như thiết kế — luôn dùng mail mới nhất.

## 4. Kiểm chứng

`PasswordResetServiceTest` (Mockito, không cần DB) — 10 test, thêm 3 test mới:

- `forgot_withinCooldown_silentlySkipsSecondMail`
- `forgot_dailyLimitReached_silentlySkips`
- `forgot_newLink_expiresPreviousActiveLinks`

```bash
cd backend && ./mvnw test        # 55/55 xanh
```

> **Bẫy khi viết test:** `@Mock ResetProperties` trả `0` cho `getMaxPerDay()` nếu không stub
> → `0 >= 0` thành true → mọi test `forgot()` bị chặn và im lặng, trông như code hỏng. Test
> nào đi tới nhánh gửi mail đều phải `when(resetProps.getMaxPerDay()).thenReturn(5)`.
