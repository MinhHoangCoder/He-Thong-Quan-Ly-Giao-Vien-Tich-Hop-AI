# Lịch dạy GV (thời khóa biểu tuần) + ca làm nhân viên

**Ngày:** 2026-06-30 · **Tầng:** Database + Entity/Repository (base layer) · **Migration:** Flyway **V9** + **V10**

> Xuất phát từ một file SQL nháp (chạy tay SSMS) của thành viên cho tính năng "phân công
> lịch dạy GV" + "ca làm nhân viên". Note này ghi lại **vì sao thiết kế lại** trước khi đưa
> vào code chính, và mô hình cuối cùng.

## Vấn đề ở bản nháp

Bản nháp thêm thẳng `DayOfWeek` + `PeriodId` vào bảng **`Assignment`**. Đây là **nhầm grain**:

- `Assignment` trong dự án là **PHÂN CÔNG mức KỲ** (vài tháng: GV ↔ trường ↔ môn ↔ lớp,
  `StartDate..EndDate`), rồi **nở ra nhiều `Schedule`** (buổi dạy cụ thể ngày-giờ-phòng, có
  duyệt PENDING→APPROVED). Bản nháp **không dùng tầng `Schedule`** đã có → dựng lại thời
  khóa biểu ngay trên `Assignment`.
- Gắn 1 cặp `(DayOfWeek, PeriodId)` (mỗi cái 1 giá trị) lên `Assignment` ⇒ ép **1 phân công =
  1 tiết/tuần**. Lớp dạy 4 tiết/tuần → 4 dòng `Assignment` lặp teacher/school/subject/class →
  vỡ ý nghĩa "mức kỳ".

> Bản thân ý tưởng tạo `Period` (danh mục tiết) **đúng và đáng giữ** — dự án đang thiếu khái
> niệm "tiết": `Schedule` chỉ có `StartTime/EndTime` thô. Cái sai là **chỗ gắn** thứ+tiết.

## Giải pháp — 3 tầng theo độ mịn

```
Assignment      (kỳ học, vài tháng — GIỮ NGUYÊN, không thêm cột)
   │ 1-n
AssignmentSlot  (MẪU lặp tuần: thứ + tiết [+ phòng mặc định])   ← bảng MỚI, đứng giữa
   │ Service trải ra
Schedule        (buổi cụ thể từng tuần — thêm PeriodId, SourceSlotId)
```

### Migration `V9__teacher_timetable.sql`
1. `Period` — khung tiết theo **TỪNG TRƯỜNG** (`SchoolId` NOT NULL → School): mỗi trường số
   tiết & giờ giấc riêng (THPT 9 tiết 35' vs Tiểu học 7 tiết 40'...). Là **dữ liệu vận hành**
   (trường sửa qua admin/UPDATE) nên có **soft-delete + audit**; unique filtered
   `UX_Period_School_Number (SchoolId, PeriodNumber) WHERE IsDeleted=0`.
   *(Đổi cột `Session` của bản nháp → `SessionType`.)*
   - **Dữ liệu tiết KHÔNG seed trong migration** mà ở `database/seed/TSDMS_Seed_Demo.sql` theo
     từng trường (mục `11b`). Lý do: dễ maintain + demo được sự khác nhau giữa trường. Lưu ý:
     DB chỉ migrate mà chưa seed demo sẽ **chưa có tiết nào** → cấu hình khung tiết khi onboard trường.
   - **Đính chính niệm "để seed cho dễ sửa":** việc trường sửa giờ tiết *sau này* dựa vào
     `Period` có `SchoolId` + là bảng **UPDATE được**, KHÔNG phải vị trí file seed (seed chỉ chạy
     lúc nạp lại). Seed chỉ lo **khởi tạo** ban đầu.
2. `AssignmentSlot` — bảng con của `Assignment`: `AssignmentId`, `TeacherId` (lưu kèm = GV của
   Assignment để dò trùng nhanh, **giống `Schedule`**), `DayOfWeek` (CHECK MON..SUN), `PeriodId`,
   `RoomId` NULL (**phòng mặc định** của ô lịch). Đủ soft-delete + audit.
   - `UX_AssignmentSlot_Assign_Day_Period` (unique, `WHERE IsDeleted=0`): 1 phân công không lặp cùng (thứ, tiết).
   - `IX_AssignmentSlot_Teacher_Day_Period` (`WHERE IsDeleted=0`): hỗ trợ dò trùng lịch GV.
3. `ALTER Schedule` thêm 2 cột **nullable** (an toàn data cũ): `PeriodId` (→Period: buổi thuộc tiết
   nào), `SourceSlotId` (→AssignmentSlot: buổi sinh từ slot nào) + `IX_Schedule_SourceSlot`.

### Schedule được sinh ra từ AssignmentSlot như thế nào (tầng Service — KHÔNG làm ở đợt này)
Với mỗi slot của một Assignment, quét mọi ngày trong `[StartDate..EndDate]` trùng `DayOfWeek`,
ghép **ngày + `Period.StartTime/EndTime`** → `Schedule.StartTime/EndTime` (DATETIME2), set
`PeriodId = slot.PeriodId`, `SourceSlotId = slot.Id`, `RoomId = slot.RoomId` (cho override).
Trùng lịch GV/phòng vẫn kiểm ở Service qua các index `IX_Schedule_*` sẵn có.

> **RoomId — chốt phương án (c):** phòng nằm ở **cả** `AssignmentSlot` (mặc định, copy xuống)
> **lẫn** `Schedule.RoomId` (override từng buổi). Trùng phòng kiểm ở tầng `Schedule` (vì có thể
> override) → `AssignmentSlot.RoomId` không cần index riêng.

### Migration `V10__employee_workshift.sql`
Mô hình **song song** với lịch dạy GV (ý định → bản thể hóa):
1. `Employee += EmploymentType` VARCHAR(20) NOT NULL DEFAULT `FULL_TIME` + CHECK FULL_TIME/PART_TIME
   (DEFAULT để data cũ không vỡ).
2. `PartTimeShiftRequest` — NV part-time **đăng ký ca** (ý định), HR duyệt/từ chối. `EmployeeId`,
   `WorkDate`, `ShiftType`, `Status` (PENDING/APPROVED/REJECTED), `ReviewedByEmployeeId`,
   `ReviewedAt`, `RejectionReason`. Unique `UX_PTRequest_Emp_Date_Shift` **loại REJECTED** để
   được đăng ký lại.
3. `EmployeeSchedule` — **lịch làm thực tế** (bản thể hóa). `Source` FIXED (full-time cố định) |
   FROM_REQUEST (sinh từ đăng ký đã duyệt) | MANUAL. `CK_EmpSchedule_SourceLink`: có
   `SourceRequestId` ⇔ `Source='FROM_REQUEST'`.

> Đối xứng: `PartTimeShiftRequest → EmployeeSchedule` ↔ `AssignmentSlot → Schedule`
> (đều là **mẫu/ý định → bản thể hóa**).

### Entity / Repository (base layer — đợt này)
- **Mới:** `Period`, `AssignmentSlot` (extends `SoftDeletableEntity`), `PartTimeShiftRequest`,
  `EmployeeSchedule` (extends `SoftDeletableEntity`) + 4 repository tương ứng (kèm finder dò
  trùng & tra theo khoảng ngày).
- **Sửa:** `Schedule.java` (+`periodId`, `sourceSlotId`), `Employee.java` (+`employmentType`).
- `Period.periodNumber` dùng `Short` (TINYINT) đúng tiền lệ `Payroll.periodMonth`/`Evaluation.score`.

## Quy ước đã theo
- Flyway tự chạy 1 lần → `CREATE TABLE` thẳng (giống V8), **KHÔNG** `DROP`/`IF NOT EXISTS` như
  bản nháp (drop-recreate sẽ **xóa data** nếu chạy lại).
- PK tên `Id` (V7), FK giữ prefix tự mô tả; audit **`DATETIME2(3)`** (bản nháp để `(0)`);
  unique **index** prefix `UX_`, unique **constraint** prefix `UQ_`.
- Bỏ Phần F (SELECT kiểm tra) + Phần G (rollback) khỏi file migration; lưu UTF-8.

## Việc còn nợ (ngoài phạm vi đợt này — tầng feature/thành viên khác)
- **Service sinh `Schedule` từ `AssignmentSlot`** + kiểm tra trùng lịch GV (so chồng `StartDate/EndDate`).
- **Service ca NV:** cửa sổ đăng ký (T4 mở · T7 12h đóng), HR duyệt `PENDING→APPROVED` rồi sinh
  `EmployeeSchedule`; full-time tự sinh T2–T6 (`Source=FIXED`); giờ ca (08–11 / 14–17) đang là hằng số Service.
- **Đồng bộ tài liệu `database/`** (schema mirror, từ điển, seed) cho 4 bảng mới (`Period`, `AssignmentSlot`, `PartTimeShiftRequest`, `EmployeeSchedule`) + 2 ALTER (`Schedule`, `Employee`) — chưa làm.
- Cân nhắc đổi tên `EmployeeSchedule` → `EmployeeWorkShift` cho khỏi lẫn với `Schedule` (lịch dạy GV).

## Cách áp dụng
1. Chạy app → Flyway tự áp **V9, V10** lên DB `TSDMS`.
2. Build kiểm chứng: `mvnw spotless:apply test-compile` (đã chạy, BUILD SUCCESS).
