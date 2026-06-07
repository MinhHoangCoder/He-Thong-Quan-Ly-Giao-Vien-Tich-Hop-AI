<script setup>
// Bộ icon SVG inline dùng chung (không cần thư viện icon ngoài).
// Dùng: <SvgIcon name="teacher" /> — màu kế thừa currentColor.
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 20 },
})

// Mỗi icon là một (hoặc nhiều) <path> theo viewBox 0 0 24 24, stroke style.
const paths = {
  dashboard: ['M3 13h8V3H3v10Z', 'M13 21h8V11h-8v10Z', 'M13 3v6h8V3h-8Z', 'M3 21h8v-6H3v6Z'],
  teacher: ['M16 11a4 4 0 1 0-8 0', 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z', 'M3 21a9 9 0 0 1 18 0'],
  school: ['M3 21h18', 'M5 21V8l7-4 7 4v13', 'M9 21v-6h6v6', 'M9 11h.01', 'M15 11h.01'],
  subject: [
    'M4 19.5A2.5 2.5 0 0 1 6.5 17H20',
    'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z',
  ],
  assignment: [
    'M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2',
    'M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2',
    'M9 12h6',
    'M9 16h6',
  ],
  schedule: [
    'M8 2v4',
    'M16 2v4',
    'M3 10h18',
    'M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z',
  ],
  attendance: ['M9 11l3 3L22 4', 'M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11'],
  payroll: [
    'M19 5H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2Z',
    'M16 12h.01',
    'M3 10h18',
  ],
  evaluation: ['M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17l-6 3.6 1.4-6.8L2.3 9.1l6.9-.8L12 2Z'],
  ai: [
    'M12 2a3 3 0 0 0-3 3v1H7a3 3 0 0 0-3 3v2a3 3 0 0 0 0 6v1a3 3 0 0 0 3 3h2v1a3 3 0 0 0 6 0v-1h2a3 3 0 0 0 3-3v-1a3 3 0 0 0 0-6V9a3 3 0 0 0-3-3h-2V5a3 3 0 0 0-3-3Z',
    'M9 12h.01',
    'M15 12h.01',
  ],
  bell: ['M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9', 'M10.3 21a1.94 1.94 0 0 0 3.4 0'],
  mail: [
    'M22 7l-10 6L2 7',
    'M2 6h20v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2Z',
  ],
  search: ['M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z', 'M21 21l-4.3-4.3'],
  menu: ['M3 6h18', 'M3 12h18', 'M3 18h18'],
  chevron: ['M6 9l6 6 6-6'],
  logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'M16 17l5-5-5-5', 'M21 12H9'],
  eye: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z'],
  'eye-off': [
    'M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a13.2 13.2 0 0 1-1.67 2.68',
    'M6.61 6.61A13.5 13.5 0 0 0 2 12s3.5 7 10 7a9.7 9.7 0 0 0 5.39-1.61',
    'M14.12 14.12a3 3 0 1 1-4.24-4.24',
    'M2 2l20 20',
  ],
  up: ['M12 19V5', 'M5 12l7-7 7 7'],
  down: ['M12 5v14', 'M5 12l7 7 7-7'],
  clock: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z', 'M12 6v6l4 2'],
  plus: ['M12 5v14', 'M5 12h14'],
  settings: [
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
    'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z',
  ],
}

const d = computed(() => paths[props.name] || [])
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.9"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(p, i) in d" :key="i" :d="p" />
  </svg>
</template>
