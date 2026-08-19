# Bổ sung 3 bộ dữ liệu + màn Lịch nghỉ + GỠ TÁC NHÂN NHÀ TRƯỜNG (2026-08-19)

Làm tiếp ngay sau [dev-note lịch nghỉ & seed điều phối](2026-08-19-fullstack-lich-nghi-va-seed-dieu-phoi.md).
Năm việc, trong đó việc cuối là một quyết định nghiệp vụ chứ không phải kỹ thuật.

---

## 1. Đánh giá giáo viên — vá ô "1.0/5" trên Bảng điều khiển

Cả bảng `TeacherEvaluation` chỉ có **đúng một dòng**, mà lại là bản ghi test điểm 1 kèm
nhận xét tục tĩu, tạo lúc 08:58 cùng ngày. Hệ quả nhìn thấy ngay: ô "Điểm đánh giá trung
bình" hiện **1.0/5** — trông như hệ thống hỏng chứ không phải thiếu dữ liệu.

`seed/TSDMS_Seed_DanhGia.sql` xóa dòng đó và dựng **74 đánh giá** cho 2 kỳ.

Ba quyết định đáng ghi lại:

**Chỉ đánh giá giáo viên ĐÃ THỰC SỰ ĐỨNG LỚP trong kỳ** (có ô thời khóa biểu thuộc phiếu
ACTIVE/COMPLETED). Đánh giá một người chưa dạy buổi nào là dữ liệu vô nghĩa — không có gì
để mà nhận xét. Vì thế con số ra 38 + 36 chứ không phải 91 × 2.

**Điểm lệch về 4-5 nhưng có vài ca kém** (5 phiếu 2 điểm, 11 phiếu 3 điểm; trung bình
**4.16/5**). Toàn 5 điểm thì đẹp mắt nhưng màn Đánh giá mất luôn thứ đáng demo nhất: lọc ra
giáo viên yếu để xử lý.

**Nhận xét viết theo nhóm môn × mức điểm** — 20 mẫu (5 nhóm môn × 4 mức), lấy nhóm môn giáo
viên dạy nhiều nhất trong kỳ. Điểm thấp luôn kèm hướng xử lý, vì một dòng "2 điểm" mà không
nói làm gì tiếp thì vô dụng với người quản lý.

Người đánh giá là nhân viên trung tâm (`daotao`, `employee`, `nhansu`) — không dùng tài
khoản trường, vì trường không còn là tác nhân (mục 5).

## 2. Phòng học + Hợp đồng dịch vụ

`seed/TSDMS_Seed_PhongHoc_HopDong.sql`: **150 phòng** (4-6 phòng/trường) và **22 hợp đồng**
dịch vụ, tổng giá trị đang hiệu lực **2,97 tỷ** — nguồn số đầu tiên cho module Doanh thu vốn
trống hoàn toàn.

**Giá trị hợp đồng tính được, không bịa:** số lớp × đơn giá/lớp/năm (TH 14 triệu, THCS 16
triệu). Căn cứ: một lớp 2 tiết/tuần × 35 tuần = 70 tiết/năm, chi phí trả giáo viên 115-125k/tiết
≈ 8-8,8 triệu; phần còn lại là thiết bị, học liệu, điều phối, lợi nhuận. Hỏi "sao ra số này"
là trả lời được.

**Ngày và trạng thái hợp đồng lấy thẳng từ `School.ContractStartDate/EndDate`**, không bịa
ngày mới — hai chỗ nói hai kiểu về cùng một hợp đồng là lỗi dữ liệu. 8 trường INACTIVE không
có ngày hợp đồng nghĩa là chưa từng ký, nên không dựng hợp đồng cho họ.

**CỐ Ý không gán phòng vào 9.712 buổi dạy đã seed.** Gán phòng hàng loạt mà không chạy luật
chống trùng phòng sẽ đẻ ra cảnh hai lớp ngồi chung một phòng lúc 14:00 — tệ hơn là để trống.
Muốn có phòng trên thời khóa biểu thì xếp qua giao diện Phân công, ở đó có kiểm tra đầy đủ.

## 3. Kho bài giảng — 234 bài

`seed/TSDMS_Seed_BaiGiang.sql`: **46 chủ đề** (2 chủ đề/môn × 23 môn) × các khối mà môn đó
phục vụ → **234 bài** (181 PUBLISHED · 21 DRAFT · 32 ARCHIVED).

Cùng một chủ đề dạy khối 3 và khối 9 là **hai bài khác nhau thật sự**: khác thời lượng (35 vs
45 phút, bám khung tiết của trường), khác mức độ (BASIC/INTERMEDIATE/ADVANCED suy từ khối),
khác lưu ý sư phạm. Nên tách thành hai bài chứ không phải một bài dùng chung.

Mỗi bài có giáo án rút gọn nhưng **có cấu trúc thật**: Mục tiêu · Chuẩn bị · Tiến trình chia
mốc thời gian · Đánh giá · Lưu ý theo khối. Mở trang xem chi tiết ra đọc được, không phải chữ
lấp chỗ trống.

Học liệu dùng **link Canva demo, không seed file vật lý** — giữ đúng quyết định ở bản vá
2026-07-11: thư mục `uploads/` đã gitignore nên seed đường dẫn file vào DB sẽ tạo bản ghi trỏ
tới file không tồn tại trên máy người khác.

Dọn kèm: bài `"abc"` và **7 file PDF mồ côi** trong `backend/uploads/lessons/{6,432,434,435,436}`
(bài giảng của chúng đã bị xóa từ lâu).

> Còn 4 file trong `backend/uploads/teachers/{10,48}` cũng có dấu hiệu mồ côi — mọi
> `Certificate.FileUrl`/`Contract.FileUrl` trong DB đều NULL. Chưa đụng vì nằm ngoài phạm vi.

## 4. Màn hình Lịch nghỉ (V30) — trả nợ món V29 để lại

V29 dựng bảng `Holiday` và nối vào generator, nhưng thêm/sửa ngày nghỉ vẫn phải gõ SQL. Trước
mỗi năm học lịch nghỉ đều đổi (Tết theo âm lịch, ngày nghỉ bù do Chính phủ chốt từng năm,
trường sửa chữa nghỉ riêng) — bắt phòng Đào tạo mở SSMS ra sửa là cách chắc chắn để dữ liệu
này không bao giờ được cập nhật.

**Quyền riêng `HOLIDAY_VIEW` / `HOLIDAY_MANAGE`** (V30), cấp cho ACADEMIC; ADMIN đi tắt bằng
`hasRole`. Không neo vào tên role — dự án đã bỏ neo tên role ở tầng service từ bản 2026-08-09.
API đọc mở thêm cho ai có `ASSIGNMENT_VIEW`/`SCHEDULE_VIEW`: khi một buổi dạy "biến mất" khỏi
thời khóa biểu, câu hỏi đầu tiên luôn là hôm đó có phải ngày nghỉ không — bắt họ đi hỏi phòng
Đào tạo mới trả lời được là bắt sai người.

**Hai thứ màn hình cố ý làm rõ:**

**Nhãn "Cần rà soát".** Các ngày suy từ âm lịch và ngày nghỉ bù 2/9 được seed kèm dấu
`[CẦN RÀ SOÁT]` trong ghi chú. Không đẩy lên giao diện thì chẳng ai nhớ đi đối chiếu, và cả
năm học sinh lịch theo một ngày đoán mò.

**Nút "Hủy N buổi dạy".** Generator chỉ bỏ ngày nghỉ **tại thời điểm sinh buổi** — khai báo
kỳ nghỉ MỚI không tự dọn lịch đã sinh trước đó, và những buổi đó rất nguy hiểm: job khép sổ
chấm công sẽ ghi VẮNG cho giáo viên vào ngày trường đóng cửa rồi trừ thẳng vào lương. Nên có
endpoint `GET /holidays/{id}/impact` đếm sẵn số buổi bị ảnh hưởng, và `POST
/holidays/{id}/cancel-sessions` để người dùng **chủ động bấm**.

Không tự hủy ngầm: hủy hàng loạt buổi dạy là việc khó lùi lại, và một kỳ nghỉ gõ nhầm năm sẽ
quét sạch lịch trước khi ai kịp nhìn. Cùng lý do có trần 120 ngày cho một kỳ nghỉ.

`cancelSessions` **chỉ đụng buổi CHƯA diễn ra**. Buổi đã qua có thể đã gắn dòng chấm công và
đã vào bảng lương của kỳ trước — hủy chúng là sửa lại quá khứ và làm lệch số tiền đã trả.

Đo thử trên dữ liệu thật: khai một kỳ nghỉ ngày 20/11/2026 → impact báo **32 buổi của 22 giáo
viên**; bấm hủy → còn 0.

## 5. GỠ TÁC NHÂN NHÀ TRƯỜNG (V31)

**Quyết định nghiệp vụ của chủ dự án:** hệ thống chỉ phục vụ HAI tác nhân — nhân sự trung tâm
(ADMIN + các phòng ban) và GIÁO VIÊN. Không làm màn hình và quyền cho nhà trường nữa.

### Ranh giới — chỗ dễ hiểu nhầm nhất

| Ở lại | Đi |
|---|---|
| Bảng `School` và **mọi** cột `SchoolId` (Assignment, AssignmentSlot, Period, SchoolClass, Room, ServiceContract, Holiday, TeacherEvaluation) | Role `SCHOOL` + 4 dòng `RolePermission` |
| Quyền `SCHOOL_VIEW` / `SCHOOL_MANAGE` — quyền của **nhân viên trung tâm** quản lý hồ sơ trường | Cột `School.AppUserId` |
| Màn "Trường khách hàng" của trung tâm | Portal trường: `SchoolDashboardPage`, `SchoolLayout`, `school.routes.js` |
| `Attendance.CheckInMethod = 'SCHOOL'` — nguồn chấm công, không phải role | Nhánh `ROLE_SCHOOL` trong `SecurityUtils.NON_STAFF_ROLES` |

Trường vẫn là **xương sống của toàn bộ điều phối**: 296 phiếu phân công, 476 tiết và 9.712
buổi dạy đều trỏ vào `School`. Bỏ bảng đó là sập cả hệ thống. Cái bỏ là **tư cách người dùng**
của trường.

### Bỏ cột `School.AppUserId` là chốt hạ ở tầng dữ liệu

Đây là cầu nối duy nhất giữa hồ sơ trường và tài khoản đăng nhập. Bỏ nó thì không còn đường
nào gắn một tài khoản vào một trường nữa, **kể cả bằng SQL tay**. Xóa mỗi role thì code cũ
vẫn chờ sẵn, ai đó `INSERT` một dòng là portal sống lại.

Migration viết phòng thủ: gỡ hết FK / default constraint / index bám vào cột trước rồi mới
`DROP COLUMN`, vì ALTER sẽ nổ nếu còn ràng buộc.

### Việc bỏ cột KÉO THEO refactor, không chỉ xóa file

Đây là phần tốn công nhất và cũng là phần đáng ghi lại nhất: `requireMySchool()` tra hồ sơ
trường **qua `School.AppUserId`**. Bỏ cột ⇒ hàm đó không tồn tại được ⇒ **9 chỗ gọi nó trong
`EvaluationService` buộc phải gỡ theo**:

`filterMeta` · `unevaluatedTeachers` · `create` · `update` · `teacherSummary` · `assertCanView`
· `assertCanManage` · `resolveCandidateTeachers` · `buildTeacherOptions`.

Cùng với đó: `ViewerCtx` bỏ 2 field, `canEdit` rút còn một dòng, và trường `source`
(`CENTER`/`SCHOOL`) bị **gỡ khỏi `EvaluationResponse`** — với tác nhân trường đã biến mất thì
nhãn "SCHOOL = trường chấm" là **nói dối**: mọi đánh giá nay đều do trung tâm chấm, cột
`SchoolId` chỉ còn nghĩa "đánh giá gắn với việc dạy tại trường nào". FE không dùng field này
nên gỡ không ảnh hưởng giao diện.

`RegistrationService` rút gọn hẳn: chỉ còn tạo tài khoản GIÁO VIÊN, bỏ tham số `fullName`,
bỏ phụ thuộc `SchoolRepository`. `DisplayNameResolver` và `UserSettingsService` bỏ nhánh hồ sơ
trường.

### ⚠ BẪY ĐÃ DẪM PHẢI: `mvn test` báo XANH trong khi code KHÔNG compile được

Giữa lúc refactor, `mvnw test` in ra `BUILD SUCCESS` với 193 test pass — trong khi
`EvaluationService` còn **9 lời gọi tới hàm đã bị xóa**, tức là không thể compile.

Nguyên nhân: backend đang chạy bằng `spring-boot:run` **giữ lock trên `target/classes`**, nên
javac không ghi đè được và Maven dùng lại class cũ. `mvn clean` cũng fail vì lý do đó — nhưng
lỗi clean thì thấy ngay, còn compile im lặng dùng class cũ thì không.

**Bài học:** khi refactor mà backend đang chạy nền, kết quả `mvn test` KHÔNG đáng tin. Dừng
server trước, hoặc ít nhất đối chiếu timestamp `.class` với `.java` trước khi tin màu xanh.
Sau khi dừng server và compile lại: 193 test pass THẬT.

## Kết quả đo được

| Bảng | Trước | Sau |
|---|---|---|
| TeacherEvaluation | 1 (rác) | **74** — điểm TB 4.16/5 |
| Room | 0 | **150** |
| ServiceContract | 0 | **22** — 2,97 tỷ đang hiệu lực |
| Lesson / LessonFile | 1 / 1 | **234 / 181** |
| Holiday | 15 (chỉ SQL sửa được) | 15 + **màn hình quản lý** |
| Role | 8 (có SCHOOL) | **7** |
| `School.AppUserId` | có | **đã xóa** |

Smoke test qua API thật sau khi khởi động lại backend: dashboard, holidays (15), schools (30),
lessons (234), evaluations (74, TB 4.16), assignments (296), filter-meta, teachers/unevaluated
— tất cả 200. Backend 193 test pass, frontend build sạch.

## Còn lại

- **Form tạo đánh giá chưa có ô chọn trường.** Trước đây `SchoolId` suy ra từ tài khoản trường
  đang đăng nhập; nay tác nhân đó không còn nên đánh giá tạo qua giao diện để `schoolId = null`.
  Các phiếu seed vẫn giữ trường của chúng (bộ lọc theo trường vẫn chạy). Muốn nhập tay thì thêm
  `schoolId` vào `EvaluationRequest` + một ô chọn trên form.
- 4 file PDF mồ côi trong `backend/uploads/teachers/{10,48}` chưa dọn.
- Đơn giá tiết dạy vẫn hard-code trong `PayrollService`.
- `Student` / `ClassEnrollment`, ca làm nhân viên (V10), `AuditLog`, 6 bảng AI: vẫn trống, chờ
  quyết định phạm vi.
