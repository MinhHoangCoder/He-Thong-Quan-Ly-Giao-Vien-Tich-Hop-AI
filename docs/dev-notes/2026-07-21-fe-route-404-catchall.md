# FE: Route 404 catch-all dùng chung (2026-07-21)

## Vấn đề

App không có route bắt lỗi — MỌI URL không khớp route nào (gõ sai, `/ai-assistant`
sau khi ẩn, link cũ chết...) đều ra **trang trắng** thay vì thông báo rõ ràng. Sắp
deploy công khai nên cần trang 404 tử tế.

## Thay đổi (3 file)

1. **`src/pages/NotFoundPage.vue`** (mới) — trang 404: số "404" lớn (gradient thương
   hiệu), tiêu đề, đường dẫn đã gõ (`route.fullPath`) hiển thị trong chip mono, 1 nút
   "về nhà". Bọc bởi `BlankLayout` (đã canh giữa sẵn). Nút **thông minh** theo trạng thái:
   - Đã đăng nhập → `roleHome(auth.roles)` + nhãn "Về trang chính".
   - Khách → `{ name: 'home' }` + nhãn "Về trang chủ".
2. **`src/router/index.js`** — thêm `notFoundRoute` với path `/:pathMatch(.*)*`, đặt
   **CUỐI CÙNG** mảng routes (sau tất cả khu vực). `meta: { layout: 'blank', public: true }`.
3. **`src/components/ui/SvgIcon.vue`** — thêm icon `home` (chưa có sẵn) cho nút.

## Vì sao làm vậy (điểm dễ sai)

- **Bắt buộc đặt CUỐI mảng**: vue-router khớp theo thứ tự, catch-all `/:pathMatch(.*)*`
  khớp MỌI thứ → để trước sẽ nuốt hết route thật (đã E2E xác nhận `/login`, `/` vẫn sống).
- **`public: true`**: nếu không, route guard (rule 1) thấy `!public && !isLoggedIn` sẽ
  đẩy khách chưa đăng nhập về `/login` thay vì hiện 404. Đặt public để 404 hiện cho CẢ
  khách lẫn người đã đăng nhập.
- **Đặt trong `index.js` chứ không trong `*.routes.js` khu vực nào**: nó là route TOÀN
  CỤC, không thuộc admin/teacher/... Các file khu vực spread TRƯỚC nên nếu để catch-all
  trong đó (vd public.routes) sẽ nuốt route khác. Đây là ngoại lệ hợp lý của quy ước
  "route mới thêm cuối file khu vực".

## Kiểm chứng

- ESLint sạch (3 file).
- E2E (Playwright, vite chạy — phần khách KHÔNG cần backend):
  - Khách gõ `/khong-ton-tai-xyz-123` → hiện 404 + "Không tìm thấy trang" + nút "Về
    trang chủ"; KHÔNG bị đẩy về `/login`. ✅
  - Route thật không bị nuốt: `/login` vẫn ra form đăng nhập, `/` vẫn ra trang chủ,
    cả hai KHÔNG dính 404. ✅
  - Dark mode + icon `home` render đúng (ảnh nf-guest-dark.png). ✅
- **Biến "đã đăng nhập" (nhãn "Về trang chính" → /dashboard)**: xác minh bằng đọc logic
  (computed 3 dòng dựa `auth.isLoggedIn`); E2E phần này TREO vì backend tắt lúc kiểm.
  Bật backend rồi test lại: đăng nhập admin → gõ URL bậy → nút "Về trang chính" → bấm về
  `/dashboard`.

## Ghi chú

- Không có ảnh minh họa/route 404 nào phía backend — đây thuần FE (SPA), server luôn
  trả `index.html`, vue-router mới là nơi quyết định 404. Khi deploy: cấu hình host FE
  (Vercel) **rewrite mọi path về `index.html`** để F5 tại URL sâu không ra 404 của host.
