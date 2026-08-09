# BE: bỏ neo TÊN ROLE ở tầng service — `SecurityUtils.isCentreStaff()` (2026-08-09)

Phần tiếp nối của [2026-07-21-be-teacher-controller-rbac.md](2026-07-21-be-teacher-controller-rbac.md).
Lần đó dọn `@PreAuthorize` ở tầng **controller**; lần này dọn nốt các chốt chặn viết tay
trong **service**, nơi `hasRole("EMPLOYEE")` vẫn còn sót lại 9 chỗ.

## Vấn đề

Có hai loại câu hỏi bị trộn lẫn vào cùng một cách viết `hasRole(...)`:

1. **"Người này có phần việc với dữ liệu này không?"** → phải hỏi **QUYỀN** (`TEACHER_VIEW`,
   `LESSON_MANAGE`, …). Đây là câu hỏi phân quyền thật sự.
2. **"Người này xem toàn hệ thống hay chỉ xem phần của mình?"** → hỏi **có phải nhân sự
   trung tâm không**, tức phân biệt người bên trong với hai cổng ngoài (giáo viên, trường
   khách hàng). Đây là câu hỏi về PHẠM VI dữ liệu, không phải về quyền.

Cả hai đều đang được viết bằng cách liệt kê tên role, và mỗi chỗ liệt kê một kiểu khác nhau:

| Nơi | Cách viết cũ |
|---|---|
| `AttendanceService.scopedTeacherId` | ADMIN + EMPLOYEE + ACCOUNTANT + HR + ACADEMIC + SALES |
| `AssignmentService.isStaff` | y hệt (chép tay) |
| `ScheduleService.scopedTeacherId` | y hệt (chép tay) |
| `EvaluationService.isStaffOrAdmin` | ADMIN + EMPLOYEE + ACADEMIC + HR, thiếu ACCOUNTANT/SALES |
| `TeacherService` ×3 | ADMIN + EMPLOYEE + quyền TEACHER_VIEW |
| `LessonController.isTeacherOnly` | phủ định ADMIN + EMPLOYEE + ACADEMIC |

Hai hậu quả:

- **Chức danh mới bị bỏ quên trong im lặng.** Dự án đang chuyển role từ phòng-ban sang chức
  danh (phòng × cấp). Thêm một chức danh mà quên sửa đủ 6 danh sách thì người đó vẫn đăng
  nhập được, vẫn qua `@PreAuthorize`, nhưng bị `scopedTeacherId` ép về hồ sơ cá nhân — mà họ
  không có hồ sơ giáo viên nên nhận 403 "Tài khoản không có hồ sơ giáo viên". Lỗi này không
  có exception nào bất thường, không có log, chỉ có người dùng kêu "sao tôi không xem được".
- **Ngược lại, role rỗng quyền vẫn mở được cửa.** Role `EMPLOYEE` trong ma trận
  `RolePermission` (V3) **không được cấp permission nào cả** — nó chỉ là cái nhãn "người của
  trung tâm". Vậy mà `TeacherService.getTeacherById` cho qua chỉ vì MANG tên role đó, trong
  khi phía sau cánh cửa là CCCD, ngày sinh, địa chỉ và lương hợp đồng. Nhân viên bị tạo
  thiếu `UserRole` (bẫy đã biết của module tạo NV) đọc được sạch hồ sơ mọi giáo viên.

## Sửa

### 1. Thêm `SecurityUtils.isCentreStaff()` — cho câu hỏi loại 2

```java
private static final Set<String> NON_STAFF_ROLES = Set.of("ROLE_TEACHER", "ROLE_SCHOOL", "ROLE_ANONYMOUS");

public static boolean isCentreStaff() {
    // có ít nhất một role KHÔNG nằm trong danh sách loại trừ
}
```

Điểm mấu chốt là **đảo danh sách liệt kê thành danh sách loại trừ**. Danh sách "ai là nhân sự
trung tâm" nở ra theo mỗi chức danh mới, còn danh sách "ai là người ngoài" thì đứng yên: dự án
chỉ có đúng hai cổng ngoài là `TEACHER` và `SCHOOL`. Chức danh mới vì thế **mặc định đúng** mà
không phải sửa dòng code nào.

Ba điểm cần nhớ khi đọc lại hàm này:

- Phải lọc `startsWith("ROLE_")`. Authority của permission (`TEACHER_VIEW`…) không có tiền tố
  đó; nếu không lọc thì bất kỳ ai có một permission bất kỳ đều thành "staff".
- Phải loại `ROLE_ANONYMOUS`. Spring gán role này cho khách chưa đăng nhập, và nó cũng bắt đầu
  bằng `ROLE_` — quên là khách vãng lai lọt vào nhánh xem toàn hệ thống.
- Người **kiêm nhiệm** (vừa `TEACHER` vừa `ACADEMIC`) được tính là staff, đúng như code cũ.
  Đây là lý do dùng "có ít nhất một role ngoài danh sách" chứ không phải "không mang role
  TEACHER" — cách sau sẽ tước quyền của người kiêm nhiệm.

### 2. Ba `scopedTeacherId` giống hệt nhau → gọi thẳng `isCentreStaff()`

`AttendanceService`, `ScheduleService`, `AssignmentService`. Danh sách 6 role cũ **đúng bằng**
toàn bộ role không phải cổng ngoài, nên hành vi hôm nay không đổi một ly nào; cái được là từ
nay ba nơi này dùng chung một định nghĩa.

### 3. `TeacherService` ×3 → hỏi QUYỀN (câu hỏi loại 1)

```java
private static boolean canViewAnyTeacher() {
    return SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasAuthority("TEACHER_VIEW");
}
```

Đây là chỗ **siết chặt** chứ không phải đổi tương đương: bỏ `hasRole("EMPLOYEE")` đi thì role
rỗng quyền không còn mở được hồ sơ. Không dùng `isCentreStaff()` ở đây — kế toán/tuyển sinh là
nhân sự trung tâm nhưng không có nghiệp vụ gì với CCCD và lương của giáo viên.

Chú ý `GET /teacher/{id}` và `GET /teacher/{id}/account` là hai endpoint **cố ý không gắn**
`@PreAuthorize` (để giáo viên tự xem của mình), nên đoạn `if` này là chốt chặn DUY NHẤT.

### 4. `EvaluationService.isStaffOrAdmin` → hai lớp

```java
if (!SecurityUtils.isCentreStaff()) return false;
return hasRole("ADMIN") || hasAuthority("EVALUATION_VIEW") || hasAuthority("EVALUATION_MANAGE");
```

Vế đầu thay cho danh sách role, vế sau giữ lại như phòng thủ nhiều lớp: nếu sau này có endpoint
đánh giá quên gắn `@PreAuthorize` thì kế toán/tuyển sinh vẫn không đọc được nhận xét về giáo
viên. Vế `!hasRole("SCHOOL") && !hasRole("TEACHER")` của code cũ nay nằm gọn trong `isCentreStaff()`.

### 5. `LessonController.isTeacherOnly` → hỏi `LESSON_MANAGE`

Cờ này quyết định "có được thấy bài giảng bản NHÁP không". Câu trả lời đúng là **người quản lý
kho bài giảng**, tức `LESSON_MANAGE` — chứ không phải "không phải giáo viên". Giáo viên chỉ có
`LESSON_VIEW` nên vẫn chỉ thấy bài PUBLISHED, không đổi.

## Cố ý KHÔNG sửa

`DashboardController` giữ nguyên `hasRole('ADMIN') or hasRole('EMPLOYEE')`. Trông giống hệt lỗi
neo role nhưng **khớp thiết kế FE**: `admin.routes.js` để `roles: ['ADMIN','EMPLOYEE']` cho cả
khu `/dashboard`, còn các chức danh HR/ACCOUNTANT/ACADEMIC/SALES đi khu `/staff/**` (điều hướng
theo permission). Đổi sang permission ở đây là **nới quyền quá thiết kế**.

Bẫy nếu sau này ai đó vẫn muốn đổi: **đừng dùng `REPORT_OPERATION_VIEW`** — V3 cấp quyền đó cho
cả role `SCHOOL` (trường khách hàng), mà dashboard có số liệu chi phí lương. Phải seed một
permission `DASHBOARD_VIEW` riêng.

## Kiểm chứng

`SecurityUtilsStaffTest` (9 test) + 1 test mới trong `TeacherServiceIdorTest`. Toàn bộ suite:
**150/150 pass**.

Đã kiểm chứng bằng cách **cố tình phá** hai chốt rồi chạy lại — không phải chỉ nhìn màu xanh:

| Phá gì | Test kêu lên |
|---|---|
| Bỏ lọc `NON_STAFF_ROLES` trong `isCentreStaff` | `pureTeacher_isNotStaff`, `schoolPortal_isNotStaff`, `anonymous_isNotStaff` |
| Thêm lại `hasRole("EMPLOYEE")` vào `canViewAnyTeacher` | `employeeRoleWithoutPermission_cannotViewProfile_throws403` |

Đúng 4 test đỏ, không hơn không kém → test bám đúng vào hành vi cần khóa.

## Tài khoản demo có vỡ không

| Tài khoản | Role | Kết quả |
|---|---|---|
| `admin` | ADMIN | không đổi (đi tắt `hasRole('ADMIN')`) |
| `employee` | EMPLOYEE + HR + ACCOUNTANT + ACADEMIC + SALES (V5) | không đổi — có `TEACHER_VIEW` qua HR, `LESSON_MANAGE` qua ACADEMIC |
| `nhansu` (HR) | HR | không đổi (đã có `TEACHER_VIEW` từ V3) |
| `daotao` (ACADEMIC) | ACADEMIC | không đổi |
| `teacher` | TEACHER | không đổi — vẫn chỉ xem được phần của mình |
| NV tạo thiếu `UserRole` | (rỗng) | **bị chặn** — đúng ý đồ, đây là lỗ hổng được vá |

Không cần migration, không đổi API, không đổi FE.
