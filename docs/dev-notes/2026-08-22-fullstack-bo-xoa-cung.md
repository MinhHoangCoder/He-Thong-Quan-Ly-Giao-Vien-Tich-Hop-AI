# Bỏ xóa cứng toàn hệ thống + hộp thoại xác nhận dùng chung (2026-08-22)

## Vấn đề: bốn chỗ xóa cứng, chỗ nặng nhất không nằm trong thùng rác

Dự án nói là "xóa mềm là chính" (22/38 bảng có `IsDeleted`), nhưng vẫn còn bốn đường xóa hẳn
khỏi DB:

| Chỗ | Xóa gì | Vào từ đâu |
|---|---|---|
| `AssignmentService.purge` | Assignment + Slot + Schedule + **Attendance** + ScheduleStatusLog | Thùng rác Phân công |
| `SchoolService.purge` | School + Period + Room | Thùng rác Trường |
| `SchoolClassService.purge` | SchoolClass | Thùng rác Lớp |
| `TeacherService.deleteTrueTeacher` | Teacher | Thùng rác Giáo viên |
| `SubjectService.delete` | Subject + **Lesson + LessonFile** + TeacherSubject | **Nút Xóa chính** |

Bốn dòng đầu ít nhất còn nằm sau một bước "vào thùng rác đã". Dòng cuối thì không: đó là nút
Xóa bình thường của trang Nhóm môn học, bấm một lần là bài giảng của giáo viên biến mất.

### Vì sao `AssignmentService.purge` là chỗ nguy hiểm nhất trong bốn chỗ có rào

Nó là nơi DUY NHẤT trong dự án xóa cứng bảng `Attendance`. Mà chấm công là nguồn duy nhất sinh
ra con số trên phiếu lương: xóa rồi thì `PayrollService.generate()` cũng không dựng lại được,
vì hàm đó chỉ ghi đè dòng `DRAFT`.

Guard cũ có chặn theo kỳ lương đã chốt/đã trả, và chặn đúng. Nhưng nó chỉ trả lời được câu hỏi
"kỳ lương nào đã khóa", không trả lời được "sau này ai cần đối chiếu lại buổi dạy tháng 3 thì
lấy ở đâu".

### Vì sao `SubjectService` mới là chỗ sai nghiệp vụ rõ nhất

Bài giảng là công sức soạn của giáo viên, không phải phụ kiện của môn học. Bản hard delete
(2026-08-07) xóa chúng như hiệu ứng phụ của một thao tác dọn dẹp danh mục — và vì là xóa cứng
nên không có thùng rác nào giữ lại.

Thêm một hệ quả ít ai để ý: dòng `Subject` biến mất thì mọi bản ghi cũ trỏ vào nó chỉ còn lại
một con số `SubjectId` không tra ra tên. Báo cáo lịch sử đọc thành "(Môn #17)".

## Đã làm

Bỏ hẳn cả bốn endpoint purge (BE + FE + nút bấm). Thùng rác chỉ còn **Khôi phục**.

`SubjectService.delete` quay lại xóa mềm, rào chắn đổi sang `DeleteGuard` để kể hết lý do
trong một lần thay vì ném lỗi ở rào đầu tiên:

```java
DeleteGuard.of("môn học " + s.getName())
        .blockIf(assignmentRepo.countBySubjectId(id), "phân công giảng dạy")
        .blockIf(lessonRepo.countBySubjectIdAndDeletedFalse(id), "bài giảng")
        .blockIf(teacherSubjectRepo.countBySubjectId(id), "giáo viên đang phụ trách môn")
        .check();
```

### Cái được và cái mất

Được: không còn đường nào làm mất dữ liệu vĩnh viễn từ trong ứng dụng.

Mất: dữ liệu nhập nhầm (trường ma, lớp gõ sai) nằm lại thùng rác mãi mãi. Đây là đánh đổi có
ý thức — dọn rác định kỳ là việc của DBA với một câu SQL có kiểm soát, không phải của một nút
bấm mà ai cầm quyền quản trị cũng chạm được.

## Hộp thoại xác nhận: ba kiểu cho cùng một việc

Trước lần này, cùng một thao tác "xóa" được hỏi bằng ba cách:

- `SchoolListPage` / `SchoolClassListPage` — modal riêng của từng trang
- `AssignmentPage` — `window.confirm()` của trình duyệt
- `PayrollPage` — `alert()` để báo lỗi

Ba kiểu nghĩa là ba lần người dùng phải đọc lại xem nút nào là nút nguy hiểm.

`components/ui/ConfirmDialog.vue` làm hai thứ `window.confirm()` không làm được, mà xóa dữ liệu
thật thì cần cả hai:

- **liệt kê những gì đang CHẶN**, kèm link đi thẳng tới chỗ phải xử lý;
- **bắt gõ lại tên bản ghi** trước khi cho bấm, dành cho thao tác không lùi được.

Kèm theo là `composables/useToast.js` + `ToastHost.vue` báo kết quả. `ToastHost` đặt ở
`App.vue` chứ không trong layout: báo "Đã xóa" xong router chuyển trang thì toast nằm trong
layout sẽ bị dựng lại và biến mất.

## Lỗi vá kèm

`router/admin.routes.js` khai **trùng** ba route `/admin/lessons`, `/new`, `/:id/edit` — cùng
path và cùng `name`, khai hai lần. Vue Router cảnh báo và ghi đè bản sau. Không gây lỗi chức
năng nhưng để nguyên thì mở Console lúc demo là có cảnh báo đỏ.

## Kiểm thử

`DeleteRestrictTest$XoaMonHoc` (3 ca): môn đang hoạt động thì chặn; còn dữ liệu con thì kể hết
lý do trong MỘT lần báo; môn sạch thì xóa mềm và **không đụng tới bảng nào khác** — vế cuối
mới là điểm chính của lần sửa này.

Các test chỉ kiểm tra hàm đã bỏ đã được gỡ (`DeleteRestrictTest$XoaVinhVienPhanCong`,
`$XoaVinhVienGiaoVien`, hai ca trong `SchoolServiceTest`, một ca trong
`TeacherDeleteLocksAccountTest`).
