<script setup>
/**
 * Trang Trợ lý AI — giao diện hội thoại. Trợ lý CHẠY CỤC BỘ (theo luật), có thể truy
 * vấn dữ liệu thật của hệ thống (số phân công, tổng giờ dạy tháng, tổng lương) để trả
 * lời. Đây là nền để sau này nối vào Claude API / module ai/ ở backend.
 */
import { ref, nextTick, onMounted } from 'vue'
import { assignmentApi } from '@/api/assignments'
import { attendanceApi } from '@/api/attendance'
import { payrollApi } from '@/api/payroll'

const messages = ref([
  {
    role: 'ai',
    text: 'Xin chào 👋 Mình là trợ lý KDC EduOps AI. Hỏi mình về phân công, chấm công, bảng lương hoặc cách dùng hệ thống nhé.',
  },
])
const input = ref('')
const busy = ref(false)
const scroller = ref(null)

const SUGGESTIONS = [
  'Có bao nhiêu phân công đang chạy?',
  'Tổng giờ dạy tháng này?',
  'Tổng lương tháng này?',
  'Cách tạo phân công?',
  'Chấm công hoạt động thế nào?',
]

function scrollDown() {
  nextTick(() => {
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  })
}

async function reply(question) {
  const q = question.toLowerCase()

  // ── Câu hỏi dữ liệu: gọi API thật ──
  try {
    if (q.includes('phân công')) {
      if (q.includes('cách') || q.includes('tạo') || q.includes('làm sao')) {
        return 'Để tạo phân công: vào **Điều phối → Phân công → “Tạo phân công”**, chọn giáo viên · môn · trường · lớp, đặt khoảng thời gian và thêm các tiết (Thứ + Tiết). Hệ thống sẽ tự sinh các buổi dạy hằng tuần.'
      }
      const { data } = await assignmentApi.list()
      const active = data.filter((a) => a.status === 'ACTIVE').length
      return `Hiện có **${data.length} phân công** trong hệ thống, trong đó **${active} đang chạy**.`
    }
    if (
      q.includes('giờ dạy') ||
      (q.includes('chấm công') && (q.includes('tổng') || q.includes('bao nhiêu')))
    ) {
      const { data } = await attendanceApi.list()
      const hours = data.reduce((s, r) => s + Number(r.hours || 0), 0)
      return `Tháng này ghi nhận **${data.length} buổi chấm công**, tổng **${hours.toFixed(2)} giờ dạy**.`
    }
    if (q.includes('chấm công')) {
      return 'Chấm công: vào **Điều phối → Chấm công**. Bấm **“Sinh từ lịch dạy”** để tạo dòng chấm công từ các buổi đã duyệt, sau đó chỉnh giờ vào/ra và trạng thái từng dòng. Số giờ dạy tự tính và dùng cho bảng lương.'
    }
    if (q.includes('lương')) {
      if (q.includes('cách') || (q.includes('tính') && q.includes('thế nào'))) {
        return 'Lương = **giờ dạy × đơn giá** + phụ cấp + thưởng − khấu trừ. Vào **Điều phối → Bảng lương**, chọn kỳ và bấm **“Tính lương từ chấm công”**.'
      }
      const now = new Date()
      const { data } = await payrollApi.list(now.getFullYear(), now.getMonth() + 1)
      const total = data.reduce((s, r) => s + Number(r.netAmount || 0), 0)
      const fmt = new Intl.NumberFormat('vi-VN').format(total)
      return `Bảng lương tháng ${now.getMonth() + 1}/${now.getFullYear()}: **${data.length} giáo viên**, tổng thực nhận **${fmt} ₫**.`
    }
  } catch (e) {
    return (
      'Mình chưa lấy được dữ liệu (' +
      (e.response?.data?.message ?? e.message) +
      '). Bạn kiểm tra lại quyền hoặc kết nối nhé.'
    )
  }

  if (q.includes('giáo viên') || q.includes('gv')) {
    return 'Quản lý giáo viên ở mục **Nhân sự → Giáo viên**: xem hồ sơ, thêm/sửa và theo dõi trạng thái.'
  }
  if (q.includes('xin chào') || q.includes('hello') || q.includes('hi')) {
    return 'Chào bạn! Mình có thể giúp tra nhanh số liệu phân công, chấm công, lương hoặc hướng dẫn thao tác.'
  }
  return 'Mình chưa chắc câu này 🤔. Thử hỏi về **phân công**, **chấm công**, **bảng lương** hoặc **giáo viên** — hoặc bấm một gợi ý bên dưới nhé.'
}

async function send(text) {
  const content = (text ?? input.value).trim()
  if (!content || busy.value) return
  messages.value.push({ role: 'user', text: content })
  input.value = ''
  busy.value = true
  scrollDown()

  const answer = await reply(content)
  // Giả lập độ trễ nhẹ cho tự nhiên
  await new Promise((r) => setTimeout(r, 350))
  messages.value.push({ role: 'ai', text: answer })
  busy.value = false
  scrollDown()
}

/** Render **đậm** đơn giản → <strong>. */
function fmt(t) {
  return t.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
}

onMounted(scrollDown)
</script>

<template>
  <div class="page ai-page">
    <div class="page-head">
      <div>
        <h2 class="title">Trợ lý AI</h2>
        <p class="subtitle">
          Hỏi đáp nhanh về vận hành trung tâm — tra số liệu và hướng dẫn thao tác.
        </p>
      </div>
    </div>

    <div class="chat card">
      <div ref="scroller" class="chat-body">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble" v-html="fmt(m.text)" />
        </div>
        <div v-if="busy" class="msg ai">
          <div class="bubble typing"><span></span><span></span><span></span></div>
        </div>
      </div>

      <div class="suggestions">
        <button v-for="s in SUGGESTIONS" :key="s" class="chip" @click="send(s)">{{ s }}</button>
      </div>

      <form class="chat-input" @submit.prevent="send()">
        <input v-model="input" placeholder="Nhập câu hỏi…" :disabled="busy" />
        <button class="btn btn-primary" type="submit" :disabled="busy || !input.trim()">Gửi</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  max-width: 860px;
}
.chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 210px);
  min-height: 440px;
  overflow: hidden;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 1.2rem;
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  background: var(--c-bg);
}
.msg {
  display: flex;
}
.msg.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 76%;
  padding: 0.65rem 0.95rem;
  border-radius: 14px;
  font-size: 0.9rem;
  line-height: 1.5;
  box-shadow: 0 2px 6px rgba(15, 40, 80, 0.06);
}
.msg.ai .bubble {
  background: #fff;
  border: 1px solid var(--c-border);
  border-bottom-left-radius: 4px;
}
.msg.user .bubble {
  background: var(--grad-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.typing {
  display: flex;
  gap: 4px;
}
.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cbd5e1;
  animation: blink 1.2s infinite both;
}
.typing span:nth-child(2) {
  animation-delay: 0.2s;
}
.typing span:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
  }
  40% {
    opacity: 1;
  }
}
.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  padding: 0.7rem 1rem;
  border-top: 1px solid var(--c-border);
  background: #fff;
}
.chip {
  border: 1px solid var(--c-border);
  background: #fff;
  color: var(--c-text);
  border-radius: 9999px;
  padding: 0.35rem 0.8rem;
  font-size: 0.8rem;
  cursor: pointer;
  transition: 0.15s;
}
.chip:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.chat-input {
  display: flex;
  gap: 0.6rem;
  padding: 0.85rem 1rem;
  border-top: 1px solid var(--c-border);
  background: #fff;
}
.chat-input input {
  flex: 1;
  padding: 0.6rem 0.9rem;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  font-size: 0.9rem;
}
.chat-input input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}
</style>
