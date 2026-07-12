<script setup>
/**
 * MỘT page cho mọi portal — Staff / Admin / School / Teacher.
 *
 * Route gắn meta.evaluationPortal:
 *   - staff | admin | school  → EvaluationWorkbench (CRUD + KPI)
 *   - teacher                 → view read-only "đánh giá của tôi"
 *
 * Layout/sidebar vẫn tách theo portal (AdminLayout, StaffLayout…);
 * chỉ gộp file page, không gộp URL (vẫn /staff/… /school/… /teacher/…).
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import EvaluationWorkbench from '@/components/evaluation/EvaluationWorkbench.vue'
import TeacherEvaluationView from '@/components/evaluation/TeacherEvaluationView.vue'

const route = useRoute()

/** staff | admin | school | teacher — fallback staff */
const portal = computed(() => route.meta.evaluationPortal || 'staff')
const isTeacher = computed(() => portal.value === 'teacher')

const copy = computed(() => {
  switch (portal.value) {
    case 'admin':
      return {
        title: 'Đánh giá giáo viên',
        subtitle: 'Quản trị toàn hệ thống — chấm điểm, lọc theo nguồn & kỳ',
      }
    case 'school':
      return {
        title: 'Đánh giá giáo viên',
        subtitle: 'Chấm điểm giáo viên đang / đã giảng dạy tại trường bạn',
      }
    case 'staff':
    default:
      return {
        title: 'Đánh giá giáo viên',
        subtitle: 'Theo dõi & chấm điểm chất lượng giảng dạy theo kỳ',
      }
  }
})
</script>

<template>
  <TeacherEvaluationView v-if="isTeacher" />
  <EvaluationWorkbench
    v-else
    :portal="portal"
    :title="copy.title"
    :subtitle="copy.subtitle"
  />
</template>
