<script setup>
/**
 * Khối "Cần xử lý ngay".
 *
 * HIỂN THỊ CẢ NHỮNG MỤC ĐANG ỔN, không chỉ mục có việc. Một danh sách rỗng thì không phân
 * biệt được "mọi thứ đều tốt" với "phần cảnh báo bị hỏng"; còn thấy dòng "Lịch chờ duyệt · 0"
 * là biết hệ thống có kiểm tra và kết quả là sạch. Mục đã ổn bị đẩy xuống cuối và làm mờ đi
 * nên vẫn không tranh chỗ với việc thật.
 *
 * Mỗi dòng dẫn thẳng tới trang xử lý — cảnh báo mà không đi kèm đường xử lý thì chỉ là lời
 * than phiền.
 */
import { computed } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'

const props = defineProps({
  canhBao: { type: Array, required: true },
})
const emit = defineEmits(['mo'])

const MUC = {
  khan: { nhan: 'Khẩn', icon: 'bell' },
  luuY: { nhan: 'Lưu ý', icon: 'clock' },
  tin: { nhan: 'Thông tin', icon: 'eye' },
  on: { nhan: 'Ổn', icon: 'check' },
}

const tongViec = computed(() =>
  props.canhBao.filter((c) => c.muc !== 'on').reduce((s, c) => s + c.soLuong, 0),
)
const soMucKhan = computed(() => props.canhBao.filter((c) => c.muc === 'khan').length)
</script>

<template>
  <section class="canh card">
    <header class="canh__head">
      <div>
        <h2 class="canh__title">Cần xử lý ngay</h2>
        <p class="canh__sub">
          <template v-if="tongViec === 0">Không có việc nào đang tồn đọng.</template>
          <template v-else>
            {{ tongViec }} việc đang chờ<template v-if="soMucKhan">
              · {{ soMucKhan }} nhóm ở mức khẩn</template
            >
          </template>
        </p>
      </div>
      <span v-if="tongViec > 0" class="canh__badge">{{ tongViec }}</span>
    </header>

    <ul class="canh__list">
      <li v-for="c in canhBao" :key="c.key" :class="'is-' + c.muc">
        <button type="button" class="canh__item" @click="emit('mo', c.route)">
          <span class="canh__ico"><SvgIcon :name="MUC[c.muc].icon" :size="15" /></span>
          <span class="canh__body">
            <strong>{{ c.nhan }}</strong>
            <small>{{ c.moTa }}</small>
          </span>
          <span class="canh__so">{{ c.soLuong }}</span>
          <SvgIcon name="chevron" :size="14" class="canh__mui" />
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.canh {
  display: flex;
  flex-direction: column;
}
.canh__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.55rem;
}
.canh__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--a-text);
}
.canh__sub {
  margin: 0.15rem 0 0;
  font-size: 0.78rem;
  color: var(--a-text-muted);
}
.canh__badge {
  flex: none;
  min-width: 1.6rem;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  background: rgba(239, 68, 68, 0.14);
  color: #b91c1c;
  font-size: 0.82rem;
  font-weight: 700;
  text-align: center;
}
:root[data-theme='dark'] .canh__badge {
  color: #f87171;
}
.canh__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
/* Mục đã ổn tụt xuống cuối bằng order, không cần sắp xếp lại mảng ở JS. */
.canh__list li.is-on {
  order: 2;
  opacity: 0.55;
}
.canh__item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.5rem 0.55rem;
  border: none;
  border-radius: 10px;
  background: none;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
  transition: background var(--t-fast);
}
.canh__item:hover {
  background: color-mix(in srgb, var(--c-primary) 8%, transparent);
}
.canh__ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  flex: none;
}
.is-khan .canh__ico {
  color: #dc2626;
  background: rgba(239, 68, 68, 0.13);
}
.is-luuY .canh__ico {
  color: #d97706;
  background: rgba(245, 158, 11, 0.15);
}
.is-tin .canh__ico {
  color: #0284c7;
  background: rgba(14, 165, 233, 0.14);
}
.is-on .canh__ico {
  color: #16a34a;
  background: rgba(34, 197, 94, 0.13);
}
.canh__body {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
  flex: 1;
}
.canh__body strong {
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--a-text);
}
.canh__body small {
  font-size: 0.74rem;
  color: var(--a-text-muted);
  line-height: 1.35;
}
.canh__so {
  font-size: 1.05rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--a-text);
}
.is-on .canh__so {
  color: var(--a-text-muted);
}
.canh__mui {
  color: var(--a-text-muted);
  flex: none;
  transform: rotate(-90deg);
}
</style>
