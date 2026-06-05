<script setup>
// ============================================================================
// ⭐ App.vue — NƠI CHỌN "KHUNG" (layout) BỌC NGOÀI MỖI TRANG
//
// Một trang hiển thị qua 4 mắt xích (đi xuyên nhiều file — nên giải thích ở đây
// vì App.vue là chỗ điều phối):
//   1) router/index.js : URL khớp ra trang + dán "nhãn" meta.layout (vd 'admin')
//   2) <RouterView/>    : chỗ Vue đổ component-trang đã khớp URL vào
//   3) App.vue (file này): đọc nhãn meta.layout → chọn đúng khung layout
//   4) layout (.vue)    : có sidebar/header..., chừa <slot/> để nhét trang vào giữa
//
// => Thêm trang admin mới CHỈ cần: tạo file trong pages/ + thêm route với
//    meta.layout:'admin'. Không phải sửa file này.
// ============================================================================
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import BlankLayout from '@/layouts/BlankLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'

const route = useRoute() // route đang đứng → đọc được route.meta.layout

// Bảng tra: chữ trong meta.layout  →  component layout tương ứng.
const layouts = { default: DefaultLayout, blank: BlankLayout, admin: AdminLayout }

// computed = tự tính lại mỗi khi route đổi. Lấy layout theo nhãn; không có thì
// mặc định DefaultLayout (nhờ '|| DefaultLayout').
const layout = computed(() => layouts[route.meta.layout] || DefaultLayout)
</script>

<template>
  <!-- <component :is="x"> = thẻ "biến hình" thành đúng component trong biến x.
       Nhờ vậy cùng 1 chỗ lúc là AdminLayout, lúc là DefaultLayout.
       <RouterView/> (trang khớp URL) trở thành nội dung con, lọt vào <slot/> của layout. -->
  <component :is="layout">
    <RouterView />
  </component>
</template>
