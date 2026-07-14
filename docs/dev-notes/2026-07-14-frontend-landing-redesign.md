# Làm lại trang chủ quảng cáo (landing) + tạm ẩn Ma trận quyền

> Ngày 2026-07-14. Trang chủ public (`/`, chưa đăng nhập) được dựng lại theo hướng
> "bấm để xem chi tiết" thay cho 4 card tĩnh; kèm việc tạm ẩn nút Ma trận quyền
> phục vụ đợt bảo vệ (demo chỉ dùng tài khoản admin + teacher).

## 1. Tạm ẩn "Ma trận quyền"

`AdminLayout.vue`: dòng khai báo menu bị **comment lại chứ không xóa** — trang
`/settings/roles` và toàn bộ RBAC phía backend vẫn hoạt động bình thường. Khi cần
demo phân quyền phòng ban trở lại, chỉ việc bỏ comment 1 dòng.

## 2. Trang chủ mới (`HomePage.vue` + `DefaultLayout.vue`)

Bố cục 5 khối, nội dung đều là chức năng CÓ THẬT trong hệ thống (tránh khối
trang trí chung chung — xem quy ước "UI không quá AI"):

1. **Hero** — giữ gradient thương hiệu, thêm 4 chip tính năng (giống hero login),
   nút chính đổi thành "Vào hệ thống".
2. **Tính năng chính — master–detail** (yêu cầu của nhóm trưởng): cột trái là 6
   phân hệ (Quản lý GV / Phân công & lịch / Chấm công & lương / Kho bài giảng /
   Trợ lý AI / Bảo mật), bấm vào mục nào thì panel phải hiện chi tiết của mục đó:
   mô tả + 3-4 khả năng cụ thể + chip "Dành cho: vai trò nào".
3. **Quy trình vận hành** — 5 bước đúng đường đi dữ liệu: Phân công → Sinh lịch
   → Dạy & chấm công → Tính lương → Theo dõi.
4. **Mỗi vai trò một cổng** — 4 card tương ứng 4 portal đã có (admin / staff /
   teacher / school).
5. **CTA cuối trang** + footer gọn.

**Menu header** rút còn 3 anchor `#features / #workflow / #roles` (bỏ "Trang chủ"
trỏ về chính nó và "Dashboard" trùng nút Vào hệ thống); ẩn menu dưới 640px.

## 3. Kỹ thuật đáng nhớ (cho người mới Vue)

- **Master–detail chỉ cần 2 dòng state**: `const selected = ref(0)` +
  `const current = computed(() => features[selected.value])`. Bấm nút chỉ là
  `@click="selected = i"` — panel tự vẽ lại vì `current` là computed.
- **`<Transition name="detail" mode="out-in">`** bọc panel, kèm `:key="current.title"`:
  đổi key là Vue coi như phần tử mới → chạy hiệu ứng mờ + trượt. `mode="out-in"`
  cho panel cũ biến mất xong panel mới mới hiện (không đè nhau).
- **Anchor cuộn mượt**: `html { scroll-behavior: smooth }` trong `main.css`, bọc
  trong `@media (prefers-reduced-motion: no-preference)` và tắt khi người dùng bật
  "giảm hiệu ứng" trong app (`:root[data-motion='reduced']`). Mỗi section có
  `scroll-margin-top: 76px` để tiêu đề không chui dưới header dính.
- **A11y**: cụm nút dùng `role="tablist"/"tab"` + `aria-selected`; panel là
  `role="tabpanel"`.
- Màu 100% qua token (`var(--c-*)`) → dark mode tự đúng, không hard-code
  (xem note 2026-07-12 về lỗi trùng màu).
