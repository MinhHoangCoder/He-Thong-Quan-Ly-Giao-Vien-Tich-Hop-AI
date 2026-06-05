<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import BlankLayout from '@/layouts/BlankLayout.vue'

// Chọn layout theo meta.layout của route (mặc định: DefaultLayout)
const route = useRoute()
const layouts = { default: DefaultLayout, blank: BlankLayout }
const layout = computed(() => layouts[route.meta.layout] || DefaultLayout)
</script>

<template>
  <component :is="layout">
    <RouterView />
  </component>
</template>
