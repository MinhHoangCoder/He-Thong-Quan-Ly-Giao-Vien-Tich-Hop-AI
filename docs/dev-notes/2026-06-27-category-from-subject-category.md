# Dev Note — Chuyển nguồn Danh mục sang bảng `SubjectCategory`

**Ngày:** 2026-06-27  
**Module:** Kho bài giảng (QLBG)  
**Branch gợi ý:** `feat/QLBG/category-from-lookup`

---

## 1. Vấn đề

Dropdown **Danh mục** ở cả trang danh sách (`LessonListPage.vue`) lẫn form thêm/sửa (`LessonFormPage.vue`) đang lấy dữ liệu từ endpoint `/lessons/subjects`, sau đó tự tính `Set<string>` từ field `category` của từng Subject trả về.

| Điểm yếu | Mô tả |
|---|---|
| Dữ liệu phụ thuộc Subject | Nếu chưa có Subject nào, dropdown danh mục rỗng — dù `SubjectCategory` đã seed đầy đủ 4 nhóm |
| Logic trùng lặp | Cả 2 trang đều viết cùng đoạn `computed` `Set` |
| Không phản ánh đúng master data | `SubjectCategory` là bảng lookup chính thức (V8), việc đọc qua Subject chỉ là side-effect |

---

## 2. Giải pháp

Dùng thẳng `GET /api/v1/subject-categories/active` — endpoint đã có sẵn từ `SubjectCategoryController`, trả về `List<SubjectCategoryResponse>` chỉ gồm các nhóm `ACTIVE`.

Không thay đổi backend hay logic lọc (backend vẫn nhận `category` là **String name** — khớp với `SubjectCategory.name`).

---

## 3. Files thay đổi

### 3.1 `frontend/src/pages/LessonListPage.vue`

| | Trước | Sau |
|---|---|---|
| Import | `import { lessonApi }` | thêm `import { subjectCategoryApi }` |
| State | `subjects = ref([])` + `categories = computed(...)` | `categories = ref([])` |
| `loadMeta()` | load `lessonApi.subjects()` + `lessonApi.gradeLevels()` | load `subjectCategoryApi.listActive()` + `lessonApi.gradeLevels()` |
| Template `<option>` | `:key="cat" :value="cat"` | `:key="cat.id" :value="cat.name"` |

**Diff chính:**

```diff
- import { ref, reactive, computed, onMounted } from 'vue'
+ import { ref, reactive, onMounted } from 'vue'
  import { lessonApi } from '@/api/lessons'
+ import { subjectCategoryApi } from '@/api/subjectCategories'

- const subjects = ref([])
- const categories = computed(() => {
-   const set = new Set()
-   subjects.value.forEach((s) => { if (s.category) set.add(s.category) })
-   return Array.from(set).sort()
- })
+ const categories = ref([])  // [{ id, code, name, ... }]

  async function loadMeta() {
-   const [sub, grade] = await Promise.all([lessonApi.subjects(), lessonApi.gradeLevels()])
-   subjects.value = sub.data
+   const [cats, grade] = await Promise.all([subjectCategoryApi.listActive(), lessonApi.gradeLevels()])
+   categories.value = cats.data
    gradeLevels.value = grade.data
  }

- <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
+ <option v-for="cat in categories" :key="cat.id" :value="cat.name">{{ cat.name }}</option>
```

---

### 3.2 `frontend/src/pages/LessonFormPage.vue`

| | Trước | Sau |
|---|---|---|
| Import | `import { lessonApi }, { branchApi }` | thêm `import { subjectCategoryApi }` |
| State | `categories = computed(...)` từ subjects | `categories = ref([])` |
| `loadMeta()` | 3 `try/catch` riêng biệt | 1 `Promise.all` duy nhất gom 4 call |
| Template `<option>` | `:key="cat" :value="cat"` | `:key="cat.id" :value="cat.name"` |

**Diff chính:**

```diff
  import { lessonApi } from '@/api/lessons'
  import { branchApi } from '@/api/branches'
+ import { subjectCategoryApi } from '@/api/subjectCategories'

- const categories = computed(() => {
-   const set = new Set()
-   subjects.value.forEach((s) => { if (s.category) set.add(s.category) })
-   return Array.from(set).sort()
- })
+ const categories = ref([])  // [{ id, code, name, ... }]

  async function loadMeta() {
-   // 3 try/catch riêng lẻ
+   const [catsRes, subRes, gradeRes, brRes] = await Promise.all([
+     subjectCategoryApi.listActive(),
+     lessonApi.subjects(),
+     lessonApi.gradeLevels(),
+     branchApi.list(),
+   ])
+   categories.value = catsRes.data
+   subjects.value = subRes.data
+   gradeLevels.value = gradeRes.data
+   branches.value = brRes.data
  }

- <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
+ <option v-for="cat in categories" :key="cat.id" :value="cat.name">{{ cat.name }}</option>
```

---

## 4. Lý do KHÔNG thay đổi backend

| Thành phần | Lý do giữ nguyên |
|---|---|
| `GET /lessons/categories` | Không còn dùng ở frontend nhưng không gây hại; có thể giữ cho backward compat hoặc xóa sau |
| `LessonRepository.search(category, ...)` | Vẫn filter theo `s.category.name = :category` — hoạt động đúng vì frontend vẫn truyền `cat.name` làm value |
| `LessonController`, `LessonService` | Không cần sửa gì |

> **Key insight:** Backend nhận `category` param là **String name** (vd: `"Kĩ năng sống"`).  
> Frontend truyền `cat.name` từ `SubjectCategoryResponse` → hoàn toàn khớp nhau.

---

## 5. Luồng dữ liệu sau thay đổi

```
SubjectCategory table (DB)
       │
       ▼
GET /api/v1/subject-categories/active
       │  returns: [{ id, code, name:"Kĩ năng sống", status:"ACTIVE", ... }]
       │
       ▼
categories = ref([{ id:4, code:"KY_NANG_SONG", name:"Kĩ năng sống" }, ...])
       │
       ▼
<select> dropdown  →  user chọn  →  filter.category = "Kĩ năng sống"
       │
       ▼
GET /api/v1/lessons?category=Kĩ+năng+sống
       │
       ▼
LessonRepository: WHERE s.category.name = 'Kĩ năng sống'   ✅
```

---

## 6. Điểm cần chú ý

- **Edit mode (`LessonFormPage`):** `selectedCategory.value = data.category` (String name từ API) vẫn hoạt động bình thường vì `filteredSubjects` so sánh `s.category === selectedCategory.value` — cả hai đều là String name.
- **Thứ tự hiển thị:** `listActive()` trả về theo thứ tự INSERT (seed V8). Nếu muốn sort A→Z, thêm `?sort=name` hoặc sort ở `SubjectCategoryService.listActive()`.
- **Tương lai V9:** Khi drop cột `Category` text cũ xong (task đã ghi note), không cần thay gì thêm ở module này vì đã dùng FK từ V8.

---

## 7. Test checklist

- [ ] Dropdown danh mục ở **LessonListPage** hiển thị đúng các nhóm từ `SubjectCategory` (Tin học, Tiếng Anh, STEM - AI, Kĩ năng sống)
- [ ] Lọc theo danh mục hoạt động đúng — bài giảng thuộc môn học có CategoryId tương ứng được trả về
- [ ] Dropdown danh mục ở **LessonFormPage** hiển thị đúng
- [ ] Chọn danh mục → dropdown môn học lọc đúng theo danh mục đó
- [ ] Edit bài giảng → danh mục được pre-select đúng theo `data.category` trả về từ API
- [ ] Thêm mới `SubjectCategory` qua trang quản lý → dropdown cập nhật ngay lần tải trang tiếp theo (không cần restart)
