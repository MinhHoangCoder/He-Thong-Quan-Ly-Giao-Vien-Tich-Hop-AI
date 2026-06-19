# Phân quyền RBAC theo permission — Ma trận quyền & quy ước cho cả nhóm

> Ngày: 2026-06-14 · Phần: backend · Nhánh: `feature/auth-jwt`

> ⭐ Đây là **tài liệu HỢP ĐỒNG QUYỀN** cho cả nhóm. Người làm nền (mình) seed quyền + ra
> quy ước; người làm tính năng chỉ việc dán đúng 1 dòng `@PreAuthorize` lên controller của họ.
> Đọc kỹ mục 6 trước khi code feature.

## 1. Vì sao làm

Hiện hệ thống mới chỉ có **4 vai trò gộp**: `ADMIN | EMPLOYEE | SCHOOL | TEACHER`
(seed trong `V1__init_schema.sql`). Mọi nhân viên trung tâm đều chung một role `EMPLOYEE`
→ **không tách được** "kế toán chỉ chấm công + báo cáo" với "nhân sự chỉ phân công giảng dạy".

Yêu cầu mới: mỗi **phòng ban** chỉ dùng được đúng nhóm tính năng của mình. Có 2 cách:

- **Cách A — theo role:** đẻ thêm role con (`ROLE_ACCOUNTANT`…) và chặn bằng `hasRole`.
  Nhanh, nhưng đổi quyền = sửa code + phát lại token.
- **Cách B — theo permission (ĐÃ CHỐT):** token mang theo các **mã quyền chi tiết**
  (vd `ATTENDANCE_VIEW`), chặn bằng `hasAuthority('ATTENDANCE_VIEW')`. Đổi quyền của một
  phòng = **thêm/bớt dòng trong bảng `RolePermission`, KHÔNG sửa code, KHÔNG sửa token**.

Chọn **Cách B** vì DB đã có sẵn đúng bảng cho nó (`Permission`, `RolePermission`) và yêu cầu
"phòng này được cái này, phòng kia được cái kia" chính là use-case kinh điển của permission-based.

## 2. Khái niệm cần nắm (cho người mới)

Mỗi request đi qua 3 trạm:

1. **Phát thẻ (đăng nhập):** `AuthService` hỏi DB user có role gì → `JwtService` đóng các
   quyền vào JWT. JWT giống **thẻ nhân viên** ghi sẵn quyền.
2. **Soát thẻ (mỗi request):** `JwtAuthenticationFilter` đọc token, nạp danh sách quyền vào
   `SecurityContext` (bộ nhớ tạm: "người gọi request này có các quyền X, Y, Z").
3. **Cửa phòng (gọi hàm):** `@PreAuthorize("...")` là **nhãn dán trên cửa một hàm controller**.
   Trước khi hàm chạy, Spring so biểu thức trong nhãn với thẻ ở trạm 2. Khớp → vào; không
   khớp → **403 Forbidden**, hàm không chạy. (Công tắc `@EnableMethodSecurity` đã bật sẵn
   trong `SecurityConfig`.)

Phân biệt **Role** vs **Permission**:

| | Role | Permission |
|---|---|---|
| Là gì | "Chức danh" / phòng ban | "Hành động" cụ thể |
| Ví dụ | `ACCOUNTANT` | `ATTENDANCE_VIEW` |
| Bảng | `Role` | `Permission` |
| Nối với nhau qua | `RolePermission` (Role ⇄ Permission) | |
| Gán cho user qua | `UserRole` (User ⇄ Role) | |

Token sẽ mang **union các permission** của tất cả role mà user có. Controller chỉ kiểm
permission (`hasAuthority`), không cần biết user thuộc phòng nào.

### ⚠️ RBAC ≠ Quyền sở hữu (đừng nhầm — đây là lỗi hay gặp nhất)

- **RBAC (`@PreAuthorize`)** trả lời: *"loại hành động này, người này có được làm không?"*
  → vd "được xem lịch dạy".
- **Ownership** trả lời: *"trên những DÒNG dữ liệu nào?"* → giáo viên chỉ xem lịch **của
  chính mình**, không xem của GV khác.

RBAC **không** lo được vế hai. Cơ chế cho vế hai đã có: `userId` nhét trong `AuthPrincipal`
(chống IDOR — xem note `2026-06-09-backend-security-idor-ratelimit-rls.md`); tầng service so
`userId` để lọc đúng dữ liệu người gọi. Một endpoint của TEACHER cần **CẢ HAI**:
`@PreAuthorize("hasAuthority('SCHEDULE_VIEW')")` **+** lọc theo `userId` trong service.

## 3. Bộ chuẩn vai trò (Role)

5 phòng ban nội bộ + 2 actor ngoài. **Mỗi phòng ban là một Role.**

| Role (code) | Phòng ban / Actor | Loại |
|---|---|---|
| `ADMIN` | Quản trị hệ thống | nội bộ — toàn quyền |
| `HR` | Nhân sự | nội bộ |
| `ACCOUNTANT` | Kế toán | nội bộ |
| `ACADEMIC` | Đào tạo / Học vụ | nội bộ |
| `SALES` | Tuyển sinh / Kinh doanh | nội bộ |
| `TEACHER` | Giáo viên | ngoài |
| `SCHOOL` | Trường khách hàng | ngoài |

> Role `EMPLOYEE` (từ V1) **bản thân nó không được seed permission nào** — nhân viên thật sẽ
> được gán role phòng ban cụ thể. Một user có thể giữ **nhiều role** (qua `UserRole`); token
> gộp (union) quyền của tất cả.
>
> **Tài khoản test `employee` (Flyway V5):** account `employee` được gán **cả 4 role phòng ban**
> (HR + ACCOUNTANT + ACADEMIC + SALES) → token có đủ quyền 4 phòng để test mọi chức năng trong
> 1 lần đăng nhập, nhưng **vẫn bị giới hạn chi nhánh** (vì không phải ADMIN). Đây là tài khoản
> tiện ích cho dev/demo, **đã ẩn khỏi chip đăng nhập nhanh** (gõ tay `employee`/`Tsdms@123`).
> Cặp đôi test: `admin` = full, không giới hạn chi nhánh · `employee` = full, scoped 1 chi nhánh.

### ⚠️ Quy ước GIỚI HẠN CHI NHÁNH (branch-scope) — cho người làm tính năng

Tách bạch với RBAC/ownership: nhiều màn danh sách (GV, lịch, lương…) phải lọc theo **chi nhánh**
của người đang đăng nhập. Quy ước: **chỉ `ADMIN` được thấy toàn bộ chi nhánh**; mọi role khác
(kể cả tài khoản test `employee`) **đều bị lọc theo `BranchId` của nhân viên** ở tầng service —
ví dụ `if (!SecurityUtils.hasRole("ADMIN")) query.filterByBranch(currentBranchId)`. Nhờ đó
`employee` test được đủ chức năng mà vẫn đúng hành vi scoped như nhân viên phòng ban thật.

## 4. Danh mục quyền (Permission) — quy ước đặt tên `MODULE_ACTION`

`ACTION` ∈ `VIEW` (xem/đọc) · `MANAGE` (thêm/sửa/xóa) · `APPROVE` (duyệt) · `EXPORT` (xuất).
Gộp thêm/sửa/xóa vào `MANAGE` cho gọn ở mức đồ án; tách nhỏ hơn sau cũng được mà không phá quy ước.

| Module | Mã quyền | Ý nghĩa |
|---|---|---|
| Tài khoản | `USER_VIEW`, `USER_MANAGE` | Xem / quản lý tài khoản người dùng |
| Phân quyền | `ROLE_MANAGE` | Gán role/quyền cho nhân viên |
| Giáo viên | `TEACHER_VIEW`, `TEACHER_MANAGE` | Hồ sơ GV |
| Hợp đồng GV | `CONTRACT_VIEW`, `CONTRACT_MANAGE` | Hợp đồng lao động GV + chứng chỉ |
| HĐ dịch vụ | `SERVICE_CONTRACT_VIEW`, `SERVICE_CONTRACT_MANAGE` | Hợp đồng dịch vụ trường↔trung tâm — **nguồn doanh thu** (bảng `ServiceContract`, xem migration riêng) |
| Phân công | `ASSIGNMENT_VIEW`, `ASSIGNMENT_MANAGE` | Phân công GV ↔ trường/lớp |
| Lịch dạy | `SCHEDULE_VIEW`, `SCHEDULE_MANAGE`, `SCHEDULE_APPROVE` | Xếp / duyệt lịch |
| Chấm công | `ATTENDANCE_VIEW`, `ATTENDANCE_MANAGE` | Điểm danh GV |
| Lương | `PAYROLL_VIEW`, `PAYROLL_MANAGE` | Bảng lương |
| Lớp | `CLASS_VIEW`, `CLASS_MANAGE` | Lớp học |
| Học sinh | `STUDENT_VIEW`, `STUDENT_MANAGE` | Hồ sơ học sinh |
| Trường KH | `SCHOOL_VIEW`, `SCHOOL_MANAGE` | Trường khách hàng |
| Đánh giá | `EVALUATION_VIEW`, `EVALUATION_MANAGE` | Đánh giá GV |
| Bài giảng | `LESSON_VIEW`, `LESSON_MANAGE` | Kho bài giảng |
| Báo cáo | `REPORT_REVENUE_VIEW`, `REPORT_OPERATION_VIEW` | Dashboard doanh thu / vận hành |

## 5. Ma trận Role × Permission (bảng quyết định để seed)

✅ = được cấp. `ADMIN` được toàn bộ (xem mục 5b nên không liệt kê từng ô).

| Permission \ Role | HR | ACCOUNTANT | ACADEMIC | SALES | TEACHER | SCHOOL |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| `USER_VIEW` | | | | | | |
| `USER_MANAGE` | | | | | | |
| `ROLE_MANAGE` | | | | | | |
| `TEACHER_VIEW` | ✅ | | ✅ | | | |
| `TEACHER_MANAGE` | ✅ | | | | | |
| `CONTRACT_VIEW` | ✅ | | | | | |
| `CONTRACT_MANAGE` | ✅ | | | | | |
| `ASSIGNMENT_VIEW` | ✅ | | ✅ | ✅ | | |
| `ASSIGNMENT_MANAGE` | ✅ | | | | | |
| `SCHEDULE_VIEW` | | | ✅ | | ✅¹ | ✅¹ |
| `SCHEDULE_MANAGE` | | | ✅ | | | |
| `SCHEDULE_APPROVE` | | | ✅ | | | |
| `ATTENDANCE_VIEW` | | ✅ | ✅ | | ✅¹ | |
| `ATTENDANCE_MANAGE` | | ✅ | | | | |
| `PAYROLL_VIEW` | | ✅ | | | | |
| `PAYROLL_MANAGE` | | ✅ | | | | |
| `CLASS_VIEW` | | | ✅ | | | |
| `CLASS_MANAGE` | | | ✅ | | | |
| `STUDENT_VIEW` | | | ✅ | | | |
| `STUDENT_MANAGE` | | | ✅ | | | |
| `SCHOOL_VIEW` | | | | ✅ | | |
| `SCHOOL_MANAGE` | | | | ✅ | | |
| `SERVICE_CONTRACT_VIEW` | | ✅ | | ✅ | | |
| `SERVICE_CONTRACT_MANAGE` | | | | ✅ | | |
| `EVALUATION_VIEW` | | | ✅ | | ✅¹ | ✅¹ |
| `EVALUATION_MANAGE` | | | ✅ | | | ✅¹ |
| `LESSON_VIEW` | | | ✅ | | ✅ | |
| `LESSON_MANAGE` | | | ✅ | | | |
| `REPORT_REVENUE_VIEW` | | ✅ | | ✅ | | |
| `REPORT_OPERATION_VIEW` | ✅ | ✅ | | | | ✅¹ |

¹ Các ô của `TEACHER`/`SCHOOL` **phải kèm lọc ownership** ở service (chỉ thấy dữ liệu của
chính mình / trường mình) — RBAC chỉ mở "cửa loại hành động", không giới hạn theo dòng.

Diễn giải nhanh: **Kế toán** = chấm công + lương + báo cáo (+ xem HĐ dịch vụ để đối soát
doanh thu). **Nhân sự** = GV + hợp đồng lao động + phân công. **Đào tạo** = lịch + lớp +
học sinh + bài giảng + đánh giá. **Tuyển sinh** = trường KH + HĐ dịch vụ (chốt doanh thu).
Đúng yêu cầu nghiệp vụ ban đầu. Combo box dashboard năm học (doanh thu/số trường/số GV) nằm
dưới `REPORT_REVENUE_VIEW`, lấy số liệu doanh thu từ bảng `ServiceContract`.

### 5b. Xử lý `ADMIN`

Chọn cách **đơn giản**: không seed từng quyền cho ADMIN. Thay vào đó quy ước annotation luôn
cho ADMIN đi tắt:

```java
@PreAuthorize("hasRole('ADMIN') or hasAuthority('ATTENDANCE_MANAGE')")
```

→ ADMIN làm được mọi thứ mà không phải bảo trì hàng chục dòng `RolePermission`.

## 6. Quy ước cho người làm tính năng (BẮT BUỘC đọc)

1. Mỗi endpoint nghiệp vụ **phải** có `@PreAuthorize` dùng đúng mã quyền ở mục 4. Không tự
   chế mã mới — thiếu mã thì báo người làm nền thêm vào danh mục + seed, đừng hardcode.
2. Quy ước biểu thức: `@PreAuthorize("hasRole('ADMIN') or hasAuthority('<MÃ_QUYỀN>')")`.
3. Endpoint của `TEACHER`/`SCHOOL`: ngoài `@PreAuthorize`, **bắt buộc** lọc theo
   `principal.userId()` trong service (ownership) — xem mục 2 ⚠️.
4. Đổi việc "phòng X được làm gì" = sửa **dữ liệu** `RolePermission` (qua migration mới),
   KHÔNG sửa code controller.
5. **Endpoint phục vụ NHIỀU loại đối tượng** (vd `POST /api/v1/auth/register` tạo *cả* GV lẫn
   trường): `@PreAuthorize` chỉ lọc **thô** "có ít nhất 1 trong các quyền liên quan"
   (`hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE') or hasAuthority('SCHOOL_MANAGE')`),
   rồi **kiểm tra CHI TIẾT trong service** theo dữ liệu request bằng `SecurityUtils.hasAuthority(...)`
   (HR chỉ tạo được GV, SALES chỉ tạo được trường). Đây là chỗ duy nhất được phép kiểm quyền
   trong service — vì annotation tĩnh không "nhìn" được nội dung request.

## 7. Phần còn thiếu để Cách B chạy (TODO triển khai)

Bảng `Permission`/`RolePermission` đang **rỗng**, token mới chỉ mang `roles`. Cần 4 mảnh:

- [ ] **Seed** — migration mới `V3__rbac_permissions.sql` (KHÔNG sửa V1/V2 vì đã apply —
      quy ước Flyway bất biến): insert 5 role phòng ban + danh mục Permission (mục 4) +
      RolePermission (ma trận mục 5). Gán thêm role phòng ban cho tài khoản demo để test.
- [ ] **Query** — thêm method ở repository: `appUserId → List<permissionCode>`
      (join `UserRole → RolePermission → Permission`).
- [ ] **Token** — thêm claim `perms` ở `JwtService.generateAccessToken` + truyền perms qua
      `AuthService.issueTokens`.
- [ ] **Filter** — `JwtAuthenticationFilter` map `perms` thành authority **không** tiền tố
      `ROLE_` (để `hasAuthority('CODE')` nhận diện), giữ nguyên `roles → ROLE_x`.
- [ ] **Mẫu** — 1 controller mẫu (vd chấm công) gắn `@PreAuthorize` để nghiệm thu chạy thật.

> Sau khi 4 mảnh xong, người làm tính năng chỉ cần dán 1 dòng `@PreAuthorize` (mục 6) —
> đó là lúc nền RBAC này thật sự đẻ ra giá trị cho cả nhóm.
