# DB: ràng buộc toàn vẹn khi xóa — Đợt 1 (RESTRICT), Đợt 2 (dữ liệu tiền) & Đợt 3 (phòng ngừa) (2026-08-17, bổ sung 19/08)

Ghi cho cả ba đợt cùng lúc vì chúng dùng chung một phát hiện gốc và chung một hạ tầng
(`DeleteGuard`). Đợt 1 merge ở PR #157, Đợt 2 merge ở PR #161, Đợt 3 ở nhánh
`feat/rang-buoc-xoa-dot-3`.

## Gọi tên cho đúng

Tên chuẩn trong ngành là **toàn vẹn tham chiếu khi xóa** (*referential integrity on delete*),
không phải "validate". Validate hỏi **một bản ghi có hợp lệ không**; cái này hỏi **quan hệ
giữa các bảng có cho phép hành động không**. Ba luật kinh điển:

| Luật | Nghĩa | Ví dụ trong TSDMS |
|---|---|---|
| **RESTRICT** | Còn con thì CẤM xóa cha | Trường còn lớp → không xóa trường |
| **CASCADE** | Xóa cha thì xóa luôn con | Xóa bài giảng → xóa file đính kèm |
| **SET NULL** | Xóa cha thì gỡ liên kết ở con | (chưa dùng chỗ nào) |

## Phát hiện gốc: vì sao 43 khóa ngoại không cứu được gì

Schema có **43 khóa ngoại** và **không một cái nào** khai `ON DELETE`. SQL Server mặc định
`NO ACTION`, tức là RESTRICT. Nghe thì tưởng đã an toàn sẵn.

Nhưng dự án **xóa MỀM là chính**: 22/38 bảng có cột `IsDeleted`, và xóa mềm chỉ là một câu
`UPDATE`. **Khóa ngoại không bao giờ nhìn thấy một câu UPDATE.**

> Khóa ngoại đang bảo vệ đúng thứ dự án hiếm khi làm (xóa cứng), và mù hoàn toàn với thứ dự
> án làm hằng ngày (xóa mềm).

Hệ quả thứ hai, âm thầm hơn: **không một câu query nào lọc theo cờ `IsDeleted` của bảng CHA**.
Đã grep toàn bộ repository — không chỗ nào join ngược lên cha để loại con mồ côi. Nên xóa mềm
một trường xong, lớp/lịch/hợp đồng của nó vẫn hiện đầy đủ ở mọi màn hình, chỉ là trỏ vào một
cái tên đã biến mất.

Kết luận: chốt chặn **phải viết ở tầng service**. Không có cách nào để DB tự lo.

---

## Hạ tầng: `DeleteGuard`

`backend/src/main/java/com/kdc/tsdms/common/DeleteGuard.java`

```java
DeleteGuard.of("trường " + s.getName())
        .blockIf(classRepo.countBySchoolIdAndDeletedFalse(id), "lớp học")
        .blockIf(assignmentRepo.countBySchoolIdAndStatusInAndDeletedFalse(id, CON_HIEU_LUC), "phân công đang chạy")
        .check();
```

Điểm thiết kế đáng nói nhất: **gom HẾT lý do rồi mới báo một lần**, thay vì ném ngay ở rào
đầu tiên. Kiểu ném-ngay bắt người dùng sửa một thứ → bấm xóa lại → gặp rào tiếp theo → lặp
tới khi hết, mỗi vòng một lần chờ mà vẫn không biết còn bao nhiêu rào phía sau. Ở đây kể hết
trong một câu để họ tự quyết định có nên xóa nữa hay không:

> Không thể xóa trường THCS Ba Đình: còn 3 lớp học, 2 phân công đang chạy, 1 hợp đồng dịch vụ
> và 40 hồ sơ học sinh. Vui lòng xử lý các mục trên trước khi xóa.

Đợt 2 thêm hai method:

- `blockAll(Collection<String>)` — nhận cả mớ rào đã thành câu sẵn, cho trường hợp một câu SQL
  gom hết dữ liệu con của nhiều bảng trong một lượt.
- `huongDan(String)` — thay câu hướng dẫn mặc định. Cần khi rào chắn là thứ người dùng **không
  có cách nào tự gỡ** (kỳ lương đã chốt, hồ sơ pháp lý). Câu mặc định "vui lòng xử lý trước khi
  xóa" lúc đó thành lời hứa suông, đẩy người dùng đi tìm một cái nút không tồn tại.

---

## Đợt 1 — chặn xóa mềm khi còn con đang sống

### `SchoolService.delete`

Trước: **12 dòng, không một câu kiểm tra nào**. Xóa trường xong thì lớp, phân công, phòng, học
sinh, hợp đồng dịch vụ, đánh giá, khung tiết **vẫn sống nguyên** — lịch dạy vẫn chạy cho một
trường không còn tồn tại.

Sau: chặn theo 4 thứ — lớp học, phân công còn hiệu lực (`ACTIVE` + `PENDING`), hợp đồng dịch
vụ, hồ sơ học sinh.

**Cố ý KHÔNG chặn theo `Room` và `Period`.** Hai bảng này là cấu hình nội bộ *của chính trường
đó* (phòng học, khung tiết), không phải dữ liệu nghiệp vụ độc lập. Chặn theo chúng thì mọi
trường được seed sẵn đều không bao giờ xóa được, mà chẳng bảo vệ được gì cả.

### `TeacherService.deleteTeacher` — ca đắt tiền nhất

Xóa mềm giáo viên **không đụng tới bảng `Schedule`**. Nên buổi dạy vẫn nằm nguyên đó, vẫn được
chấm công, và **vẫn chảy vào bảng lương** của một người đã "nghỉ". Chặn theo phân công còn hiệu
lực + buổi dạy sắp tới (`startTime > now`, trạng thái `PENDING`/`APPROVED`).

### `LessonService.delete` — CASCADE, không phải RESTRICT

Khác hai chỗ trên. `LessonFile` **không có đời sống riêng**: không có màn hình nào quản lý file
đính kèm độc lập với bài giảng, xóa bài giảng mà giữ file lại thì file thành rác không ai trỏ
tới. Nên ở đây xóa mềm bài giảng kéo theo xóa mềm file. **Không** đụng tới file vật lý trên đĩa
— xóa mềm là còn khôi phục được, xóa file đi thì khôi phục ra cái vỏ rỗng.

---

## Đợt 2 — bảo vệ dữ liệu tiền bạc

### 1. `AssignmentService.purge` — khóa cứng theo kỳ lương

Đây là chỗ **duy nhất** trong dự án xóa CỨNG bảng `Attendance`. Mà chấm công là nguồn duy nhất
sinh ra con số trên phiếu lương. Xóa phân công của một tháng đã trả lương = xóa mất phần đối
chiếu của một khoản tiền đã đi khỏi tài khoản: phiếu lương vẫn ghi 40 tiết nhưng không còn dòng
nào chứng minh 40 tiết đó có thật. Chạy lại `generate()` cũng không dựng lại được — nó chỉ ghi
đè các dòng `DRAFT`.

Chốt: kỳ lương `FINALIZED` hoặc `PAID` thì cấm hẳn. Kỳ `DRAFT` vẫn cho, vì dựng lại được.

Câu SQL phải viết **native**, không dùng JPQL được: các entity ở đây nối nhau bằng cột khóa
trần (`Integer`/`Long`) chứ không phải `@ManyToOne`, nên JPQL không có đường join. Nối kỳ lương
với chấm công theo đúng cách `PayrollService.generate` gom số — cùng giáo viên + cùng tháng/năm
của `WorkDate`:

```sql
SELECT CONCAT(p.PeriodMonth, '/', p.PeriodYear)
FROM Payroll p
JOIN Attendance a ON a.TeacherId = p.TeacherId
                 AND YEAR(a.WorkDate) = p.PeriodYear
                 AND MONTH(a.WorkDate) = p.PeriodMonth
JOIN Schedule s ON s.Id = a.ScheduleId
WHERE s.AssignmentId = :assignmentId AND p.Status IN ('FINALIZED', 'PAID')
GROUP BY p.PeriodYear, p.PeriodMonth
ORDER BY p.PeriodYear, p.PeriodMonth
```

Hệ thống **không có nút mở lại kỳ lương**, nên đây là rào không gỡ được. Thông báo phải nói
thẳng chuyện đó thay vì hứa suông:

> Không thể xóa vĩnh viễn phân công này: còn chấm công thuộc kỳ lương đã chốt/đã trả (8/2026).
> Phiếu lương đã chốt phải giữ nguyên bằng chứng chấm công, nên phân công này chỉ có thể nằm
> lại trong thùng rác.

### 2. `deleteTrueTeacher` — hai cái sai chồng nhau

Bản cũ:

```java
try {
    ceRepo.deleteAll(ceRepo.findByTeacherId(id));      // xóa CỨNG chứng chỉ
    contractRepo.deleteAll(contractRepo.findByTeacherId(id));  // xóa CỨNG hợp đồng
    teacherRepo.delete(t);
    teacherRepo.flush();
} catch (DataIntegrityViolationException e) {
    throw new ApiException(CONFLICT, "Không thể xóa vĩnh viễn: giáo viên id=" + id);
}
```

- **Sai về nghiệp vụ:** hợp đồng lao động và bằng cấp là hồ sơ pháp lý, ở đây bị hủy như hiệu
  ứng phụ của một thao tác dọn dẹp — và vì là xóa cứng nên không có thùng rác nào giữ lại.
- **Sai về thông tin:** khi khóa ngoại chặn thật (còn phân công, chấm công, phiếu lương…),
  người dùng chỉ nhận đúng một câu không nói gì. Không biết vướng ở đâu, không biết phải làm
  gì tiếp.

Nay: hỏi thẳng DB xem còn dòng nào ở **cả 11 bảng con**, kể tên đầy đủ, và **không xóa hộ thứ
gì cả**.

Chỗ này chọn gom vào **một câu SQL `UNION ALL`** thay vì tiêm thêm 9 repository:
`TeacherService` đã có 8 dependency, thêm 9 cái nữa chỉ để gọi `count` là biến class thành cái
tủ chứa repository. Quan trọng hơn: danh sách bảng con nằm gọn ở **đúng một chỗ**, ai thêm bảng
mới trỏ vào `Teacher` thì thêm đúng một dòng.

**Câu SQL này cố ý KHÔNG lọc `IsDeleted`** — khác mọi query nghiệp vụ khác trong dự án. Lý do:
sắp chạy `DELETE` thật, mà khóa ngoại chặn theo **sự tồn tại** của dòng con chứ không quan tâm
cờ xóa mềm. Một chứng chỉ đã "xóa" vẫn khiến `DELETE Teacher` nổ.

Hệ quả cố ý: **giáo viên đã từng làm việc thật thì gần như không bao giờ xóa cứng được.** Họ
nằm lại trong thùng rác và khôi phục được. Chỉ hồ sơ tạo nhầm (chưa gắn dữ liệu nào) mới xóa
hẳn — đúng thứ tính năng này thực sự cần phục vụ.

### 3. `deleteCertificate` — chuyển sang xóa mềm

Bản cũ xóa hẳn dòng DB **và** `Files.deleteIfExists` luôn file PDF trên đĩa, lý do ghi trong
comment là "xóa mềm chỉ tổ dồn rác vô ích". Nhưng thứ bị dọn ở đây là bản scan bằng đại học của
một con người: bấm nhầm một cái là mất vĩnh viễn, không thùng rác, không dấu vết ai đã bấm.

Nay xóa mềm, giữ đủ `DeletedAt`/`DeletedBy`. Và **file PDF phải ở nguyên trên đĩa** — dòng DB
vẫn trỏ vào nó, xóa file đi là biến bản ghi còn sống thành cái vỏ rỗng.

---

## Test: mutation testing lộ ra một test vô dụng

Bộ test có 27 unit + 6 IT. Nhưng con số đó không nói lên gì nếu không thử **gỡ chốt xem test có
đỏ không**. Kết quả 5 lần mutation:

| Gỡ cái gì | Test đỏ |
|---|---|
| Chốt kỳ lương ở `purge` | 2 (unit) |
| `deleteAll` chứng chỉ/hợp đồng đặt lại TRƯỚC guard | 2 (unit) |
| `deleteCertificate` quay về `ceRepo.delete(c)` | 1 (unit) |
| Bỏ 1 bảng khỏi câu `UNION` | 1 (**chỉ IT bắt được**) |
| Bỏ `AND p.Status IN ('FINALIZED','PAID')` | 2 (**chỉ IT bắt được**) |

Lần mutation thứ hai lộ ra một **test vô dụng** mà đọc code thì không thấy: test cũ chỉ kiểm
`ceRepo.deleteAll` không được gọi trên **đường BỊ CHẶN** — mà đường đó vốn đã ném lỗi trước khi
tới đoạn xóa, nên kiểm gì cũng xanh. Đặt lại `deleteAll` đúng chỗ của bản cũ (TRƯỚC guard) thì
test vẫn xanh như thường. Phải thêm `verify` trên cả **đường đi trót lọt** (hồ sơ trống, xóa
được) thì mới thật sự khóa được hành vi.

## Hai câu SQL thuần phải có IT — unit test không đụng tới chúng

`DeleteIntegrityIT` (`AbstractJpaSliceIT`, SQL Server thật qua Testcontainers). Unit test bằng
Mockito chỉ chứng minh service xử lý đúng cái mà repository **trả về**; nó không hề đụng tới
nội dung câu SQL. Mà cả hai câu ở đây đều là `nativeQuery` — sai tên bảng, sai tên cột, hay
dùng hàm SQL Server không có thì Java vẫn biên dịch ngon lành và chỉ nổ đúng lúc người dùng
bấm nút xóa.

Test đáng chú ý nhất là **lưới tự bảo trì**: đọc thẳng câu SQL trên annotation `@Query` bằng
reflection, rút tên bảng bằng regex, rồi so với `sys.foreign_keys`:

```java
Set<String> trongSchema = jdbc.queryForList("""
        SELECT DISTINCT OBJECT_NAME(fk.parent_object_id)
        FROM sys.foreign_keys fk
        WHERE OBJECT_NAME(fk.referenced_object_id) = 'Teacher'
        """, String.class) ...
assertThat(trongSql).isEqualTo(trongSchema);
```

Không chép tay danh sách lần thứ hai, nên không có cách nào lệch mà vẫn xanh. Thành viên thêm
bảng mới trỏ vào `Teacher` mà quên khai vào câu `UNION` → test đỏ ngay, kèm tên bảng bị thiếu.

## Bẫy gặp phải

- **`List.of(new Object[] {...})` bị flatten.** Native query trả `List<Object[]>`; stub bằng
  `List.of(new Object[] {"contract", 1})` thì javac suy ra `List<Object>` (2 phần tử) chứ không
  phải một mảng, và báo lỗi kiểu rất khó đọc. Phải viết `List.<Object[]>of(...)`.
- **`git checkout <file>` để hoàn tác một mutation sẽ xóa luôn phần code chưa commit của file
  đó.** Đã mất nguyên phần sửa `AssignmentService` vì việc này. Commit trước rồi hãy mutation,
  hoặc hoàn tác đúng đoạn đã sửa.
- **Sửa file bằng script rồi chạy test ngay sẽ vướng spotless** (khác line-ending). Luôn
  `./mvnw spotless:apply` sau khi sửa bằng script.

## Đợt 3 (19/08) — chốt phòng ngừa cho 5 bảng CHƯA có luồng xóa

`Branch`, `Employee`, `Room`, `Student`, `Period` chưa hề có API xóa. Chốt luật TRƯỚC khi
thành viên nào đó viết chức năng xóa và vô tình tái sinh đúng lỗ hổng của Nhóm A. Mỗi bảng
một `service.delete()` dùng sẵn `DeleteGuard` — **ai làm feature xóa thì gọi method có sẵn,
đừng tự viết**:

| Bảng | Service | Luật |
|---|---|---|
| Branch | `BranchService.delete` | RESTRICT trên CẢ 5 bảng con (trường, GV, NV, bài giảng, HĐ dịch vụ) — chi nhánh rỗng thật sự mới xóa được |
| Employee | `EmployeeService.delete` | Chỉ chặn NGHĨA VỤ TƯƠNG LAI: ca `SCHEDULED` chưa tới ngày + đơn xin ca `PENDING`. Dấu vết ai-phân-công/ai-duyệt cố tình KHÔNG chặn (xóa mềm giữ nguyên dòng nên tên vẫn tra được) |
| Room | `RoomService.delete` | Chặn theo thứ SẮP DÙNG: buổi dạy tương lai + ô TKB hằng tuần. Buổi đã dạy là lịch sử, không chặn |
| Student | `StudentService.delete` | `ClassEnrollment` không có cờ xóa mềm — còn dòng là còn đang học: rút khỏi lớp trước, xóa hồ sơ sau |
| Period | `PeriodService.delete` | Tiết là chỗ NEO của TKB (`AssignmentSlot.PeriodId` + `Schedule.PeriodId`) — còn ai dùng thì cấm |

14 method đếm/tìm mới trên 12 repository, toàn bộ là **derived query** — không thêm câu SQL
thuần nào, Hibernate tự đối chiếu tên cột lúc dựng context (tức là `SchemaMigrationValidationIT`
phủ luôn phần validation, không cần IT riêng như hai câu native của Đợt 2).

Về bullet "cân nhắc `ON DELETE` tường minh cho FK xóa cứng": **cân nhắc rồi, quyết định KHÔNG
làm.** SQL Server mặc định `NO ACTION` đã là RESTRICT — thêm `ON DELETE NO ACTION` tường minh
là đổi 43 constraint để được đúng hành vi đang có; còn đổi sang `CASCADE` thì ngược với quyết
định "chặn hẳn" đã chốt. Không có thay đổi hành vi nào đáng để đụng schema giữa kỳ.

## Còn lại

- **Đợt 4** — migration rà và dọn dữ liệu mồ côi đã có sẵn (con đang trỏ vào cha `IsDeleted=1`).
  Chỉ làm sau khi Đợt 1–3 đã chặn được nguồn sinh mồ côi mới. Giờ là lúc làm được rồi.
- **Chưa xử lý (báo để quyết):** `TeacherService.saveContract` ghi ĐÈ hợp đồng cũ tại chỗ —
  số hợp đồng, lương, thời hạn cũ mất sạch không dấu vết. Nếu coi hợp đồng là hồ sơ pháp lý thì
  đây còn nặng hơn cả xóa cứng. Sửa cho đúng phải lưu trữ bản cũ rồi tạo bản mới, mà `ContractNo`
  đang `UNIQUE` toàn cục (không lọc `IsDeleted`) nên cần một migration đổi sang filtered unique
  index — việc riêng, không gộp vào đợt này.
