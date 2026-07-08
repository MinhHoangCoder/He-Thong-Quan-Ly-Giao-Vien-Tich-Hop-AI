# FE — Dark mode & bộ tiện ích trang Cài đặt

> Ngày viết: 2026-07-07 · Phạm vi: chỉ frontend, KHÔNG đụng backend/API.

## Có gì mới

1. **Theme Sáng / Tối / Theo hệ thống** — chọn trong Cài đặt → tab "Giao diện", hoặc bấm
   nút mặt trăng/mặt trời trên topbar (đảo nhanh sáng↔tối).
2. **Cỡ chữ** (Mặc định / Lớn) và **Giảm hiệu ứng chuyển động** — cùng tab Giao diện.
3. **Trang Cài đặt bố cục 2 cột**: cột trái là 4 tab nội dung, cột phải là "rail" tiện ích
   luôn hiển thị — *Quyền của tôi* (đọc từ JWT trong store, không tốn API), *Tài khoản
   trên máy này* (chuyển nhanh multi-account), *Mẹo bảo mật*.
4. Tiện ích nhỏ: nút **sao chép** username/email (icon đổi thành ✓ khi chép xong), nút
   **mắt hiện/ẩn** cho 3 ô mật khẩu, dòng "**còn N ngày**" cạnh hạn của mỗi phiên đăng nhập.

## Kiến trúc theme — 1 sơ đồ là hiểu

```
Người dùng chọn "Tối"
        │
        ▼
stores/ui.js  ──lưu──▶  localStorage 'tsdms.ui'   (mỗi máy nhớ riêng)
        │
        ▼  (watchEffect)
<html data-theme="dark">
        │
        ▼
main.css:  :root[data-theme='dark'] { --c-surface: #111f33; --c-text: #e7edf6; ... }
        │
        ▼
Mọi component viết  background: var(--c-surface)  →  TỰ đổi màu, không sửa gì thêm
```

Điểm mấu chốt: **component không biết "dark mode" là gì**. Nó chỉ biết "tô nền bằng biến
`--c-surface`". Ai định nghĩa biến đó (khối `:root` sáng hay khối `[data-theme='dark']`)
là việc của `main.css`. Đây là lý do quy ước **CẤM hard-code màu nền/chữ** trong component
— một chỗ `background: #fff` cứng là chỗ đó thành "đèn pha" giữa màn hình tối.

### Các file tham gia

| File | Vai trò |
|---|---|
| `src/stores/ui.js` | Nguồn sự thật: theme/fontSize/motion; áp `data-*` lên `<html>`; lưu localStorage |
| `src/assets/main.css` | Token sáng (`:root`) + token tối (`:root[data-theme='dark']`) + cỡ chữ + giảm hiệu ứng |
| `index.html` | Script 5 dòng áp theme TRƯỚC khi Vue chạy (chống chớp trắng) |
| `src/App.vue` | Gọi `useUiStore()` lúc khởi động để store hoạt động ngay từ trang đầu |
| `src/layouts/PortalShell.vue` | Nút toggle nhanh trên topbar |
| `src/pages/SettingsPage.vue` | Tab Giao diện (UI chọn theme/cỡ chữ/hiệu ứng) + rail tiện ích |

### Token mới thêm (dùng từ giờ trở đi)

- `--c-surface` — nền card / topbar / modal / ô nhập (thay cho `#fff`).
- `--c-surface-2` — nền phụ trong card: hover, nền icon, khối nhấn (thay `#f1f5f9`, `#f8fafc`).
- `--c-border-soft` — kẻ dòng mảnh trong card (thay `#f1f5f9` khi làm viền).
- `--c-input-border` — viền ô nhập (thay `#d5dde8`, `#d1d5db`).

Đã chạy một đợt thay hàng loạt trên ~15 file `.vue` (sed): `background: #fff` →
`var(--c-surface)`, nền xám nhạt → `var(--c-surface-2)`, viền xám → token viền. Màu
thương hiệu (cam/xanh lam/gradient) GIỮ NGUYÊN ở cả hai theme để nhận diện không đổi.

## Từng quyết định kỹ thuật & lý do

### 1. Vì sao lưu `tsdms.ui` tách khỏi `tsdms.session`?

Vòng đời khác nhau: đăng xuất phải xóa phiên nhưng **không được** reset theme người dùng
đã chọn. Gộp chung một key là dính nhau.

### 2. Script chống "chớp trắng" trong `index.html` để làm gì?

Thứ tự tải trang: HTML → CSS → **Vue chạy** (mất vài trăm ms). Nếu đợi Vue chạy rồi mới
gắn `data-theme="dark"`, người dùng theme tối sẽ thấy màn hình **lóe trắng** mỗi lần F5.
Script inline trong `<head>` đọc localStorage và gắn `data-theme` NGAY khi HTML được parse,
trước cả khi CSS kịp vẽ — nên không lóe. Lưu ý: logic script này phải khớp `stores/ui.js`
(nguồn sự thật); script chỉ là "bản áp sớm".

### 3. "Theo hệ thống" hoạt động thế nào?

`window.matchMedia('(prefers-color-scheme: dark)')` hỏi hệ điều hành đang sáng hay tối,
và có sự kiện `change` — đổi theme Windows là web đổi theo **ngay lập tức**, không cần F5
(xem `systemDark` trong `ui.js`).

### 4. Cỡ chữ "Lớn" chỉ là 1 dòng CSS?

`:root[data-fontsize='large'] { font-size: 17.5px }`. Vì đa số kích thước trong app khai
bằng **rem** (đơn vị tính theo font gốc của `<html>`), phóng font gốc là cả app to theo
tỷ lệ. Bài học: dùng rem cho font-size thì sau này thêm accessibility rất rẻ.

### 5. Override dark trong `<style scoped>` có chạy không?

Có. Vue scoped chỉ gắn attribute `[data-v-xxx]` vào **selector cuối cùng**, nên
`:root[data-theme='dark'] .chip--ok { ... }` biên dịch thành
`:root[data-theme='dark'] .chip--ok[data-v-xxx]` — vẫn khớp. SettingsPage dùng kỹ thuật
này để chỉnh màu chữ pastel chip cho đủ tương phản trên nền tối.

### 6. Chỗ nào CỐ TÌNH hard-code màu?

Ô preview 3 theme trong tab Giao diện (`.tp--light`, `.tp--dark`...): ô "Sáng" phải luôn
trông sáng **bất kể theme đang bật** — nó là hình đại diện, không phải bề mặt UI. Đừng
"sửa giúp" thành token.

### 7. `color-scheme: light/dark` là gì?

Báo trình duyệt vẽ các thứ NGOÀI tầm CSS của ta (scrollbar, ô select mặc định, autofill)
theo đúng tông — thiếu dòng này dark mode sẽ có scrollbar trắng lạc quẻ.

## Quy ước cho thành viên nhóm (QUAN TRỌNG)

Khi viết component mới:

1. Nền card/panel/input → `var(--c-surface)`; nền phụ/hover → `var(--c-surface-2)`.
2. Chữ → `var(--c-text)` / `var(--c-text-muted)`; viền → `var(--c-border)` /
   `var(--c-border-soft)` / `var(--c-input-border)`.
3. **Không bao giờ** viết `#fff`, `#f1f5f9`, `#0f172a`... trực tiếp cho nền/chữ/viền.
4. Màu ngữ nghĩa nhỏ (chip xanh/đỏ/vàng) dùng nền `rgba(...)` mờ thay vì pastel đặc —
   tự hòa với cả hai theme; nếu chữ thiếu tương phản trên nền tối thì thêm override
   `:root[data-theme='dark'] .lop-cua-ban { color: ... }` ngay trong style scoped.
5. Muốn thử nhanh dark mode khi dev: bấm nút mặt trăng trên topbar, hoặc đổi theme
   Windows nếu đang để "Theo hệ thống".

## Đã kiểm chứng

- `npm run build` và `npm run lint` sạch.
- Chưa soi mắt từng trang ở dark mode — khi chạy app, đảo theme rồi lướt qua các trang
  chính (dashboard, danh sách GV, bài học, cài đặt); thấy mảng sáng lạc lõng tức là còn
  màu hard-code sót — sửa theo quy ước ở trên.
