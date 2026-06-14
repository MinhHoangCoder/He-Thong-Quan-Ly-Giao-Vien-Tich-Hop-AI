# Bảng ServiceContract — Hợp đồng dịch vụ trường↔trung tâm (nguồn doanh thu)

> Ngày: 2026-06-14 · Phần: database · Nhánh: `feature/auth-jwt`

## 1. Vì sao thêm bảng này

Dashboard "doanh thu theo năm học" (combo box 8/2025–6/2026) cần một **nguồn doanh thu**,
nhưng trước đó **không bảng nào chứa doanh thu**: tiền duy nhất trong hệ thống là `Payroll`
(lương trả GV = **chi phí**, không phải doanh thu). Đồng thời `School` đã có sẵn
`ContractStartDate/EndDate` (ngầm thừa nhận có "hợp đồng dịch vụ") nhưng lưu trơ trọi vài
ngày tháng, không có giá trị tiền và không giữ được lịch sử.

→ Tách hẳn một bảng `ServiceContract`.

## 2. Vì sao bảng riêng, không thêm cột vào `School`

Một trường **ký lại hợp đồng mỗi năm học** → cần **nhiều** hợp đồng theo thời gian. Cột
inline trên `School` chỉ giữ được **1** hợp đồng hiện tại → không tính được doanh thu từng
năm. Bảng riêng cho phép lưu lịch sử + cột tiền `ContractValue`.

> ⚠️ Đừng nhầm với bảng `Contract` (đã có) = hợp đồng **LAO ĐỘNG của giáo viên**.
> `ServiceContract` = hợp đồng **DỊCH VỤ với trường khách hàng**. Hai khái niệm khác nhau.

## 3. Cấu trúc (file `V4__service_contract.sql`)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `ServiceContractId` | INT IDENTITY PK | |
| `SchoolId` | INT FK→School | trường nào |
| `BranchId` | INT FK→Branch | chi nhánh phụ trách (đồng bộ với các bảng khác) |
| `ContractCode` | NVARCHAR(50) UNIQUE | vd `HDDV-2025-001` |
| `StartDate`, `EndDate` | DATE | kỳ hạn; CHECK `EndDate >= StartDate` |
| `ContractValue` | DECIMAL(18,2) | giá trị HĐ = doanh thu; CHECK `>= 0` |
| `Status` | VARCHAR(20) | DRAFT/ACTIVE/EXPIRED/TERMINATED |
| + 7 cột audit/xóa mềm | | khớp `SoftDeletableEntity` |

Index: `IX_ServiceContract_School`, `IX_ServiceContract_Start`. Seed sẵn 3 HĐ cho
"Trường THPT Demo" (2 thuộc năm học 2025–2026, 1 năm trước) để dashboard có số demo.

## 4. Entity & Repository

- [ServiceContract.java](../../backend/src/main/java/com/kdc/tsdms/entity/ServiceContract.java) —
  `extends SoftDeletableEntity`, tiền dùng `BigDecimal`, ngày dùng `LocalDate`.
- [ServiceContractRepository.java](../../backend/src/main/java/com/kdc/tsdms/repository/ServiceContractRepository.java) —
  có `sumRevenueByPeriod(from, to)` tính tổng doanh thu 1 năm học.

## 5. Quy tắc tính doanh thu năm học (đã chốt)

Năm học `Y` = khoảng `[01/08/Y, 01/08/(Y+1))`. Doanh thu năm học = `SUM(ContractValue)`
các HĐ có `StartDate` rơi trong khoảng đó (**ghi nhận theo ngày ký**, không trùng năm).
Ví dụ năm học 2025–2026: `sumRevenueByPeriod(2025-08-01, 2026-08-01)` = 120tr + 60tr = 180tr
(HĐ 2024 không tính vì StartDate 09/2024 nằm ngoài khoảng).

> Nếu sau này trung tâm muốn tính theo "HĐ còn hiệu lực trong năm" thì đổi điều kiện sang
> overlap khoảng `[StartDate, EndDate]` với khoảng năm học.

## 6. Phân quyền liên quan

Truy cập bảng này chịu RBAC (xem [ma trận quyền](2026-06-14-backend-rbac-permission-matrix.md)):
`SERVICE_CONTRACT_MANAGE` (SALES) để tạo/sửa; `SERVICE_CONTRACT_VIEW` (SALES + ACCOUNTANT)
để xem; dashboard doanh thu nằm dưới `REPORT_REVENUE_VIEW`.
