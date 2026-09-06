# Lịch nghỉ: nút "Buổi dạy" chỉ hiện khi thật sự có việc + trang bám page-common

> Ngày: 2026-09-06 · Phần: fullstack · Nhánh: `feat/huy-phan-cong-ngay-hieu-luc` · Migration: **không có**
>
> Nối tiếp [2026-08-22-fullstack-lich-day-lich-nghi.md](2026-08-22-fullstack-lich-day-lich-nghi.md)
> (thùng rác Lịch nghỉ) và [2026-09-05-fullstack-huy-phan-cong-ngay-hieu-luc.md](2026-09-05-fullstack-huy-phan-cong-ngay-hieu-luc.md)
> — đợt trước ghi rõ *"thư mục làm việc lúc đó còn 5 file của module Lịch nghỉ đang làm dở"*.
> Đây chính là 5 file đó, làm nốt.

---

## 0. Vì sao có đợt này

Màn **Lịch nghỉ** vẽ nút **"Buổi dạy"** ở **mọi** dòng. Bấm vào thì phần lớn nhận về một hộp
thoại rỗng — vì đa số kỳ nghỉ chẳng vướng buổi nào.

Nghe như chuyện thẩm mỹ, nhưng hậu quả thật hơn thế:

| # | Vấn đề | Hậu quả |
|---|---|---|
| 1 | Nút hiện ở mọi dòng | Mắt không biết dòng nào cần xử lý. Bảng 20 dòng thì 20 nút giống hệt nhau. |
| 2 | Không vướng gì thì hộp thoại **tự đóng, im lặng** | Bấm xong không thấy gì xảy ra — nút trông y như một nút **hỏng**. Người dùng bỏ qua luôn kỳ nghỉ đó thay vì biết rằng hệ thống đã kiểm và sạch. |
| 3 | Gọi API lỗi cũng **tự đóng, im lặng** | Tệ hơn hẳn: mất quyền hoặc backend chết mà màn hình phản ứng y hệt lúc "không có gì" → người dùng yên tâm rằng lịch sạch, trong khi buổi dạy vẫn nằm nguyên trong kỳ nghỉ. |
| 4 | Dọn xong, hộp thoại đóng, bảng **không tải lại** | Nút vẫn nằm đó. Bấm lại chỉ nhận "không vướng buổi nào" — trông như thao tác vừa rồi không ăn. |

Và một món nợ kỹ thuật đi kèm: `HolidayListPage.vue` **tự khai lại từ đầu** nút, bảng, badge,
modal, phân trang — trong khi 11 trang khác đã bám `assets/page-common.css` từ lâu.

---

## 1. Cái bẫy quyết định toàn bộ thiết kế backend

Yêu cầu nghe rất tầm thường: *"cho tôi biết trong 20 kỳ nghỉ đang hiện, kỳ nào còn việc."*
Bản năng đầu tiên là một câu SQL nối thẳng `Holiday` với `Schedule`.

**Đã thử ba cách, cả ba đều nổ khi chạy THẬT qua ứng dụng:**

| Cách viết | sqlcmd | Qua JDBC (ứng dụng) |
|---|---|---|
| Truy vấn con tương quan | nhanh | treo |
| JOIN + GROUP BY | **0,5 giây** | **147 GIÂY** |
| CTE gom trước | — | **26 giây** |

Lý do không nằm ở câu SQL mà ở **chế độ phiên**: driver JDBC để `ARITHABORT OFF`, còn sqlcmd
để `ON`. Hai chế độ → hai kế hoạch thực thi khác nhau → cùng một câu lệnh chạy nửa giây ở
cửa sổ này và hơn hai phút ở cửa sổ kia. Bản CTE thì thua vì SQL Server **bung CTE ra** chứ
không hiện thực hóa nó, nên nó bị tính lại nhiều lần.

> **Bài học ghi lại cho lần sau:** đo một câu SQL bằng sqlcmd rồi kết luận "nhanh" là **vô
> nghĩa**. Phải đo qua đúng đường mà ứng dụng đi.

### Cách đi vòng: đừng cho optimizer chỗ nào để bóp méo

Bỏ hẳn ý định JOIN. Hỏi **hai câu phẳng**, mỗi câu một lượt quét rồi gom trùng:

```sql
-- những NGÀY còn buổi dạy chưa hủy, kèm trường
SELECT DISTINCT CAST(s.StartTime AS DATE), sl.SchoolId
  FROM Schedule s LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
 WHERE s.IsDeleted = 0 AND s.Status <> 'CANCELLED'
   AND s.StartTime >= :from AND s.StartTime < :to
```

```sql
-- những NGÀY có dòng chấm công VẮNG do máy tự ghi, kèm trường
SELECT DISTINCT a.WorkDate, sl.SchoolId
  FROM Attendance a LEFT JOIN Schedule s ON s.Id = a.ScheduleId
                    LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
 WHERE a.Status = 'ABSENT' AND a.CheckInMethod = 'SYSTEM'
   AND a.WorkDate BETWEEN :from AND :to
```

Không có gì để optimizer chọn sai: quét một lượt, gom trùng, xong. **~230 ms cho 5.200 dòng**
trên bộ 86.865 buổi, đo đúng qua chế độ của JDBC.

Việc đối chiếu ngày với khoảng của từng kỳ nghỉ làm **bên Java** — 20 kỳ nghỉ × 5.200 dòng là
vài trăm nghìn phép so sánh hai `LocalDate`, tính bằng micro giây. Đây là chỗ đổi một thứ đắt
(kế hoạch thực thi của SQL Server, không kiểm soát được) lấy một thứ rẻ và **đoán trước được**.

> Vì sao không thêm chỉ mục `(IsDeleted, StartTime)` cho seek được? **Đã thử rồi và phải gỡ** —
> nó làm hỏng kế hoạch của truy vấn lịch sắp tới. Xem
> [2026-08-22-fullstack-du-lieu-lon-quet-lo-hong.md](2026-08-22-fullstack-du-lieu-lon-quet-lo-hong.md).

---

## 2. Vì sao là endpoint RIÊNG, không gộp vào `GET /holidays`

Hai câu trên quét toàn bảng nên tốn **~0,4 giây**. Gộp vào danh sách là bắt bảng kỳ nghỉ chờ
chừng đó **mỗi lần mở trang**, chỉ để biết có vẽ một cái nút hay không.

Nên tách `GET /holidays/with-issues?ids=...`, và phía Vue gọi nó **rời, không `await`**, sau
khi bảng đã vẽ xong:

```js
items.value = res.data?.content ?? []
total.value = res.data?.totalElements ?? 0
loadIssues()          // ← cố ý không await
```

Được hai thứ: bảng hiện **ngay**, nút xuất hiện sau; và lúc câu này chậm hay hỏng thì người
dùng **vẫn đọc và sửa được lịch nghỉ** — chức năng chính không chết theo một chi tiết phụ.

Một chi tiết nhỏ dễ mất cả buổi nếu quên:

```java
@GetMapping("/with-issues")   // phải đặt TRƯỚC @GetMapping("/{id}")
```

`"with-issues"` cũng khớp mẫu `{id}` — để sau là Spring gọi `detail("with-issues")` rồi ném
lỗi ép kiểu.

Và ở FE, kết quả về muộn phải biết tự bỏ đi:

```js
// Danh sách có thể đã đổi trong lúc chờ (đổi trang, đổi bộ lọc) — kết quả cũ về sau thì bỏ.
if (items.value.some((h, i) => h.id !== ids[i])) return
```

---

## 3. Luật của màn hình: **có nút ⇔ mở ra là có nội dung**

Luật này nghe hiển nhiên nhưng nó ràng buộc backend rất chặt: `holidaysWithIssues` phải định
nghĩa "còn việc" **đúng bằng** thứ hộp thoại sẽ hiện, không hơn không kém.

Hộp thoại hiện bốn con số, đến từ **hai hàm khác**: `impact()` (buổi chưa diễn ra + buổi đã
diễn ra) và `absences()` (dòng vắng sửa được + dòng vắng bị khóa kỳ lương). Điều kiện tự đóng
của nó là cả bốn đều bằng 0.

Nên phép kiểm tra mới phải khớp từng chi tiết với hai hàm kia:

| Chi tiết | `impact()`/`absences()` | Câu mới |
|---|---|---|
| Buổi bị loại | `IsDeleted`, `Status = CANCELLED` | y hệt (`Status` là `NOT NULL` nên `<>` an toàn) |
| Hai đầu khoảng ngày | đóng cả hai (`toDate` lấy tới `LocalTime.MAX`) | `>= from` và `< toDate+1` — tương đương |
| Buổi ĐÃ diễn ra | vẫn được kể trong hộp thoại | vẫn tính là "còn việc" |
| Dòng vắng | chỉ nguồn `SYSTEM` | y hệt |
| Dòng vắng bị **khóa kỳ lương** | vẫn hiện (báo riêng) | vẫn tính |
| Phạm vi trường | `sourceSlotId → schoolId`, kỳ nghỉ toàn hệ thống nhận tất | y hệt |

**Ca tinh tế nhất — và là lý do phải hỏi hai câu chứ không một:** một kỳ nghỉ đã hủy sạch buổi
dạy rồi thì câu đếm buổi **không thấy gì**, nhưng hủy buổi **KHÔNG xóa** dòng vắng mà job nền
đã ghi. Kỳ nghỉ đó vẫn còn việc phải dọn. Chỉ hỏi buổi dạy là ẩn mất nút ở đúng dòng cần nó
nhất. Ca này đã được khóa lại bằng test (mục 6).

### Hỏng thì HIỆN HẾT nút, đừng ẩn hết

```js
catch { issuesFailed.value = true }   // → v-if="issuesFailed || issues.has(id)"
```

Không đối xứng, và cố ý: ẩn nhầm là **cắt mất đường vào** chỗ duy nhất dọn được buổi dạy vướng
kỳ nghỉ; hiện thừa thì cùng lắm bấm vào và nhận "không vướng gì". Khi không chắc, nghiêng về
phía sai ít thiệt hại hơn.

---

## 4. Ba chỗ im lặng, nay đều nói ra

```js
if (nothing) {
  impact.open = false
  showToast(`"${h.name}" không vướng buổi dạy nào — không phải xử lý gì.`)
}
```

```js
} catch (e) {
  impact.open = false
  showToast(e.response?.data?.message || 'Không kiểm tra được buổi dạy của kỳ nghỉ này.', 'error')
}
```

Và đóng hộp thoại **sau khi đã làm gì đó** thì nạp lại bảng, nếu không nút vẫn nằm nguyên chỗ
cũ theo dữ liệu của lần tải trước:

```js
function closeImpact() {
  const worked = !!impact.done || !!absence.done
  impact.open = false
  if (worked) load()
}
```

---

## 5. Trang bám `page-common.css` — xóa nhiều hơn thêm

`HolidayListPage.vue` tự khai lại `.btn`, `.badge`, `table`, `.modal`, `.pagination`,
`.empty-state`… trong khi 11 trang khác đã dùng chung từ lâu. Đợt này gỡ hết, chỉ giữ lại
những gì **riêng của Lịch nghỉ** (tab Đang dùng/Thùng rác, dòng mốc năm, hai phần của hộp
thoại "Buổi dạy"). Cụm phân trang tự chế thay bằng `components/ui/Pagination.vue`.

Ba thứ thêm vào cho dễ đọc bảng dài:

- **Dòng mốc năm** xen giữa các kỳ nghỉ. Server trả đã sắp `fromDate` giảm dần nên cùng năm
  vốn nằm liền nhau — chỉ cắt khúc, không phải sắp lại. Làm bằng một mảng **phẳng** (`rows`)
  để bảng vẫn chỉ có một `<tbody>`.
  Tab **Thùng rác bỏ qua mốc năm**: nó sắp theo *ngày xóa*, chèn vào sẽ ra "2026 · 2025 · 2026".
- **Badge "Đang nghỉ" / "Đã qua"** — `isPast` cũ chỉ phân hai loại, nay `phase()` trả
  `past | now | next`. Vẫn gọi `isoToday()` mỗi lần chứ không tính sẵn hằng số: trang này hay
  mở suốt ngày, hằng số sẽ đứng yên qua nửa đêm.
- Dòng đã qua làm nhạt **chữ** chứ không hạ `opacity` cả dòng, để nhãn "Đã qua" còn đọc được.

---

## 6. Kiểm thử

`HolidayWithIssuesTest` khóa lại đúng luật ở mục 3 — cần thiết vì phép kiểm tra mới **không
dùng chung mã** với `impact()`/`absences()`, hai bên lệch nhau lúc nào không biết:

| Ca | Khóa cái gì |
|---|---|
| `khong_hoi_gi_thi_khong_cham_vao_db` | danh sách rỗng / `null` → về sớm, không gọi repo |
| `chi_tra_ve_ky_nghi_that_su_con_viec` | 4 kỳ nghỉ, chỉ 2 kỳ có việc — trong đó **kỳ chỉ còn dòng vắng** phải được tính |
| `ky_nghi_toan_he_thong_nhan_ngay_cua_moi_truong` | buổi không gắn ô lịch (`schoolId` null) vẫn tính cho kỳ nghỉ toàn hệ thống |
| `ngay_sat_ngoai_khoang_khong_tinh_la_con_viec` | hai đầu khoảng ngày là **đóng** — 09/05 và 12/05 nằm ngoài kỳ 10–11/05 |

Ca thứ hai cũng khóa luôn phạm vi trường: kỳ nghỉ riêng của trường A không nhận buổi của
trường B, dù rơi đúng ngày.

Toàn bộ **352 unit test xanh** (348 của đợt trước + 4 ca mới).

> Nhắc lại bài học của đợt trước: `mvnw compile` xanh **không** nói gì về `src/test`. Đợt này
> chạy `mvnw test-compile` rồi mới chạy test — và `spotless:check` chỉ nổ ở pha `test`, nên
> `test-compile` xanh cũng chưa đủ để yên tâm commit.

---

## 7. Bản đồ file

| File | Việc |
|---|---|
| `repository/HolidayRepository.java` | 2 truy vấn native phẳng + ghi lại lý do không JOIN |
| `service/HolidayService.java` | `holidaysWithIssues(ids)` — đối chiếu ngày ↔ khoảng bên Java |
| `controller/HolidayController.java` | `GET /holidays/with-issues` (đặt **trước** `/{id}`) |
| `api/holidays.js` | `withIssues(ids)` |
| `pages/HolidayListPage.vue` | nút theo điều kiện, 3 chỗ im lặng nay báo ra, nạp lại sau khi dọn, mốc năm + badge giai đoạn, bám `page-common.css`, dùng `Pagination` |
| `test/.../HolidayWithIssuesTest.java` | 4 ca ở mục 6 |
| `docs/kich-ban-demo-huy-phan-cong.md` | sửa theo buổi diễn tập 05/09 (xem dưới) |

### Kịch bản demo — sửa theo những gì thực tế chạy ra

Diễn tập trọn một lượt ngày 05/09 lộ bốn chỗ bản viết trên giấy nói sai:

- Lọc tên "Tô Bảo Mai" ra **3 dòng**, không phải 1 — phải nói rõ dòng nào là dòng cần dùng.
- Cửa sổ B **bắt buộc** bật bộ lọc Trường, không thì lưới trộn cả buổi ở trường khác và
  **không nhìn ra điều đang nói**.
- Bước 6 tìm dòng #445 bằng cách cuộn → thay bằng bấm tab **"Kết thúc sớm"**, ra đúng một dòng.
- Thêm một dòng vào bảng sự cố: form Tạo phân công kẹt ở "Đang tải…" (đã gặp một lần ngay sau
  khi đăng nhập; API đo được chỉ 0,4 giây nên không phải chậm — **F5 là xong**, đừng ngồi chờ).
