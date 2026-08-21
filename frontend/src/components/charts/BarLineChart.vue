<script setup>
/**
 * Biểu đồ cột + đường, hai trục dọc (Chart.js).
 * Cột = số buổi dạy (trục trái), đường = chi phí lương (trục phải).
 */
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import { mauTheme, soVN } from '@/utils/chart'

const props = defineProps({
  nhan: { type: Array, required: true }, // nhãn trục X
  cot: { type: Array, required: true },
  duong: { type: Array, required: true },
  tenCot: { type: String, default: 'Cột' },
  tenDuong: { type: String, default: 'Đường' },
})

const MAU_COT = '#f97316'
const MAU_DUONG = '#0ea5e9'

const data = computed(() => ({
  labels: props.nhan,
  datasets: [
    {
      label: props.tenCot,
      data: props.cot,
      backgroundColor: MAU_COT,
      borderRadius: 3,
      yAxisID: 'y',
      order: 2,
    },
    {
      type: 'line',
      label: props.tenDuong,
      data: props.duong,
      borderColor: MAU_DUONG,
      backgroundColor: MAU_DUONG,
      borderWidth: 2,
      pointRadius: 3,
      yAxisID: 'y2',
      order: 1,
    },
  ],
}))

const options = computed(() => {
  const mau = mauTheme()
  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { labels: { color: mau.chu, usePointStyle: true, boxWidth: 8 } },
      tooltip: {
        callbacks: {
          // Trục phải là tiền nên phải kèm đơn vị, không thì hai dòng tooltip nhìn như nhau
          label: (c) =>
            c.dataset.yAxisID === 'y2'
              ? `${c.dataset.label}: ${soVN(c.parsed.y)} đ`
              : `${c.dataset.label}: ${soVN(c.parsed.y)}`,
        },
      },
    },
    scales: {
      x: { ticks: { color: mau.chu }, grid: { display: false } },
      y: {
        beginAtZero: true,
        position: 'left',
        ticks: { color: MAU_COT, callback: soVN },
        grid: { color: mau.luoi },
      },
      y2: {
        beginAtZero: true,
        position: 'right',
        ticks: { color: MAU_DUONG, callback: (v) => soVN(v / 1e6) + ' tr' },
        grid: { drawOnChartArea: false }, // hai lưới chồng nhau thì rối, chỉ giữ lưới trục trái
      },
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
  height: 300px;
}
</style>
