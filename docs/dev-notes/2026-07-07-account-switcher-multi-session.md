# Account switcher — đăng nhập nhiều tài khoản, chuyển nhanh không cần logout

> Ngày: 2026-07-07 · Phạm vi: FE thuần (store + router + layout), KHÔNG đổi backend.
> Vì sao có: dev & demo phải nhảy vai trò liên tục (admin → staff → teacher → school);
> logout/login mỗi lần rất mất thời gian. Pattern giống GitHub/Google account switcher.

## 1. Kiến trúc store (`stores/auth.js`)

Trước: store giữ đúng 1 phiên (`accessToken`/`refreshToken`/`user` là `ref`).
Sau: giữ **danh sách phiên**, nhưng phần còn lại của app KHÔNG phải sửa:

```
accounts        = [{ refreshToken, user }]   // login mới nhất đứng đầu
activeUsername  = tài khoản đang dùng
ramTokens       = { username -> accessToken }  // CHỈ trong RAM như trước
```

`accessToken` / `refreshToken` / `user` đổi từ `ref` thành **computed trỏ vào tài khoản
active** — đây là điểm mấu chốt: "hợp đồng" đọc của http.js / router guard / App.vue /
mọi trang giữ nguyên, chỉ nguồn dữ liệu phía sau đổi. `setSession` thành **upsert theo
username**: login tài khoản mới → thêm vào danh sách + thành active (tài khoản cũ ở lại);
refresh/cập nhật hồ sơ tài khoản đang có → chỉ thay token/snapshot của nó.

localStorage (`tsdms.session`) đổi shape sang `{ accounts, activeUsername }` — lúc khôi
phục vẫn đọc được shape cũ `{ refreshToken, user }` nên người đang đăng nhập không bị
văng khi cập nhật code.

## 2. Các luồng

| Luồng | Cách chạy |
|---|---|
| **Thêm tài khoản** | dropdown avatar → "Thêm tài khoản" → `/login?add=1`. Guard router vốn đá người đã đăng nhập khỏi /login; thêm ngoại lệ `?add=1`. Login OK → `setSession` upsert, phiên cũ giữ nguyên. |
| **Chuyển tài khoản** | dropdown avatar liệt kê `auth.accounts` (avatar chữ cái đầu + tên đậm + `@username · role`, ✓ ở tài khoản đang dùng). Bấm → `switchAccount()` + **điều hướng cứng** (`window.location.assign`) về `roleHome` của tài khoản đích. |
| **Đăng xuất** | chỉ đăng xuất tài khoản ĐANG DÙNG (server thu hồi đúng refresh token đó) → `dropActiveAccount()` → còn tài khoản khác thì rơi về "nhà" của nó (giống Google), hết mới về /login. |
| **Refresh token chết** (401 refresh fail / F5 fail) | chỉ gỡ tài khoản đó (`dropActiveAccount`), KHÔNG xóa cả chùm; reload về /login để guard tự phân luồng. |

## 3. Hai quyết định đáng giải thích (hội đồng có thể hỏi)

1. **Chuyển tài khoản không gọi refresh ngay** — access token của tài khoản đích có thể
   không còn trong RAM (mất khi F5) hoặc hết hạn (TTL 15'). Không sao: request đầu tiên
   trả 401 → interceptor trong `http.js` tự lấy refresh token (giờ là computed → tự trỏ
   đúng tài khoản active mới) xin access token mới rồi phát lại request. Đây y hệt luồng
   F5 đã có, không thêm code; và `/auth/refresh` không nằm trong rate-limit nên chuyển
   qua lại thoải mái.
2. **Điều hướng cứng (reload) thay vì `router.push`** — các trang đã mở đang giữ dữ liệu
   của tài khoản cũ trong state (dashboard, danh sách…). Reload đảm bảo mọi thứ nạp lại
   theo tài khoản mới, đổi lấy ~1s trắng màn hình. Đơn giản > mượt trong ca này.

## 4. Trade-off bảo mật (có ý thức, ghi để bảo vệ được)

localStorage giờ giữ refresh token của **tất cả** tài khoản đang đăng nhập → nếu dính XSS
thì kẻ tấn công trộm được cả chùm thay vì một. Chấp nhận cho đồ án vì: (1) access token
vẫn chỉ ở RAM, (2) refresh có rotation + reuse-detection phía backend, (3) tab "Thiết bị
đăng nhập" cho từng tài khoản thấy và thu hồi phiên lạ. Muốn nâng chuẩn production thì
chuyển refresh token sang HttpOnly cookie — ngoài phạm vi hiện tại.

## 5. File đụng tới

- `stores/auth.js` — viết lại multi-account (mục 1).
- `api/http.js`, `App.vue`, `composables/useLogout.js` — refresh-fail/logout chỉ gỡ 1 tài khoản.
- `router/index.js` — ngoại lệ `?add=1` ở guard.
- `pages/LoginPage.vue` — banner chế độ thêm tài khoản (+ nút quay lại).
- `layouts/PortalShell.vue` — khối "Chuyển tài khoản" trong dropdown avatar.
- `components/ui/SvgIcon.vue` — thêm icon `check`.
