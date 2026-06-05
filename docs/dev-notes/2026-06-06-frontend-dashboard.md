# Layout + Dashboard quản trị (Frontend) — Bản đồ

> Ngày 2026-06-06. **Giải thích chi tiết nằm INLINE ngay trong code** (comment ⭐).
> File này chỉ là bản đồ + phần backend (chưa có code để đặt comment).

## Đọc giải thích ở đâu (inline trong file)
| Chủ đề | File có comment ⭐ |
|---|---|
| Luồng "một trang hiển thị thế nào" (xuyên 4 file) | `src/App.vue` (đầu file) |
| Lazy-load & `meta.layout` | `src/router/index.js` (route /dashboard) |
| Toán vẽ đường cong mượt (Catmull-Rom→Bézier), quy đổi số liệu→pixel | `src/components/charts/LineChart.vue` |

## Bản đồ file
```
App.vue                       ← chọn layout bọc ngoài (điều phối)
router/index.js               ← URL → trang + nhãn layout
layouts/AdminLayout.vue       ← sidebar + topbar, có <slot/> nhét trang vào
pages/DashboardPage.vue       ← nội dung dashboard (đang dùng DỮ LIỆU MẪU cứng)
components/ui/SvgIcon.vue      ← icon SVG theo tên
components/ui/StatCard.vue     ← thẻ số liệu
components/charts/LineChart.vue← biểu đồ đường tự vẽ SVG
components/charts/MiniBars.vue ← sparkline cột
assets/main.css               ← biến màu (CSS variables)
```

## ⏳ Khi nối backend (chưa code — note để dành)
Dashboard đang dùng dữ liệu mẫu cứng trong `DashboardPage.vue`. Khi nối API:
- BE nên có 1 endpoint gộp `GET /api/v1/dashboard/summary` trả JSON: số liệu thẻ thống
  kê + mảng số liệu biểu đồ theo tháng + danh sách yêu cầu gần đây + lịch hôm nay.
  (Gộp 1 request cho dashboard load nhanh, tránh gọi 5-6 API rời.)
- FE tạo `src/api/dashboard.js` (theo mẫu `api/http.js`), gọi trong `onMounted()` rồi
  thay các mảng mẫu bằng dữ liệu thật.
- Quy ước BE: controller chỉ nhận request & gọi service; logic tổng hợp nằm ở service.
  (Khi code phần này sẽ giải thích kỹ ngay trong file Java.)
