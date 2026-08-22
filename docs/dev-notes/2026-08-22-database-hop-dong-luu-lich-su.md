# DB: hợp đồng giáo viên giữ lịch sử, không ghi đè (V37) — 2026-08-22

Phần đuôi của [chuỗi ràng buộc toàn vẹn khi xóa](2026-08-17-database-rang-buoc-toan-ven-khi-xoa.md).
Đợt 2 chốt luật "hợp đồng & chứng chỉ là hồ sơ pháp lý, chỉ được xóa mềm" — nhưng lúc đó mới bịt
đường **xóa**. Còn một đường nữa làm mất dữ liệu y hệt mà không ai gọi là xóa: **ghi đè**.

## Vấn đề

`TeacherService.saveContract` (bản cũ):

```java
Contract contract = contractRepo.findByTeacherIdAndDeletedFalse(teacherId)
        .orElseGet(() -> { ... });     // ← tìm hợp đồng ĐANG CÓ
contract.setContractNo(req.getContractNo());
contract.setBaseSalary(req.getBaseSalary());   // ← ghi thẳng đè lên
...
return contractRepo.save(contract);
```

Sửa mức lương xong là con số cũ biến mất. Không thùng rác, không nhật ký, không cách nào biết
trước đó ghi bao nhiêu hay ai sửa.

**Vì sao nó tệ hơn xóa cứng:** xóa thì ít ra còn biết là đã mất — dòng biến mất, màn hình trống,
có người sẽ hỏi. Ghi đè thì dòng vẫn nằm đó trông hoàn toàn bình thường, chỉ là nội dung đã
khác. Lỗi này **không có triệu chứng**. Nó chỉ lộ ra đúng lúc tệ nhất: khi có tranh chấp lương
và câu cần trả lời là *"hợp đồng ký ngày đó ghi bao nhiêu"*.

Và nó mâu thuẫn thẳng với luật đã chốt: cấm xóa cứng mà vẫn cho ghi đè thì mới bịt được một nửa.

## Cách sửa: đóng bản cũ — mở bản mới

| | Bản cũ | Bản mới |
|---|---|---|
| Hợp đồng đang có | ghi đè lên chính dòng đó | `Status = TERMINATED` + `IsDeleted = 1` |
| Nội dung mới | (không có dòng mới) | một dòng RIÊNG, `Status = ACTIVE` |
| Bấm Lưu mà không sửa gì | vẫn `save()` | không đẻ phiên bản mới |

**Vì sao dùng cả `TERMINATED` lẫn `IsDeleted = 1`:** `TERMINATED` nói **vì sao** nó hết hiệu lực
(đúng ngữ nghĩa, và `CK_Contract_Status` từ V1 đã cho phép giá trị này). Còn `IsDeleted = 1` ở
bảng này **không mang nghĩa "đã hủy" mà là "đã bị thay thế"** — đặt nó để giữ bất biến *"mỗi
giáo viên tối đa MỘT hợp đồng chưa xóa"* mà mọi đường đọc đang dựa vào
(`findByTeacherIdAndDeletedFalse` trả `Optional`, hai dòng sống là nổ). Giữ bất biến đó thì
không phải sửa lại toàn bộ đường đọc chỉ vì một thay đổi ở đường ghi.

Đánh đổi đã cân nhắc: sửa một lỗi gõ nhầm cũng đẻ ra một phiên bản. Chấp nhận — với hồ sơ pháp
lý thì giữ mọi phiên bản **chính là** hành vi đúng, và "v1 ghi 8tr, v2 ghi 9,5tr, đổi lúc nào,
ai đổi" mới là thứ trả lời được khi có tranh chấp.

## Vì sao bắt buộc phải có migration V37

`ContractNo` được V1 khai `VARCHAR(50) NOT NULL UNIQUE`, tính trên **toàn bảng**, kể cả dòng đã
xóa mềm. Ca phổ biến nhất lại là **sửa lương mà giữ nguyên số hợp đồng** (phụ lục sửa lương
không đổi số HĐ) — bản mới sẽ đụng ngay vào bản cũ vừa đóng và toàn bộ thao tác nổ.

V37 đổi sang **chỉ mục unique có lọc**, chỉ soi các dòng còn hiệu lực:

```sql
CREATE UNIQUE INDEX UX_Contract_No_Active ON Contract(ContractNo) WHERE IsDeleted = 0;
```

Đây đúng ý nghĩa nghiệp vụ ngay từ đầu: hai hợp đồng **đang sống** không được trùng số; bản đã
đóng giữ nguyên số của nó là chuyện bình thường. Dự án đã dùng đúng khuôn này ở `Subject` (V21),
`Room`, `SchoolClass`, `Period`.

## Ba cái bẫy

**1. `DROP CONSTRAINT`, không phải `DROP INDEX`.** `UNIQUE` khai thẳng trong `CREATE TABLE` sinh
ra một **ràng buộc** (tên tự sinh `UQ__Contract__...`), và SQL Server từ chối `DROP INDEX` trên
chỉ mục do ràng buộc sở hữu (lỗi 3723). Đây đúng chỗ `V31` đã vấp và làm chết Flyway cho cả dự
án — xem note của Đợt 4.

**2. Phải `saveAndFlush` bản cũ trước khi chèn bản mới.** Hibernate xếp `INSERT` **trước**
`UPDATE` trong hàng đợi hành động. Để nó tự quyết thì dòng mới vào DB khi dòng cũ vẫn còn
`IsDeleted = 0` → chỉ mục mới thấy hai hợp đồng sống trùng số → nổ. Đây là loại lỗi chỉ xuất
hiện khi chạy thật, unit test mock repository không bao giờ thấy.

**3. So tiền bằng `compareTo`, KHÔNG bằng `equals`.** `BigDecimal.equals` so cả phần thập phân
dư: DB khai `DECIMAL(18,2)` nên đọc lên là `8000000.00`, còn form gửi lên là `8000000`.
`equals` bảo hai số đó khác nhau → chốt "không sửa gì thì không đẻ phiên bản" mất tác dụng
hoàn toàn, mỗi lần bấm Lưu lại thêm một bản y hệt bản trước.

## Test

10 test, và quan trọng là chúng **bắt được lỗi thật**:

| Gỡ cái gì | Test đỏ |
|---|---|
| `compareTo` → `equals` | 1 (unit) |
| bỏ mệnh đề `WHERE IsDeleted = 0` của chỉ mục | 2 (**chỉ IT bắt được**) |

Lần thứ hai là lý do phải có IT: unit test mock `ContractRepository` thì lưu kiểu gì cũng
"thành công" — không có gì trong Java biết chỉ mục ngoài kia đang chặn hay không.

`ContractHistoryIT` cũng chốt luôn chiều ngược lại: **nới lỏng không được nới quá tay** — hai
hợp đồng *còn hiệu lực* trùng số vẫn phải bị chặn. Và một test kiểm ràng buộc UNIQUE cũ đã thật
sự bị gỡ: nếu còn sót thì cái cũ chặn trước, luồng thật vẫn nổ trong khi test khác vẫn xanh.

## Đọc lại lịch sử

`TeacherResponse.Response` thêm `contractHistory` — các bản đã bị thay thế, mới nhất trước, tách
khỏi `contract` (bản đang hiệu lực) để màn hồ sơ không phải tự đoán đâu là bản hiện tại. Trường
mới nên FE cũ không vỡ; dựng UI dòng thời gian hợp đồng là việc của module Giáo viên.
