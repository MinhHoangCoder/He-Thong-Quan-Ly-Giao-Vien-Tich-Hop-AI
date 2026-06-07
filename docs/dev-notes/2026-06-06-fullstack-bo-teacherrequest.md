# Bỏ bảng TeacherRequest — đổi luồng nghiệp vụ (Database + Frontend)

> Ngày 2026-06-06. Quyết định nghiệp vụ sau khi team chốt lại: **trường KHÔNG gửi
> yêu cầu giáo viên nữa**; trung tâm **toàn quyền** phân công; trường chỉ **xem
> thống kê & báo cáo**.

## 1. Vì sao bỏ
`TeacherRequest` sinh ra để mô hình hóa việc *trường gửi yêu cầu → nhân viên xử lý*
(có vòng trạng thái `NEW → IN_REVIEW → MATCHED → FULFILLED → REJECTED → CANCELLED`).
Khi trường không còn gửi yêu cầu, bảng này mất sạch lý do tồn tại → giữ lại chỉ gây
rối model và khó giải thích khi bảo vệ.

**Bỏ lúc này là rẻ nhất:** backend mới có file scaffold (`TsdmsApplication.java`),
chưa có entity/service nào đụng tới — nên gần như chỉ sửa tài liệu DB + frontend.

## 2. Luồng MỚI
```
        CŨ:  School → TeacherRequest → Assignment → Schedule
        MỚI: Employee (trung tâm) → Assignment → Schedule
```
- `Assignment` (phân công) giờ là **điểm bắt đầu** của luồng điều phối. Trung tâm tự
  tạo, không cần "yêu cầu gốc".
- Model vốn đã sẵn sàng: cột `Assignment.RequestId` trước đây **để NULL được** — tức
  Assignment đã có thể tồn tại không cần request. Bỏ bảng chỉ là dọn cột FK đó đi.

## 3. Thay đổi ở DATABASE
File `database/schema/TSDMS_Schema.sql` **và** bản sao `backend/.../db/migration/
V1__init_schema.sql` (hai file giống hệt nhau — sửa schema xong copy sang migration):
- **Xóa hẳn** bảng `TeacherRequest` (Bảng 19 cũ) + 2 index `IX_Request_*`.
- Bảng `Assignment`: bỏ cột `RequestId` + FK `REFERENCES TeacherRequest`.
- **Đánh số lại** các bảng: Assignment thành Bảng 19, …, AuditLog thành Bảng 27.
  → Tổng còn **27 bảng lõi** (trước là 28).
- Sửa comment luồng điều phối, header NHÓM 5, comment bảng Subject/School/Notification
  (bỏ chỗ nhắc TeacherRequest). `Notification.Type` bỏ giá trị `REQUEST`.

Từ điển `database/TSDMS_TuDien_DB.md`: bỏ mục TeacherRequest, sửa sơ đồ luồng, đánh số
lại danh sách (lõi 1–27, AI 28–33), đổi `AiMatchSuggestion` từ "ghép GV ⇄ yêu cầu"
thành "ghép GV ⇄ **nhu cầu phân công** (trung tâm chọn)".

> ⚠️ Lưu ý migration: `V1__init_schema.sql` bị **ghi đè** (không tạo `V2`) vì DB chưa
> deploy, còn ở giai đoạn dựng schema. Khi đã chạy thật trên DB chung thì KHÔNG sửa
> file Vn cũ nữa mà phải thêm migration mới (Flyway kiểm tra checksum).

## 4. Thay đổi ở FRONTEND
- `AdminLayout.vue`: bỏ mục menu **"Yêu cầu"** (badge 6) trong nhóm Điều phối.
- `DashboardPage.vue`:
  - Thẻ "Yêu cầu chờ duyệt" → **"Phân công đang chạy"**.
  - Thẻ phụ "Yêu cầu chưa ghép GV" → **"Buổi dạy chờ duyệt"**.
  - Bảng "Yêu cầu giáo viên gần đây" → **"Phân công gần đây"** (cột: Giáo viên /
    Trường / Môn / Ngày / Trạng thái; trạng thái theo enum Assignment: Đang dạy /
    Sắp bắt đầu / Đã hủy).
- `HomePage.vue`: bỏ chữ "yêu cầu" trong mô tả tính năng & hero.

## 5. Phần AI có bị ảnh hưởng không?
Không mất giá trị, chỉ **đổi góc nhìn**: thay vì "AI ghép GV cho *yêu cầu*", thành
*trung tâm chọn (trường + môn + lớp + thời gian) → AI gợi ý GV phù hợp nhất* (theo môn
dạy được, không trùng lịch, đánh giá cao…). Các tính năng AI khác (tự xếp lịch, cảnh
báo trùng lịch, chatbot) giữ nguyên.

## 6. Việc còn lại (sau này)
- Trường (role SCHOOL) cần một **trang báo cáo/thống kê** riêng chỉ-xem. Hiện frontend
  mới có khu admin của trung tâm; trang cho trường làm khi dựng phần phân quyền.
