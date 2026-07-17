# FE: Gộp sửa liên hệ vào trang Hồ sơ, Cài đặt bỏ tab Hồ sơ (2026-07-17)

## Vấn đề

Sau khi có trang "Hồ sơ của tôi" (2026-07-15), thông tin hồ sơ bị TRÙNG hai nơi:

- `MyProfilePage` xem đầy đủ (CCCD, ngày sinh, công việc, bằng cấp, quyền...) nhưng
  **chỉ xem** — người dùng thấy "có vài trường không sửa được" mà không rõ vì sao.
- Tab "Hồ sơ" trong `SettingsPage` chỉ có một phần thông tin đó + form sửa email/SĐT.

## Quyết định

**Một nơi duy nhất cho hồ sơ**: `MyProfilePage` vừa xem vừa sửa-tại-chỗ phần được phép;
`SettingsPage` chỉ còn đúng vai trò "cài đặt tài khoản" (mật khẩu / thiết bị / giao diện).

## Trường nào sửa được, trường nào không (và VÌ SAO)

| Trường | Sửa được? | Lý do |
|---|---|---|
| Email | ✅ (mọi tài khoản) | `PUT /me/profile` — email nhận link quên mật khẩu, check trùng |
| SĐT | ✅ (trừ admin) | phone nằm trên hồ sơ Teacher/Employee/School; admin (actorType `NONE`) không có hồ sơ → backend `ifPresent` **bỏ qua lặng lẽ** ⇒ FE phải ẨN ô SĐT với admin, đừng để nhập rồi mất |
| Họ tên, CCCD, ngày sinh, giới tính, địa chỉ, công việc, bằng cấp | ❌ | Gắn hợp đồng & bảng lương — chỉ phòng Nhân sự sửa (qua module Giáo viên/Nhân sự). Đây là ranh giới **cố ý**, không phải thiếu tính năng; UI có ghi chú ngay dưới card |

## Thay đổi chính

- `MyProfilePage.vue`
  - Card "Thông tin cá nhân" thêm nút bút → 2 dòng email/SĐT biến thành input đúng
    tại chỗ (pattern xem-trước-sửa-sau chuyển nguyên từ SettingsPage, giữ validate
    mirror backend: email bắt buộc + hợp lệ, SĐT rỗng hoặc `0/+84 + 9-10 số`).
  - Lưu xong gọi `authApi.me()` đồng bộ lại store (topbar đọc email từ đó).
  - Nút góc phải đổi "Chỉnh sửa liên hệ & bảo mật" → "Cài đặt tài khoản" (icon settings).
- `SettingsPage.vue`
  - Bỏ tab "Hồ sơ" (cả 2 card) + toàn bộ logic sửa liên hệ, copyText; tab mặc định
    thành `password`. Hero giữ nguyên (nhận diện nhanh) + thêm link "Xem hồ sơ đầy đủ"
    (suy path `/x/settings → /x/profile`, đối xứng với `settingsPath` bên hồ sơ).
  - Dọn style mồ côi: `.editbtn .info-grid .copyable .minibtn .form-grid .chip--muted .card__foot--row`.
- Backend KHÔNG đổi gì — `PUT /me/profile` dùng nguyên.

## Kiểm chứng (shot-profile-edit.mjs — cần BE + vite đang chạy)

`node frontend/shot-profile-edit.mjs` (chạy từ **repo root**; ảnh ra `../claude-context`
tính từ CWD). Script tự: login teacher → sửa SĐT sai (hiện lỗi) → sửa đúng → Lưu
(hiện "Đã lưu...", view cập nhật) → Cài đặt còn đúng 3 tab → link hero về `/teacher/profile`
→ admin KHÔNG có ô SĐT, có ô email → **khôi phục SĐT gốc** qua API. Đã pass toàn bộ
2026-07-17, không pageerror, dark mode OK (4 ảnh trong claude-context/).

## Bỏ khối "Quyền của tôi" (cùng ngày, commit sau)

User + nhóm trưởng thống nhất: hồ sơ chỉ hiển thị thông tin CON NGƯỜI, không hiển thị
phân quyền (khối này trùng vai trò với rail "Quyền của tôi" bên Cài đặt — vẫn còn đó,
đọc từ JWT). KHÔNG bù khối filler nào (quy tắc "chỉ thêm khối khi có dữ liệu thật");
thay vào đó cân lại lưới: GV/NV 2 cột (Thông tin cá nhân | Công việc + Bằng cấp),
admin/trường 1 cột full-width. `permGroups` trong utils/labels.js vẫn được giữ cho
rail Cài đặt.

## Bẫy cho người sau

- Muốn cho admin sửa SĐT: phải thêm cột phone cho AppUser hoặc hồ sơ riêng — đừng chỉ
  bỏ `v-if` ở FE, số nhập vào sẽ mất không báo lỗi.
- Route `profile`/`settings` của 4 khu vực suy ra nhau bằng replace path — thêm khu vực
  mới thì giữ đúng cặp path `/khu/profile` & `/khu/settings` để 2 nút chéo còn đúng.
