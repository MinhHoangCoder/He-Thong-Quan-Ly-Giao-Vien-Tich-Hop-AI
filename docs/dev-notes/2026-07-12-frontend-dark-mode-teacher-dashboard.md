# Quét lỗi trùng màu dark mode toàn FE + làm lại Dashboard Giáo viên

> Ngày 2026-07-12. Hai việc trong một đợt: (1) rà toàn bộ frontend tìm chỗ **chữ
> trắng trên nền trắng** khi bật giao diện tối và sửa dứt điểm; (2) làm lại trang
> Dashboard Giáo viên theo hướng **chỉ hiển thị số liệu, không so sánh kỳ trước**.

## 1. Lỗi trùng màu dark mode: vì sao xảy ra?

Cơ chế theme của app (xem note `2026-06-06-frontend-theme-animation.md`): mọi màu
nền/chữ/viền nằm trong **design token** ở `src/assets/main.css`; khi bật dark mode,
`stores/ui.js` gắn `data-theme="dark"` lên `<html>` và khối `:root[data-theme='dark']`
**lật** giá trị các token (`--c-surface` trắng → navy đậm, `--c-text` đen → gần trắng…).

Lỗi xuất hiện khi một component **trộn** hai thế giới:

```css
/* SAI: nền cứng + chữ token */
.card  { background: #fff; }          /* dark mode vẫn TRẮNG */
.title { color: var(--c-text); }      /* dark mode lật thành GẦN TRẮNG */
/* → chữ trắng trên nền trắng, không đọc được */
```

Chiều ngược lại cũng lỗi: chữ cứng màu đậm (`#374151`) đặt trên nền token (lật thành
navy) → chữ đen trên nền đen. **Quy tắc: nền/chữ/viền trong component KHÔNG hard-code
— luôn đọc token.**

## 2. Ba mẫu sửa đã áp dụng (dùng cho code mới về sau)

1. **Nền/viền “kết cấu” → token cùng vai trò** (light gần như không đổi):
   - `#fff` → `var(--c-surface)` · `#f7fafc/#f8fafc/#f3f4f6/#fafafa` → `var(--c-surface-2)`
   - kẻ dòng `#f1f5f9/#eee/#f3f4f6` → `var(--c-border-soft)` · viền input `#cbd5e1/#d1d5db` → `var(--c-input-border)`
2. **Nền nhấn màu (cam/xanh nhạt kiểu `#fff7ed`, `#eff6ff`, `#dcfce7`…) → rgba mờ**:
   `rgba(249,115,22,.09)`, `rgba(37,99,235,.12)`, `rgba(34,197,94,.14)`… Nền mờ “hòa”
   được cả 2 theme vì nó pha với màu nền phía dưới.
3. **Chữ màu đậm trên nền mờ → override riêng cho dark** (chữ tối chìm trên nền tối):
   ```css
   .badge-green { background: rgba(34,197,94,.14); color: #166534; }
   :root[data-theme='dark'] .badge-green { color: #4ade80; }
   ```
   Selector này viết được trong `<style scoped>` — Vue chỉ gắn attribute vào vế cuối
   (`.badge-green[data-v-x]`), `:root[...]` phía trước vẫn khớp `<html>`.

Bảng tông chữ dark hay dùng: xanh lá `#4ade80` · đỏ `#f87171` · vàng `#fbbf24` ·
xanh dương `#93c5fd` · chàm `#a5b4fc` · cam `#fdba74`.

## 3. Những chỗ đã sửa (nặng nhất trước)

| File | Lỗi chính |
|---|---|
| `assets/page-common.css` | **Nặng nhất** — style chung của 4 trang Phân công/Chấm công/Bảng lương/Trợ lý AI: card, bảng, toolbar, input, modal đều nền `#fff` cứng → cả 4 trang trắng xóa ở dark |
| `pages/SchedulePage.vue` | Ô lịch tháng, bảng TKB tuần, chip buổi dạy nền trắng/pastel cứng |
| `pages/AiAssistantPage.vue` | Bong bóng chat AI, dải gợi ý, ô nhập nền `#fff` |
| `layouts/PortalShell.vue` | Panel thông báo (chuông topbar) nền `#fff` |
| `pages/SubjectCategoryListPage.vue` | Header bảng con, hover dòng, `code` nền `#f3f4f6` |
| `pages/DashboardPage.vue`, `SchoolDashboardPage.vue` | Nút ghost/state nền trắng; viền hover `#d2e8e2` (sót từ palette teal cũ) → đổi `rgba(249,115,22,.35)`; chấm timeline viền `#fff` → `var(--c-surface)` |
| `pages/TeacherListPage.vue` | Dòng được chọn nền `#fff5f5`; chấm avatar viền trắng |
| `pages/TeacherLesson*`, `LessonFormPage`, `LessonListPage` | Nút ghost `#e2e8f0`, badge pastel, viền input cứng |
| `pages/LoginPage.vue`, `ResetPasswordPage.vue`, `PayrollPage.vue`, `AssignmentPage.vue` | msg lỗi/ok, banner, label `#374151`, select viền cứng |
| `components/ui/StatCard.vue`, `charts/LineChart.vue` | Chữ trend xanh/đỏ đậm thiếu override dark; điểm chấm biểu đồ stroke `#fff` → `var(--c-surface)` |

Badge pastel (nền sáng + chữ đậm hard-code cả cặp) không “tàng hình” nhưng chói/lạc
tông ở dark → cũng chuyển hết sang mẫu rgba + override cho đồng bộ với
`LessonListPage`/`SettingsPage` (2 trang đã làm đúng từ trước, lấy làm chuẩn).

## 4. Dashboard Giáo viên làm lại (`pages/TeacherDashboardPage.vue`)

Yêu cầu: chỉ số liệu (bỏ % tăng giảm, bỏ hint xám), bỏ khối Thông báo (trùng chuông
topbar) + 3 thẻ mini (Giờ dạy 7 ngày / Tỉ lệ đúng giờ / Buổi sắp tới), lấp chỗ trống
bằng lịch dạy.

Bố cục mới:
1. **4 thẻ số**: Buổi dạy hôm nay · Buổi dạy tuần này · Giờ công tháng này · Điểm đánh giá
   (StatCard không truyền `hint`/`trend` → tự ẩn dòng meta).
2. **Lịch dạy hôm nay** (timeline) + **Số liệu giảng dạy** (Trường/Lớp/Môn/Buổi đã dạy).
3. **Lịch dạy tuần này**: lưới 7 cột T2→CN, chip buổi dạy màu theo môn, cột hôm nay
   viền cam; cuộn ngang trên màn hình hẹp (`overflow-x`).

Điểm kỹ thuật đáng nhớ — **một nguồn dữ liệu, mọi con số là `computed`**:
- `weekSchedule` (mock) là nguồn duy nhất; “buổi hôm nay” = `weekSchedule[todayIdx]`,
  “buổi tuần” = tổng `sessions.length`, số trường/lớp/môn đếm bằng
  `new Set(...flatMap(...)).size` → số không bao giờ lệch nhau, sau này chỉ cần thay
  mock bằng API.
- `todayIdx = (new Date().getDay() + 6) % 7` vì `getDay()` trả 0 = Chủ nhật.
- Chip buổi dạy tô màu inline `color + '17'` (~9% opacity) — màu theo **dữ liệu** môn
  học nên không đưa vào token, nhưng nền mờ vẫn hợp cả 2 theme (mẫu số 2 ở trên).

## 5. Sửa kèm: sidebar sáng cam hàng loạt

Các mục menu chưa có trang dùng `to: '#'` — vue-router coi `#` là **trang hiện tại**
nên gắn class active cho tất cả. Sửa ở `PortalShell.vue`:
`:active-class="item.to === '#' ? '' : 'is-active'"`.

## 6. Cách tự kiểm tra lại

Backend + `npm run dev` đang chạy, rồi:
```bash
cd frontend && node shot-dark.mjs
```
Script đăng nhập bằng tài khoản demo (`teacher`, `admin` / mật khẩu demo), đặt theme
qua localStorage `tsdms.ui` **trước khi** trang load, chụp các trang chính cả 2 theme
vào `claude-context/*.png` (đã gitignore). Muốn soát thủ công: bật dark mode rồi rà
những chỗ nghi ngờ bằng grep mã màu cứng:
`(color|background|border)[^;]*#[0-9a-f]{3,8}` trong `src/**/*.vue`.
