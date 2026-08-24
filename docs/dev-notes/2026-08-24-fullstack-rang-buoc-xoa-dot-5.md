# Ràng buộc toàn vẹn khi xóa — Đợt 5: những chỗ còn sót

> Ngày: 2026-08-24 · Phần: fullstack · Nhánh: `fix/rang-buoc-xoa-dot-5`
>
> Tiếp nối [2026-08-17-database-rang-buoc-toan-ven-khi-xoa.md](2026-08-17-database-rang-buoc-toan-ven-khi-xoa.md) (Đợt 1–4).
> Đợt 4 từng được chốt là đợt cuối. Một lần rà lại toàn dự án cho thấy chưa phải.

---

## 0. Vì sao có đợt này

Đợt 1–4 dựng `DeleteGuard` và gắn nó vào 9 service. Nhưng gắn rào vào 9 chỗ **không**
bằng "hệ thống đã được bảo vệ" — nó chỉ bằng "9 chỗ đó đã được bảo vệ". Đợt 5 đi hỏi
câu khác hẳn:

> Có đường nào **đi vòng** qua những cái rào đã dựng không?

Có. Và cái lớn nhất nằm ngay cạnh thứ ta bảo vệ kỹ nhất.

Cách rà: liệt kê **12 endpoint xóa dữ liệu** (`@DeleteMapping`), rồi với mỗi bảng cha
soi lại toàn bộ bảng con của nó trong 43 khóa ngoại của schema — chứ không chỉ đọc
service nào có chữ `DeleteGuard`.

| Nhóm | Kết quả rà |
|---|---|
| Có `DeleteGuard` | Trường, Lớp, Môn học, Giáo viên |
| Có rào riêng | Đơn giá lương, Kỳ nghỉ (cố ý không chặn, có màn xem tác động) |
| **Không có rào nào** | **Nhóm môn học, Bài giảng, File bài giảng, Phân công, Đánh giá GV, Chứng chỉ** |

Một phát hiện phụ đáng nhớ: 5 service của Đợt 3 (`Branch`, `Employee`, `Room`,
`Student`, `Period`) có guard viết đầy đủ nhưng **chưa controller nào gọi tới**. Chúng
là bảo hiểm cho tương lai, không phải sự bảo vệ đang có hiệu lực. Đừng nhìn số lượng
`DeleteGuard.of(...)` mà kết luận độ phủ.

---

## 1. Lỗ hổng nặng nhất — xóa nhóm môn học quét sạch kho bài giảng

### Chuyện gì đã xảy ra

`SubjectService.delete` có rào rất chặt:

```java
DeleteGuard.of("môn học " + s.getName())
        .blockIf(assignmentRepo.countBySubjectId(id), "phân công giảng dạy")
        .blockIf(lessonRepo.countBySubjectIdAndDeletedFalse(id), "bài giảng")
        .blockIf(teacherSubjectRepo.countBySubjectId(id), "giáo viên đang phụ trách môn")
        .check();
```

Còn `SubjectCategoryService.delete` thì cascade **thẳng**: gắn cờ xóa cho mọi môn
trong nhóm, rồi cho mọi bài giảng của các môn đó — **không hỏi rào nào cả**.

Nói cách khác, cùng một luật có **hai bản**, và bản đi đường vòng là bản **không có
rào**:

- Xóa một môn còn **1** bài giảng → bị chặn.
- Xóa cả nhóm chứa **20 môn và 300 bài giảng** → trôi tuột.

### Đường đi thật, chỉ 2 bước

1. **Sửa nhóm môn → đổi trạng thái sang "Đã tắt".** `apply()` gán thẳng
   `sc.setStatus(req.status())`, không kiểm tra gì, kể cả khi nhóm đang có môn ACTIVE
   với phân công đang chạy.
2. **Bấm Xóa.** Qua cửa vì đã DISABLED.

Quyền cần có: `LESSON_MANAGE` — **cùng quyền với người sửa bài giảng**, không phải
quyền admin. Người quản lý kho bài giảng xóa được cả danh mục môn của trung tâm.

Hậu quả: kho bài giảng của các môn đó biến mất; môn biến mất khỏi dropdown nên không
tạo được phân công mới; và (trước mục 3 dưới đây) **không có thùng rác nào lấy lại**.

### Cách vá — một luật, một chỗ

Tách rào chắn của môn học ra thành thứ **dùng lại được**, thay vì để mỗi bên tự viết:

```java
// SubjectService — trả về DeleteGuard CHƯA ném lỗi
DeleteGuard raoChanXoaMon(Integer subjectId, String tenMon) {
    return DeleteGuard.of("môn học " + tenMon)
            .blockIf(assignmentRepo.countBySubjectId(subjectId), "phân công giảng dạy")
            .blockIf(lessonRepo.countBySubjectIdAndDeletedFalse(subjectId), "bài giảng")
            .blockIf(teacherSubjectRepo.demGiaoVienConSongTheoMon(subjectId), "giáo viên đang phụ trách môn");
}
```

- `SubjectService.delete` gọi `.check()` → ném 409 như cũ.
- `SubjectCategoryService.delete` gọi `.lyDo()` → lấy danh sách lý do để ghép vào
  thông báo của mình.

`DeleteGuard` được thêm đúng một method cho việc đó:

```java
public List<String> lyDo() {
    return List.copyOf(blockers);
}
```

Rồi nhóm môn hỏi **tất cả** môn con **trước khi đụng vào dòng nào**:

```java
DeleteGuard guard = DeleteGuard.of("nhóm môn học " + sc.getName());
for (Subject s : subjects) {
    List<String> lyDo = subjectService.raoChanXoaMon(s.getId(), s.getName()).lyDo();
    guard.blockWhen(!lyDo.isEmpty(), "môn " + s.getName() + " (" + String.join(", ", lyDo) + ")");
}
guard.huongDan("Xóa nhóm môn sẽ xóa theo mọi môn trong nhóm, nên môn nào còn dữ liệu là cả "
                + "thao tác dừng lại. …")
        .check();
```

Thông báo ra màn hình:

> Không thể xóa nhóm môn học **Tin học**: còn môn Tin học 6 (3 phân công giảng dạy,
> 120 bài giảng) và môn Tin học 7 (5 bài giảng). Xóa nhóm môn sẽ xóa theo mọi môn
> trong nhóm, nên môn nào còn dữ liệu là cả thao tác dừng lại…

### Hệ quả gọn gàng

Sau khi vá, cascade **chỉ còn chạm tới môn RỖNG**. Vòng lặp xóa bài giảng của bản cũ
mất hết lý do tồn tại → bỏ luôn, kéo theo `LessonRepository` ra khỏi constructor.

**Bài học cho hội đồng:** một luật nghiệp vụ được viết ở hai chỗ thì chỗ thứ hai sẽ
lỏng hơn. Không phải vì người viết cẩu thả, mà vì chỗ thứ hai được viết cho *mục đích
khác* (ở đây là "đừng để môn mồ côi") và không ai nhớ nó cũng phải gánh luật của chỗ
thứ nhất.

### Vì sao KHÔNG chặn luôn việc đổi trạng thái sang DISABLED

Đã cân nhắc và cố ý không làm. Nhóm DISABLED vẫn bị ẩn khỏi dropdown tạo môn mới
(`listActive` lọc `status = ACTIVE`) trong khi các môn cũ vẫn chạy — đó là quy trình
**khai tử dần** hợp lệ. Rào ở bước xóa đã đủ đóng lỗ hổng.

---

## 2. Xóa giáo viên làm môn học kẹt vĩnh viễn

Rào `"giáo viên đang phụ trách môn"` gọi `countBySubjectId` trên bảng nối
`TeacherSubject`. Mà `TeacherSubject`:

- **không có cờ xóa mềm** (nó là bảng nối khóa kép, `V1`);
- **không ai dọn nó** khi giáo viên bị xóa mềm.

Nên sau khi xóa giáo viên, dòng nối vẫn còn và vẫn được đếm. Kết quả là **bế tắc vĩnh
viễn**:

> Thông báo: "còn 3 giáo viên đang phụ trách môn"
> → Người dùng mở danh sách giáo viên, tìm mãi không thấy ai (họ nằm trong thùng rác)
> → Cũng không có màn hình nào gỡ liên kết đó ra
> → Môn học **không bao giờ xóa được nữa**.

Vá bằng một câu đếm có lọc:

```java
@Query("SELECT COUNT(ts) FROM TeacherSubject ts WHERE ts.subjectId = :subjectId"
        + " AND ts.teacherId IN (SELECT t.id FROM Teacher t WHERE t.deleted = false)")
long demGiaoVienConSongTheoMon(@Param("subjectId") Integer subjectId);
```

**Đánh đổi đã cân, ghi lại để khỏi bàn lại:** giáo viên trong thùng rác vẫn giữ nguyên
liên kết, nên khôi phục họ *sau khi* môn đã bị xóa thì liên kết trỏ vào một môn đã xóa.
Chấp nhận được vì `TeacherSubject` chỉ được đọc ở **đúng một chỗ** (gợi ý ghép giáo
viên trong `AssignmentService`) và chỗ đó lọc theo môn còn sống.

> Ngược lại, rào đếm **phân công** thì CỐ Ý *không* lọc cờ xóa: phiếu trong thùng rác
> khôi phục lại được, mà khôi phục xong trỏ vào một môn đã xóa thì còn tệ hơn.
> Hai bảng, hai quyết định khác nhau, vì khả năng "sống lại" của chúng khác nhau.

---

## 3. Phân công vào thùng rác kéo theo cả buổi ĐÃ DẠY

### Mâu thuẫn nằm cạnh nhau trong cùng một file

`cancel()` rất cẩn thận — chỉ hủy buổi **tương lai**, cố ý giữ buổi quá khứ:

```java
boolean neverConfirmed = a.getConfirmedAt() == null;
for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
    if (!"CANCELLED".equals(s.getStatus()) && (neverConfirmed || s.getStartTime().isAfter(now))) {
        s.setStatus("CANCELLED");
    }
}
```

Rồi `softDelete()` chạy **ngay sau đó** gắn cờ xóa lên **TẤT CẢ** buổi — kể cả buổi đã
dạy xong đã có chấm công. Sự thận trọng vừa rồi bị xóa sạch trong 5 dòng.

### Hậu quả thật sự (đã truy đến cùng, không suy đoán)

`Attendance` **không có xóa mềm** và **không nằm trong cascade** này. Nên dòng chấm
công vẫn còn, nhưng buổi dạy sinh ra nó đã ẩn khỏi mọi màn hình — đúng thứ mà
`PayrollRepository.demChamCongMoCoi` đếm để cảnh báo trước khi chốt lương.

Hai điều cần nói rõ vì dễ kết luận quá tay:

1. **KHÔNG mất tiền.** Câu tính lương `findPayableWithGrade` join `Schedule` nhưng
   **không lọc `IsDeleted`**, nên tiền vẫn được trả đủ.
2. **Nhưng cảnh báo không phải là rào chắn.** Mức của `CHAM_CONG_MO_COI` là
   `CANH_BAO` chứ không phải `CHAN`, nên kế toán vẫn chốt kỳ được — và phiếu lương trả
   tiền cho những buổi dạy không còn tồn tại ở đâu cả.

### Cách vá

Dùng **đúng luật của `cancel()`**:

```java
boolean chuaTungXacNhan = a.getConfirmedAt() == null;
LocalDateTime bayGio = BusinessTime.now();
for (Schedule s : scheduleRepo.findByAssignmentIdAndDeletedFalse(id)) {
    if (!chuaTungXacNhan && !s.getStartTime().isAfter(bayGio)) {
        continue; // buổi đã tới giờ của phiếu đã từng chạy: để lại làm bằng chứng
    }
    // …gắn cờ xóa…
}
```

- Phiếu **chưa từng xác nhận** → không buổi nào có hiệu lực → xóa sạch, kể cả quá khứ.
- Phiếu **đã từng chạy** → buổi đã tới giờ là bằng chứng, giữ nguyên.

Buổi giữ lại vẫn hiện ở Lịch dạy và Chấm công dù phiếu đã nằm trong thùng rác. **Đó là
chủ đích**: tiền đã trả cho buổi nào thì buổi ấy phải tra ra được.

**Ô thời khóa biểu vẫn cascade đầy đủ** — nó là *mẫu lặp tuần*, không phải bằng chứng
của buổi nào, nên phiếu vào thùng rác thì mẫu phải biến khỏi thời khóa biểu.

`restore()` **không phải sửa**: nó chỉ bỏ cờ cho dòng *nào đang* bị gắn cờ.

### Cố ý KHÔNG đụng tới `PayrollHealthService`

Nâng `CHAM_CONG_MO_COI` lên `CHAN` là cách "sửa ở chỗ báo động". Sửa ở **nguồn** đúng
tầm hơn, và module bảng lương là việc của thành viên khác.

---

## 4. Thùng rác cho Kho bài giảng

### Vấn đề

Kho bài giảng xóa **mềm** nhưng **không có màn hình nào lôi bài đã xóa ra**. Trên thực
tế, "xóa mềm" ở đây bằng đúng xóa vĩnh viễn từ góc nhìn người dùng: dòng vẫn nằm trong
DB nhưng chỉ lấy lại được bằng câu `UPDATE` tay trong SSMS.

Bài giảng là **thứ tốn công soạn nhất hệ thống** mà lại là thứ duy nhất không có đường
lùi. (Modal xác nhận xóa còn ghi thẳng "sẽ không thể khôi phục" — đúng với hiện trạng
lúc đó, nay đã sửa lại theo sự thật mới.)

Danh sách đầy đủ những chỗ **có xóa nhưng không có thùng rác**: Bài giảng, File bài
giảng, Môn học, Nhóm môn học, Đánh giá GV, Chứng chỉ. Đợt này làm **Bài giảng** — thứ
đắt nhất; các thứ còn lại ghi lại ở mục 7.

### Backend

| Thành phần | Việc |
|---|---|
| `LessonRepository` | `findByIdAndDeletedTrue`, `findByDeletedTrueOrderByDeletedAtDesc` |
| `LessonFileRepository` | `findByLessonIdAndDeletedTrue` |
| `LessonTrashItem` | DTO gọn cho màn thùng rác |
| `LessonService` | `listTrash()`, `restore(id)` |
| `LessonController` | `GET /lessons/trash`, `POST /lessons/trash/{id}/restore` |

Quyền là `LESSON_MANAGE` chứ không phải `LESSON_VIEW`: giáo viên chỉ đọc bài đã xuất
bản, không có việc gì trong thùng rác của người soạn.

`/trash` phải khai **trước** `/{id}` — cùng lý do đã ghi ở `HolidayController`.

### Hai quyết định thiết kế đáng nhớ

**(a) Không khôi phục bừa mọi file đã xóa của bài.**

File bị xóa **riêng** bằng nút xóa file trước đó phải nằm nguyên chỗ đã xóa — người
dùng đã cố ý bỏ nó. Phân biệt bằng **dấu thời gian**: `delete()` gắn *cùng một*
`deletedAt` cho bài giảng và mọi file bị cuốn theo, nên file trùng khít mốc đó chính là
file biến mất **vì** bài giảng.

```java
private List<LessonFile> fileVeTheoBai(Lesson lesson) {
    Instant xoaLuc = lesson.getDeletedAt();
    if (xoaLuc == null) return List.of();
    return lessonFileRepo.findByLessonIdAndDeletedTrue(lesson.getId()).stream()
            .filter(f -> xoaLuc.equals(f.getDeletedAt()))
            .toList();
}
```

Con số này cũng được đưa lên màn thùng rác (`soFileKemTheo`) — vì "khôi phục lại được
12 file" mới là lý do người ta bấm Khôi phục thay vì soạn lại.

**(b) Dựng lại cả cái giá đỡ.**

Nếu môn học (và nhóm môn) của bài đang ở trạng thái đã xóa thì khôi phục theo. Một bài
giảng sống dưới một môn đã chết chính là "môn mồ côi" mà cả dự án đang chống.

Trường hợp này **có thật**, không phải giả định: xóa bài giảng cuối cùng của một môn
làm môn đó rỗng, mà môn rỗng thì xóa được (rào chắn chỉ đếm bài giảng *còn sống*). Chuỗi
khôi phục là 3 tầng: Bài giảng → Môn học → Nhóm môn.

Trạng thái **cố ý giữ nguyên `DISABLED`** — cùng lý lẽ với `SchoolService.restore` giữ
trường ở `INACTIVE`: thứ vừa moi ra khỏi thùng rác chưa chắc đã muốn cho chạy lại ngay,
người dùng tự bật khi đã chắc.

### Frontend (`LessonListPage.vue`)

Dùng lại đúng pattern của trang Trường/Lớp: một `viewMode` với hai tab.

```js
const viewMode = ref('list')   // 'list' | 'trash'

function switchView(mode) {
  viewMode.value = mode
  if (mode === 'trash') loadTrash()   // chỉ gọi API khi thật sự mở tab
}
```

Template chia làm hai nhánh `<template v-if="viewMode === 'list'"> … </template>` và
`<template v-else> … </template>`, nên toàn bộ phần lọc + cây Khối/Danh mục + phân trang
không phải sửa gì.

Sau khi khôi phục thì nạp lại **cả hai** danh sách:

```js
await lessonApi.restore(restoreTarget.value.id)
await loadTrash()
await loadLessons()   // bài vừa khôi phục phải có mặt ngay khi bấm sang tab Danh sách
```

**Màu lấy từ token** `--c-primary` / `--c-surface-2` trong `main.css` chứ không ghi cứng
hex như `SchoolListPage` đang làm (`#f97316`) — nếu không thì dark mode lệch. Xem thêm
[fe-dark-mode-va-tien-ich-cai-dat.md](fe-dark-mode-va-tien-ich-cai-dat.md).

---

## 5. Dọn những câu xóa cứng đang nằm chờ

Năm câu, **không nơi nào gọi**:

| Nơi | Câu |
|---|---|
| `ScheduleRepository` | `deleteAttendanceByAssignmentId` — `DELETE FROM Attendance` |
| `RoomRepository` | `xoaCungTheoTruong` |
| `PeriodRepository` | `xoaCungTheoTruong` |
| `LessonRepository` | `findBySubjectId` |
| `LessonFileRepository` | `deleteByLessonIdIn` |

Ba cái đầu là **súng đã lên đạn để nằm đó**. Chú thích của chúng ghi *"chỉ gọi khi xóa
vĩnh viễn trường khỏi thùng rác"* — mà chức năng xóa vĩnh viễn đã bị gỡ từ `01b5136`.
Ai bật lại tính năng đó rồi gọi chúng là xóa cứng `Room`/`Period` đang bị `Schedule` và
`AssignmentSlot` trỏ vào. Hai cái sau là tàn dư của bản hard-delete môn học.

Mã chết không phải chỉ là rác: nó là **lời mời gọi sai**. Người sau đọc thấy có sẵn
`deleteAttendanceByAssignmentId` sẽ tưởng đó là cách làm được duyệt.

---

## 6. Rào sửa phân công — chốt bằng DỮ LIỆU, không chỉ bằng TRẠNG THÁI

`AssignmentService.update` xóa **cứng** toàn bộ `Schedule` của phiếu rồi sinh lại.
`Attendance.ScheduleId` là khóa ngoại và schema **không khai `ON DELETE` ở đâu cả**, nên
còn một dòng chấm công là câu `DELETE` đâm thẳng vào ràng buộc → **lỗi 500 SQL thô**,
không phải một câu tiếng Việt người dùng đọc được.

Hôm nay chuyện đó chưa xảy ra được. Đã truy đủ **cả ba** đường sinh chấm công:

| Đường | Điều kiện | Kết luận |
|---|---|---|
| Tự check-in (`AttendanceService`) | `if (!"APPROVED".equals(s.getStatus()))` → chặn | không chạm |
| Job quét (`AttendanceSweepService`) | chỉ lấy `…AndStatus…("APPROVED")` | không chạm |
| Kỳ nghỉ (`HolidayService.fixAbsences`) | chỉ *sửa* dòng đã có | không tạo mới |

Mà phiếu `isEditable` (PENDING / REJECTED / EXPIRED) thì chưa buổi nào `APPROVED`.

**Nhưng đó là một suy luận bắc cầu qua ba file khác nhau, và không có gì giữ nó lại.**
Thêm một trạng thái vào `isEditable`, hoặc thêm một đường ghi `Attendance` mới, là nó
gãy trong im lặng — không test nào đỏ, chỉ có một lỗi 500 ở production.

Nay hỏi thẳng dữ liệu, **ngay sau cửa trạng thái và trước mọi bước kiểm tra khác**:

```java
long soChamCong = attendanceRepo.demChamCongTheoPhanCong(id);
if (soChamCong > 0) {
    throw new ApiException(HttpStatus.CONFLICT,
            "Không sửa được phân công này: đã có " + soChamCong + " bản ghi chấm công …"
                    + "Hãy hủy phiếu này rồi tạo phiếu mới cho giai đoạn còn lại.");
}
```

Đặt sớm để **dừng trước mọi câu xóa cứng**, chứ không phải sau khi đã dọn nửa vời.

> **Dấu hiệu có người đã lường trước:** câu `DELETE FROM Attendance` ở mục 5 được viết
> sẵn nhưng chưa bao giờ được nối vào đâu. Ai đó đã thấy vấn đề, viết ra phương án
> *xóa luôn chấm công*, rồi (may thay) không dùng.

---

## 7. Những chỗ CỐ Ý không sửa — ghi lại để lần sau khỏi bàn lại

**`SchoolService.delete` không đếm thêm hai bảng:**

- **`Holiday.SchoolId`** — lịch nghỉ là *cấu hình riêng của trường*, cùng loại với
  `Room`/`Period`, nên theo đúng lý lẽ đã có thì không chặn.
- **`TeacherEvaluation.SchoolId`** — phiếu đánh giá là **lịch sử**, không phải nghĩa vụ
  đang treo. Chặn theo lịch sử thì một trường từng hợp tác là **vĩnh viễn không xóa
  được** — đúng cái bẫy mà rào "phân công *đang chạy*" đã tránh bằng cách chỉ đếm phiếu
  còn hiệu lực.

Cả hai chỉ để lại dòng trỏ vào một trường ở thùng rác, mà mọi chỗ hiển thị tên trường
đều tra bằng `findById` (không lọc cờ xóa) nên tên vẫn hiện đúng.

**File vật lý không bao giờ được dọn.** Xóa bài giảng / file đính kèm / chứng chỉ đều
cố ý giữ file trên đĩa — đúng, vì xóa mềm phải khôi phục được. Với thùng rác ở mục 4 thì
điều này nay đã có ý nghĩa thật. Vẫn còn là **rò rỉ dung lượng** về lâu dài.

**Lưới an toàn của Đợt 4 đã mất giao diện.** Commit `4f78724` gỡ màn *Nhật ký thao tác*
+ *Rà soát dữ liệu mồ côi* theo quyết định bỏ chức năng. Bảng `OrphanScan` và thủ tục
`usp_ScanOrphanRows` (V35) vẫn còn, `OrphanScanIT` vẫn xanh, nhưng muốn quét phải vào
SSMS gõ `EXEC usp_ScanOrphanRows`. Đáng chú ý hơn: **`AuditLog` cũng bị gỡ**, nên ai xóa
cái gì chỉ còn lại hai cột `DeletedBy`/`DeletedAt` trên chính dòng bị xóa.

**Còn thiếu thùng rác:** Môn học, Nhóm môn học, Đánh giá GV, Chứng chỉ. Đợt này chỉ làm
Bài giảng.

---

## 8. Kiểm thử

| File | Số test | Canh cái gì |
|---|---|---|
| `DeleteRestrictTest$XoaNhomMonHoc` | 4 | cascade nhóm môn không đi vòng qua rào |
| `DeleteRestrictTest$XoaMonHoc` | +1 | giáo viên thùng rác không giữ môn lại |
| `AssignmentTrashKeepsPastTest` | 3 | buổi đã dạy ở lại khi phiếu vào thùng rác |
| `LessonTrashRestoreTest` | 5 | phân biệt file theo `deletedAt`, dựng lại giá đỡ |
| `AssignmentUpdateAttendanceGuardTest` | 2 | dừng trước mọi câu xóa cứng |

**Kết quả cuối:** `mvnw clean verify` — **347 unit + 28 IT xanh, `Skipped: 0`**, 37
migration áp lên `v38`. FE `eslint` + `prettier` + `build` xanh.

### Đối chiếu bằng mutation

Test xanh chưa chứng minh test có giá trị. Với chỗ tinh tế nhất (mục 3), đã bỏ dòng
`continue` trong `softDelete` để cố ý gây lại lỗi cũ → `AssignmentTrashKeepsPastTest`
đỏ ngay → khôi phục → xanh lại. Không làm bước này thì không biết test đang canh gì.

> **Bẫy đã gặp lại:** Docker tắt thì các lớp `*IT` **im lặng bị bỏ qua** và build vẫn
> báo `BUILD SUCCESS`. Luôn đọc dòng `Skipped:` chứ đừng đọc chữ `SUCCESS`.

---

## 9. Hai thứ vướng dọc đường (không liên quan đợt này)

1. **FE build gãy trên master nếu chưa `npm install`.** PR trước thêm `chart.js` và
   `vue-chartjs` vào `package.json` nhưng máy nào chưa cài thì rolldown không resolve
   được `vue-chartjs`. Chạy `npm install` là xong; `package-lock.json` **không** commit
   kèm đợt này vì đó chỉ là nhiễu do cài đặt cục bộ.
2. **Mockito `UnfinishedStubbingException`.** Viết
   `when(repo.findAllById(...)).thenReturn(List.of(taoSubject()))` mà `taoSubject()` bên
   trong lại gọi `when(...)` → stubbing lồng nhau, Mockito ném lỗi khó hiểu ở test
   *khác*. Dựng dữ liệu **trước**, rồi mới `when`.
