# Dev Note — Rebrand "KDC EduOps AI" + đổi theme Trắng/Cam/Xanh lam

> Ngày 2026-06-09 · Frontend. Đổi tên thương hiệu, bảng màu mới, và làm lại trang đăng nhập cho bắt mắt.

## 1. Tên hệ thống: hiển thị vs nội bộ

- **Tên chính thức (hiển thị cho người dùng): "KDC EduOps AI".**
- **Code/local vẫn giữ `tsdms`** để KHỎI refactor toàn dự án: package name `tsdms-frontend`, key localStorage `tsdms.session`, comment, README… đều giữ nguyên.
- Chỉ đổi các **chuỗi người dùng nhìn thấy**: `index.html <title>`, brand ở `DefaultLayout`, tên sidebar trong `PortalShell`, tiêu đề `LoginPage`.

> Nguyên tắc: "tên thương hiệu" là dữ liệu hiển thị, không phải định danh code. Tách 2 thứ này ra → đổi brand không kéo theo sửa logic.

## 2. Logo

- **Đã chọn Logo 3** (robot giơ ngón cái, nền xanh lam + cam, chữ "KDC EDUCATION") trong 5 mẫu — vì đúng tông trắng/cam/xanh lam và **sạch, scale tốt** ở kích thước nhỏ (sidebar/favicon). Logo 1 đẹp nhưng nền navy tối + quá nhiều chi tiết → rối khi thu nhỏ.
- **Đã gắn ảnh thật**: `frontend/src/assets/KDC_EduOps_AI_Logo.jpg`, import qua Vite trong [`components/ui/BrandLogo.vue`](../../frontend/src/components/ui/BrandLogo.vue) (`import logoUrl from '@/assets/...'` rồi `<img :src="logoUrl">`). **Bo tròn** (`border-radius: 50%`) để khớp logo hình tròn và **ẩn 4 góc trắng** của file JPG. Mọi nơi (sidebar, login, landing) dùng chung component này nên đồng bộ.
- Lưu ý nhỏ: file ~557 KB hơi nặng cho logo hiển thị 34–48px → sau có thể nén/resize (vd xuất bản 128px) cho nhẹ.

## 3. Bảng màu mới (Trắng · Cam · Xanh lam)

Đổi **tập trung ở `frontend/src/assets/main.css` (`:root`)** — các component đọc lại qua `var()` nên đổi token là cả web đổi theo.

| Vai trò | Cũ (teal+green) | Mới |
|---|---|---|
| Chính / CTA / active | `--c-primary` `#0d9488` | **`#f97316` (cam)** |
| Nhấn phụ | `--c-accent` `#22c55e` | **`#2563eb` (xanh lam)** |
| Sidebar | `#0f2e2c` (teal đậm) | **`#0b2a4a` (navy)** |
| Gradient hero | — | **`--grad-hero`** xanh→navy→cam |
| Trạng thái tốt/tăng | (lẫn vào accent) | **`--c-success` `#22c55e`** (giữ xanh lá) |

**Lưu ý có chủ đích:** xanh lá CHỈ còn dùng cho **ngữ nghĩa tích cực** — badge "tăng %" (`.is-up`), chấm trạng thái `.is-ok`. Màu **chuỗi dữ liệu** trong biểu đồ/dashboard đổi từ xanh-lá → **xanh lam** để ăn theo brand cam+xanh (giữ thêm `sky #0ea5e9` và `amber #f59e0b` làm category phụ).

## 4. Trang đăng nhập — làm lại cho bắt mắt (theo ảnh bìa CLB KDC)

[`LoginPage.vue`](../../frontend/src/pages/LoginPage.vue) chuyển thành **layout 2 cột**:
- **Trái = hero**: nền `--grad-hero`, lưới mạch điện mờ, 2 quầng **glow** (cam + xanh) blur, brandmark + headline + các **"hexchip"** tính năng (Phân công · Lịch dạy · AI · Báo cáo) — mô phỏng các ô lục giác phát sáng trên ảnh bìa.
- **Phải = form** trên thẻ trắng bo góc, đổ bóng xanh-navy nhẹ.
- **Responsive**: ≤880px **ẩn hero**, chỉ còn form (mobile gọn gàng).
- Logic script (đăng nhập / quên mật khẩu / tài khoản demo) **giữ nguyên 100%**, chỉ đổi giao diện.

## 5. Tệp đã đổi

- **Thêm:** `components/ui/BrandLogo.vue`.
- **Theme:** `assets/main.css` (toàn bộ token + gradient + shadow nút).
- **Tên hiển thị:** `index.html`, `layouts/DefaultLayout.vue`, `layouts/PortalShell.vue`, `pages/LoginPage.vue`.
- **Đổi màu hardcode → cam/xanh:** `PortalShell`, `LoginPage`, `HomePage`, `StatCard`, `MiniBars`, `RegisterUserPage`, `ResetPasswordPage`, `DashboardPage`, `SchoolDashboardPage`, `TeacherDashboardPage`.

## 6. Kiểm tra

```bash
cd frontend
npm run dev      # mở http://localhost:5173/login để xem hero mới
npm run build    # đã chạy: format + build PASS, 0 lỗi
```
> Đã xác nhận `npm run format` + `npm run build` xanh sau thay đổi.

## 7. Còn lại (tùy chọn)

- **Nén/resize** file logo (~557 KB → ~128px) cho nhẹ trang.
- Thêm **favicon** từ Logo 3 (đặt vào `frontend/public/favicon.ico` hoặc `.png` + `<link rel="icon">` trong `index.html`).
