# FE: Tạm ẩn phần tích hợp AI (2026-07-20)

## Bối cảnh & lý do

Nhóm chốt **tạm ẩn** mọi dấu vết "tích hợp AI" khỏi giao diện cho đợt bảo vệ tốt nghiệp
và bản deploy công khai. Lý do: với quy mô đồ án, trợ lý hiện tại chỉ là **chatbot chạy
theo luật (if-else)** trong `AiAssistantPage.vue`, chưa phải AI thật — muốn làm thật cần
vector DB, mô hình ngôn ngữ, thường phải thêm service Python riêng. Quảng bá "tích hợp
AI" khi chưa có là điểm dễ bị hội đồng hỏi ngược.

> **Chỉ ẨN, không xóa.** Backend giữ nguyên 100%. Trang `AiAssistantPage.vue` vẫn còn
> trong repo. Mọi chỗ ẩn đều có comment `TẠM ẨN (2026-07-20)` ngay tại chỗ.

## Bảng thay đổi đầy đủ (11 file)

### Nhóm 1 — Đổi tên thương hiệu: `KDC EduOps AI` → `KDC EduOps`

| File | Chỗ sửa | Cũ → Mới |
|---|---|---|
| `frontend/index.html` | `<title>` | `KDC EduOps AI — Quản lý...` → `KDC EduOps — Quản lý...` |
| `src/components/ui/BrandLogo.vue` | prop `text` default | `'KDC EduOps AI'` → `'KDC EduOps'` |
| `src/components/ui/BrandLogo.vue` | `alt` của `<img>` | `"KDC EduOps AI"` → `"KDC EduOps"` |
| `src/layouts/PortalShell.vue` | `.sidebar__name` | `KDC EduOps AI` → `KDC EduOps` |
| `src/layouts/DefaultLayout.vue` | footer | `© {{year}} KDC EduOps AI —...` → `KDC EduOps —...` |
| `src/pages/LoginPage.vue` | `.login-title` | `KDC EduOps AI` → `KDC EduOps` |
| `src/pages/AiAssistantPage.vue` | lời chào bot | `trợ lý KDC EduOps AI` → `trợ lý KDC EduOps` |
| `src/assets/main.css` | comment đầu file | `Bảng màu KDC EduOps AI` → `Bảng màu KDC EduOps` |

`BrandLogo` là **nguồn duy nhất** của tên hiển thị ở hầu hết nơi — chỉ 3 chỗ hard-code
riêng (sidebar, login title, footer) vì có cỡ chữ/bố cục riêng.

### Nhóm 2 — Bỏ quảng cáo AI

| File | Chỗ sửa | Nội dung |
|---|---|---|
| `src/pages/HomePage.vue` | mảng `features` | **Gỡ hẳn** object `{ icon:'ai', title:'Trợ lý AI', ... }` → cụm phân hệ còn **5** mục |
| `src/pages/HomePage.vue` | `.hero__desc` | Bỏ câu `Kèm trợ lý AI hỏi đáp vận hành ngay trong hệ thống.` → thay bằng `không phải nhập tay lại ở bất kỳ bước nào.` |
| `src/layouts/DefaultLayout.vue` | `.brand__sub` | `Điều phối giáo viên · tích hợp AI` → `Quản lý & điều phối giáo viên` |
| `src/pages/LoginPage.vue` | `.auth__headline-accent` | `tích hợp AI` → `cho trung tâm giáo dục` |
| `src/pages/LoginPage.vue` | mảng `features` | `{ icon:'ai', label:'Trợ lý AI hỗ trợ' }` → **thay bằng** `{ icon:'payroll', label:'Chấm công & bảng lương' }` |

Ở LoginPage cố tình **thay** chứ không xóa: danh sách là lưới 4 ô, bỏ 1 ô còn 3 sẽ lệch.

### Nhóm 3 — Chặn lối vào Trợ lý AI

| File | Chỗ sửa | Nội dung |
|---|---|---|
| `src/layouts/AdminLayout.vue` | mảng `nav` | Comment **cả nhóm** `{ title: 'Hệ thống', items: [...] }` |
| `src/router/admin.routes.js` | mảng routes | Comment route `/ai-assistant` |
| `src/layouts/PortalShell.vue` | `v-for` nav group | **Thêm** `v-show="group.items.length"` |
| `frontend/shot-dark.mjs` | danh sách trang chụp | Comment dòng `['/ai-assistant', 'ai-dark.png']` |

**Vì sao comment cả nhóm "Hệ thống"**: nhóm đó chỉ còn đúng 2 mục và **cả hai đều đang
ẩn** — "Trợ lý AI" (lần này) và "Ma trận quyền" (ẩn từ 2026-07-14 cho phạm vi demo
admin+teacher). Để lại nhóm rỗng thì sidebar trơ mỗi tiêu đề `HỆ THỐNG` không có mục nào.

**Vì sao thêm `v-show` ở PortalShell**: đó là lớp bảo vệ chung — `PortalShell` dùng chung
cho cả 4 khu vực (admin/staff/teacher/school), từ nay nhóm nào rỗng cũng tự biến mất,
không tái diễn lỗi tiêu đề trơ khi ai đó ẩn mục khác.

**Vì sao đóng luôn route** (không chỉ giấu nút): hệ thống sắp deploy công khai — để route
sống thì gõ thẳng `/ai-assistant` vẫn vào được trang AI trong khi ta đang tuyên bố không
có tính năng này. Lưu ý: repo **không có route 404 catch-all**, nên gõ `/ai-assistant`
bây giờ ra **trang trắng** (không phải trang lỗi đẹp) — chấp nhận được vì không chỗ nào
liên kết tới.

## Cố ý KHÔNG đụng

- **Toàn bộ backend** — không sửa 1 dòng.
- **`AiAssistantPage.vue`** — giữ nguyên file.
- **Môn học `STEM - AI`, `AI cơ bản`** (trong `api/lessons.js`, `TeacherListPage.vue`,
  dashboard...) — đây là **tên môn trung tâm dạy thật**, không phải quảng bá tính năng AI
  của phần mềm. Đừng nhầm mà xóa.
- **Ảnh logo** `KDC_EduOps_AI_Logo.jpg` — ảnh chỉ ghi "KDC EDUCATION", **không có chữ AI**
  nên giữ (tên file có "AI" chỉ là tên file, không hiện ra UI). Lưu ý ảnh có vẽ **mascot
  robot** nên vẫn hơi "AI-ish"; muốn trung tính hơn phải xin ảnh khác từ phía KDC.

## Cách BẬT LẠI toàn bộ (checklist)

1. `src/router/admin.routes.js` — bỏ comment khối route `/ai-assistant`.
2. `src/layouts/AdminLayout.vue` — bỏ comment nhóm `Hệ thống` (và bỏ comment mục
   `Ma trận quyền` bên trong nếu đợt đó cũng muốn mở lại).
3. `src/pages/HomePage.vue` — thêm lại object feature `Trợ lý AI` vào mảng `features`
   (nội dung cũ chép lại được từ commit này bằng `git show <commit>^:...`).
4. `src/pages/LoginPage.vue` — đổi ô `Chấm công & bảng lương` về `Trợ lý AI hỗ trợ`
   (`icon: 'ai'`), và dòng nhấn về `tích hợp AI` nếu muốn.
5. Tên thương hiệu: sửa lại 8 chỗ ở **Nhóm 1** nếu muốn gắn lại hậu tố "AI".
6. `frontend/shot-dark.mjs` — bỏ comment dòng chụp `/ai-assistant`.

Giữ nguyên `v-show="group.items.length"` ở PortalShell — nó vô hại và có ích lâu dài.

## Kiểm chứng đã làm

- ESLint sạch trên toàn bộ 8 file `.vue`/`.js` đã sửa.
- Chạy app, quét text thật: **landing** và **login** đều không còn chuỗi `EduOps AI`,
  `Trợ lý AI`, `tích hợp AI`; `<title>` đã đúng; không có lỗi console.
- **Sidebar admin: ĐÃ kiểm (2026-07-21, backend bật)** — đăng nhập `admin`, sidebar còn
  đúng 5 nhóm `Tổng quan | Nhân sự | Môn học | Trường học | Điều phối`; nhóm `HỆ THỐNG`
  biến mất hoàn toàn (không trơ tiêu đề); không còn mục `Trợ lý AI`; tên sidebar hiển thị
  `KDC EduOps`; toàn trang admin không còn chuỗi `EduOps AI`; không lỗi console.
- **Gõ thẳng `/ai-assistant`: ĐÃ kiểm** — không hiện trang Trợ lý AI nữa. Đúng như dự
  đoán ở trên, do repo không có route 404 catch-all nên nó ra **trang trắng** (chỉ còn
  header + footer của DefaultLayout). Mục tiêu "không lộ AI" đạt, nhưng nếu muốn đẹp hơn
  thì thêm 1 dòng redirect vào `admin.routes.js`:
  `{ path: '/ai-assistant', redirect: '/dashboard' },` — hoặc làm route 404 dùng chung
  cho cả app (nên làm, vì hiện MỌI URL sai đều ra trang trắng chứ không riêng route này).
