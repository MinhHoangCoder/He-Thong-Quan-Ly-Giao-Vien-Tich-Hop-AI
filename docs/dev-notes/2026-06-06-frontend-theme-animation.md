# Đổi theme (xanh ngọc + xanh lá) & thêm hiệu ứng hover/animation — Frontend

> Ngày 2026-06-06. Đổi bảng màu cho hợp web **giáo dục** và làm giao diện "sống"
> hơn (hover, micro-animation). Mức độ: **vừa phải, chuyên nghiệp**.

## 1. Vì sao trước đây thấy "web tĩnh, không hover"?
Code thật ra ĐÃ có hover, nhưng có 2 lý do hay gây hiểu nhầm:
- **Xem nhầm bản build cũ** trong `frontend/dist/` (file tĩnh đã đóng gói từ lần build
  trước) thay vì chạy `npm run dev`. `dist/` KHÔNG tự cập nhật khi sửa code — phải
  `npm run build` lại. Lúc dev luôn dùng `npm run dev` (có hot-reload, sửa là thấy ngay).
- Hiệu ứng cũ mờ nhạt & chỉ có ở vài chỗ. Lần này làm **đều tay** ở mọi thành phần
  tương tác.

## 2. Đổi màu CHỈ sửa 1 chỗ: `src/assets/main.css`
Toàn bộ màu nằm trong khối `:root { --c-*: ... }` (gọi là **CSS variables / design
tokens**). Các component KHÔNG ghi mã màu cứng nữa mà đọc lại qua `var(--c-primary)`…

→ Muốn đổi cả web sang màu khác: chỉ sửa vài dòng trong `main.css`, không phải đụng
từng file. Đây là lý do nên gom màu về một chỗ ngay từ đầu.

Bảng màu hiện tại (giáo dục):
| Biến | Mã | Dùng cho |
|---|---|---|
| `--c-primary` | `#0d9488` teal | màu chủ đạo: nút, link, icon |
| `--c-accent` | `#22c55e` xanh lá | điểm nhấn (thanh active, trend tăng) |
| `--c-amber` | `#f59e0b` | cảnh báo nhẹ, badge số |
| `--c-sky` | `#0ea5e9` | màu phụ cho biểu đồ |
| `--grad-primary` | teal→green | gradient logo, nút chính, link đang chọn |
| `--a-sidebar` | `#0f2e2c` | nền sidebar (xanh than ngả ngọc) |

> Lưu ý: vài chỗ vẫn còn mã màu "thật" cố ý — ví dụ màu từng series biểu đồ và màu
> từng thẻ thống kê nằm trong **dữ liệu mẫu** ở `DashboardPage.vue` (mỗi mục một màu),
> vì đó là dữ liệu chứ không phải token giao diện.

## 3. Biến chuyển động dùng chung (cũng trong `main.css`)
```css
--ease: cubic-bezier(0.4, 0, 0.2, 1);  /* tiết tấu mượt tiêu chuẩn */
--t-fast: 0.15s var(--ease);           /* hiệu ứng nhanh: nút, icon */
--t:      0.22s var(--ease);           /* mặc định: card, link */
--t-slow: 0.35s var(--ease);           /* chậm: cột biểu đồ */
```
Lý do gom lại: mọi transition cùng "nhịp" → cảm giác đồng bộ, chuyên nghiệp; chỉnh
tốc độ 1 chỗ là cả web đổi theo.

## 4. Các kiểu hover đã dùng (mẹo CSS nên biết)
- **Nhấc thẻ lên**: `transform: translateY(-4px)` + đổi `box-shadow` đậm hơn khi
  `:hover`. (Dùng `transform` chứ không đổi `top/margin` vì transform mượt & không
  làm layout nhảy.) → xem `StatCard.vue`, `.card`, `.teacher`.
- **Gạch chân chạy từ trái** (link "Xem tất cả"): một `::after` cao 2px, mặc định
  `transform: scaleX(0)`, hover thì `scaleX(1)` với `transform-origin: left`.
  Mượt hơn `text-decoration: underline` bật/tắt cứng. → `.card__more`.
- **Vạch màu nhấn bên trái thẻ**: `::before` `scaleY(0)`→`scaleY(1)` khi hover. →
  `StatCard.vue`.
- **Icon nhích/scale**: hover thẻ cha → con scale (`.navlink:hover .navlink__icon`,
  nút "+" xoay 90° trong Dashboard dùng `:deep(svg)` để chọc vào SVG con).
- **Sáng viền khi focus** ô tìm kiếm/select: `:focus-within` + `box-shadow` làm
  "vòng sáng" teal. → `AdminLayout.vue` `.topbar__search`.

## 5. Hiệu ứng ĐẾM SỐ (count-up) — `StatCard.vue`
Khi thẻ thống kê hiện ra, con số nhảy từ 0 → giá trị thật trong ~0.9s.
Cách làm (đọc comment ⭐ trong file):
- `onMounted()` chạy 1 lần khi thẻ gắn vào trang.
- `requestAnimationFrame(tick)`: nhờ trình duyệt gọi lại `tick` ~60 lần/giây để vẽ
  mượt (đừng dùng `setInterval` cho animation — dễ giật).
- `easeOutCubic` (`1 - (1-p)^3`): chạy nhanh lúc đầu, chậm dần về cuối → tự nhiên.
- Chỉ áp dụng khi `value` là **số**; nếu là chuỗi (vd `"96%"`, `"4.6/5"`) thì hiển
  thị nguyên, không đếm.

## 6. Tôn trọng người tắt hiệu ứng (accessibility)
Cuối `main.css` có `@media (prefers-reduced-motion: reduce)` — nếu người dùng bật chế
độ giảm chuyển động trong HĐH, mọi animation/transition gần như tắt. Đây là chuẩn nên
có cho web nghiêm túc.

## 7. File đã đụng tới
```
assets/main.css            ← bảng màu mới + biến chuyển động + nút + keyframe + reduced-motion
layouts/AdminLayout.vue    ← sidebar gradient, link active (thanh trái), focus tìm kiếm, hover icon
layouts/DefaultLayout.vue  ← (không sửa; tự đổi màu nhờ đọc var() — footer/brand teal theo token)
pages/HomePage.vue         ← màu chữ hero hợp teal, thẻ tính năng hover nảy icon
pages/DashboardPage.vue    ← đổi màu dữ liệu mẫu, hover card/link/timeline, nút "+" xoay
components/ui/StatCard.vue  ← count-up + vạch nhấn + hover
components/charts/LineChart.vue ← điểm dữ liệu phình to khi hover
components/charts/MiniBars.vue  ← màu teal, cột sáng lên khi hover
```
