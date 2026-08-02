# Teacher: đổi bộ giá trị EmploymentType + thêm TeachingExperience (V20)

**Ngày:** 2026-08-02
**Phạm vi:** backend/DB — `V20`, `Teacher` entity, `TeacherResponse`, `TeacherService`
**Yêu cầu từ:** bạn phụ trách module Quản lý giáo viên

---

## 1. Đổi gì

| | Trước | Sau |
|---|---|---|
| `Teacher.EmploymentType` | `FULL_TIME` / `PART_TIME` / `CONTRACT` | `CO_HUU` / `THINH_GIANG` |
| Kinh nghiệm giảng dạy | (chưa có) | `TeachingExperience NVARCHAR(500) NULL` |

## 2. Vì sao bỏ hẳn `CONTRACT` — đã rà, không chỗ nào dùng

Trước khi bỏ một giá trị enum thì phải chắc không ai phụ thuộc vào nó. Kết quả rà toàn dự án:

- **Không dropdown nào** cho chọn: bộ lọc (`TeacherListPage.vue:978`), form Tạo (`:1419`), form Sửa (`:1777`) đều chỉ liệt kê 2 lựa chọn.
- **Không logic backend nào** rẽ nhánh theo `"CONTRACT"` — grep toàn `backend/src/main/java` không ra dòng nào.
- **Seed V1 chỉ đặt `FULL_TIME`** cho giáo viên demo.
- Dấu vết còn lại chỉ là 2 bảng map nhãn ở FE (xem mục 5).

Nên giá trị này thực tế đã chết từ lâu. Dòng dữ liệu cũ nào lỡ mang `CONTRACT` (seed tay / gọi API trực tiếp) thì V20 dồn về `THINH_GIANG` — gần nghĩa hơn `CO_HUU` vì cả hai đều là "không phải biên chế trọn thời gian".

### ⚠ Đừng nhầm với bảng `Contract`

Đây là **hai thứ khác nhau**, tên gần giống nhau nên rất dễ xóa nhầm:

| | `EmploymentType = 'CONTRACT'` | Bảng `Contract` |
|---|---|---|
| Là gì | Một giá trị loại hình làm việc | Hợp đồng lao động của GV: số HĐ, ngày bắt đầu/kết thúc, lương cơ bản, phụ cấp, file scan |
| Trạng thái | **Đã bỏ ở V20** | **Vẫn dùng, giữ nguyên** |
| Bằng chứng đang dùng | — | `Contract` entity + `ContractRepository`, `TeacherService` nạp vào `ContractDTO`, endpoint `PUT /teacher/{id}/contract`, quyền riêng `CONTRACT_VIEW`/`CONTRACT_MANAGE` seed cho role HR ở V3 |

Và cả hai đều **không liên quan** tới `ServiceContract` (hợp đồng dịch vụ giữa trung tâm với trường khách hàng, V4).

Hiện backend cho hợp đồng GV đã xong nhưng **FE chưa dựng UI** — không trang nào gọi `/teacher/{id}/contract` hay đọc `data.contract`. Đó là việc còn treo của module giáo viên, không phải rác để dọn.

## 3. Vì sao `NVARCHAR(500)` chứ không `NVARCHAR(255)` hay `NVARCHAR(MAX)`

- **NVARCHAR chứ không VARCHAR:** nội dung tiếng Việt có dấu. Để VARCHAR thì "Kinh nghiệm" thành "Kinh nghi?m".
- **500 thay vì 255:** NVARCHAR là kiểu **biến độ dài** — khai 500 không tốn thêm byte nào cho ô ngắn, con số chỉ là *trần*. Ô nhập là `textarea` nên người dùng hay xuống dòng/liệt kê nhiều ý, 255 ký tự tiếng Việt (~2-3 câu) rất dễ chạm trần.
- **Không dùng NVARCHAR(MAX):** không index được, lưu off-row khi lớn, và mở đường cho việc dán cả CV vào ô "ghi chú nhanh" làm vỡ layout bảng danh sách.

**Bẫy phải chặn kèm:** vượt trần cột thì SQL Server ném lỗi 2628 *"String or binary data would be truncated"*, rơi vào nhánh `Exception` của `GlobalExceptionHandler` → người dùng nhận **500 "Lỗi hệ thống"** không hiểu gì. Nên DTO có `@Size(max = 500)` để thành 400 với câu tiếng Việt rõ ràng, và FE phải đặt `maxlength="500"` để chặn ngay từ ô nhập.

Chưa tách cột "số năm kinh nghiệm" kiểu số vì UI hiện là ghi chú tự do. Nếu sau này trung tâm muốn lọc *"GV từ 3 năm kinh nghiệm trở lên"* thì thêm `ExperienceYears SMALLINT NULL` — chỉ là `ALTER TABLE ADD` cột nullable, rẻ, không cần làm trước.

## 4. Hai cái bẫy khi viết migration này

**a. Đánh số V20, KHÔNG phải V18.** Dự án đang khuyết V18 (nhảy thẳng từ V17 lên V19). Thêm file `V18__...` bây giờ thì trên mọi DB đã chạy V19, Flyway `validate` sẽ báo *"Detected resolved migration not applied to database: 18"* và **cả nhóm không khởi động được app** (mặc định `outOfOrder=false`). Luật chung: luôn lấy số lớn hơn version cao nhất đang có, kể cả khi ở giữa có lỗ.

**b. KHÔNG đụng `Employee.EmploymentType`.** Bảng `Employee` cũng có cột trùng tên với `FULL_TIME`/`PART_TIME` (đặt ở V10) nhưng là loại hình của **nhân viên trung tâm**, và module xếp ca đang đòi đúng `FULL_TIME` để generate lịch (`EmployeeScheduleGenerateRequest`). Ai làm ẩu kiểu find-and-replace toàn bộ `FULL_TIME` là gãy module ca làm.

Thứ tự trong V20 cũng quan trọng: phải **DROP constraint cũ trước** rồi mới UPDATE, vì CHECK cũ đang cấm giá trị `CO_HUU`. Cả 4 bước đều có guard (`IF EXISTS` / `COL_LENGTH ... IS NULL`) nên chạy lại nhiều lần vẫn an toàn.

## 5. Việc còn lại — KHÔNG thuộc phần base, bàn giao cho bạn phụ trách QLGV

Backend đã xong và tự chạy được; các mục dưới đây nằm ở FE (và 1 mục BE nhỏ), làm sau vẫn không vỡ gì:

| # | Việc | Chỗ sửa |
|---|---|---|
| 1 | Đổi `value` của 3 dropdown loại hình sang `CO_HUU`/`THINH_GIANG` | `TeacherListPage.vue` dòng ~980 (lọc), ~1421 (form Tạo), ~1779 (form Sửa) |
| 2 | Sửa bảng map nhãn, bỏ `CONTRACT` | `TeacherListPage.vue:512` — `empLabel` |
| 3 | Sửa bảng map nhãn trang Hồ sơ (đang là "Toàn thời gian/Bán thời gian/Hợp đồng", để nguyên sẽ hiện chữ thô `CO_HUU`) | `MyProfilePage.vue:136-140` — `EMPLOYMENT_LABELS` |
| 4 | Thêm `maxlength="500"` cho textarea Kinh nghiệm giảng dạy + bind `teachingExperience` vào form Tạo/Sửa | `TeacherListPage.vue` |
| 5 | Hiển thị Kinh nghiệm giảng dạy ở màn chi tiết GV (API đã trả sẵn trường này) | `TeacherListPage.vue` |
| 6 | (BE, nhỏ) Muốn khoe kinh nghiệm ở trang "Hồ sơ của tôi" thì thêm trường vào `ProfileResponse` + `UserSettingsService` | `UserSettingsService.java:97` |

Lưu ý cho mục 1: `PERM_MODULES` trong `utils/labels.js` cũng có khóa `CONTRACT: 'Hợp đồng giáo viên'` — **đừng đụng vào**, đó là nhãn của *quyền* `CONTRACT_VIEW`/`CONTRACT_MANAGE`, không phải loại hình làm việc.
