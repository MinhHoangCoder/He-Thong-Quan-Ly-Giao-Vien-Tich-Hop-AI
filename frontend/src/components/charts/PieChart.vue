<script setup>
/** Biểu đồ tròn khuyết (Chart.js) — cơ cấu buổi dạy theo nhóm môn. */
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { MAU_BIEU_DO, mauTheme, soVN } from '@/utils/chart'

const props = defineProps({
  nhan: { type: Array, required: true },
  giaTri: { type: Array, required: true },
})

const tong = computed(() => props.giaTri.reduce((s, v) => s + v, 0))

const data = computed(() => ({
  labels: props.nhan,
  datasets: [
    {
      data: props.giaTri,
      backgroundColor: props.nhan.map((_, i) => MAU_BIEU_DO[i % MAU_BIEU_DO.length]),
      borderWidth: 0,
    },
  ],
}))

const options = computed(() => {
  const mau = mauTheme()
  return {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '58%',
    plugins: {
      legend: {
        position: 'right',
        labels: { color: mau.chu, usePointStyle: true, boxWidth: 8, padding: 12 },
      },
      tooltip: {
        callbacks: {
          // Chỉ hiện số tuyệt đối thì người xem phải tự nhẩm tỉ lệ, mà đó mới là thứ
          // biểu đồ tròn dùng để trả lời
          label: (c) => {
            const pt = tong.value ? ((c.parsed / tong.value) * 100).toFixed(1) : '0'
            return ` ${c.label}: ${soVN(c.parsed)} buổi (${pt}%)`
          },
        },
      },
    },
  }
})
</script>

<template>
  <div class="chart-box">
    <Doughnut :data="data" :options="options" />
  </div>
</template>

<style scoped>
.chart-box {
  position: relative;
  height: 260px;
}
</style>
