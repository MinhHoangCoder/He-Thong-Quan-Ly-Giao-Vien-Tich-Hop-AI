<script setup>
/**
 * MỘT page cho mọi portal — Staff / Admin / Teacher.
 *
 * Route gắn meta.evaluationPortal:
 *   - admin    → EvaluationWorkbench (CRUD + KPI), URL /admin/evaluations
 *   - teacher  → view read-only "đánh giá của tôi", URL /teacher/evaluations
 *
 * Từ Flyway V33 chỉ còn hai tác nhân nên portal 'staff' (URL /staff/…, StaffLayout) đã bị
 * gỡ cùng các role phòng ban.
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import EvaluationWorkbench from '@/components/evaluation/EvaluationWorkbench.vue'
import TeacherEvaluationView from '@/components/evaluation/TeacherEvaluationView.vue'

const route = useRoute()

/** staff | admin | teacher — fallback staff */
// Chỉ còn hỏi ĐÚNG MỘT câu: đây có phải góc nhìn của giáo viên không. Trước đây biến `portal`
// nhận thêm giá trị 'staff' và rơi về đó khi route quên khai meta — mà portal ấy đã bị gỡ ở
// V33, tức là một nhánh chết chờ người thêm route mới là nổ. Nay mặc định là góc nhìn quản
// trị, và không có giá trị nào khác để rơi nhầm vào.
const isTeacher = computed(() => route.meta.evaluationPortal === 'teacher')
</script>

<template>
  <TeacherEvaluationView v-if="isTeacher" />
  <EvaluationWorkbench v-else title="Đánh giá giáo viên" />
</template>
