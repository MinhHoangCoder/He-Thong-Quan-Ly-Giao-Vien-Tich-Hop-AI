# Portal Giáo viên & Trường — tách khung chung PortalShell

> Ngày 2026-06-08. Dựng UI/UX cho 2 actor TEACHER & SCHOOL **cùng phong cách**
> dashboard admin/nhân viên, dựa trên sơ đồ phân rã chức năng.

## 1. Vấn đề
Cần dashboard cho Giáo viên & Trường giống hệt style admin (sidebar tối + topbar
trắng + thẻ thống kê + bảng/timeline, theme teal/lá). Nếu copy `AdminLayout`
2 lần → ~400 dòng x2 trùng lặp, khó bảo trì.

## 2. Giải pháp: 1 khung dùng chung + nhiều menu
- **`layouts/PortalShell.vue`** — khung chung: sidebar (nhận `nav` qua prop),
  topbar (tìm kiếm, chuông/mail, user thật + nút đăng xuất). Tự suy `roleLabel`
  từ `auth.primaryRole`.
- `AdminLayout` / `TeacherLayout` / `SchoolLayout` giờ chỉ là wrapper mỏng:
  khai báo mảng `nav` riêng rồi `<PortalShell :nav="nav"><slot/></PortalShell>`.
- `App.vue` thêm 2 nhãn layout: `teacher`, `school` (map `meta.layout` → component).

→ Sửa style 1 chỗ (PortalShell) là cả 4 vai trò đồng bộ. DRY, dễ mở rộng.

## 3. Phân quyền trang (đã có sẵn từ trước)
Route `/teacher` (`meta.roles:['TEACHER']`, layout `teacher`) và `/school`
(`['SCHOOL']`, layout `school`). Guard trong `router/index.js` đẩy người dùng về
"nhà" theo vai trò (`roleHome`) nếu vào nhầm khu.

## 4. Trang dashboard (dữ liệu mẫu)
- `TeacherDashboardPage`: 4 thẻ thống kê (buổi dạy hôm nay, giờ công, buổi tuần,
  điểm đánh giá) + lịch dạy hôm nay (timeline) + thông báo + 3 mini-chart (MiniBars).
- `SchoolDashboardPage`: 4 thẻ + bảng "Giáo viên đang dạy tại trường" + lịch hôm nay.
  Đúng quy ước **trường chỉ XEM** (không có nút tạo/sửa dữ liệu điều phối).
- Tái dùng `StatCard`, `MiniBars`, `SvgIcon` y như dashboard admin. Dữ liệu còn là
  mẫu — sau nối API (báo cáo theo trường / lịch theo giáo viên).

## 5. Phạm vi từ sơ đồ phân rã
Sơ đồ phân rã là **góc nhìn admin/nhân viên** (9 module: quản trị, GV, trường,
giảng dạy, chấm công, lương, đánh giá, thông báo, AI). Từ đó suy ra phần
self-service cho GV (lịch dạy, chấm công, phiếu lương, hồ sơ, đánh giá, thông báo)
và phần chỉ-xem cho Trường (lịch tại trường, GV, lớp/HS, thống kê, phản hồi).
Các mục menu chưa làm để `to:'#'` (giống cách AdminLayout đang làm) — dựng dần sau.

## TODO
- Nối API thật cho các thẻ/bảng (hiện mock).
- Dựng các trang con đang `#`: lịch dạy GV, bảng công, phiếu lương; báo cáo trường.
