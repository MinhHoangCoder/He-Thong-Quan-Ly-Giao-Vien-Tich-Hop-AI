# Tính năng CÀI ĐẶT (cá nhân + hệ thống) & các bản vá bảo mật

> Ngày: 2026-07-06 · Phạm vi: BE + FE · Không cần migration mới (tận dụng bảng có sẵn:
> `AppUser.Status`, `RefreshToken`, `Role/Permission/RolePermission/UserRole`).

## 1. Tổng quan kiến trúc

Khu Cài đặt chia 2 tầng, tách biệt rõ theo mô hình phân quyền:

| Tầng | Endpoint | Ai dùng | Chặn bằng gì |
|---|---|---|---|
| **Cá nhân** | `/api/v1/me/**` | mọi user đã đăng nhập | chỉ cần đăng nhập — mọi thao tác dùng id **từ token** (`SecurityUtils.currentUserId()`), không nhận id từ request → miễn nhiễm IDOR |
| **Hệ thống** | `/api/v1/admin/**` | ADMIN (đi tắt) hoặc ai có `USER_VIEW`/`USER_MANAGE`/`ROLE_MANAGE` | `@PreAuthorize` theo **permission** — V11 chỉ cần gán quyền cho chức danh trưởng phòng là dùng được, không sửa code |

File chính:

- BE: `UserSettingsController/Service` (cá nhân), `UserAdminController/Service` (hệ thống),
  DTO mới trong `dto/` (`ProfileResponse`, `ChangePasswordRequest`, `SessionResponse`,
  `UserAccountResponse`, `UpdateUserRolesRequest`, `RoleMatrixResponse`…).
- FE: `pages/SettingsPage.vue` (dùng CHUNG cho 4 layout — đăng ký route riêng từng khu:
  `/settings`, `/staff/settings`, `/teacher/settings`, `/school/settings`) và
  `pages/RoleMatrixPage.vue` (`/settings/roles`); API client `api/settings.js` +
  `api/adminUsers.js`.
- **Phân công (cập nhật 2026-07-07):** trang UI "quản lý tài khoản" (danh sách/khóa-mở/
  gán vai trò) thuộc về THÀNH VIÊN làm module tài khoản → FE base KHÔNG dựng trang này
  (bản nháp `UserAdminPage.vue` đã gỡ). Backend `/api/v1/admin/users/**` GIỮ NGUYÊN làm
  nền cho bạn ấy gọi — các luật chống leo quyền (mục 3) đã nằm sẵn ở service, UI chỉ việc
  hiển thị lỗi 400/403 backend trả về. Giao diện SettingsPage bám design tokens
  `assets/main.css` (hero gradient thương hiệu + avatar chữ cái đầu, CTA cam, checklist
  mật khẩu sống).

## 2. Cài đặt cá nhân (`/api/v1/me`)

### 2.1 Hồ sơ (`GET/PUT /me/profile`)
- `GET` trả về định danh (AppUser) + thông tin liên hệ lấy từ hồ sơ tác nhân
  (Teacher/Employee/School — nhớ là AppUser không còn giữ họ tên từ V6).
- `PUT` chỉ cho tự sửa **email + SĐT**. Email phải duy nhất (check `findByEmailAndDeletedFalse`
  loại trừ chính mình → 409 nếu trùng). **Họ tên KHÔNG cho tự sửa** — dữ liệu pháp lý gắn
  hợp đồng/lương, thuộc quyền phòng Nhân sự (đã ghi chú ngay trên form).

### 2.2 Đổi mật khẩu (`POST /me/change-password`)
Khác luồng quên-mật-khẩu (token email): luồng này xác nhận chính chủ bằng **mật khẩu hiện
tại**. Vì sao bắt nhập lại: kẻ trộm được access token (XSS…) sẽ KHÔNG chiếm hẳn được tài
khoản. Sau khi đổi:
- thu hồi **mọi phiên khác** (`revokeAllActiveByAppUserIdExceptHash`) — kẻ đang chiếm phiên
  bị văng; thiết bị đang thao tác ở lại nhờ FE gửi kèm `refreshToken` hiện tại;
- endpoint được đưa vào **rate-limit** (5 lần/phút/IP) chống brute-force mật khẩu hiện tại.

### 2.3 Thiết bị đăng nhập (`/me/sessions`)
"Phiên" = 1 dòng `RefreshToken` còn sống (chưa thu hồi, chưa hết hạn). DB chỉ lưu **hash**
token nên: FE gửi refresh token của mình trong **body** (POST, không bao giờ trên URL) →
BE hash lại để đánh dấu `current`. Thao tác:
- `POST /me/sessions` — liệt kê; `DELETE /me/sessions/{id}` — đăng xuất 1 thiết bị (chỉ xóa
  được phiên của CHÍNH mình, id người khác trả 404 để không lộ tồn tại);
- `POST /me/sessions/revoke-others` — đăng xuất mọi thiết bị khác.

## 3. Cài đặt hệ thống (`/api/v1/admin`)

- `GET /admin/users` — phân trang (trần size=50), tìm theo username/email; cột vai trò gom
  bằng **1 query** cho cả trang (`findRoleNamePairsByAppUserIds`) tránh N+1.
- `PATCH /admin/users/{id}/status` — ACTIVE|LOCKED. **Khóa = thu hồi luôn mọi refresh
  token** → phiên đang mở chết theo (xem mục 4.1).
- `PUT /admin/users/{id}/roles` — THAY trọn bộ danh sách role (gán chức danh, hỗ trợ kiêm
  nhiệm nhiều role). Sau khi đổi: thu hồi refresh token của người đó để bộ quyền mới có
  hiệu lực ở lần đăng nhập/refresh kế tiếp (quyền cũ chỉ sống tối đa TTL access token 15').
- `GET /admin/roles` — ma trận Role × Permission, **chỉ đọc**: sửa ma trận là việc của
  migration (idempotent), không có API ghi — tránh mỗi môi trường một kiểu quyền.

**Luật chống leo quyền** (nằm ở `UserAdminService`, áp dụng cả khi V11 trao quyền cho
trưởng phòng): không tự khóa/tự đổi role chính mình; chỉ ADMIN được thao tác lên tài khoản
đang giữ ADMIN; chỉ ADMIN được cấp role ADMIN; danh sách role không được rỗng (tránh bẫy
"tài khoản không quyền" — muốn chặn đăng nhập thì dùng Khóa).

## 4. Các bản vá bảo mật đi kèm (code nền)

### 4.1 `AuthService.refresh` bỏ qua trạng thái tài khoản (NGHIÊM TRỌNG)
Trước đây refresh chỉ check token, không check `AppUser.Status`/`IsDeleted` → tài khoản
**đã bị khóa vẫn xoay vòng refresh token sống vô hạn**. Đã thêm: user không ACTIVE hoặc đã
xóa mềm → thu hồi toàn bộ token + trả 403. Kết hợp "khóa = revoke token" (mục 3) thành 2
lớp: phiên cũ chết ngay, và kể cả sót token cũng không refresh được.

### 4.2 Lộ thông tin nội bộ qua lỗi 500
`GlobalExceptionHandler.handleOther` trả nguyên `ex.getMessage()` (có thể chứa SQL, tên
bảng…) cho client. Đã sửa: message gốc chỉ ghi **log server**, client nhận thông báo chung.

### 4.3 Rate-limit cho đổi mật khẩu
Thêm `/api/v1/me/change-password` vào `RateLimitingFilter.LIMITED_PATHS` (verify thật: lần
thử sai thứ 5 trong phút trả 429).

### 4.4 Avatar tải từ dịch vụ ngoài
`PortalShell.vue` hard-code ảnh `i.pravatar.cc` → mỗi lần mở trang gửi request (kèm IP
người dùng) cho bên thứ 3, và vỡ ảnh khi demo offline. Đã thay bằng avatar 2 chữ cái đầu
của tên hiển thị (CSS thuần, không request ngoài).

## 5. Đã verify thế nào

- `mvnw test` — 52 unit test PASS; `mvnw verify` — 9 IT (Testcontainers SQL Server 2022 +
  Flyway V1→V10 + Hibernate validate + boot full context, tự validate mọi `@Query` mới).
- **E2E thật**: dựng SQL Server container riêng (port 14333), chạy app trỏ vào đó, bắn 28
  request qua API như người dùng: toàn bộ luồng hồ sơ/đổi mật khẩu/phiên/khóa-mở/gán role
  + các probe (email trùng 409, tự khóa mình 400, role không tồn tại 400, IDOR xóa phiên
  người khác 404, refresh khi bị khóa 401, login mật khẩu cũ 401, rate-limit 429) đều đúng.
- FE: `npm run lint` + `npm run build` PASS. (Chưa click UI bằng browser tự động — repo
  chưa có Playwright; luồng UI cần bấm tay khi chạy dev.)

## 6. Bẫy đã gặp / lưu ý cho người sau

1. **Đổi role bảng nối `UserRole` (khóa kép)**: xóa hết dòng cũ rồi insert → phải
   `userRoleRepo.flush()` giữa DELETE và INSERT, không Hibernate có thể dồn lệnh và đụng
   trùng khóa khi gán lại role cũ.
2. **`Math.clamp` chưa dùng được** với toolchain hiện tại → dùng `Math.min/Math.max`.
3. **FE build đỏ sau khi pull code nhóm**: thành viên thêm `@fortawesome/fontawesome-free`
   vào `package.json` — phải `npm install` lại, không phải lỗi code.
4. Quyền trong token là **snapshot lúc phát hành**: đổi role không tự đổi token đang sống.
   Chuẩn xử lý ở đây: thu hồi refresh token → tối đa 15' (TTL access token) là quyền cũ hết
   hiệu lực. Đừng "fix" bằng cách query DB mỗi request — mất luôn cái lợi của JWT.
5. `USER_VIEW`/`USER_MANAGE`/`ROLE_MANAGE` đã seed từ V3 nhưng **chưa gán cho role nào**
   (ADMIN đi tắt) → hiện chỉ admin thấy được trang /settings/users. V11 (chức danh) sẽ gán
   cho `HR_MANAGER`… theo bảng hỏi chủ trung tâm — thiết kế sẵn sàng, không phải sửa code.

## 7. Dropdown avatar trên topbar (cập nhật 2026-07-07)

Bỏ mục "Cài đặt" khỏi sidebar của cả 4 layout — sidebar chỉ còn chức năng **nghiệp vụ**;
việc **cá nhân** (Cài đặt tài khoản, Đăng xuất) chuyển vào dropdown bấm từ avatar trên
topbar, theo pattern quen thuộc của GitHub/Google. Nút đăng xuất rời trên topbar cũng gộp
vào dropdown cho gọn.

Cách làm (toàn bộ nằm trong `PortalShell.vue` nên 4 layout hưởng chung):

- PortalShell nhận thêm prop `settingsTo` — mỗi layout truyền route cài đặt của khu mình
  (`/settings`, `/staff/settings`, `/teacher/settings`, `/school/settings`). Route không
  đổi, chỉ đổi lối vào.
- Khối user trên topbar đổi `div` → `button` (kèm `aria-haspopup`/`aria-expanded`), có
  chevron xoay khi mở. Panel gồm: đầu mục tên + @username · vai trò, "Cài đặt tài khoản",
  "Đăng xuất" (đỏ, `--c-danger`).
- Đóng panel khi: click ra ngoài (listener `document` — nhớ gỡ ở `onBeforeUnmount` tránh
  leak), nhấn Escape, hoặc chọn một mục. Hiệu ứng mở dùng `<Transition>` fade + trượt nhẹ.
- `buildStaffNav` (staffModules.js) bỏ nhóm "Hệ thống" chỉ chứa Cài đặt; nhóm "Hệ thống"
  của Teacher/School còn lại "Thông báo", của Admin còn "Trợ lý AI" + "Ma trận quyền"
  (ma trận là chức năng hệ thống, không phải cá nhân → vẫn hợp lý ở sidebar).

## 8. Tab Hồ sơ: xem-trước-sửa-sau (cập nhật 2026-07-07)

Tab đầu của SettingsPage đổi từ "form lúc nào cũng sửa được" sang bố cục 2 card — ranh
giới "ai quản cái gì" tự nó rõ, không cần dòng ghi chú dài:

- **Thông tin cơ bản** (chỉ đọc): họ tên, tên đăng nhập, loại tài khoản, chức vụ — gắn chip
  "Phòng Nhân sự quản lý" (tooltip hướng dẫn liên hệ khi cần đổi). Nhớ là AppUser không giữ
  họ tên từ V6 — dữ liệu này thuộc hồ sơ tác nhân, sửa là việc của nghiệp vụ Nhân sự.
- **Thông tin liên hệ** (email + SĐT): mặc định hiển thị **chữ tĩnh** (SĐT trống hiện
  "Chưa cập nhật" in nghiêng); bấm **icon bút** góc card mới chuyển sang form + nút Lưu/Hủy
  → chống sửa nhầm, trang đọc như danh thiếp thay vì trang nhập liệu.

Chi tiết hiện thực đáng lưu ý: `startEditProfile()` nạp lại form từ `profile.data` ngay lúc
bấm bút (không phải lúc load trang) → bấm Hủy chắc chắn quay về đúng bản đang lưu, kể cả
khi trước đó đã gõ dở rồi Hủy; lưu thành công thì tự thoát chế độ sửa. Icon `pencil` thêm
vào bộ `SvgIcon.vue` dùng chung.

**Validate on-blur** (bổ sung cùng ngày): email/SĐT báo lỗi ngay khi **rời ô** thay vì đợi
bấm Lưu; trong lúc gõ chỉ re-check nếu ô đó ĐANG lỗi → lỗi biến mất đúng lúc sửa xong,
không cằn nhằn giữa chừng. Ô "nhập lại mật khẩu" bên tab Mật khẩu cũng áp cùng pattern
(chỉ báo khi đã gõ gì đó). Luật SĐT (đổi 2026-07-07 vì dự án chỉ phục vụ người Việt):
`^(\+84|0)\d{9,10}$` — bắt đầu 0 hoặc +84, tổng 10–11 chữ số, mirror ở cả FE lẫn
`UpdateProfileRequest`; đây cũng chính là quy ước module Giáo viên đang dùng
(`TeacherListPage` / `TeacherResponse`) → toàn dự án 1 luật.
