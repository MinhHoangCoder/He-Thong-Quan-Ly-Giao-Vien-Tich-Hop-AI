# Quy ước làm việc nhóm — chống merge conflict

> Áp dụng cho mọi thành viên. Mục tiêu: nhiều người làm song song mà KHÔNG giẫm chân nhau.
> Nguyên tắc chung: **feature mới = THÊM file mới**; file dùng chung chỉ **thêm vào cuối**, không sửa/sắp xếp lại phần của người khác.

## 1. Quy trình Git (quan trọng nhất) — cập nhật 2026-07-07

> Đổi quy ước: nhóm đã dùng "Squash and merge" trên thực tế từ nhiều PR nay, nên quy trình
> nhánh phải đổi THEO cho khớp. Squash chỉ chạy đúng khi nhánh sống ngắn (lý do ở cuối mục).

- **Mỗi việc một nhánh MỚI, cắt từ master mới nhất** — làm chức năng hay fix bug đều vậy:

  ```
  git switch master
  git pull
  git switch -c feature/ten-viec   # hoặc fix/ten-bug
  ```

- **Merge PR bằng "Squash and merge"** → master sạch, mỗi dòng = 1 tính năng kèm số PR,
  các commit "wip"/"fix typo" không lọt vào master.
- **PR merge xong → XÓA nhánh** (nút Delete branch), **không tái sử dụng nhánh cũ**.
  Vì sao bắt buộc: squash tạo 1 commit MỚI trên master, các commit gốc trên nhánh không
  bao giờ vào lịch sử master → commit lại tiếp trên nhánh cũ thì PR sau sẽ hiện lại toàn
  bộ commit cũ, càng ngày càng phình (bài học từ chuỗi PR #48→#54 của feature/core).
  Tiện thể: nhánh chỉ có 1 commit thì GitHub tự điền tiêu đề + mô tả PR từ commit message.
- **Nhánh sống ngắn**: tối đa 1–3 ngày là mở PR. Nhánh càng lâu, conflict càng to.
  Nhánh sống quá 1 ngày thì kéo master về: `git fetch origin; git merge origin/master`.
- Nhánh cá nhân nên đặt `git config pull.rebase true` — hết cảnh tự merge với chính mình
  ("Merge branch 'X' of https://..." khi bản local và bản trên GitHub lệch nhau).
- **Chia việc theo MODULE DỌC** (một người ôm trọn cả BE+FE của "Phân công", người khác ôm "Chấm công"...), không chia theo tầng (một người ôm "toàn bộ service" là cách nhanh nhất để cả nhóm sửa chung một file).

## 2. Database / Flyway — bẫy nguy hiểm nhất, Git KHÔNG cảnh báo

- Hai người tạo 2 file cùng số `V2__...` ở 2 nhánh → merge "sạch" (khác tên file) nhưng **backend chết khi khởi động** (Flyway gặp 2 version trùng).
- **Trước khi tạo migration phải "xí số" trong nhóm chat** ("tôi lấy V2"), hoặc dùng số theo ngày giờ: `V20260615_1430__mo_ta.sql`.
- **Không bao giờ sửa file migration đã chạy** (kể cả sửa chính tả) — Flyway lưu checksum, sửa là lỗi. Muốn đổi gì: tạo file V tiếp theo.
- Đổi schema = sửa `database/schema/TSDMS_Schema.sql` (bản thiết kế) **và** tạo migration mới **và** cập nhật entity tương ứng — trong CÙNG một PR.

## 3. Backend

- **`entity/` đã ĐÓNG BĂNG** (map đủ 28 bảng, đủ cột — xem note 2026-06-11): không sửa, trừ khi schema đổi.
- **Repository**: chỉ THÊM method vào cuối interface có sẵn. Không đổi method người khác đã viết.
- **Phân quyền cho endpoint mới**: chặn theo PERMISSION — dùng `@PreAuthorize("hasRole('ADMIN') or hasAuthority('<MÃ_QUYỀN>')")` ngay trên controller/method của mình (đã bật `@EnableMethodSecurity`). Lấy đúng mã quyền từ ma trận `docs/dev-notes/2026-06-14-backend-rbac-permission-matrix.md`, **đừng tự chế mã mới** (thiếu thì báo người làm nền seed thêm). **KHÔNG thêm rule vào `SecurityConfig.filterChain()`** — file đó chỉ giữ các endpoint auth công khai + `anyRequest().authenticated()`.
- Lỗi nghiệp vụ: ném `ApiException(status, message)` — **không** tạo exception class mới, không sửa `GlobalExceptionHandler`.
- `application.yaml`: cấu hình của feature mới đặt thành block riêng dưới `tsdms:`, thêm vào cuối.
- Thêm dependency vào `pom.xml`: **báo nhóm trước**, một người thêm.
- Trước khi push: `mvnw spotless:apply test` phải xanh.

## 4. Frontend

- **Router đã tách theo khu vực** — chỉ sửa file khu của mình, thêm route vào CUỐI mảng:
  - `router/public.routes.js` — trang công khai
  - `router/admin.routes.js` — khu quản trị (ADMIN & EMPLOYEE)
  - `router/school.routes.js` — portal trường
  - `router/teacher.routes.js` — portal giáo viên
  - `router/index.js` chỉ ghép mảng + route guard → **không ai sửa** trừ khi đổi luật guard.
- **Menu sidebar** (`layouts/AdminLayout.vue`, `TeacherLayout.vue`, `SchoolLayout.vue`): chỉ điền link `'#'` của trang MÌNH làm; không thêm/xóa/sắp xếp lại mục của người khác — muốn thêm nhóm menu mới thì báo nhóm.
- **`SvgIcon.vue`, `assets/main.css`**: chỉ thêm vào cuối file (icon mới, class mới); không sửa token màu đã có.
- **API**: mỗi nhóm chức năng một file mới trong `src/api/` (theo mẫu `branches.js`); mỗi trang mới một file trong `src/pages/`; state mới một store mới trong `src/stores/`.
- Thêm thư viện npm: **báo nhóm trước**. Nếu `package-lock.json` conflict: **đừng resolve tay** — lấy nguyên bản master rồi chạy lại `npm install`.
- Trước khi push: `npm run lint` và `npm run build` phải xanh.

## 5. File CẤM ĐỤNG (sửa phải hỏi cả nhóm)

| File | Lý do |
|---|---|
| `frontend/src/stores/auth.js`, `frontend/src/api/http.js` | Lõi phiên đăng nhập (sắp chuyển sang cookie — sửa lung tung sẽ phá kế hoạch) |
| `backend/.../security/*` | Lõi bảo mật đã rà soát (xem note 2026-06-10) |
| `backend/.../entity/*` | Đã đóng băng (xem mục 3) |
| `db/migration/V1__init_schema.sql` | Migration đã chạy |
| `router/index.js` (phần guard) | Luật điều hướng toàn app |

## 6. Conflict vặt thường gặp & cách xử

- `docs/dev-notes/README.md` (bảng index): hai người cùng thêm dòng cuối bảng → khi conflict **giữ cả hai dòng**, xếp theo ngày.
- Mật khẩu/validate: chính sách mật khẩu nằm Ở HAI NƠI phải khớp nhau — `dto/RegisterRequest.java` + `dto/ResetPasswordRequest.java` (BE) và `frontend/src/utils/password.js` (FE). Đổi một nơi phải đổi cả hai.

## 7. Checklist trước khi mở PR

1. Đã kéo master mới nhất về nhánh và chạy lại app.
2. BE: `mvnw spotless:apply test` xanh. FE: `npm run lint` + `npm run build` xanh.
3. Không sửa file trong danh sách CẤM ĐỤNG (mục 5).
4. Nếu có migration: số version đã "xí" trong nhóm.
5. Tính năng khó/quan trọng: đã viết dev-note vào `docs/dev-notes/` + thêm dòng vào README.
