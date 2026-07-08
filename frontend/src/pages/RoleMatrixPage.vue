<!-- src/pages/RoleMatrixPage.vue -->
<script setup>
/**
 * Trang MA TRẬN PHÂN QUYỀN (chỉ đọc): hàng = permission, cột = role, ô ✓ = role có quyền.
 * Dữ liệu lấy thẳng từ bảng Role/Permission/RolePermission qua GET /admin/roles.
 * KHÔNG có nút sửa: ma trận quyền là "luật" của hệ thống, chỉ thay đổi qua migration
 * (đã seed idempotent) — tránh môi trường mỗi máy một kiểu quyền.
 */
import { computed, onMounted, ref } from 'vue'
import { adminUsersApi } from '@/api/adminUsers'

const loading = ref(true)
const error = ref('')
const roles = ref([]) // [{ id, name, description, permissions: [{code, description}] }]

// Gom mọi permission xuất hiện ở ít nhất 1 role -> danh sách HÀNG của ma trận.
const allPerms = computed(() => {
  const map = new Map()
  for (const r of roles.value) {
    for (const p of r.permissions) {
      if (!map.has(p.code)) map.set(p.code, p.description)
    }
  }
  return [...map.entries()]
    .map(([code, description]) => ({ code, description }))
    .sort((a, b) => a.code.localeCompare(b.code))
})

// Tra nhanh "role X có quyền Y?" bằng Set thay vì quét mảng từng ô.
const permSetByRole = computed(() => {
  const m = new Map()
  for (const r of roles.value) m.set(r.name, new Set(r.permissions.map((p) => p.code)))
  return m
})

const has = (roleName, code) => permSetByRole.value.get(roleName)?.has(code)

onMounted(async () => {
  try {
    const { data } = await adminUsersApi.roles()
    roles.value = data
  } catch (e) {
    error.value = e.response?.data?.message ?? 'Không tải được dữ liệu phân quyền'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="rm-page">
    <div class="rm-header">
      <h2 class="rm-title">Ma trận phân quyền</h2>
      <p class="rm-sub">
        Mỗi cột là một vai trò, mỗi hàng là một quyền. Bảng CHỈ ĐỌC — thay đổi ma trận được thực
        hiện qua migration cơ sở dữ liệu để mọi môi trường giống hệt nhau. ADMIN không cần liệt kê:
        vai trò này đi tắt mọi quyền.
      </p>
    </div>

    <p v-if="loading" class="text-muted">Đang tải...</p>
    <p v-else-if="error" class="msg-err">{{ error }}</p>

    <div v-else class="rm-table-wrap">
      <table class="rm-table">
        <thead>
          <tr>
            <th class="perm-col">Quyền</th>
            <th v-for="r in roles" :key="r.id" :title="r.description">{{ r.name }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in allPerms" :key="p.code">
            <td class="perm-col">
              <code>{{ p.code }}</code>
              <div class="text-muted small">{{ p.description }}</div>
            </td>
            <td v-for="r in roles" :key="r.id" class="cell">
              <span v-if="has(r.name, p.code)" class="tick">✓</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.rm-page {
  padding: 1.5rem;
  max-width: 1100px;
}
.rm-header {
  margin-bottom: 1rem;
}
.rm-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0;
}
.rm-sub {
  margin: 4px 0 0;
  font-size: 0.85rem;
  color: var(--c-text-muted);
  max-width: 760px;
}
.rm-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
}
.rm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}
.rm-table th {
  background: var(--c-surface-2);
  padding: 0.55rem 0.8rem;
  text-align: center;
  font-weight: 600;
  border-bottom: 1px solid var(--c-border);
  white-space: nowrap;
}
.rm-table td {
  padding: 0.45rem 0.8rem;
  border-bottom: 1px solid var(--c-border-soft);
}
.rm-table tbody tr:last-child td {
  border-bottom: none;
}
.rm-table tbody tr:hover td {
  background: var(--c-surface-2);
}
.perm-col {
  text-align: left !important;
  min-width: 260px;
}
.cell {
  text-align: center;
}
.tick {
  color: #16a34a;
  font-weight: 700;
}
:root[data-theme='dark'] .tick {
  color: #4ade80;
}
/* Chip mã quyền: nền phải theo token — nền sáng cứng + chữ token = trắng trên trắng ở dark */
code {
  background: var(--c-surface-2);
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-family: monospace;
}
.text-muted {
  color: var(--c-text-muted);
}
.small {
  font-size: 0.78rem;
}
.msg-err {
  color: var(--c-danger);
  font-size: 0.85rem;
}
</style>
