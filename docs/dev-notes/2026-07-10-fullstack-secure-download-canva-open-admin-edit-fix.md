# 2026-07-10 — fullstack: tải file bảo mật + mở link Canva mới nhất + sửa bug ADMIN "Sửa bài giảng" bị đá về dashboard

Bản vá này gộp 3 việc liên quan tới module **Bài giảng** (Lesson), phát sinh từ
cùng 1 buổi rà soát khi kiểm tra màn `TeacherLessonViewPage.vue`:

1. Bấm "Xem" trên slide Canva phải mở đúng **link Canva mới nhất**.
2. Bấm "Xem" trên phiếu bài tập (PDF) phải **tự động tải file về máy**.
3. ADMIN bấm "Sửa bài giảng" (ở `/admin/lessons`) bị **văng ngược về Dashboard**.

Việc 1 và 2 kéo theo phải đóng luôn lỗ hổng bảo mật đang tồn tại (mục 2 bên dưới),
nối tiếp `2026-07-09-backend-uploads-public-scope.md`.

---

## 1. Bug ADMIN bấm "Sửa bài giảng" → về Dashboard

### Nguyên nhân

`LessonListPage.vue` (danh sách bài giảng) được **DÙNG CHUNG** cho 2 khu vực route
khác nhau:

| Khu vực | Path danh sách | Path sửa | `meta.roles` (đã sửa) |
|---|---|---|---|
| ADMIN (`admin.routes.js`) | `/admin/lessons` (`admin-lesson-list`) | `/admin/lessons/:id/edit` (`admin-lesson-edit`) | `ADMIN, EMPLOYEE` |
| STAFF (`staff.routes.js`) | `/staff/lessons` (`lesson-list`) | `/staff/lessons/:id/edit` (`lesson-edit`) | `ACCOUNTANT, HR, ACADEMIC, SALES` |

Có **2 lỗi cộng dồn**:

1. **Copy-paste sai `meta.roles`**: `admin-lesson-new` và `admin-lesson-edit` trong
   `admin.routes.js` bị gán nhầm `roles: ['ACCOUNTANT','HR','ACADEMIC','SALES']`
   (copy từ `staff.routes.js`) thay vì `['ADMIN','EMPLOYEE']` như route
   `admin-lesson-list` đứng ngay trên nó.
2. **Nút "Sửa"/"+ Thêm bài giảng" push CỨNG tên route khu STAFF**: dù đang đứng ở
   `/admin/lessons`, code vẫn `router.push({ name: 'lesson-edit', ... })` /
   `{ name: 'lesson-new' }` — tên này CHỈ tồn tại ở khu STAFF.

Route guard toàn cục (`router/index.js`, bước 3) kiểm tra:

```js
if (to.meta.roles && auth.isLoggedIn && !to.meta.roles.some((r) => auth.roles.includes(r))) {
  return roleHome(auth.roles)
}
```

ADMIN không nằm trong `meta.roles` của route đích (do lỗi 1 và/hoặc do bị điều
hướng nhầm sang route khu STAFF do lỗi 2) → bị đá thẳng về `roleHome()`, với
ADMIN chính là trang `dashboard`. Đúng như hiện tượng người dùng gặp phải.

### Cách sửa

- **`admin.routes.js`**: sửa lại `meta.roles` của `admin-lesson-new` /
  `admin-lesson-edit` thành `['ADMIN', 'EMPLOYEE']` — khớp với `admin-lesson-list`.
- **`LessonListPage.vue`** và **`LessonFormPage.vue`**: không push cứng tên route
  nữa. Thay vào đó suy ra "đang ở khu nào" dựa vào `route.name` hiện tại
  (`useRoute()`), rồi chọn tên route tương ứng bằng `computed`:

  ```js
  // LessonListPage.vue
  const isAdminArea = computed(() => route.name === 'admin-lesson-list')
  const editRouteName = computed(() => (isAdminArea.value ? 'admin-lesson-edit' : 'lesson-edit'))
  const newRouteName = computed(() => (isAdminArea.value ? 'admin-lesson-new' : 'lesson-new'))
  ```

  ```js
  // LessonFormPage.vue — route.name lúc này là 'admin-lesson-new/edit' hoặc 'lesson-new/edit'
  const isAdminArea = computed(() => route.name?.toString().startsWith('admin-'))
  const listRouteName = computed(() => (isAdminArea.value ? 'admin-lesson-list' : 'lesson-list'))
  const editRouteName = computed(() => (isAdminArea.value ? 'admin-lesson-edit' : 'lesson-edit'))
  ```

  Mọi `router.push`/`router.replace` trong 2 file này (nút "Danh sách", nút "Sửa",
  chuyển trang sau khi Tạo mới thành công) đều dùng biến `computed` này thay vì
  chuỗi tên route viết cứng.

> Ghi chú cho lần sau: khi 1 trang được DÙNG CHUNG cho nhiều khu route (nhiều tên
> route trỏ cùng 1 component), **không được** viết cứng tên route trong
> `router.push`/`router.replace` bên trong trang đó — luôn tính theo `route.name`
> hiện tại như trên. Đây là lỗi dễ tái diễn nếu sau này thêm khu vực thứ 3.

---

## 2. Mở link Canva mới nhất + tự động tải PDF về máy (kèm vá bảo mật)

### 2.1 Vì sao link Canva có thể không phải bản "mới nhất"

`LessonService.addCanvaLink()` (được gọi khi bấm "Thêm" link Canva ở
`LessonFormPage.vue`) **trước đây luôn `INSERT` một `LessonFile` mới**:

```java
LessonFile lf = new LessonFile();
...
lf.setFileType("canva");
...
return LessonFileResponse.fromEntity(lessonFileRepo.save(lf));
```

Nếu người soạn bài **sửa/cập nhật lại** link Canva của 1 bài giảng đã có link từ
trước, hệ thống không hề xoá/thay bản ghi cũ — mà cộng dồn thêm 1 bản ghi
`fileType = canva` mới. Kết quả: 1 bài giảng có thể có **nhiều dòng "canva"** song
song (cũ lẫn mới) trong danh sách tài liệu. Giáo viên xem trang chi tiết bài giảng
thấy nhiều mục trông giống nhau, bấm "Xem" có thể trúng **bản ghi cũ** (link Canva
đã đổi/xoá ở phía Canva) thay vì bản mới nhất vừa cập nhật.

**Sửa**: coi mỗi bài giảng chỉ có **1 link Canva "chính"**. `addCanvaLink()` giờ
tìm file `fileType = canva` hiện có của bài giảng đó trước — nếu có thì **UPDATE**
(`fileName`, `fileUrl`, `updatedAt/updatedBy`) ngay trên bản ghi đó; nếu chưa có
mới `INSERT` mới. Nhờ vậy chỉ tồn tại đúng 1 bản ghi canva/bài giảng, và nó luôn
là bản mới nhất.

### 2.2 Endpoint tải file trước đây là "dead code"

`LessonService.openFile(Integer fileId)` (xử lý redirect Canva / trả file kèm
`Content-Disposition: attachment`) đã được viết sẵn từ trước nhưng **không có
Controller nào gọi tới** — code chết, không dùng được. FE (`TeacherLessonViewPage.vue`,
`LessonFormPage.vue`) phải trỏ thẳng `<a :href="file.fileUrl" target="_blank">`
vào đường dẫn tĩnh `/uploads/lessons/{id}/<uuid>.pdf`.

Cách làm này có 2 vấn đề:

- **Bảo mật**: để `<a>` mở được (không có `Authorization` header), team đã phải mở
  public `GET /uploads/lessons/**` trong `SecurityConfig` (xem dev-note
  `2026-07-09-backend-uploads-public-scope.md`). Nghĩa là **bất kỳ ai có link đều
  tải được tài liệu bài giảng, không cần đăng nhập, không kiểm tra
  `LESSON_VIEW`/trạng thái PUBLISHED**.
- **Trải nghiệm**: `<a target="_blank">` trỏ vào file tĩnh thường MỞ file trong tab
  trình duyệt (PDF viewer nội bộ) thay vì **tự động tải về máy** như yêu cầu.

### 2.3 Sửa: endpoint tải file có xác thực + FE tự tải bằng blob

**Backend**

- `LessonController` — thêm endpoint mới, dùng lại `openFile()` đã có sẵn:

  ```java
  @GetMapping("/{id}/files/{fileId}/download")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('LESSON_VIEW')")
  public ResponseEntity<?> downloadFile(@PathVariable Integer id, @PathVariable Integer fileId) {
      return lessonService.openFile(id, fileId, isTeacherOnly());
  }
  ```

- `LessonService.openFile(...)` đổi chữ ký từ `openFile(Integer fileId)` thành
  `openFile(Integer lessonId, Integer fileId, boolean forcePublished)`:
  - Kiểm tra `file.getLessonId().equals(lessonId)` — chặn dò `fileId` của bài
    giảng khác qua đúng `lessonId` mình được phép xem (IDOR).
  - Nếu người gọi là TEACHER (`forcePublished = true`) mà bài giảng **chưa
    PUBLISHED** → trả `404`, khớp đúng rule đang áp dụng ở `getDetail()`.
  - `fileType = canva` → trả `302 Found` kèm header `Location` = link Canva.
  - Còn lại → trả file kèm `Content-Disposition: attachment` (ép tải về).

- `SecurityConfig` — **gỡ bỏ** `permitAll()` cho `GET /uploads/lessons/**` (không
  cần nữa vì FE không còn gọi trực tiếp đường dẫn tĩnh này). Từ giờ MỌI request
  tới `/uploads/**` đều rơi vào `anyRequest().authenticated()`, tức bắt buộc có
  JWT hợp lệ. Đây là bước ĐÓNG lại lỗ hổng đã nói ở mục 2.2.

**Frontend**

- `api/lessons.js` — thêm hàm gọi endpoint tải file, dùng `responseType: 'blob'`:

  ```js
  downloadFile(lessonId, fileId) {
    return http.get(`/lessons/${lessonId}/files/${fileId}/download`, { responseType: 'blob' })
  }
  ```

  Gọi qua `http` (axios instance có interceptor tự đính `Authorization: Bearer`),
  nên request này CÓ gửi token — khác hẳn thẻ `<a>` cũ.

- `TeacherLessonViewPage.vue` — thay `<a :href="file.fileUrl" target="_blank">`
  bằng nút gọi hàm `openFile(file)`:

  ```js
  async function openFile(file) {
    if (file.fileType === 'canva') {
      // fileUrl của file canva chính LÀ link Canva thật (không phải file vật lý
      // trên server) -> chỉ cần mở tab mới, không cần gọi API tải blob.
      window.open(file.fileUrl, '_blank', 'noopener')
      return
    }
    // Các loại file vật lý (pdf/png/jpg...) -> tải dạng blob rồi tự bấm "download"
    const response = await lessonApi.downloadFile(lesson.value.id, file.id)
    const blobUrl = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = file.fileName || 'tai-lieu'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(blobUrl)
  }
  ```

  Có thêm `downloadingFileId` + `downloadError` để disable nút và báo lỗi khi
  tải thất bại (file lớn / mất mạng / hết hạn token).

### Vì sao Canva không đi qua API tải blob

`fileUrl` của 1 file `canva` **không phải đường dẫn tới file trên server** — nó
chính là URL Canva thật (`https://www.canva.com/design/...`) được lưu nguyên văn
lúc gọi `POST /lessons/{id}/canva`. Vì vậy chỉ cần `window.open()` thẳng URL đó ở
tab mới là đủ, không cần gọi backend. Endpoint `/download` vẫn xử lý được case
canva (trả `302 Location`) để hỗ trợ khi có nơi khác dùng link `/download` trực
tiếp (vd. chia sẻ link), nhưng FE trong SPA không cần dùng nhánh đó.

---

## 3. Việc CHƯA làm / cần lưu ý cho các bạn khác

- Không đổi schema DB — không có migration mới, chỉ đổi hành vi service/route/FE.
- Chưa thêm nút "Tải về" cho các file PDF trong `LessonFormPage.vue` (màn ADMIN
  soạn bài) — màn đó hiện chỉ hiện tên file (không có link nào cho non-canva).
  Có thể tái sử dụng đúng `lessonApi.downloadFile()` + đoạn code blob ở trên nếu
  cần thêm sau này.
- `openFile()`/`downloadFile` dùng chung quyền `LESSON_VIEW` như xem chi tiết —
  chưa có audit log riêng cho hành vi tải file (nếu thầy cô cần biết ai tải file
  nào, phải làm thêm ở đợt khác).
