<script setup>
/**
 * Biểu đồ thanh ngang (Chart.js) — xếp hạng trường theo số buổi dạy.
 * Để ngang vì nhãn là tên trường ("THCS Trần Thành Ngọ"), cột đứng sẽ phải xoay chữ.
 */
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import { mauTheme, soVN } from '@/utils/chart'

const props = defineProps({
  nhan: { type: Array, required: true },
  giaTri: { type: Array, required: true },
})

const data = computed(() => ({
  labels: props.nhan,
  datasets: [
    {
      label: 'Số buổi',
      data: props.giaTri,
      backgroundColor: '#0ea5e9',
      borderRadius: 3,
      barThickness: 14,
    },
  ],
}))

const options = computed(() => {
  const mau = mauTheme()
  return {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: { label: (c) => ` ${soVN(c.parsed.x)} buổi` } },
    },
    scales: {
      x: {
        beginAtZero: true,
        ticks: { color: mau.chu, callback: soVN },
        grid: { color: mau.luoi },
      },
      y: { ticks: { color: mau.chu, font: { size: 11 } }, grid: { display: false } },
    },
  }
})
</script>

<template>
  <div class="chart-box">
    <Bar :data="data" :options="options" />
  </div>
</template>

<style scoped>
.chart-box {
  position: relative;
  height: 330px;
}
</style>
