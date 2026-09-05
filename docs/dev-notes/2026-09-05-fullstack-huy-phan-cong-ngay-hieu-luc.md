# Hủy phân công kể từ một ngày + Đơn xin nghỉ của giáo viên (V39)

> Ngày: 2026-09-05 · Phần: fullstack · Nhánh: `feat/huy-phan-cong-ngay-hieu-luc` · Migration: **V39**
>
> Liên quan: [2026-08-24-fullstack-rang-buoc-xoa-dot-5.md](2026-08-24-fullstack-rang-buoc-xoa-dot-5.md)
> mục 3 (buổi đã dạy ở lại khi phiếu vào thùng rác) và
> [2026-08-19-fullstack-lich-nghi-va-seed-dieu-phoi.md](2026-08-19-fullstack-lich-nghi-va-seed-dieu-phoi.md)
> (buổi "ma" ngày lễ) — đợt này đụng lại cả hai.

---

## 0. Vì sao có đợt này

Câu hỏi ban đầu rất đời thường: *"Đang giữa kỳ mà giáo viên xin nghỉ thì bấm gì?"*

Bấm **Hủy**. Và đó là toàn bộ vấn đề — nút Hủy cũ làm **hai việc cùng lúc và làm tất cả
hoặc không làm gì**:

```js
/* Hủy phân công = đưa thẳng vào thùng rác (một thao tác). */
async function confirmCancel() {
  await assignmentApi.remove(cancelTarget.value.id)   // DELETE → softDelete()
```

Nghĩa là hủy = phiếu chết **ngay lập tức** + rơi thẳng vào thùng rác. Không có khái niệm
"nghỉ từ ngày 15/10". Muốn thế thì chỉ còn cách hủy sạch rồi tạo phiếu mới — đứt mạch dữ
liệu, và không ai tra được lớp đó trước đây ai dạy.

Ba lỗ hổng đi kèm, xếp theo mức độ:

| # | Vấn đề | Hậu quả thật |
|---|---|---|
| 1 | **Giáo viên không được báo gì cả** | `cancel()` chỉ gọi `closeOpenInvites` (tắt nút Xác nhận trong chuông). Mà màn giáo viên chỉ hiện buổi `APPROVED` → các buổi bị cắt **lặng lẽ biến mất** khỏi lịch. Hôm sau họ vẫn tới trường dạy một lớp đã giao cho người khác. |
| 2 | **Không lưu lý do, không lưu ai hủy** | Chỉ có `UpdatedBy` trần. `RejectionReason` là của *giáo viên từ chối*, không phải *admin hủy*. |
| 3 | **Đang đứng lớp thì chặn cứng cả thao tác** | `countDangDayDoTheoPhanCong > 0` → ném 409. An toàn cho chấm công nhưng admin không xử lý được ca gấp: một dòng check-in bỏ quên từ hôm trước cũng khóa luôn cả phiếu. |

Và một chi tiết đáng nhớ: hai endpoint `POST /{id}/cancel` và `POST /{id}/reactivate`
**đã có sẵn trong `AssignmentController` từ lâu** nhưng `frontend/src/api/assignments.js`
**chưa bao giờ khai** — code chết nằm im, không ai biết. Đợt này nối lại.

---

## 1. Cái bẫy quyết định toàn bộ thiết kế

Suy nghĩ đầu tiên ai cũng có: *"hủy từ ngày X thì đổi `Status` rồi đánh dấu các buổi từ
X trở đi là CANCELLED, xong."*

**Sai.** Và sai theo kiểu tệ hơn cả lỗi đang định sửa.

Lý do nằm ở chỗ không ai ngờ tới — `TeacherTimeConflictChecker` và hai truy vấn "giờ bận"
đều lọc theo **giai đoạn của phiếu**, không phải theo từng buổi:

```java
// AssignmentService.teacherBusy() và classBusy() — cùng một dòng
if (startDate != null && !datesOverlap(startDate, endDate, a.getStartDate(), a.getEndDate())) {
    continue;
}
```

Tức là chính cặp `StartDate`/`EndDate` là thứ hệ thống dùng để nói *"khung Thứ+Tiết này
đã có người"*. Giữ nguyên `EndDate = 22/08/2027` mà chỉ đổi trạng thái buổi thì:

- lưới xếp lịch **vẫn tô xám** ô "1A1 · Thứ 2 · Tiết 1" cho tới tận tháng 8 sang năm;
- admin **không xếp nổi giáo viên thay** vào đúng cái lớp vừa trống;
- hủy xong thành ra **tự khóa chính mình**.

Nên luật là: **hủy giữa kỳ = THU HẸP `EndDate` về hôm trước ngày hiệu lực.**

Mà thu hẹp rồi thì mất mốc gốc → "Bỏ hủy" không biết trả về đâu. Vì thế phải có
`OriginalEndDate`. Hai cột này sinh ra từ **một** ràng buộc kỹ thuật, không phải từ mong
muốn "lưu nhiều cho chắc".

> Đây cũng là câu trả lời khi bị hỏi *"cột `OriginalEndDate` có thừa không?"* — không, nó
> là cái giá phải trả để `EndDate` được dùng làm khóa của luật chống trùng lịch.

---

## 2. Mô hình dữ liệu (V39)

```sql
ALTER TABLE Assignment ADD
    CancelEffectiveDate DATE          NULL,  -- ngày đầu tiên KHÔNG dạy nữa
    OriginalEndDate     DATE          NULL,  -- EndDate trước khi bị thu hẹp
    CancelReason        NVARCHAR(500) NULL,  -- bắt buộc ở tầng service
    CancelledAt         DATETIME2(3)  NULL,
    CancelledByUserId   INT           NULL;
```

### KHÔNG nới `CK_Assignment_Status` — vì sao

Bản năng đầu tiên là thêm `'TERMINATED'` vào ràng buộc CHECK. Nhưng nghĩ kỹ thì **sai về
nghiệp vụ**: phiếu "kết thúc sớm" *vẫn còn buổi phải dạy* cho tới ngày dừng — dưới CSDL nó
**đang chạy thật**. Ghi là "đã hủy" mới là nói dối.

Nên `TERMINATED` chỉ là **nhãn hiển thị**, tính tại chỗ — đúng cách dự án đã làm với nhãn
"Hết hạn" của phiếu quá hạn xác nhận:

```java
private static String effectiveStatus(Assignment a) {
    if (a.isExpiredPending()) {
        return AssignmentStatus.EXPIRED;
    }
    return a.isTerminated() ? AssignmentStatus.TERMINATED : a.getStatus();
}
```

```java
// Assignment.java
public boolean isTerminated() {
    return cancelEffectiveDate != null && AssignmentStatus.ACTIVE.equals(status);
}
```

Được ba thứ: migration nhẹ hơn (không phải drop/tạo lại CHECK trên bảng có dữ liệu), không
có trạng thái nào lệch với thực tế, và `statusCounts()` + bộ lọc tab tự đúng theo vì cả hai
vốn đã đi qua `effectiveStatus`.

### Bảng `AssignmentLeaveRequest` — vì sao không thêm cột vào `Assignment`

Một phiếu có thể bị **xin nghỉ → bị từ chối → xin lại**. Nhét vào `Assignment` là lần sau
ghi đè lần trước, mất đúng phần lịch sử cần để đối chiếu. Thêm một index lọc:

```sql
CREATE UNIQUE INDEX UX_AssignmentLeaveRequest_Pending
    ON AssignmentLeaveRequest(AssignmentId) WHERE Status = 'PENDING';
```

Mỗi phiếu đúng **một** đơn đang chờ; đơn đã duyệt/từ chối không vướng ràng buộc nên xin
lại được. (Tầng service vẫn kiểm trước để người dùng nhận câu tiếng Việt thay vì lỗi SQL
thô — index là chốt chặn cuối, không phải nơi báo lỗi.)

---

## 3. Hai nhánh của `cancel()`

```java
boolean huyToanBo = a.getConfirmedAt() == null || !tuNgay.isAfter(a.getStartDate());
if (huyToanBo) {
    tuNgay = a.getStartDate();
}
```

| Nhánh | Khi nào | Làm gì |
|---|---|---|
| **Hủy toàn bộ** | phiếu **chưa từng** được xác nhận, hoặc ngày hủy rơi vào/trước ngày bắt đầu | `Status = CANCELLED`, hủy **mọi** buổi kể cả buổi quá khứ. Không đụng `EndDate` — `holdsTimeSlot()` vốn đã trả `false` cho `CANCELLED` nên khung giờ tự nhả. |
| **Kết thúc sớm** | phiếu **đã chạy thật** và ngày hủy nằm giữa giai đoạn | `Status` giữ `ACTIVE`, buổi trước ngày ấy **giữ nguyên**, `EndDate ← tuNgay - 1`. |

Nhánh một giữ đúng luật cũ: phiếu chưa ai xác nhận thì **không buổi nào có hiệu lực**, xóa
sạch cũng chẳng mất gì; chỉ phiếu đã chạy mới phải giữ buổi đã dạy cho chấm công/lương.

Một chỗ nhỏ nhưng dễ mất dữ liệu nếu quên:

```java
// Chỉ ghi mốc gốc ở lần thu hẹp ĐẦU TIÊN
if (a.getOriginalEndDate() == null) {
    a.setOriginalEndDate(a.getEndDate());
}
```

Không có `if` này thì hủy lần hai (dời ngày hủy sớm hơn) sẽ ghi đè mốc gốc bằng mốc **đã bị
thu hẹp**, và "Bỏ hủy" trả phiếu về sai chỗ — mất hẳn phần đuôi.

---

## 4. Buổi đang dạy dở — bỏ hàng rào 409, thay bằng mốc cắt

Hàng rào cũ chặn **cả thao tác**. Thay bằng một dòng số học:

```java
LocalDateTime bayGio = BusinessTime.now();
LocalDateTime dauNgayHuy = tuNgay.atStartOfDay();
LocalDateTime mocCat = dauNgayHuy.isAfter(bayGio) ? dauNgayHuy : bayGio;
...
if (!huyToanBo && s.getStartTime().isBefore(mocCat)) {
    continue; // buổi đã dạy xong hoặc đang dạy dở — giữ nguyên
}
```

**Mốc cắt không bao giờ lùi về trước thời điểm hiện tại.** Hệ quả:

- buổi **đang dạy dở** (bắt đầu 08:00, giờ 08:30) → `startTime < mocCat` → không bị đụng,
  giáo viên bấm check-out bình thường và vẫn được tính công;
- buổi **vừa dạy xong sáng nay đã có chấm công** → cũng an toàn, dù ngày hủy là hôm nay;
- buổi **chưa bắt đầu** → bị hủy, kể cả buổi 08:00 khi bấm lúc 07:55 (đúng: hủy trước giờ dạy).

Nhờ vậy `AttendanceRepository.countDangDayDoTheoPhanCong` không còn chỗ dùng → **gỡ hẳn**
thay vì để lại làm code chết. (`countDangDayDoTheoGiaoVien` là truy vấn **khác**, dùng cho
luồng xóa hồ sơ giáo viên — vẫn giữ.)

---

## 5. "Bỏ hủy" nay làm ba việc, và có một hàng rào mới

```java
a.setEndDate(ngayKetThucGoc);        // 1. trả mốc gốc
a.setOriginalEndDate(null);
a.setCancelEffectiveDate(null);
...
checkTeacherTimeConflict(..., a.getStartDate(), ngayKetThucGoc, id);   // 2. dò trùng
conflictChecker.checkClass(..., a.getStartDate(), ngayKetThucGoc, id); //    TRÊN GIAI ĐOẠN ĐẦY ĐỦ
```

Chi tiết dễ bỏ sót ở bước 2: phải dò trên **`ngayKetThucGoc`**, không phải `EndDate` đang
có. Khung giờ vừa được nhả ra chính là phần **đuôi** của phiếu — nếu chỉ dò tới mốc đã thu
hẹp thì đúng cái đoạn có nguy cơ bị chiếm lại là đoạn không ai kiểm.

### Hàng rào NGÀY NGHỈ — lỗi này đã tồn tại từ trước, đợt này mới lộ

Việc thứ ba là bật lại các buổi. Bản cũ bật lại **mọi** buổi `CANCELLED` của phiếu:

```java
for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
    if ("CANCELLED".equals(s.getStatus())) {   // ← bản cũ
        s.setStatus(restored);
```

Vấn đề: `HolidayService` cũng đánh buổi ngày lễ là `CANCELLED`. Hai loại buổi **cùng một
trạng thái**, vòng lặp nhìn chúng y hệt nhau. Bật lại buổi ngày lễ là dựng lại đúng cái
buổi "ma" mà V29 sinh ra để chặn: `AttendanceSweepService` quét buổi đã qua mà không ai
chấm rồi **tự ghi VẮNG**, trừ thẳng vào lương của người không hề được gọi đi dạy.

Bản mới có hai điều kiện:

```java
LocalDate ngay = s.getStartTime().toLocalDate();
if (ngay.isBefore(tuNgayHuy) || holidays.isOff(ngay, truongCuaO.get(s.getSourceSlotId()))) {
    continue;
}
```

- `ngay.isBefore(tuNgayHuy)` — chỉ bật lại buổi bị **chính lần hủy đó** tắt. Buổi bị hủy
  trước đó vì lý do khác thuộc về một quyết định khác, không được hồi sinh ngầm.
- `holidays.isOff(...)` — tra theo trường của **từng ô lịch** (`sourceSlotId → schoolId`),
  vì từ V27 một phiếu trải nhiều trường mà kỳ nghỉ riêng chỉ thuộc một trường.

**Bằng chứng đo được trên dữ liệu thật:** hủy rồi bỏ hủy phiếu #445 (120 buổi) thì đúng
**3 buổi ngày 31/05/2027 nằm im** — cả ba rơi vào kỳ *Nghỉ hè 2027*. Không có hàng rào
này thì ba buổi đó quay lại `APPROVED` và trở thành ba dòng vắng.

---

## 6. Đơn xin nghỉ — bản gọn, không đẻ thêm màn quản lý

Luồng: giáo viên gửi → thông báo **có nút bấm** tới mọi người có `ASSIGNMENT_MANAGE` →
admin Duyệt/Từ chối **ngay trên chuông**.

Điểm thiết kế quan trọng nhất — **đơn không tự sửa dữ liệu**:

```java
applicationContext.getBean(AssignmentService.class)
        .cancel(don.getAssignmentId(), don.getEffectiveDate(), "Giáo viên xin nghỉ: " + don.getReason());
```

Duyệt đơn gọi **đúng** luồng hủy ở mục 3–5. Hai đường (admin bấm tay / duyệt đơn) chạy qua
cùng một đoạn mã nên **không thể lệch nhau** về cách xử lý buổi đã dạy, `EndDate`, hay
thông báo. Gọi qua `ApplicationContext` chứ không tiêm thẳng vì `AssignmentService` đã tiêm
`AssignmentApprovalService`, mà service này cũng tiêm nó — tiêm thẳng chiều còn lại là một
vòng phụ thuộc lúc khởi động.

### Quyền: chốt bằng SỞ HỮU, không bằng permission

`AssignmentLeaveRequestController` phần của giáo viên **cố ý không gắn** `@PreAuthorize`.
Vai trò `TEACHER` chỉ được seed 4 quyền (`SCHEDULE_VIEW`, `ATTENDANCE_VIEW`,
`EVALUATION_VIEW`, `LESSON_VIEW`) — đòi `ASSIGNMENT_VIEW` ở đây là **khóa cửa chính giáo
viên**. Chốt chặn là quyền sở hữu do service ép theo hồ sơ giáo viên của người đang đăng
nhập, đúng cách `NotificationController` đã làm với lời mời dạy.

> Ghi chú cho lần sau: comment trong `AssignmentService.scopedTeacherId` nói *"GV có
> ASSIGNMENT_VIEW nhưng không phải staff"* — **không đúng với seed hiện tại**. Endpoint
> `/leave-requests/my-assignments` dùng lại `AssignmentService.list()` được là nhờ nó tự ép
> phạm vi theo hồ sơ, chứ không nhờ quyền.

Hai bổ sung dùng chung ở `NotificationService`:

- `publishToPermission(..., boolean requiresAction)` — thông báo có nút bấm gửi theo quyền;
- `closePendingActions(refEntity, refId, actionStatus)` — đóng nút của **mọi** người nhận
  khi việc đã được quyết. Thiếu bước này thì người thứ hai bấm Duyệt là duyệt vào khoảng
  không. `AssignmentApprovalService.closeOpenInvites` nay ủy quyền cho hàm này thay vì lặp
  lại vòng lặp.

---

## 7. Màn quản trị — tách HỦY khỏi XÓA

| | Trước | Sau |
|---|---|---|
| **Hủy** | = xóa, phiếu rơi thẳng vào thùng rác | dừng dạy kể từ một ngày; phiếu **vẫn trong danh sách**, bỏ hủy được |
| **Xóa** | không tồn tại riêng | mới có, đưa vào thùng rác (chỉ hiện với phiếu không còn "sống") |
| **Bỏ hủy** | endpoint có nhưng FE không gọi | nút thật trên dòng |

Modal Hủy hỏi **hai** thứ: ngày dừng (mặc định hôm nay) và lý do (bắt buộc, ghi thẳng vào
thông báo của giáo viên). Cùng một modal dùng cho hủy một phiếu và hủy hàng loạt — hai
đường hỏi đúng ba thứ giống hệt nhau nên gộp làm một `cancelForm`.

Thêm tab **"Kết thúc sớm"**, và dòng phụ dưới badge: *Dừng từ 01/10/2026 (đáng lẽ đến
22/08/2027)* + lý do trong ngoặc kép — dùng lại đúng class `.reject-why` sẵn có.

Hủy hàng loạt nay chọn được cả phiếu **Đang dạy** (`isSelectable`) — đó chính là lúc cần
nhất: một giáo viên nghỉ việc là rút khỏi cả chục lớp một lúc. `bulkCancel` chuyển từ gọi
`softDelete` sang gọi `cancel`, mỗi phiếu một giao dịch riêng qua proxy, lỗi gom lại trả về
theo từng phiếu.

**Dashboard** cũng phải sửa theo: `nhanTrangThaiPhanCong` đọc thêm `CancelEffectiveDate`,
nếu không phiếu kết thúc sớm hiện "Đang dạy" trong khi màn Phân công nói "Kết thúc sớm" —
hai màn cãi nhau về cùng một phiếu.

---

## 8. Màn giáo viên

Hai thứ, đúng phạm vi đã chốt:

1. **Thông báo trong chuông** khi bị cắt lịch — ngày dừng + số buổi + lý do nguyên văn.
   Đây là thứ vá lỗ hổng số 1 ở mục 0.
2. **Nút "Xin nghỉ dạy"** trên *Lịch dạy của tôi* → `LeaveRequestModal.vue`. Modal làm hai
   việc: gửi đơn mới, và xem các đơn đã gửi kèm kết quả — đó đúng là hai câu hỏi liên tiếp
   của người dùng, tách thành hai màn chỉ tổ bắt họ đi tìm.

Buổi bị hủy **vẫn ẩn** khỏi lịch (`/schedules/mine` chỉ trả `APPROVED`) — quyết định có ý
thức, không phải bỏ sót: lịch giữ sạch, còn việc báo tin giao cho chuông.

Khung nút Xác nhận/Từ chối trong `PortalShell.vue` được dùng lại cho cả hai loại thông báo
cần hành động, chỉ đổi endpoint và chữ trên nút theo `refEntity`:

```js
const isLeaveRequest = (n) => n.refEntity === 'AssignmentLeaveRequest'
```

---

## 9. Những chỗ CỐ Ý không làm — ghi lại để lần sau khỏi bàn lại

| Không làm | Vì sao |
|---|---|
| Hủy **một buổi lẻ** / bỏ **một tiết/tuần** | Đã chốt phạm vi: cấp phiếu là đủ cho mọi tình huống nghỉ thật. Hủy một tiết/tuần còn phải đụng `AssignmentSlot` và luật trùng lịch. |
| Luồng **chuyển giao** GV A → GV B | Hủy xong admin tạo phiếu mới như bình thường. Nút một-bấm nghe hay nhưng thêm một đường ghi mới vào chỗ dễ trùng lịch nhất. |
| Bảng **nhật ký trạng thái phiếu** (đối xứng `ScheduleStatusLog`) | Cấp buổi đã có trigger ghi đủ. Cấp phiếu chỉ giữ **lần hủy gần nhất** — chấp nhận được vì hủy rồi bỏ hủy nhiều lần là hiếm. |
| Màn quản lý đơn xin nghỉ riêng | Bản gọn: admin quyết ngay trên chuông. Có sẵn `GET /leave-requests/pending` nếu sau này cần dựng màn. |
| Cập nhật `database/schema/TSDMS_Schema.sql` | File đó tự khai là bản mirror **sau V10**, không theo migration mới (V17 cũng không có trong đó). |

---

## 10. Kiểm thử

### Migration

| Môi trường | Kết quả |
|---|---|
| DB **trắng** dựng lại từ đầu (`TSDMS_V39_BLANK`) | 38 migration V1→V39 chạy sạch, app khởi động OK, đã kiểm 5 cột + bảng + 2 index rồi **drop DB** |
| DB **thật** 86.745 buổi dạy | `Successfully applied 1 migration, now at version v39` — **125 ms** |

### Đo trên phiếu #445 (dữ liệu thật, hủy từ 01/10/2026 rồi bỏ hủy)

| Đo cái gì | Trước | Sau |
|---|---|---|
| Giờ bận của GV, tháng 11/2026 | 3 ô | **0 ô** ✓ xếp được người thay |
| Giờ bận của GV, 10–20/09 (trước ngày hủy) | 3 ô | **3 ô** ✓ vẫn phải dạy |
| Ô lớp "1A1 · Thứ 2 · Tiết 1" trong lưới, từ 05/10 | bị khóa | **trống** ✓ |
| Lịch giáo viên tháng 9, trường đó | 12 buổi | **12 buổi** ✓ giữ nguyên |
| Lịch giáo viên tháng 11, trường đó | 15 buổi | **0 buổi** (tổng 47 → 32, các trường khác vẫn còn) |
| Buổi ngày nghỉ 31/05/2027 sau khi bỏ hủy | — | **vẫn CANCELLED** ✓ không hồi sinh |

### Trường hợp biên (qua API thật)

`400` hủy không lý do · `400` hủy ngày quá khứ · `403` giáo viên tự duyệt đơn của mình ·
`404` xin nghỉ phân công không thuộc về mình · `409` gửi đơn trùng · `409` duyệt lại đơn đã
xử lý.

### Unit test — và cái suýt lọt

**348 unit test xanh**, nhưng chỉ sau khi sửa một test bị chính đợt này làm hỏng:

```
AssignmentReactivateConflictTest.khoiPhuc_vanGiuNguyenLuatTrungGioCuaChinhGiaoVien
NullPointerException: ...HolidayRepository.findOverlapping(...) because "this.holidayRepo" is null
```

Test dựng service bằng `@InjectMocks` và **không khai** `@Mock HolidayRepository` — Mockito
tiêm `null`, mà `reactivate` nay hỏi lịch nghỉ. Đã thêm mock, và thêm luôn một ca mới
`boHuy_khongHoiSinhBuoiRoiVaoNgayNghi` khóa lại đúng hành vi tinh tế nhất của đợt này.

> **Bài học:** `mvnw compile` xanh **không** nói gì về `src/test`. Đợt này chạy `compile`
> nhiều lần và tưởng đã ổn; chỉ tới lúc dựng `git worktree` riêng để kiểm commit trước khi
> đẩy mới lộ. Cách kiểm chắc chắn: `git worktree add --detach <đường-dẫn-NGẮN> <commit>` rồi
> `mvnw test` **trong worktree đó** — vừa chạy đủ test, vừa chứng minh commit đứng một mình
> được (thư mục làm việc lúc đó còn 5 file của module Lịch nghỉ đang làm dở, không nằm trong
> commit này). Đường dẫn worktree phải ngắn, để trong `.../Temp/claude/<session>/scratchpad`
> thì Windows báo *Filename too long*.

---

## 11. Bản đồ file

| File | Việc |
|---|---|
| `V39__huy_phan_cong_co_ngay_hieu_luc.sql` | 5 cột trên `Assignment` + bảng `AssignmentLeaveRequest` + 2 index |
| `entity/Assignment.java` | 5 trường mới + `isTerminated()` |
| `entity/AssignmentStatus.java` | hằng `TERMINATED` (chỉ để hiển thị) |
| `entity/AssignmentLeaveRequest.java` · `repository/...` | đơn xin nghỉ |
| `service/AssignmentService.java` | `cancel(id, ngày, lý do)`, `reactivate` viết lại, `softDelete`/`restore`/`bulkCancel` theo sau, `effectiveStatus` |
| `service/AssignmentLeaveRequestService.java` | luồng đơn; duyệt → gọi lại `cancel` |
| `service/AssignmentApprovalService.java` | 3 hàm thông báo mới; `closeOpenInvites` ủy quyền |
| `service/NotificationService.java` | `publishToPermission(..., requiresAction)` + `closePendingActions` |
| `repository/AttendanceRepository.java` | **gỡ** `countDangDayDoTheoPhanCong` |
| `repository/DashboardQueryRepository.java` | nhãn trạng thái đọc thêm `CancelEffectiveDate` |
| `controller/AssignmentController.java` · `AssignmentLeaveRequestController.java` | API |
| `dto/` | `AssignmentCancelRequest`, `AssignmentBulkCancelRequest`, 3 DTO đơn xin nghỉ, `AssignmentResponse` +4 trường |
| `pages/AssignmentPage.vue` | tách Hủy/Xóa, tab mới, modal ngày+lý do, nút Bỏ hủy |
| `components/LeaveRequestModal.vue` · `pages/TeacherSchedulePage.vue` | màn giáo viên |
| `layouts/PortalShell.vue` | chuông xử lý thêm loại thông báo đơn xin nghỉ |
| `api/assignments.js` · `api/leaveRequests.js` | nối `cancel`/`reactivate` (vốn bị bỏ quên) + API đơn |
| `docs/kich-ban-demo-huy-phan-cong.md` | kịch bản demo buổi bảo vệ |
| `database/TSDMS_TuDien_DB.md` | mục 19d/19e |
