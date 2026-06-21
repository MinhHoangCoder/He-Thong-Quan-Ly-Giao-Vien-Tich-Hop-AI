# Portal phòng ban (staff) + tách route theo khu vực

> Ngày: 2026-06-16 · Phần: frontend · Nhánh: `feature/auth-jwt`

> Ghi bù (note viết sau, đầu kiến trúc đã có sẵn trong code). Liên quan chặt với
> `2026-06-14-backend-rbac-permission-matrix.md` (quyền) và `quy-uoc-lam-viec-nhom.md`.

## 1. Vấn đề

Hai chuyện cần giải cùng lúc:

1. **Một chỗ làm việc cho 4 phòng ban nội bộ** (Kế toán / Nhân sự / Đào tạo / Tuyển sinh).
   Nhưng mỗi phòng chỉ được thấy đúng nhóm chức năng của mình — không thể hard-code 4 menu
   khác nhau, vì quyền có thể đổi (RBAC: quyền nằm ở dữ liệu `RolePermission`, không ở code).
2. **`router/index.js` phình to và ai cũng sửa chung 1 file** → mỗi lần thêm route là một
   ổ merge conflict cho cả nhóm.

## 2. Tách route theo KHU VỰC (giảm merge conflict)

`router/index.js` giờ **không còn khai báo route**, chỉ **ghép** các nhóm + giữ route guard:

```js
import { publicRoutes }  from '@/router/public.routes'
import { adminRoutes }   from '@/router/admin.routes'
import { schoolRoutes }  from '@/router/school.routes'
import { teacherRoutes } from '@/router/teacher.routes'
import { staffRoutes }   from '@/router/staff.routes'
const routes = [...publicRoutes, ...adminRoutes, ...schoolRoutes, ...teacherRoutes, ...staffRoutes]
```

→ Người làm tính năng của khu nào **chỉ sửa file `*.routes.js` của khu đó**. `index.js` gần
như không bao giờ phải đụng nữa → hết tranh chấp ở file router chung. Đây là một mảnh của
quy ước chống conflict (xem `quy-uoc-lam-viec-nhom.md`).

## 3. Menu ĐỘNG theo quyền — `router/staffModules.js`

Đây là phần "khó" đáng hiểu kỹ. Ý tưởng: token đăng nhập đã mang sẵn danh sách `perms`
(do RBAC backend nhét vào — xem note RBAC). FE **không cần biết user thuộc phòng nào**, chỉ
cần hỏi: *"user có quyền X không? Có thì hiện module X."*

- `STAFF_MODULES`: mảng khai báo mọi module nội bộ, **mỗi module gắn 1 `perm`** (vd
  `PAYROLL_VIEW`) + `section` (nhóm sidebar) + `label`/`icon`/`to`/`desc`.
- `accessibleModules(perms)`: lọc ra những module mà `perms` chứa đúng `perm` của nó.
- `buildStaffNav(perms)`: gom các module được phép theo `section` → ra cấu trúc sidebar
  cho `PortalShell`. Luôn có nhóm "Tổng quan" về `/staff`.

```js
export function accessibleModules(perms = []) {
  const set = new Set(perms)
  return STAFF_MODULES.filter((m) => set.has(m.perm))
}
```

→ Kế toán đăng nhập chỉ thấy Lương/Doanh thu/HĐ dịch vụ; Nhân sự chỉ thấy GV/Hợp đồng…
**cùng một code, khác nhau ở token**. Đổi quyền một phòng = sửa dữ liệu `RolePermission`,
menu tự đổi theo, không phải build lại FE.

> `to: '#'` = trang feature **chưa làm** (thành viên khác build sau). Vẫn liệt kê để nhìn
> thấy toàn bộ cấu trúc — giống cách `AdminLayout` đang để các mục chưa làm.

## 4. `StaffLayout` + `StaffHomePage`

- `StaffLayout.vue`: wrapper **mỏng** quanh `PortalShell` — chỉ tính `nav` bằng
  `buildStaffNav(auth.perms)` rồi `<PortalShell :nav="nav"><slot/></PortalShell>`. Tái dùng
  toàn bộ khung chung (sidebar + topbar) như admin/teacher/school (xem note 06-08).
- `StaffHomePage.vue`: trang chủ phòng ban — lời chào theo `DEPARTMENT_LABELS`
  (ACCOUNTANT→"Kế toán"…) + lưới thẻ module mà user có quyền (cũng từ `accessibleModules`).

## 5. Định tuyến "về nhà" theo vai trò

- `App.vue`: đăng ký thêm nhãn layout `staff` (map `meta.layout` → component).
- `roleHome.js`: role phòng ban (ACCOUNTANT/HR/ACADEMIC/SALES) → đẩy về `staff-home` (`/staff`).
- `LoginPage.vue`: thêm 4 chip đăng nhập nhanh cho 4 phòng ban (ketoan/nhansu/daotao/tuyensinh).
- `PortalShell.vue`: nhận diện thêm 4 vai trò phòng ban để hiện đúng `roleLabel` trên topbar.

## 6. Route guard 3 lớp (nhắc lại, ở `index.js`)

`router.beforeEach` chặn theo thứ tự:
1. Trang cần đăng nhập mà chưa login → về `/login` (nhớ `redirect`).
2. Đã login mà vào `/login` → về thẳng "nhà" theo vai trò (`roleHome`).
3. Vào khu **không thuộc** `meta.roles` của mình → về "nhà" của vai trò.

> Lưu ý: guard chỉ chặn **ở mức trang/khu**. Việc "thấy được những DÒNG dữ liệu nào" (ownership)
> và lọc chi nhánh là việc của backend — xem note RBAC mục 2 ⚠️.

## TODO
- Build các trang đang `to:'#'` trong `STAFF_MODULES` (lương, chấm công, phân công…).
- Nối API thật cho thẻ/menu (hiện cấu trúc, chưa có số liệu).
