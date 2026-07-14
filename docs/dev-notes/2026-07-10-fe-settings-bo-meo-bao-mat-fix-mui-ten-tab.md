# 2026-07-10 — FE Cài đặt: bỏ card "Mẹo bảo mật" + fix 2 mũi tên ▲▼ lạ ở thanh tab

File: `frontend/src/pages/SettingsPage.vue` (2 việc nhỏ, cùng một trang).

## 1. Bỏ card "Mẹo bảo mật" ở rail phải

Team góp ý card này mang tính chất "AI-generated" (khối lời khuyên chung chung không
gắn với dữ liệu thật của người dùng) nên bỏ khỏi UI. Đã gỡ trọn gói:

- Section `<section class="card rail-card rail-tips">` trong template.
- CSS chỉ phục vụ nó: `.rail-tips ul`, `.rail-tips li`, `.rail-ico--green`
  (đã grep — không nơi nào khác dùng).
- Dòng nhắc "Mẹo bảo mật" trong comment mô tả bố cục đầu file.

Rail phải giờ còn 2 card: **Quyền của tôi** và **Tài khoản trên máy này** — cả hai
đều hiển thị dữ liệu thật (roles/permissions từ JWT, multi-account từ store auth),
không phải nội dung tĩnh.

## 2. Fix 2 mũi tên ▲▼ vô nghĩa ở cuối thanh tab

**Hiện tượng:** cuối thanh tab (Hồ sơ / Mật khẩu & bảo mật / Thiết bị đăng nhập /
Giao diện) hiện 2 nút mũi tên lên–xuống bé xíu, bấm không thấy tác dụng gì.

**Nguyên nhân — chuỗi 3 mắt xích:**

1. `.st-tabs` có `overflow-x: auto` (chủ ý: cho cuộn ngang khi màn hình hẹp).
   Theo spec CSS, khi một trục là `auto`/`scroll` thì trục kia **không được phép**
   giữ `visible` — trình duyệt tự đổi `overflow-y` thành `auto` luôn.
2. Nút tab có `margin-bottom: -1px` (trick quen thuộc để border cam 2px của tab
   active đè lên đường kẻ 1px của thanh nav). Margin âm làm nút **cao hơn hộp chứa
   đúng 1px** → nội dung "tràn" theo trục dọc 1px.
3. Trục dọc lúc này là `auto` (mắt xích 1) + có tràn 1px (mắt xích 2) → trình duyệt
   sinh **scrollbar dọc cuộn được đúng 1px**. Trên Windows scrollbar kiểu cổ điển
   có 2 nút mũi tên ở 2 đầu; thanh tab lùn nên phần ray bị ép còn 0 — chỉ còn trơ
   2 nút mũi tên ▲▼ chồng nhau ở mép phải. (macOS dùng overlay scrollbar nên không
   ai thấy — kiểu bug "chỉ hiện trên máy Windows".)

**Fix:** bỏ `margin-bottom: -1px` (hiệu ứng đè border vốn cũng không render được vì
đã bị cắt vào vùng cuộn) và khóa hẳn `overflow-y: hidden` trên `.st-tabs` để sau này
có ai thêm gì tràn dọc 1px cũng không tái phát. Cuộn ngang khi màn hẹp vẫn giữ nguyên.

## Kiểm chứng

- `npm run build` — pass.
- Grep `rail-tips` / `rail-ico--green` / "Mẹo bảo mật" — 0 kết quả còn lại.
- Xem lại trang Cài đặt trên trình duyệt: thanh tab hết mũi tên, rail còn 2 card.
