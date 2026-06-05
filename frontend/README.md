# TSDMS — Frontend (VueJS 3 + Vite)

Đã scaffold sẵn: Vue 3 + Vite + Pinia + Vue Router + Axios. Trang chủ mẫu ở [src/pages/HomePage.vue](src/pages/HomePage.vue).

## Chạy

```bash
cd frontend
npm install          # lần đầu, hoặc khi có người thêm package
npm run dev          # mở http://localhost:5173
```

Lệnh khác: `npm run build` (đóng gói production), `npm run preview` (xem thử bản build).

> Backend chạy ở cổng `8080`. Vite đã cấu hình proxy: mọi request `/api/...` từ FE tự chuyển sang backend (xem [vite.config.js](vite.config.js)) — khỏi lo CORS khi dev.

## Cấu trúc thư mục

| Thư mục / file | Chứa gì |
|---|---|
| `src/api/` | File gọi API (axios). `http.js` = instance dùng chung; mỗi resource một file (`auth.js`...) |
| `src/components/` | Component tái sử dụng |
| `src/layouts/` | Layout: `DefaultLayout` (có header/footer), `BlankLayout` (trống, cho login) |
| `src/pages/` | Các trang. Hiện có `HomePage.vue` |
| `src/router/` | Vue Router + route guard (`index.js`) |
| `src/stores/` | Pinia store. `auth.js` = token/user/role |
| `src/utils/` | Helper (`format.js`: ngày, tiền tệ...) |
| `src/assets/` | CSS chung (`main.css`) |

## Thêm trang mới (vd trang đăng nhập)

1. Tạo `src/pages/LoginPage.vue`.
2. Khai báo route trong `src/router/index.js` (mở comment ví dụ có sẵn). Dùng `meta.layout: 'blank'` nếu không muốn header/footer.
3. Gọi API qua file trong `src/api/` (vd `authApi.login(...)`), lưu phiên bằng `useAuthStore().setSession(...)`.

## Quy ước
- State: **Pinia**. `authStore` giữ token trong **bộ nhớ** (KHÔNG `localStorage` cho access token).
- Axios interceptor tự đính kèm `Authorization`; chỗ xử lý refresh 401 đã chừa sẵn trong `src/api/http.js`.
- Import dùng alias `@` = thư mục `src` (vd `import x from '@/utils/format'`).

## Cần thêm sau (khi làm trang lịch)
FullCalendar (spec §11) — cài khi cần:
```bash
npm install @fullcalendar/core @fullcalendar/vue3 @fullcalendar/daygrid @fullcalendar/timegrid @fullcalendar/interaction
```
