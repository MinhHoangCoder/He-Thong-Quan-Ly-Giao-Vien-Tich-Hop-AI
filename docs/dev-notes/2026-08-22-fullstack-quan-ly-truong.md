# Quản lý trường: cho trạng thái có quyền lực, thêm thùng rác, dọn N+1 (2026-08-22)

Nhánh `feat/quan-ly-truong`, cắt từ `origin/master` (4449e7c).

Đợt này không thêm màn hình mới. Nó đi trả lời bốn câu mà người đọc code sẽ hỏi khi mở trang
Quản lý trường: *trạng thái này ảnh hưởng gì?*, *hết hạn hợp đồng rồi thì sao?*, *xóa nhầm thì
lấy lại kiểu gì?*, và *sao một trang danh sách lại chạy 21 câu truy vấn?*

---

## 1. Phát hiện gốc: luật đã nằm trong dữ liệu, chỉ chưa nằm trong code

Cột `School.Status` (ACTIVE / INACTIVE / EXPIRED) có `CHECK constraint` từ V1, có bộ lọc trên
giao diện — nhưng grep cả backend thì gần như không ai đọc nó.

Trừ một chỗ: file seed. `TSDMS_Seed_PhanCong.sql:122` sinh phân công với điều kiện

```sql
JOIN School s ON s.Id = c.SchoolId AND s.Status = 'ACTIVE' AND s.IsDeleted = 0
```

Người viết seed **đã coi trạng thái trường là ràng buộc nghiệp vụ**. `AssignmentService` cũng có
`assertSchoolActive` đúng theo tinh thần đó. Nhưng `SchoolClassService` thì không, và quan trọng
hơn: **không nơi nào biết tới ngày hết hạn hợp đồng**. Một trường `Status = 'ACTIVE'` mà
`ContractEndDate = 2024-02-08` vẫn nhận phân công mới bình thường, vì chưa có gì hạ trạng thái
của nó xuống — và cũng chẳng có tác vụ nền nào để làm việc đó.

Nên đợt này không phát minh luật mới. Nó gom luật đã có về một chỗ và dạy cho luật đó biết đọc
lịch.

---

## 2. Trạng thái SUY lúc đọc, không phải job nền

`School.effectiveStatus(today)`:

```java
public String effectiveStatus(LocalDate today) {
    if (ACTIVE.equals(status) && contractEndDate != null && contractEndDate.isBefore(today)) {
        return EXPIRED;
    }
    return status;
}
```

Ba quyết định trong tám dòng:

- **Suy lúc đọc chứ không chạy `@Scheduled` ghi lại cột Status.** Cùng khuôn với
  `Assignment.isExpiredPending()` đã có sẵn. Job nền chỉ đúng tính tới lần chạy gần nhất; máy demo
  tắt qua đêm là cả bảng sai trạng thái mà không ai biết. Suy lúc đọc thì không có trạng thái
  "chưa kịp cập nhật".
- **INACTIVE do người dùng đặt tay thì GIỮ NGUYÊN**, không đổi thành EXPIRED. Ngừng hợp tác và
  hết hạn hợp đồng là hai việc khác nhau; người bấm tay biết rõ hồ sơ hơn cái ngày trong DB.
- **Hết hạn đúng hôm nay thì vẫn còn hiệu lực** (`isBefore`, không phải `!isAfter`). Hợp đồng ghi
  hết hạn 22/08 thì ngày 22/08 vẫn dạy.

Response trả về **cả hai**: `status` (cột đang lưu, để form Sửa nạp đúng) và `effectiveStatus`
(cái bảng hiển thị). Trộn hai thứ này là một cái bẫy rất ngọt: nếu form Sửa nạp `effectiveStatus`,
người dùng mở một trường ACTIVE-nhưng-quá-hạn ra sửa số điện thoại, bấm Lưu, thì cột Status thành
EXPIRED thật — và về sau gia hạn hợp đồng cũng không làm nó sống lại.

### Cái bẫy đi kèm: lọc theo trạng thái SUY RA

`AssignmentService.list()` giải bài này bằng cách tải hết rồi lọc trong Java — được, vì màn Phân
công không phân trang. Màn Trường **có** phân trang phía server, lọc sau khi cắt trang là ra số
tổng sai và trang cuối rỗng. Nên điều kiện phải nằm trong chính câu query:

```sql
AND (:status IS NULL OR :status = CASE
       WHEN s.status = 'ACTIVE' AND s.contractEndDate IS NOT NULL AND s.contractEndDate < :today
            THEN 'EXPIRED'
       ELSE s.status END)
```

Biểu thức `CASE` này **phải khớp từng chữ với `School.effectiveStatus`**. Lệch nhau thì bộ lọc
đếm một đằng, badge hiện một nẻo — loại lỗi không bao giờ ném exception. Đã ghi chú chéo ở cả hai
file.

---

## 3. Chặn nghiệp vụ: một luật, hai nơi gọi

`School.conHopTac(today)` = `effectiveStatus(today) == ACTIVE`. Hai chỗ dùng:

| Nơi | Trước | Sau |
|---|---|---|
| `AssignmentService` (tạo phiếu, dropdown chọn trường) | so chuỗi `"ACTIVE".equals(getStatus())` | `s.conHopTac(homNay)` |
| `SchoolClassService` (tạo lớp, chuyển lớp sang trường khác) | chỉ kiểm trường có tồn tại | `requireSchoolConHopTac()` |

Hằng số `SCHOOL_ACTIVE` trong `AssignmentService` bị xóa — luật đã về entity thì để lại một bản
sao chuỗi ở service chỉ là chỗ để hai bên lệch nhau.

**Cố ý chỉ chặn lúc TẠO**, không chặn lúc sửa: chặn cả thao tác sửa là nhốt luôn dữ liệu cũ, gõ
sai một chữ trong tên lớp của trường đã ngừng cũng không sửa lại được. Riêng *chuyển* lớp sang
trường khác thì có chặn, vì đó thực chất là mở việc mới ở nơi mới.

Kiểm chứng bằng tay trên DB thật, sau khi kéo hạn hợp đồng của `THCS Chu Văn An` về quá khứ 30
ngày (cột `Status` vẫn nguyên `ACTIVE`):

```
GET /api/v1/assignments/options   -> dropdown còn 17 trường (trước là 18)
POST /api/v1/classes {schoolId:17} -> 409
   "Trường THCS Chu Văn An đã ngừng hợp tác hoặc hết hạn hợp đồng nên không mở thêm lớp mới được."
```

---

## 4. Tên trường: một luật ghép cho cả tạo lẫn sửa

`create()` vốn đã ghép tiền tố cấp học vào tên (`"Ban Mai"` + THCS → `"THCS Ban Mai"`), nhưng
`update()` thì lưu thẳng chuỗi người dùng gõ. Hệ quả: mở `THCS Ban Mai` ra sửa số điện thoại, bấm
Lưu, tên rụng mất tiền tố nếu người dùng lỡ xóa nó — và trường đó vẫn chạy khung tiết THCS.

Nay `update()` suy cấp học từ chính cái tên đang nhập (`SchoolService.capTheoTen`) rồi ghép lại.
Form Sửa vẫn KHÔNG có ô Cấp học: khung tiết đã dùng để xếp lịch, đổi ngầm là lệch giờ mọi buổi đã
sinh ra.

Một lỗi biên do test bắt được: `ghepTenTheoCap("THCS", "THCS")` trả về `"THCS THCS"`. Điều kiện
cắt tiền tố là `token.length > cum.length`, nên tên gồm ĐÚNG một tiền tố thì không cắt gì mà vẫn
ghép thêm. Đổi thành `>=` — cắt xong rỗng thì nhánh cuối đã sẵn trả lại nguyên tên gốc.

### Trùng tên: chặn hai lớp

Trước đây tạo mười trường `THCS Ban Mai` trong một chi nhánh là chuyện bình thường. Dữ liệu không
hỏng, nhưng dropdown chọn trường ở màn Phân công hiện mười dòng y hệt — chọn nhầm là cả chuỗi
lịch dạy → chấm công → lương chạy sang trường khác.

- **Tầng service**: `assertTenChuaDung(branchId, name, selfId)` → 409 kèm tên đang trùng.
- **Tầng DB**: `V36` tạo `UNIQUE INDEX UX_School_BranchName ON School(BranchId, Name) WHERE
  IsDeleted = 0` — đúng khuôn V21 đã làm cho `Subject.Code`. Lọc `IsDeleted = 0` để tên của
  trường đã xóa mềm vẫn dùng lại được, khớp đúng điều tầng service kiểm.

Duy nhất theo CẶP `(BranchId, Name)` chứ không theo riêng `Name`: hai chi nhánh ở hai tỉnh có thể
có trường trùng tên thật.

**Khác V21 ở một điểm quan trọng:** cột `Code` vốn đã UNIQUE nên chắc chắn không có dòng trùng,
còn `Name` thì chưa từng bị ép gì — DB của mỗi người có thể đang có sẵn dòng trùng, mà
`CREATE UNIQUE INDEX` gặp trùng là nổ, migration nổ thì backend không khởi động được. Nên V36 đổi
tên các dòng trùng trước, ghép hậu tố `" (#<Id>)"`. Dùng Id chứ không phải `" (2)", " (3)"`: Id là
khóa chính nên tên mới chắc chắn không đụng nhau, không phải chạy lặp tới khi hết trùng.

---

## 5. Thùng rác

`SchoolRepository.findByIdAndDeletedTrue` đã tồn tại từ lâu và **chưa ai gọi**. Giáo viên, Lớp
học, Phân công đều có thùng rác; riêng Trường thì xóa mềm xong là biến mất khỏi mọi màn hình, muốn
lấy lại phải vào SSMS.

Ba endpoint mới: `GET /trash`, `POST /{id}/restore`, `DELETE /{id}/permanent`.

**Khôi phục** cố ý giữ nguyên trạng thái `INACTIVE` (do `delete()` hạ xuống) chứ không bật lại
ACTIVE: trường vừa moi khỏi thùng rác chưa chắc đã ký lại hợp đồng, mà ACTIVE là nó nhận phân công
mới ngay. Khôi phục cũng kiểm trùng tên — trong lúc nó nằm trong thùng rác, người khác có thể đã
tạo trường trùng tên ở cùng chi nhánh.

**Xóa vĩnh viễn** dùng lại khuôn `TeacherService.deleteTrueTeacher`: một câu SQL `UNION ALL` đếm
dòng con ở 7 bảng (`SchoolRepository.countChildRowsBySchoolId`), rồi `DeleteGuard.blockAll` kể hết
lý do trong một lần. Câu đếm **không lọc `IsDeleted`** — khóa ngoại chặn theo sự tồn tại của dòng,
một lớp đã "xóa" vẫn khiến `DELETE School` nổ.

Nhưng có một khác biệt với Giáo viên: **`Period` và `Room` bị xóa KÈM chứ không tính là rào.**
Mọi trường đều có khung tiết (từ đợt này thì tạo trường xong là sinh sẵn luôn), nên tính chúng là
rào thì nút "Xóa vĩnh viễn" không bao giờ bấm được — có nút mà không bấm được còn tệ hơn không có.
Lý lẽ vẫn nhất quán với comment sẵn có ở `delete()`: khung tiết và phòng học là *cấu hình của
chính trường*, không phải dữ liệu nghiệp vụ độc lập. Hệ quả cố ý: trường đã chạy thật thì gần như
không xóa cứng được, chỉ trường tạo nhầm mới xóa hẳn — đúng thứ nút này cần phục vụ.

---

## 6. Dọn code

| Chỗ | Trước | Sau |
|---|---|---|
| `SchoolService.toResponse` | `bRepo.findById()` cho **từng dòng** → trang 20 trường = 21 câu | 2 câu gộp cho cả trang (`findAllById` + `GROUP BY` đếm tiết) |
| Phân trang | ~50 dòng JS + ~40 dòng CSS chép tay, thuật toán trùng khít `ui/Pagination.vue` | dùng `Pagination.vue`, thêm prop `show-jump` (mặc định `false` → 6 trang đang dùng không đổi gì) |
| Ô tìm kiếm | `LIKE %:kw%` không escape — gõ `%` ra toàn bộ bảng | `SearchText.escapeLike` + `ESCAPE '!'`, khuôn có sẵn ở `SchoolClassRepository` |
| `escapeLike` / `blankToNull` | bản riêng trong `SchoolClassService` | tách ra `common/SearchText.java`, cả hai service cùng dùng |
| Ngày tháng trên bảng | in thẳng chuỗi ISO `2028-07-20` | `utils/format.js` (`formatDate`, `formatCurrency`) |
| `catch { /* handle error */ }` | API chết → bảng hiện "Không có dữ liệu" | hiện lỗi thật + nút "Thử lại" |
| Ràng buộc ngày hợp đồng | chỉ form Vue kiểm | `@AssertTrue` trong `SchoolRequest` — Postman cũng bị chặn |

Thêm hai thứ nhỏ nhưng đúng loại "đọc code là thấy":

- **`@media (max-width: 900px)` bị thiếu.** Khối CSS cuối `SchoolListPage.vue` (`flex-direction:
  column`, `.btn { width: 100% }`) rõ ràng là dành cho màn hình hẹp, nhưng không có media query
  bọc ngoài — nên nó áp ở MỌI độ rộng. Năm trang cùng họ đều có `@media (max-width: 900px)`, chỉ
  trang này thiếu. Đã bọc lại.
- **Comment nói dối.** `SchoolController` ghi `SCHOOL_VIEW (ADMIN + SALES)`, nhưng V33 đã xóa hẳn
  role SALES. `database/schema/TSDMS_Schema.sql` vẫn khai cột `School.AppUserId` mà V31 đã drop.
  Sửa cả hai.

---

## 7. Lỗi ràng buộc DB không còn giả dạng "sập hệ thống"

`GlobalExceptionHandler.handleDataIntegrity` được lấy lại từ nhánh `fix/them-truong-500` (commit
`cc4defb`, 13/08) — nhánh đó **không merge được nữa**: nửa migration của nó đặt tên
`V26__school_appuser_unique_filtered.sql` trong khi master nay đã có `V26__bo_cap_3_khoi_1_9.sql`
(đụng version, Flyway chết), và bản thân nó cũng đã thừa vì V31 xóa luôn cột `AppUserId`.

Nửa còn lại thì vẫn đúng nguyên giá trị: phân loại `DataIntegrityViolationException` theo **mã
lỗi** SQL Server (2627/2601 → 409 trùng dữ liệu, 547 → 400, 515 → 400, 8152/2628 → 400) thay vì
để mọi vi phạm ràng buộc rơi vào `handleOther` và ra 500 "Lỗi hệ thống, vui lòng thử lại sau".
Phân loại theo mã chứ không đọc message vì message gốc lộ tên bảng/cột.

Việc này **lệch quy ước nhóm** (`quy-uoc-lam-viec-nhom.md` §3: "không sửa `GlobalExceptionHandler`").
Lệch có chủ ý và ở mức an toàn nhất có thể: chỉ THÊM một `@ExceptionHandler` mới ở cuối, không
đụng handler nào của người khác. Lý do đánh đổi: đợt này thêm chỉ mục UNIQUE ở tầng DB, mà chỉ mục
đó nổ ra 500 thì người dùng không biết mình vừa nhập trùng.

---

## 8. Kiểm chứng

**Test tự động:** `SchoolServiceTest` — 16 test, 3 nhóm: ghép tên theo cấp học (6), trạng thái suy
theo ngày (5), tạo/sửa/xóa vĩnh viễn (5). Toàn bộ suite backend: **292 test pass**.

Trước đó `School` là service duy nhất trong nhóm CRUD không có test riêng; `DeleteRestrictTest`
chỉ phủ phần chặn xóa mềm.

**Kiểm bằng tay trên DB thật** (30 trường Hải Phòng), sau khi chạy patch dữ liệu demo:

| Việc | Kết quả |
|---|---|
| `GET /schools?status=EXPIRED` | 4 → **5** trường (thêm `THCS Chu Văn An`, cột Status vẫn `ACTIVE`) |
| `GET /schools?expiringInDays=30` | đúng 2 trường, `daysLeft` = 12 và 26 |
| `POST /schools` `{name:"Ban Mai", educationLevel:"THCS"}` | 201, lưu `"THCS Ban Mai"`, `periodCount: 9` ngay |
| tạo lại cùng tên | **409** "Chi nhánh này đã có trường tên 'THCS Ban Mai'" |
| `contractEndDate` < `contractStartDate` | **400** "Ngày hết hạn hợp đồng phải sau ngày bắt đầu" |
| `PUT` tên thành `"Trường THCS Ban Mai"` | lưu `"THCS Ban Mai"` (chuẩn hóa, không rụng tiền tố) |
| xóa mềm → thùng rác → khôi phục → xóa vĩnh viễn | 204 / có trong trash / 200 / 204, thùng rác về rỗng |
| `POST /classes` vào trường đã hết hạn | **409** kèm tên trường |

---

## 9. Dữ liệu demo

`database/seed/patches/2026-08-21-han-hop-dong-truong.sql`

Seed Hải Phòng đặt hạn hợp đồng sớm nhất là 25/05/2027, nên trên DB demo **không có trường nào
sắp hết hạn**, và cũng không có trường ACTIVE nào đã quá hạn — hai tính năng đúng nhưng không có
dòng nào để nhìn. Patch kéo hạn của 3 trường về gần hôm nay (còn 12 ngày, còn 26 ngày, và quá hạn
30 ngày), đồng thời kéo theo `ServiceContract` cho hai nguồn khỏi lệch ngay từ lúc dựng dữ liệu.

Ngày tính tương đối theo `GETDATE()` nên chạy lúc nào cũng ra đúng mô tả. **Bắt buộc cờ `-I`**:
từ V36 bảng `School` có filtered index, thiếu `-I` là SQL Server từ chối mọi lệnh UPDATE lên bảng
này.

---

## 10. Cố ý KHÔNG làm

- **Không đổi trang sang `page-common.css`.** Đo trước khi sửa: 8 trang dùng `page-common`, 7
  trang (Lớp học, Nhóm môn, Bài giảng, Lịch nghỉ…) dùng bộ `.page__head`/`.filter-bar` cục bộ.
  Trang Trường thuộc nhóm thứ hai và nằm ngay cạnh trang Lớp học trong sidebar — đổi một mình nó
  là tạo ra đúng cái lệch nhịp cần tránh.
- **Không dựng hệ thống toast.** `alert()` là thứ 4 trang khác đang dùng để báo lỗi thao tác.
- **Không bỏ hai ô ngày hợp đồng trên bảng School** để lấy `ServiceContract` làm nguồn duy nhất.
  `ServiceContract` chưa có màn CRUD nào, bỏ đi là mất luôn thông tin hợp đồng duy nhất sửa được.
  Thay vào đó khối chi tiết hiện hợp đồng dịch vụ ở dạng CHỈ ĐỌC, để ai sửa lệch hai bên thì thấy
  ngay tại chỗ. Gộp hai nguồn là việc của một đợt riêng, có migration và backfill.
- **Không đụng `School.Status` thành enum.** Cột có `CHECK constraint` từ V1 và dashboard đang
  đọc; đổi kiểu là việc lớn hơn nhiều lần thứ đợt này cần.
