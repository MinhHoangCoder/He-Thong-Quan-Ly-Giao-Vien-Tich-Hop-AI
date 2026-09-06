# Kịch bản demo — Hủy phân công có ngày hiệu lực

> Bảo vệ ĐATN · 8:00 ngày 07/09/2026 · phần "Hủy phân công + Đơn xin nghỉ".
> Mọi con số trong file đo được từ chính máy demo (DB có 86.745 buổi dạy), không phải ví dụ.
> **Đã diễn tập trọn một lượt ngày 05/09** — các bước dưới đây là bản đã sửa theo những gì
> thực tế chạy ra, không phải bản viết trên giấy.

| | |
|---|---|
| **Phiếu diễn** | #445 — Tô Bảo Mai · TH Đằng Giang · 3 lớp 1A1/1A2/1A3 |
| **Giai đoạn** | 23/08/2026 → 22/08/2027 |
| **Ngày hủy sẽ chọn** | 01/10/2026 |
| **Thời lượng** | ≈ 4 phút · 6 bước (+1 bước tùy chọn) |
| **Cửa sổ trình duyệt** | A = `admin` · B = `gv.tobaomai` (cửa sổ ẩn danh) |

---

## 1. Chuẩn bị (xong trước 15 phút)

- [ ] **Dọn cổng 8080 và 5173 trước khi chạy.** Tiến trình `java` và `vite` sống sót qua việc
      đóng cửa sổ terminal. Còn tiến trình cũ thì code mới không lên mà nhìn bề ngoài vẫn "chạy
      bình thường".
- [ ] **Chạy backend, chờ dòng `Started TsdmsApplication`.** Nếu Flyway báo *checksum mismatch*
      thì DỪNG, đừng xóa dòng nào trong `flyway_schema_history` — xem mục 4.
- [ ] **Kiểm tra phiếu #445 đang sạch** (câu lệnh ở mục 5). Đây là bước hay quên nhất: hôm trước
      diễn tập mà quên bấm **Bỏ hủy** thì sáng hôm sau phiếu đang ở trạng thái "Kết thúc sớm" và
      cả kịch bản mất điểm nhấn.
- [ ] **Cửa sổ A** — đăng nhập `admin`, mở **Phân công**, bấm tab **Đang dạy**, gõ "Tô Bảo Mai"
      vào ô tìm kiếm rồi bấm **Lọc** để dòng đó nằm ngay đầu bảng.
- [ ] **Cửa sổ B (ẩn danh)** — đăng nhập `gv.tobaomai`, mở **Lịch dạy của tôi**, rồi
      **chọn sẵn bộ lọc Trường = TH Đằng Giang** (bước 3 cần).
      Phải là cửa sổ ẩn danh: đã thử, app lưu phiên vào **một khoá `tsdms.session` duy nhất**
      nên đăng nhập giáo viên là **đá văng phiên admin**, kể cả khi vào bằng `/login?add=1`.
- [ ] **Diễn tập trọn vẹn một lượt, kể cả bước 5.** Bước 5 phải đi qua ba màn của form tạo phân
      công — đây là chỗ duy nhất có thể lúng túng vì *thao tác*, không phải vì code.

```powershell
# Khởi động backend (PowerShell, trong thư mục backend)
$env:DB_USERNAME='tsdms_app'
$env:DB_PASSWORD='Tsdms@App123!'
.\mvnw.cmd -DskipTests spring-boot:run

# Dọn cổng cũ nếu cần
Get-NetTCPConnection -LocalPort 8080 -State Listen
Get-NetTCPConnection -LocalPort 5173 -State Listen
# có kết quả thì: Stop-Process -Force -Id <OwningProcess>
```

---

## 2. Kịch bản chính (6 bước · ≈ 4 phút)

Mỗi bước có ba phần: **Làm** (thao tác chính xác), **Nói** (lời thoại), **Phải hiện** (thứ để tự
kiểm — đúng thì đi tiếp, sai thì sang mục 4).

### Bước 1 — Đặt vấn đề trước khi bấm bất cứ thứ gì · 0:00 · 20 giây

- **Làm:** cửa sổ A, chỉ vào dòng **Tô Bảo Mai** trên bảng Phân công.
  ⚠️ Lọc theo tên ra **3 dòng** (cô Mai còn phiếu Tiếng Anh 8 tiết ở 4 trường). Dòng cần dùng là
  **dòng đầu tiên — môn *Kĩ năng quản lý thời gian & tài chính cá nhân*, TH Đằng Giang**.
- **Nói:** "Trong điều phối giáo viên, việc khó không phải là xếp lịch mà là *rút* lịch. Cô Mai
  đang dạy ba lớp ở TH Đằng Giang, phân công kéo tới tháng 8 sang năm. Bây giờ cô xin nghỉ từ đầu
  tháng 10 — hệ thống phải xử lý thế nào để không mất công đã dạy?"
- **Phải hiện:** dòng #445 · TH Đằng Giang · 1A1, 1A2, 1A3 · 23/08/2026 → 22/08/2027 · nhãn
  **Đang dạy**.

### Bước 2 — Hủy kể từ một ngày · 0:20 · 40 giây

- **Làm:** bấm **Hủy** → ô **Dừng dạy từ ngày** nhập `01/10/2026` → ô **Lý do hủy** nhập
  `Giáo viên nghỉ chế độ thai sản` → bấm **Hủy phân công**.
- **Nói:** "Điểm mấu chốt nằm ở ô ngày. Hệ thống không xóa phiếu và cũng không tắt cả phiếu — nó
  *cắt* phiếu tại đúng ngày này. Lý do là bắt buộc, vì lát nữa giáo viên sẽ đọc được nguyên văn nó."
- **Phải hiện:** nhãn đổi thành **Kết thúc sớm**; ngay dưới có dòng **"Dừng từ 01/10/2026 (đáng lẽ
  đến 22/08/2027)"** và lý do trong ngoặc kép; cột Giai đoạn đổi thành 23/08/2026 → **30/09/2026**.

### Bước 3 — Chứng minh dữ liệu cũ không mất · 1:00 · 35 giây

- **Làm:** sang cửa sổ B, **F5**. Bấm **Tháng**. Kiểm bộ lọc **Trường = TH Đằng Giang** đã bật
  (không bật thì lưới trộn cả buổi ở trường khác và **không nhìn ra điều đang nói**). Lịch mở
  sẵn ở **tháng 9** — chỉ cần bấm **›** hai lần sang **tháng 11**.
- **Nói:** "Tháng 9 — trước ngày hủy — bốn thứ Hai vẫn còn nguyên, mỗi buổi 3 tiết, tổng 12 buổi.
  Đó là bằng chứng để chấm công và tính lương, hệ thống không được phép xóa. Còn tháng 11, sau
  ngày dừng — trống trơn."
- **Phải hiện:** tháng 9 có **4 ngày, mỗi ngày "3 Tiết"**; tháng 11 **không còn ô nào**.

### Bước 4 — Giáo viên được báo, không phải tự đoán · 1:35 · 25 giây

- **Làm:** vẫn ở cửa sổ B, bấm **chuông thông báo** trên thanh trên cùng.
- **Nói:** "Bản trước của nhóm em hủy xong là giáo viên không biết gì cả — lịch chỉ hiện buổi đã
  duyệt nên các buổi bị cắt lặng lẽ biến mất, hôm sau thầy cô vẫn đến trường dạy một lớp đã giao
  cho người khác. Giờ thì có thông báo, kèm ngày dừng, số buổi và lý do."
- **Phải hiện:** thông báo **"Lịch dạy của bạn kết thúc sớm"** — *"…dừng từ ngày 01/10/2026. Các
  buổi TRƯỚC ngày này thầy/cô vẫn dạy và vẫn được tính công. 102 buổi đã bị hủy. Lý do: …"*

### Bước 5 — Khung giờ được nhả ra cho người thay · 2:00 · 50 giây · ĐIỂM NHẤN

- **Làm:** về cửa sổ A → **+ Tạo phân công** →
  **Bước 1**: môn `Kĩ năng quản lý thời gian & tài chính cá nhân`, chọn một giáo viên khác,
  ngày bắt đầu `05/10/2026`, ngày kết thúc `31/12/2026` →
  **Bước 2**: trường `TH Đằng Giang`, lớp `1A1`.
- **Nói:** "Đây là chỗ dễ sai nhất và cũng là lý do phải thu hẹp ngày kết thúc của phiếu. Nếu chỉ
  đổi trạng thái, luật chống trùng lịch vẫn coi ô *Thứ 2 – Tiết 1 – lớp 1A1* là đã có người, và
  trung tâm không xếp nổi giáo viên thay — hủy xong lại thành ra tự khóa chính mình."
- **Phải hiện:** ô **Thứ 2 · Tiết 1** của lớp 1A1 **không còn bị khóa**, chọn được.
- ⚠️ **KHÔNG** bấm sang Bước 3 và **không** gửi phiếu. Bấm **← Quay lại danh sách**.
  Lưới tải lâu quá 20 giây thì bỏ bước này, nói bằng lời rồi đi tiếp — đừng đứng chờ.

### Bước 6 — Bỏ hủy, và nói rõ nó không chỉ là đổi lại trạng thái · 2:50 · 30 giây

- **Làm:** bấm tab **"Kết thúc sớm"** — đúng **một dòng** hiện ra, không phải cuộn tìm.
  Ở dòng đó bấm **Bỏ hủy**.
- **Nói:** "Bỏ hủy trả ngày kết thúc về mốc gốc 22/08/2027 — đó là lý do phải lưu thêm cột *ngày
  kết thúc gốc*. Và nó có dò trùng lịch: nếu trong lúc phiếu bị hủy mà lớp đã giao cho giáo viên
  khác thì hệ thống chặn, chứ không tạo ra hai người cùng một tiết."
- **Phải hiện:** tab "Kết thúc sớm" **trống trở lại**; sang tab **Đang dạy** thấy phiếu với giai
  đoạn 23/08/2026 → 22/08/2027 và dòng "Dừng từ…" đã biến mất.

### Bước 7 (tùy chọn) — Đơn xin nghỉ do giáo viên tự gửi

- **Làm:** cửa sổ B: **Xin nghỉ dạy** → chọn phân công TH Đằng Giang, ngày `15/10/2026`, lý do
  bất kỳ → **Gửi đơn**. Cửa sổ A: bấm chuông → thông báo **"Giáo viên xin nghỉ dạy"** → **Duyệt**.
- **Nói:** "Đơn không tự sửa dữ liệu. Khi duyệt, nó gọi lại đúng luồng hủy vừa demo — nên hủy tay
  và duyệt đơn không thể lệch nhau về cách xử lý buổi đã dạy."
- **Phải hiện:** phiếu chuyển **Kết thúc sớm**, dừng từ 15/10/2026, lý do ghi
  "Giáo viên xin nghỉ: …".
- ⚠️ Diễn xong bước này phải bấm **Bỏ hủy** lần nữa, rồi chạy đoạn dọn dẹp ở mục 5.

---

## 3. Hội đồng sẽ hỏi (trả lời trong 2–3 câu)

**H: Vì sao phải thêm cột lưu ngày kết thúc gốc? Không thừa sao?**
Đ: Vì cặp ngày bắt đầu – ngày kết thúc chính là thứ luật chống trùng lịch dùng để nói "khung giờ
này đã có người". Muốn nhả khung giờ cho người thay thì buộc phải thu hẹp ngày kết thúc; mà thu
hẹp rồi thì phải có chỗ nhớ mốc cũ, nếu không "Bỏ hủy" không biết trả về đâu. Đó là bước 5 và
bước 6 em vừa demo.

**H: Sao không thêm hẳn trạng thái "Kết thúc sớm" vào cơ sở dữ liệu?**
Đ: Vì phiếu kết thúc sớm *vẫn còn buổi phải dạy* cho tới ngày dừng — dưới CSDL nó đang chạy thật,
ghi là "đã hủy" mới là sai. Nhãn được tính tại chỗ từ ngày hiệu lực, giống hệt cách nhãn "Hết hạn"
đang được tính cho phiếu quá hạn xác nhận. Nhờ vậy cũng không phải nới ràng buộc CHECK trên cột
trạng thái.

**H: Hủy như vậy có mất dữ liệu cũ không?**
Đ: Không, ở ba mức. Buổi đã dạy giữ nguyên trạng thái đã duyệt. Buổi tương lai chỉ đổi sang "đã
hủy" chứ không xóa, vẫn tra ra được. Và mỗi lần buổi đổi trạng thái đều có trigger trong CSDL ghi
vào bảng nhật ký lịch dạy — ai đổi, lúc nào, từ trạng thái gì sang trạng thái gì.

**H: Nếu giáo viên đang đứng lớp mà quản trị viên bấm hủy thì sao?**
Đ: Mốc cắt không bao giờ lùi về trước thời điểm hiện tại, nên buổi đang dạy dở vẫn khép sổ bình
thường và vẫn được tính công; chỉ cắt từ buổi kế tiếp. Bản trước chặn cứng cả thao tác, nhưng như
vậy thì quản trị viên không xử lý được ca gấp.

**H: Đơn xin nghỉ sao không lưu luôn vào bảng phân công cho gọn?**
Đ: Vì một phiếu có thể bị xin nghỉ, bị từ chối, rồi xin lại. Nhét vào bảng phân công thì lần sau
ghi đè lần trước, mất đúng phần lịch sử cần để đối chiếu. Bảng riêng còn có một index lọc bảo đảm
mỗi phiếu chỉ có đúng một đơn đang chờ, tránh duyệt hai lần cho cùng một việc.

**H: Phần này đã kiểm thử thế nào?**
Đ: Migration chạy sạch hai lần: trên CSDL trắng dựng lại từ đầu, và trên bản dữ liệu thật hơn 86
nghìn buổi dạy. Các trường hợp biên đều trả đúng lỗi: hủy không lý do và hủy ngày quá khứ trả 400,
giáo viên tự duyệt đơn của mình trả 403, gửi đơn trùng và duyệt lại đơn đã xử lý trả 409.

**H: Em thấy bỏ hủy xong vẫn còn vài buổi ở trạng thái đã hủy?**
Đ: Đúng, ba buổi — cả ba rơi vào ngày 31/05/2027 nằm trong kỳ nghỉ hè. Bỏ hủy cố ý không bật lại
buổi rơi vào ngày nghỉ, vì buổi đó không có thật: tác vụ nền quét buổi đã qua mà không ai chấm
công rồi tự ghi vắng, và giáo viên bị trừ lương cho một ngày trường đóng cửa.

---

## 4. Khi có sự cố (quyết trong 20 giây)

| Triệu chứng | Xử lý |
|---|---|
| Form **Tạo phân công** kẹt ở "Đang tải…" | Đã gặp một lần, ngay sau khi đăng nhập. API `/assignments/options` đo được chỉ **0,4 giây** nên không phải chậm — **F5 là xong**. Đừng ngồi chờ. |
| Trang trắng, gọi API lỗi mạng | Backend chưa lên. Kiểm tra cổng 8080 có ai nghe không **trước đã** — đừng đi tìm lỗi trong phần đăng nhập. |
| Flyway báo *checksum mismatch* | Đừng xóa dòng nào trong bảng lịch sử migration và đừng tắt kiểm tra. Bỏ demo phần này, chuyển sang chức năng khác — sửa sai lúc đang bị chấm còn tốn điểm hơn là không demo. |
| Trang 5173 trắng, báo lỗi *optimize dep* | Do chạy `npm run dev` lần thứ hai khi đã có server chạy. Tắt tiến trình nghe cổng 5173 rồi chạy lại **đúng một lần**. |
| Lưới xếp tiết ở bước 5 tải lâu | Thoát form, nói kết luận bằng lời rồi sang bước 6. Bước 5 là điểm nhấn chứ không phải điều kiện để hiểu phần còn lại. |
| Bấm **Bỏ hủy** báo trùng lịch | Đó là hệ thống làm **đúng**, không phải lỗi — nói thẳng như vậy: khung giờ vừa nhả ra đã có người khác chiếm nên không thể khôi phục nguyên trạng. |

---

## 5. Dọn dẹp sau khi diễn tập (bắt buộc)

Trả phiếu về nguyên trạng bằng nút **Bỏ hủy** trên giao diện — đó là đường an toàn nhất. Câu lệnh
dưới đây chỉ để **kiểm tra lại** và dọn các đơn / thông báo sinh ra lúc diễn tập.

```sql
-- Kiểm tra phiếu đã sạch: phải ra  ACTIVE | NULL | NULL | 2027-08-22
SELECT Status, CancelEffectiveDate, OriginalEndDate, EndDate
FROM Assignment WHERE Id = 445;

-- Dọn đơn xin nghỉ và thông báo sinh ra lúc diễn tập
DELETE FROM Notification
WHERE Title LIKE N'%kết thúc sớm%' OR Title LIKE N'%xin nghỉ%';
DELETE FROM AssignmentLeaveRequest;
DBCC CHECKIDENT('AssignmentLeaveRequest', RESEED, 0);
```

Lưu đoạn trên vào một file `.sql` rồi chạy từ thư mục chứa file:

```
sqlcmd -S localhost -E -d TSDMS -I -f 65001 -i <tên-file>.sql
```

Thiếu `-f 65001` thì chữ tiếng Việt trong câu lệnh sẽ sai và `DELETE` không khớp dòng nào.
Thiếu `-I` thì bảng `Payroll` (có index trên cột computed) sẽ báo lỗi 1934.

---

## 6. Một câu nếu chỉ kịp nói một câu

> Hủy phân công không phải là xóa dữ liệu, mà là **cắt một phiếu tại một mốc thời gian** — phần
> trước mốc là công đã làm và phải được trả lương, phần sau mốc là chỗ trống phải giao lại được
> cho người khác.
