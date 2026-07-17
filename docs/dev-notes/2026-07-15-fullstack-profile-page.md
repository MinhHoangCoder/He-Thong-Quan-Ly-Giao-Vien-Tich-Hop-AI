# Trang "Hồ sơ của tôi" tách khỏi Cài đặt + Việt hóa quyền chi tiết

> Ngày 2026-07-15. Ba việc trong một đợt: (1) trang HỒ SƠ riêng (chỉ xem) cho mọi vai
> trò, tách khỏi trang Cài đặt; (2) `/me/profile` trả thêm thông tin theo vai trò
> (GV: CCCD, ngày sinh, địa chỉ, bằng cấp; NV: chi nhánh, chức vụ); (3) mã quyền
> (SCHEDULE_VIEW...) dịch sang tiếng Việt ở mọi chỗ hiển thị.

## 1. Vì sao tách Hồ sơ khỏi Cài đặt?

Trước đây "xem mình là ai" và "đổi mật khẩu/phiên đăng nhập" chung một trang Cài đặt,
trong khi sidebar giáo viên đã có nút "Hồ sơ của tôi" (placeholder `#`). Nguyên tắc
chia mới:

- **Hồ sơ** (`MyProfilePage.vue`) = trang CHỈ XEM: mình là ai, làm ở đâu, có bằng cấp
  gì, được phép làm gì. Không có form.
- **Cài đặt** (`SettingsPage.vue`) = nơi SỬA: liên hệ (email/SĐT), mật khẩu, thiết bị
  đăng nhập, giao diện. Trang Hồ sơ có nút dẫn sang đây.

Đường dẫn theo khu vực (đăng ký cuối mỗi file `*.routes.js` theo quy ước chống
conflict): `/profile` (admin) · `/teacher/profile` · `/staff/profile` ·
`/school/profile`. PortalShell nhận thêm prop `profile-to` (giống `settings-to`) —
dropdown avatar giờ có 2 mục tách bạch "Hồ sơ của tôi" và "Cài đặt tài khoản".
Trong `MyProfilePage`, đường sang Cài đặt suy từ path hiện tại
(`/teacher/profile` → `/teacher/settings`) nên không phải khai báo từng layout.

## 2. `/me/profile` trả gì thêm (backend)

`ProfileResponse` giữ nguyên 7 field cũ (FE cũ không vỡ) và THÊM:

| Field | Có với | Ghi chú |
|---|---|---|
| `branchName` | GV, NV | tra `BranchRepository` theo `branchId` |
| `employmentType` | GV, NV | FULL_TIME / PART_TIME / CONTRACT |
| `profileStatus` | GV, NV, trường | trạng thái HỒ SƠ (khác trạng thái tài khoản) |
| `teacher` (nested) | GV | `idCardNo, dateOfBirth, gender, address, hireDate, certificates[]` |

Nguyên tắc chọn field: đây là dữ liệu **chính chủ tự xem** (id lấy từ token, không
nhận từ ngoài → miễn nhiễm IDOR) nên CCCD/ngày sinh trả đầy đủ, không che. Bằng cấp
không kèm `fileUrl` — file scan thuộc trang quản lý GV của staff. Employee KHÔNG có
CCCD trong hồ sơ vì bảng `Employee` chưa có cột đó (muốn thêm phải có migration mới —
để sau bảo vệ, tránh đổi schema sát ngày).

## 3. Việt hóa mã quyền — `frontend/src/utils/labels.js`

Vấn đề: khối "Quyền của tôi" hiển thị thẳng `SCHEDULE_VIEW`, `ACCOUNTANT`... người
vận hành không đọc được. Giải pháp: một bảng dịch DÙNG CHUNG:

- `ROLE_LABELS` — 8 role → tên tiếng Việt (PortalShell bỏ bảng map cục bộ, import từ đây).
- `permParts/permLabel/permGroups` — mã quyền quy ước `MODULE_ACTION` được tách và dịch:
  `SCHEDULE_VIEW` → "Xem lịch dạy"; `permGroups` gom theo phân hệ cho dạng bảng
  (Lịch dạy → [Xem]).

Chỗ dùng: trang Hồ sơ (bảng "Quyền của tôi" + ghi chú GV chỉ xem dữ liệu của chính
mình; ADMIN hiện dòng "toàn quyền" thay vì danh sách rỗng) và rail trong Cài đặt
(danh sách mã mono cũ → dòng tiếng Việt có icon check).

Bẫy nhớ khi thêm permission mới ở Flyway: **thêm nhãn vào `PERM_MODULES` cùng lúc**,
nếu quên thì FE hiện nguyên mã (không vỡ, chỉ xấu).

## 4. Kiểm tra

- BE: `mvnw test` (unit) + `spotless:apply` trước commit — Spotless chặn CRLF/format.
- FE: `vite build` + eslint sạch.
- E2E: `frontend/shot-profile.mjs` (cần backend + `npm run dev`) — đăng nhập demo
  teacher/admin, chụp `/teacher/profile` (2 theme), rail quyền trong Cài đặt, `/profile`
  admin vào `claude-context/*.png`.
