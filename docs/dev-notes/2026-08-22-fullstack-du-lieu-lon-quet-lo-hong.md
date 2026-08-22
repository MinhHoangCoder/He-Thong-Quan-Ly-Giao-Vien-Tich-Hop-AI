# Dữ liệu lớn + quét lỗ hổng toàn hệ thống (2026-08-22)

Ghi chú này gộp bốn việc làm cùng một đợt, vì chúng nối nhau: **vá luật → seed dữ liệu lớn →
đo hiệu năng → thử phá**. Mỗi bước sau chỉ lộ ra vấn đề nhờ bước trước.

> Điểm quan trọng nhất của cả đợt: **phần lớn lỗi trong đây không nhìn thấy được ở dữ liệu
> nhỏ.** Với 27.000 buổi dạy mọi thứ đều xanh. Với 86.000 buổi thì màn Phân công mất 4,8 giây,
> màn Lịch dạy trả HTTP 500, và có 674 lớp học xóa được sạch dù đang nằm trong thời khóa biểu.

---

## 1. Quy mô dữ liệu sau đợt seed

| Bảng | Số dòng |
|---|---|
| Giáo viên | 150 — 30 cơ hữu / 120 thỉnh giảng (10 người đã nghỉ việc hoặc tạm đình chỉ) |
| Trường đang hợp tác | 27 (chừa 4 trường hết hạn + chưa có lớp để thử chốt chặn) |
| Lớp học | 864 |
| Phiếu phân công | 444 |
| Ô thời khóa biểu | 4.749 |
| **Buổi dạy** | **86.745** |
| Bản ghi chấm công | 55.125 |
| Phiếu lương | 1.400 (đủ ba trạng thái Nháp / Đã chốt / Đã trả) |

File: `database/seed/TSDMS_Seed_2026_DuLieuLon.sql` (chạy ~60 giây) và
`database/seed/TSDMS_Rollback_2026_DuLieuLon.sql`.

### Mô hình xếp lịch — vì sao chia như thế

Đơn vị phân việc là **LÀN**: một giáo viên phụ trách trọn một buổi (sáng hoặc chiều) tại một
trường trong một ngày.

- buổi sáng: 1 làn mỗi trường
- buổi chiều: 2 làn ở trường tiểu học, 1 làn ở THCS

Chiều nhiều hơn sáng vì trung tâm dạy **môn phụ** (STEM, kỹ năng số) — nhà trường giữ buổi
sáng cho môn chính khóa. Kết quả: **900 tiết chiều so với 675 tiết sáng** mỗi tuần.

Làn giao cho **cơ hữu** thì một người dạy trọn buổi (19–20 tiết/tuần). Làn giao cho **thỉnh
giảng** thì cắt đôi, mỗi người 2–3 tiết (6–12 tiết/tuần) — đúng kiểu người dạy theo tiết.

### Vì sao chắc chắn không trùng lịch

Đây là phần đáng đọc nhất của file seed.

`Cell` = (ngày, buổi) — có **đúng 10 cell** trong một tuần. Trong một cell, các phần việc được
đánh số liên tiếp rồi lấy giáo viên bằng **phép chia dư** trên danh sách:

```sql
g.Rn = x.Gidx % @soThinh
```

Số phần việc của một cell (nhiều nhất 54) **luôn nhỏ hơn** số giáo viên trong danh sách
(110–120), nên phép chia dư không thể trả về cùng một người hai lần trong cùng một cell. Đó là
toàn bộ chứng minh cho luật *"một giáo viên không ở hai nơi cùng lúc"* — **không cần vòng lặp
dò trùng nào**.

Phía lớp cũng vậy: hai làn cùng trường cùng tiết lệch nhau 5 bậc trong danh sách lớp, mà
trường ít lớp nhất cũng có 12 lớp.

PHẦN 13 của file seed đếm lại sáu luật toàn vẹn, tất cả phải ra **0**.

### Mốc thời gian — và cái bẫy đã suýt dính

Năm học 2025–2026 trọn vẹn + HK1 2026–2027 **tựu trường 17/08/2026**.

Ban đầu định để năm học mới bắt đầu 05/09 cho "đúng lịch khai giảng". Nhưng hôm demo là cuối
tháng 8 — rơi trúng **lỗ nghỉ hè**: mở phần mềm lên, Lịch dạy trống, Chấm công trống, Dashboard
"tháng này" bằng 0. Người xem sẽ tưởng hệ thống chưa có dữ liệu.

Tựu trường 17/08 là **lịch thật của Hải Phòng**, không phải ngày bịa cho tiện.

---

## 2. Hiệu năng — đo trước và sau

| Màn hình / endpoint | Trước | Sau | Cải thiện |
|---|---|---|---|
| Phân công — danh sách | 4,79 s · 1,58 MB | **0,16 s · 7 KB** | 30× nhanh, 210× nhẹ |
| Lịch dạy — 1 tuần | 3,60 s | **0,22 s** | 16× |
| Lịch dạy — lưới tháng | 4,09 s | **0,24 s** | 17× |
| Phân công — nạp form | 1,01 s | **0,58 s** | 1,7× |

### Nguyên nhân 1 — N+1 ở `AssignmentService.toResponse`

Tra tên **ngay trong vòng lặp**: mỗi phiếu hỏi giáo viên, môn, ô lịch; rồi mỗi ô lại hỏi tiết +
lớp + trường. Có cache, nhưng cache nằm **bên trong** vòng lặp nên reset theo từng phiếu.

→ ~15.000 câu SQL cho một lần mở màn hình.

**Sửa:** lớp `TenGoi` nạp sẵn mọi tên bằng vài câu. Nạp cả bảng chấp nhận được vì đây đều là
bảng tra cứu nhỏ và ổn định — 150 GV + 23 môn + 31 trường + 864 lớp + 294 tiết, cộng lại chưa
tới 1.400 dòng.

### Nguyên nhân 2 — N+1 ở `ScheduleService.buildEvents`

Cache đánh theo `SourceSlotId`, mà **mỗi buổi dạy sinh ra từ một ô lịch riêng** nên cache
không bao giờ trúng. Một tuần 1.575 buổi thành hơn 1.700 câu SQL.

> Bài học: cache mà khóa của nó là duy nhất theo từng dòng thì không phải cache, chỉ là một
> cái Map tốn bộ nhớ.

### Nguyên nhân 3 — trả cả bảng cho một màn hiện 10 dòng

`/assignments` trả **cả 444 phiếu kèm toàn bộ ô lịch**; trình duyệt tải hết rồi cắt trang bằng
JavaScript. 97% dữ liệu truyền đi chỉ để bị giấu đi.

**Sửa:** `Page<AssignmentResponse>`, cắt trang ở server. Thùng rác vẫn cắt ở client vì hiếm
khi quá vài chục phiếu.

---

## 3. Hai lỗi chỉ lộ ra khi dữ liệu lớn

### 3.1 Vượt trần 2.100 tham số của SQL Server

Sau khi sửa N+1, `GET /schedules?from=2025-09-01&to=2027-06-30` trả **HTTP 500**:

```
The incoming request has too many parameters.
The server supports a maximum of 2100 parameters.
```

`findAllById` của Spring Data dựng **một dấu hỏi cho mỗi khóa**. Khoảng ngày đó gom 4.749 ô
lịch → 4.749 tham số.

Điểm đáng nhớ: **lỗi này không lộ ra ở dữ liệu nhỏ.** 1.000 dòng vẫn xanh; phải tới khoảng
3.000 dòng mới hỏng — tức là hỏng đúng lúc hệ thống bắt đầu được dùng thật.

**Sửa:** `com.kdc.tsdms.common.BatchIn` chia lô 1.000 khóa một lần.

### 3.2 Không có trần khoảng ngày

Cùng URL đó còn gom trọn 86.745 buổi vào bộ nhớ. **Không cần sửa một dòng code nào — chỉ cần
sửa URL trên thanh địa chỉ.**

**Sửa:** `ScheduleService` chốt trần **45 ngày** mỗi lần gọi (lưới tháng chỉ cần 42).

Đo thực tế: 45 ngày = 1,5 MB / 0,5 s; 100 ngày = 8,3 MB / 1,5 s.

---

## 4. Hai mươi kịch bản phá hệ thống

Chạy thật qua đúng API mà giao diện dùng. **17/20 bị chặn ngay từ đầu; 3 ca còn lại lộ ra hai
lỗ hổng, đã vá và chạy lại.**

| # | Kịch bản | Mã | Hệ thống trả lời |
|---|---|---|---|
| 1 | Xóa GV còn lịch dạy + lương chưa chi | 409 | "còn 1 phân công đang chạy, 219 buổi dạy sắp tới và 2 phiếu lương chưa chi" |
| 2 | Hủy phân công đang chạy | 200 | **Đúng thiết kế** — giữ buổi đã dạy, chỉ hủy buổi tương lai |
| 3 | Xóa trường còn lớp học | 409 | "còn 30 lớp học, 23 phân công đang chạy, 1 hợp đồng dịch vụ và 22 ô thời khóa biểu" |
| 4 | Xóa lớp còn phân công | 409 | "còn 2 ô thời khóa biểu" — **trước khi vá: 204, xóa được!** |
| 5 | Xóa môn học còn phân công | 409 | "môn học đang ở trạng thái hoạt động" |
| 6 | Phân công vào trường chưa có lớp | 400 | "đang ở trạng thái hết hạn hợp đồng nên không nhận phân công mới" |
| 7 | Phân công bỏ trống ngày kết thúc | 400 | "Vui lòng nhập ngày kết thúc" |
| 8 | Phân công kết thúc năm 2099 | 400 | "không kéo dài quá 12 tháng" |
| 9 | Phân công bắt đầu trong quá khứ | 400 | "Ngày bắt đầu không được là ngày trong quá khứ" |
| 10 | Sửa phiếu lương đã trả | 409 | "Dòng lương đã chốt/đã trả — không thể chỉnh sửa" |
| 11 | Chốt lại phiếu lương đã trả | 409 | "Chỉ chốt được dòng lương đang ở trạng thái nháp" |
| 12 | Kéo lịch dạy 2 năm một lần | 400 | "Chỉ xem được tối đa 45 ngày một lần" — **trước khi vá: 500** |
| 13 | `?size=999999` trên chấm công | 200 | Kẹp về 100 dòng (`Paging.MAX_SIZE`) |
| 14 | Tạo lớp trùng tên cùng năm học | 409 | "đã tồn tại ở trường này trong năm học 2026-2027" |
| 15 | Tạo lớp 7 ở trường tiểu học | 400 | "Trường tiểu học chỉ mở được khối 1-5" — **luật mới** |
| 16 | Xóa kỳ nghỉ đang che buổi dạy | 204 | Xóa mềm, khôi phục được từ thùng rác |
| 17 | Xóa chi nhánh còn giáo viên | 404 | Không có endpoint xóa chi nhánh |
| 18 | Xem phiếu bằng id không tồn tại | 404 | "Không tìm thấy phân công" |
| 19 | Năm học lệch (2026-2030) | 400 | "Năm học phải liên tiếp" |
| 20 | Tên lớp sai định dạng (5X) | 400 | "Tên lớp dạng 7A1 / 6B20" |

### Lỗ hổng lớn nhất tìm ra: guard đếm nhầm tầng

Từ **V16** lớp thật nằm ở `AssignmentSlot.ClassId`; từ **V27** trường thật nằm ở
`AssignmentSlot.SchoolId`. Lớp/trường ghi ở cấp `Assignment` chỉ còn là **giá trị đại diện**
của ô đầu tiên.

Nhưng chốt chặn xóa vẫn chỉ hỏi cấp phiếu. Đo trên dữ liệu thật:

```sql
SELECT COUNT(*) FROM SchoolClass c
 WHERE c.IsDeleted = 0
   AND EXISTS     (SELECT 1 FROM AssignmentSlot sl WHERE sl.ClassId = c.Id AND sl.IsDeleted = 0)
   AND NOT EXISTS (SELECT 1 FROM Assignment    a  WHERE a.ClassId  = c.Id AND a.IsDeleted  = 0);
-- 674
```

**674 lớp** đang nằm trong thời khóa biểu nhưng không phải lớp đại diện của phiếu nào → bấm Xóa
là mất sạch, giáo viên vẫn tới trường dạy còn ô lịch thì trỏ vào một cái tên không còn tồn
tại. Tám trường dính đúng kiểu đó.

> Đây là cùng một họ lỗi với cái đã vá ở bảng lương ("đọc lớp cấp phiếu là tính sai tiền").
> Cứ mỗi lần dữ liệu được đẩy xuống một tầng sâu hơn, mọi chỗ đang đọc ở tầng cũ đều thành
> sai — và chúng không báo lỗi, chỉ trả về con số nhỏ hơn sự thật.

### Lỗ hổng thứ hai: không tạo được lớp cho năm học mới

`assertNoDuplicate` nhận tham số `year` nhưng **không dùng tới**, chỉ hỏi *"trường này đã có
lớp tên 6A1 chưa"*. Trong khi chỉ mục thật là `UX_Class_School_Name_Year` = (trường + tên +
**năm học**).

Hệ quả: sang năm học mới **không tạo được lớp nào cả** — mọi tên lớp đều đã tồn tại ở năm cũ —
và thông báo lỗi không hề gợi ý rằng nó đang nói về một năm học khác. Lỗi chỉ lộ ra **đúng một
lần mỗi năm, vào lúc bận nhất**.

Đáng chú ý: tầng nghiệp vụ đang **chặt hơn cả ràng buộc thật của database**. Kiểu lệch khó
thấy nhất, vì không có gì báo lỗi cả.

---

## 5. Hai mươi câu thầy sẽ hỏi — và câu trả lời

**1. Dữ liệu này thật hay bịa?**
Bịa hoàn toàn, và cố ý bịa cho đúng định dạng. Họ tên, CCCD, số điện thoại đều sinh tự động.
Email dùng đuôi `@tsdms.local` — tên miền nội bộ không định tuyến ra Internet, nên luồng Quên
mật khẩu không thể gửi nhầm vào hòm thư người thật.

**2. Sao biết lịch không bị trùng?**
Không dựa vào niềm tin. Xem mục 1: chứng minh bằng phép chia dư, và PHẦN 13 của file seed đếm
lại — phải ra 0. Ngoài ra `TeacherTimeConflictChecker` chặn ở tầng nghiệp vụ khi tạo phiếu qua
giao diện, so **giờ thật** chứ không so `PeriodId` (hai trường khác nhau có `PeriodId` khác
nhau nhưng cùng khung 14:00–14:45).

**3. Giáo viên đang dạy mà xóa phân công thì sao?**
Bị chặn. `AssignmentService.cancel` hỏi `countDangDayDoTheoPhanCong` — nếu có bản ghi chấm công
đã check-in mà chưa check-out thì từ chối. Lý do: hủy giữa tiết để lại một dòng chấm công
không bao giờ khép được (giáo viên bấm check-out thì buổi đã `CANCELLED` nên hệ thống từ
chối), và cuối tháng buổi ấy biến mất khỏi phiếu lương của người đã dạy thật.
Hủy phiếu **không đang trong tiết** thì được, nhưng chỉ hủy buổi **tương lai** — buổi đã dạy
và đã chấm công giữ nguyên.

**4. Xóa trường thì lớp và lịch đi đâu?**
Không đi đâu cả, vì **không xóa được**. `DeleteGuard` kể hết trong một câu: còn bao nhiêu lớp,
phân công, hợp đồng dịch vụ, hồ sơ học sinh, ô thời khóa biểu. Gom hết rồi báo một lần thay vì
ném ở rào đầu tiên — kiểu ném-ngay bắt người dùng sửa một thứ, bấm lại, gặp rào tiếp theo, lặp
tới khi hết.

**5. Xóa là xóa hẳn hay xóa mềm?**
Xóa mềm ở 22/38 bảng, có thùng rác và khôi phục. Toàn bộ xóa cứng đã bị bỏ (xem ghi chú
2026-08-22 *bỏ xóa cứng*).

**6. Khóa ngoại của database không đủ bảo vệ à?**
Không. Schema có 43 khóa ngoại và không cái nào khai `ON DELETE`, nên SQL Server mặc định
`NO ACTION` (= RESTRICT). Nghe thì tưởng an toàn sẵn. **Nhưng xóa mềm chỉ là một câu `UPDATE`,
mà khóa ngoại không bao giờ nhìn thấy một câu `UPDATE`.** Khóa ngoại đang bảo vệ đúng thứ dự
án hiếm khi làm, và mù hoàn toàn với thứ dự án làm hằng ngày. Vì vậy chốt chặn phải viết ở
tầng service (`DeleteGuard`).

**7. Ai đó gọi thẳng API bằng Postman thì sao?**
Mọi luật đều ở **server**, không ở giao diện. Cụ thể ba luật từng chỉ tồn tại ở frontend và đã
được đưa xuống server trong đợt này: chặn trường chưa có lớp, chặn khối sai cấp học, chặn
khoảng ngày quá rộng. Ngoài ra `Paging` kẹp `size` về tối đa 100 ở **một chỗ dùng chung** —
trước đó sáu endpoint chỉ có `Math.max(size, 1)`, tức là chặn số âm mà bỏ ngỏ số lớn.

**8. Giáo viên có xem được dữ liệu của người khác không?**
Không. `scopedTeacherId` ép `teacherId` về hồ sơ của chính người gọi và **bỏ qua** `teacherId`
client gửi lên. Endpoint `/schedules/mine` không nhận `teacherId` từ ngoài.

**9. Sao `TaughtHours` lại là số tiết chứ không phải số giờ?**
Tên cột là di sản từ schema đầu. Đổi tên cột kéo theo migration + entity + DTO + frontend, nên
giữ tên và ghi chú rõ tại `PayrollService.generate`. Giá trị lưu là **SỐ TIẾT**.

**10. Tăng đơn giá tiết dạy thì lương tháng cũ có bị tính lại sai không?**
Không. Bảng `PayRate` lưu theo **khoảng hiệu lực** `[EffectiveFrom, EffectiveTo]`, và service
tra giá theo **ngày dạy của từng buổi** chứ không theo ngày bấm nút. Tăng giá là **đóng dòng cũ
rồi thêm dòng mới**, không sửa đè — sửa đè là xóa mất lịch sử giá và mọi phiếu lương cũ tính
lại sẽ ra số khác với số đã trả.

**11. Nghỉ phép có được trả tiền không?**
Không. Chỉ `PRESENT` và `LATE` được tính tiền. `LEAVE` và `ABSENT` không. Xem
`findPayableWithGrade`.

**12. Ngày lễ có bị trừ lương oan không?**
Không, và đây từng là lỗi thật (vá ở Flyway V29). Generator lịch hỏi bảng `Holiday` mỗi lần
trải ô thời khóa biểu. Thiếu bước đó thì lịch đẻ ra buổi dạy vào 30/4, 2/9, Tết; job khép sổ
chấm công thấy buổi đã qua mà không ai chấm nên ghi **VẮNG**; và tiền bị trừ khỏi bảng lương vì
một buổi chưa từng tồn tại — **không ai bấm nút nào sai cả**.

**13. Kỳ nghỉ có phân biệt nghỉ toàn hệ thống với nghỉ riêng một trường không?**
Có. `Holiday.SchoolId = NULL` là nghỉ toàn hệ thống; có giá trị là nghỉ riêng và **không** đụng
tới lịch của trường khác.

**14. Khai báo kỳ nghỉ mới thì lịch đã sinh có tự dọn không?**
**Không, và cố ý không.** Màn Lịch nghỉ đếm sẵn số buổi bị ảnh hưởng rồi để người dùng tự bấm
nút "Hủy N buổi dạy". Tự hủy ngầm thì một kỳ nghỉ gõ nhầm năm sẽ quét sạch lịch trước khi ai
kịp nhìn.

**15. Cơ hữu và thỉnh giảng khác nhau chỗ nào trong tính lương?**
Cơ hữu có **lương cứng** đọc từ `Contract.BaseSalary` cộng với tiền tiết. Thỉnh giảng **chỉ ăn
tiền tiết**. Vì vậy seed đặt `BaseSalary = 0` cho hợp đồng thỉnh giảng — để số khác 0 là dữ
liệu nói dối: màn Hợp đồng ghi 6,5 triệu mà phiếu lương không bao giờ cộng vào.

**16. 150 giáo viên mà chỉ 140 người có lịch dạy?**
Đúng. 7 người đã nghỉ việc (`RETIRED`) và 3 người tạm đình chỉ (`SUSPENDED`).
`assertTeacherAvailable` không giao việc mới cho họ, nhưng hồ sơ và lịch sử vẫn còn nguyên.

**17. Vì sao có 4 trường không có lớp nào?**
Cố ý chừa lại. Chúng là dữ liệu để thử hai chốt chặn: *"trường chưa có lớp"* và *"trường hết
hạn hợp đồng"*. Không có chúng thì hai luật đó không demo được.

**18. Hệ thống chịu được bao nhiêu dữ liệu?**
Đã đo ở 86.745 buổi dạy: mọi màn dưới **1 giây** (xem bảng mục 2). Ba chỗ từng vượt ngưỡng đều
đã vá, và nguyên nhân đã ghi lại. Trần bảo vệ: 45 ngày mỗi lần xem lịch, 100 dòng mỗi trang,
1.000 khóa mỗi câu `IN`.

**19. Thêm lớp cho cả một trường mới có phải bấm 15 lần không?**
Không. Màn **Thêm lớp hàng loạt** có hai tab: *Sinh theo mẫu* (chọn khối + số lớp mỗi khối,
máy tự đặt tên 1A1, 1A2…) và *Nhập dữ liệu* (dán từ Excel hoặc tải file `.xlsx`/`.csv`). Ba
nguồn nhập nhưng dùng **chung một bộ kiểm và một đường ghi** — viết bộ kiểm thứ hai thì hai bộ
sẽ trôi ra khác nhau. Luôn có bước **xem trước** kể rõ từng dòng bị loại vì lý do gì.

**20. Chạy lại file seed hai lần thì sao?**
An toàn. Phần trường/lớp/giáo viên có chốt chặn `NOT EXISTS`; phần điều phối xóa sạch rồi sinh
lại. Đã thử round-trip **seed → rollback → seed**, cho ra **đúng cùng bộ số liệu** (số "ngẫu
nhiên" suy từ `CHECKSUM(Id)` chứ không dùng `RAND()`). Rollback nhận diện dòng cần xóa bằng
**bảng cột mốc** `seed_2026_Moc` (ghi Id lớn nhất trước khi chèn) chứ không đoán theo
`CreatedAt` — bản đầu đoán theo ngày và đã xóa lố hai lớp vốn được tạo cùng ngày.

---

## 6. Những gì còn nợ

Đã bàn và đồng ý làm, chưa làm trong đợt này:

- Màn **Quản lý đơn giá tiết dạy** — bảng `PayRate` đã có (V37) nhưng chưa có màn hình nào
  xem/sửa, nên tính năng "đơn giá ra khỏi code" mới đi được nửa đường.
- Màn **Nhật ký hệ thống** (`AuditLog`) — bảng có sẵn trong schema, đang rỗng.
- Màn **Dữ liệu mồ côi** — bảng `OrphanScan` đã có 2 dòng nhưng không ai xem được.
- **Xuất Excel** cho Bảng lương và Chấm công (Lịch dạy đã có CSV).
- **Kiểm tra sức khỏe dữ liệu** trước khi chốt lương.
- Xóa code chết của portal phòng ban (`Employee`, `Feedback`, `PartTimeShiftRequest`,
  `EmployeeSchedule`) — cần một migration để gỡ khóa ngoại `Assignment.AssignedByEmployeeId`
  trước, nên không gộp vào đợt này.
