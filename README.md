[TSMS_TuDien_DB.md](https://github.com/user-attachments/files/28629495/TSMS_TuDien_DB.md)
# TSMS — Từ điển & Hướng dẫn đọc Database (tiếng Việt)

> Tài liệu dành cho cả nhóm, kể cả thành viên chưa quen thuật ngữ tiếng Anh chuyên ngành.
> Đọc kèm hai file: `TSMS_Schema.sql` (lõi, 28 bảng) và `TSMS_Schema_AI.sql` (6 bảng AI, làm sau).

---

## Phần A — Từ điển thuật ngữ tiếng Anh

### A.1 Thuật ngữ database

| Thuật ngữ | Tiếng Việt | Giải thích ngắn |
|---|---|---|
| Table | Bảng | Một loại dữ liệu, vd bảng giáo viên |
| Column / Field | Cột / Trường | Một thông tin trong bảng, vd cột "họ tên" |
| Row / Record | Dòng / Bản ghi | Một mục dữ liệu, vd một giáo viên cụ thể |
| Primary Key (PK) | Khóa chính | Cột định danh duy nhất mỗi dòng (vd TeacherId) |
| Foreign Key (FK) | Khóa ngoại | Cột trỏ sang bảng khác để tạo quan hệ |
| Index | Chỉ mục | "Mục lục" giúp tìm/lọc dữ liệu nhanh hơn |
| Unique | Duy nhất | Không cho phép giá trị trùng (vd email) |
| Constraint | Ràng buộc | Quy tắc dữ liệu phải tuân theo |
| CHECK constraint | Ràng buộc kiểm tra | Giới hạn giá trị hợp lệ (vd điểm 0–10) |
| Composite key | Khóa kép | Khóa chính gồm nhiều cột (bảng nối) |
| IDENTITY | Tự tăng | Cột tự sinh số 1, 2, 3... |
| Nullable / NULL | Cho phép để trống | Cột có thể không có giá trị |
| Trigger | Bẫy/Trình kích hoạt | Đoạn lệnh tự chạy khi dữ liệu thay đổi |
| Seed | Dữ liệu mồi | Dữ liệu tạo sẵn ban đầu (vd 4 vai trò) |

### A.2 Kiểu dữ liệu

| Kiểu | Dùng cho | Ghi chú |
|---|---|---|
| INT | Số nguyên, khóa bảng thường | Tối đa ~2,1 tỷ |
| BIGINT | Số nguyên lớn, khóa bảng log/sự kiện | Khi có thể vượt 2,1 tỷ dòng |
| TINYINT / SMALLINT | Số nhỏ | vd tháng, mức điểm |
| DECIMAL(18,2) | Tiền tệ | Chính xác tuyệt đối, không sai số |
| BIT | Đúng/Sai (1/0) | vd giới tính, đã đọc/chưa |
| DATE | Ngày | vd ngày sinh |
| TIME(0) | Giờ trong ngày | vd giờ check-in |
| DATETIME2(3) | Ngày + giờ chính xác | Lưu theo giờ UTC |
| VARCHAR | Chữ chỉ có ký tự ASCII | username, email, mã |
| NVARCHAR | Chữ có dấu tiếng Việt | tên, địa chỉ, mô tả |

### A.3 Khái niệm thiết kế trong dự án

| Thuật ngữ | Tiếng Việt | Giải thích |
|---|---|---|
| Soft delete | Xóa mềm | Không xóa thật, chỉ đánh dấu `IsDeleted = 1` để giữ lịch sử |
| Audit columns | Cột kiểm toán | `CreatedAt/By`, `UpdatedAt/By` ghi ai tạo/sửa, lúc nào |
| RBAC | Phân quyền theo vai trò | Quyền hạn dựa trên vai trò (Admin, Employee...) |
| Lookup / Danh mục | Bảng tra cứu | Bảng dữ liệu cố định (vd Subject, Role) |
| Bảng nối (junction) | Bảng trung gian | Nối quan hệ nhiều-nhiều (vd TeacherSubject) |
| Enum | Tập giá trị cố định | Danh sách trạng thái cho phép (vd PENDING/APPROVED) |

### A.4 Thuật ngữ nghiệp vụ (tên bảng ↔ ý nghĩa)

| Tên (Anh) | Tiếng Việt |
|---|---|
| Branch | Chi nhánh |
| AppUser | Tài khoản đăng nhập |
| Role / Permission | Vai trò / Quyền |
| Employee | Nhân viên trung tâm |
| Teacher | Giáo viên |
| Subject | Môn học |
| Certificate / Contract | Bằng cấp - chứng chỉ / Hợp đồng |
| School | Trường (khách hàng) |
| Room | Phòng học |
| SchoolClass | Lớp học |
| Student | Học sinh |
| Enrollment | Ghi danh (vào lớp) |
| TeacherRequest | Yêu cầu giáo viên (trường gửi) |
| Assignment | Phân công |
| Schedule | Lịch dạy (từng buổi) |
| Attendance | Chấm công |
| Payroll | Bảng lương |
| Evaluation | Đánh giá |
| Feedback | Phản hồi |
| Notification | Thông báo |
| AuditLog | Nhật ký hệ thống |

---

## Phần B — Quan hệ giữa các bảng (đọc theo lời)

**1. Tổ chức.** `Branch` (chi nhánh) là gốc. Mỗi `Employee`, `Teacher`, `School` đều thuộc về một chi nhánh — nhờ vậy nhân viên chỉ thấy dữ liệu chi nhánh mình.

**2. Đăng nhập.** Mọi người dùng đều có một dòng trong `AppUser`. Hồ sơ `Employee`, `Teacher`, `School` mỗi cái nối **1-1** tới một `AppUser`. Việc một người có vai trò gì được ghi ở bảng nối `UserRole` (nối `AppUser` với `Role`).

**3. Giáo viên.** Một giáo viên dạy được nhiều môn → quan hệ **nhiều-nhiều** với `Subject` thông qua bảng nối `TeacherSubject`. Mỗi giáo viên còn có `Certificate` (bằng cấp) và `Contract` (hợp đồng) riêng.

**4. Trường khách hàng.** Một `School` sở hữu nhiều `Room` (phòng), `SchoolClass` (lớp) và `Student` (học sinh). Học sinh vào lớp qua bảng nối `ClassEnrollment`.

**5. Luồng điều phối — phần quan trọng nhất:**

```
School  ──gửi──►  TeacherRequest  ──nhân viên xử lý──►  Assignment  ──sinh ra──►  Schedule
(trường)         (yêu cầu cần GV)                       (phân công)             (từng buổi dạy)
```

- `TeacherRequest`: trường nêu cần giáo viên cho môn/lớp nào.
- `Assignment`: nhân viên chốt **giáo viên nào** dạy **trường/môn/lớp nào** trong **giai đoạn nào**. Một giáo viên có thể được phân công cho **nhiều trường**.
- `Schedule`: từ một phân công, tạo ra **các buổi dạy cụ thể** (ngày, giờ, phòng) với trạng thái duyệt `PENDING → APPROVED/REJECTED/CANCELLED`.

**6. Sau buổi dạy.** Mỗi buổi `Schedule` có thể gắn một `Attendance` (chấm công). Mỗi lần `Schedule` đổi trạng thái, hệ thống tự ghi vào `ScheduleStatusLog` (qua trigger).

**7. Vận hành khác.** `Payroll` (lương theo tháng, mỗi giáo viên), `TeacherEvaluation` (đánh giá giáo viên), `Feedback` (phản hồi), `Notification` (thông báo), `AuditLog` (nhật ký toàn hệ thống).

---

## Phần C — Mục đích từng bảng (1 dòng)

**Lõi (TSMS_Schema.sql):**

1. **Branch** — chi nhánh của trung tâm.
2. **AppUser** — tài khoản đăng nhập của mọi người dùng.
3. **Role** — 4 vai trò: ADMIN, EMPLOYEE, SCHOOL, TEACHER.
4. **Permission** — danh mục quyền chi tiết.
5. **RolePermission** — nối vai trò ⇄ quyền.
6. **UserRole** — nối tài khoản ⇄ vai trò.
7. **RefreshToken** — token làm mới phiên đăng nhập (JWT).
8. **Employee** — hồ sơ nhân viên trung tâm.
9. **Subject** — danh mục môn học.
10. **Teacher** — hồ sơ giáo viên.
11. **TeacherSubject** — giáo viên dạy được môn nào.
12. **Certificate** — bằng cấp & chứng chỉ của giáo viên.
13. **Contract** — hợp đồng giáo viên.
14. **School** — trường khách hàng.
15. **Room** — phòng học (thuộc trường).
16. **SchoolClass** — lớp học (thuộc trường).
17. **Student** — học sinh (không điểm/học phí).
18. **ClassEnrollment** — nối lớp ⇄ học sinh.
19. **TeacherRequest** — yêu cầu giáo viên do trường gửi.
20. **Assignment** — phân công giáo viên.
21. **Schedule** — lịch dạy từng buổi (bảng trung tâm).
22. **ScheduleStatusLog** — nhật ký đổi trạng thái lịch.
23. **Attendance** — chấm công.
24. **Payroll** — bảng lương theo tháng.
25. **TeacherEvaluation** — đánh giá giáo viên.
26. **Feedback** — phản hồi.
27. **Notification** — thông báo.
28. **AuditLog** — nhật ký hệ thống.

**AI (TSMS_Schema_AI.sql) — làm ở giai đoạn cuối:**

29. **AiSchedulingJob** — một lần chạy AI xếp lịch.
30. **AiScheduleProposal** — các buổi do AI đề xuất.
31. **AiMatchSuggestion** — gợi ý ghép giáo viên ⇄ yêu cầu.
32. **AiConflictAlert** — cảnh báo trùng lịch / quá tải.
33. **AiConversation** — phiên chatbot.
34. **AiMessage** — từng tin nhắn trong phiên chat.
